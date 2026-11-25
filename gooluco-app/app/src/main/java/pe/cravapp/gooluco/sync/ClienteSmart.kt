package pe.cravapp.gooluco.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import okhttp3.OkHttpClient
import okhttp3.Request
import pe.cravapp.gooluco.BuildConfig
import pe.cravapp.gooluco.fhir.Fhir
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Cliente SMART App Launch 2.x (standalone, paciente) con AppAuth: descubrimiento SMART, PKCE S256 (AppAuth),
 * parámetro `aud`, canje del código y renovación con rotación del refresh token.
 */
class ClienteSmart(private val ctx: Context, private val almacen: AlmacenSeguro, private val http: OkHttpClient = Http.cliente) {

    @Serializable
    private data class ConfigSmart(val issuer: String? = null, val authorization_endpoint: String, val token_endpoint: String)

    suspend fun configuracion(): AuthorizationServiceConfiguration = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$FHIR_BASE/.well-known/smart-configuration").header("Accept", "application/json").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw ErrorSync("Descubrimiento SMART: HTTP ${r.code}")
            val c = Fhir.json.decodeFromString(ConfigSmart.serializer(), r.body!!.string())
            AuthorizationServiceConfiguration(Uri.parse(c.authorization_endpoint), Uri.parse(c.token_endpoint))
        }
    }

    /** Intent de la Custom Tab con la página de autorización (usuarios de prueba ana / beto). */
    suspend fun intentAutorizacion(servicio: AuthorizationService): Intent {
        val cfg = configuracion()
        val req = AuthorizationRequest.Builder(cfg, CLIENT_ID, ResponseTypeValues.CODE, Uri.parse(REDIRECT))
            .setScope(SCOPES)
            .setAdditionalParameters(mapOf("aud" to FHIR_BASE))
            .build() // AppAuth genera state, nonce y code_verifier/challenge S256
        return servicio.getAuthorizationRequestIntent(req)
    }

    /** Completa el login con el resultado de la Custom Tab. Devuelve el paciente del token. */
    suspend fun completarLogin(servicio: AuthorizationService, data: Intent?): String {
        val resp = data?.let { AuthorizationResponse.fromIntent(it) }
        val ex = data?.let { AuthorizationException.fromIntent(it) }
        if (resp == null) throw ErrorSync(ex?.let { "Autorización cancelada o denegada: ${it.errorDescription ?: it.error ?: it.message}" } ?: "Autorización cancelada.")
        val estado = AuthState(resp, ex)
        val token = suspendCancellableCoroutine { cont ->
            servicio.performTokenRequest(resp.createTokenExchangeRequest()) { t, e ->
                if (t != null) cont.resume(t) else cont.resumeWithException(ErrorSync("Canje del código: ${e?.errorDescription ?: e?.error ?: e?.message}"))
            }
        }
        estado.update(token, null)
        val paciente = token.additionalParameters["patient"] ?: throw ErrorSync("El token no trae contexto de paciente (launch/patient).")
        almacen.guardarAuthState(estado)
        almacen.fijarSesion(paciente, token.scope)
        return paciente
    }

    private val mutex = Mutex()

    /** Access token vigente; renueva con el refresh token (rotación: el nuevo se guarda cifrado). */
    suspend fun tokenVigente(forzarRenovacion: Boolean = false): String = mutex.withLock {
        val estado = almacen.authState() ?: throw ErrorSync("Sin sesión. Conéctate al servidor.", requiereLogin = true)
        if (forzarRenovacion) estado.needsTokenRefresh = true
        val servicio = AuthorizationService(ctx)
        try {
            suspendCancellableCoroutine { cont ->
                estado.performActionWithFreshTokens(servicio) { access, _, ex ->
                    if (access != null) cont.resume(access)
                    else cont.resumeWithException(
                        ErrorSync(
                            "No se pudo renovar la sesión: ${ex?.errorDescription ?: ex?.error ?: ex?.message}",
                            requiereLogin = ex?.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR,
                            transitorio = ex?.type == AuthorizationException.TYPE_GENERAL_ERROR,
                        )
                    )
                }
            }
        } finally {
            almacen.guardarAuthState(estado) // persiste el refresh token rotado (o el error)
            servicio.dispose()
        }
    }

    companion object {
        val API_BASE: String = BuildConfig.API_BASE
        val FHIR_BASE: String = "$API_BASE/fhir"
        const val CLIENT_ID = "gooluco-android"
        const val REDIRECT = "pe.cravapp.gooluco:/oauth2redirect"
        /** Lo que concede el servidor a gooluco-android (src/auth/clients.ts). */
        const val SCOPES = "launch/patient openid fhirUser offline_access patient/Patient.rs patient/Observation.crs patient/QuestionnaireResponse.crs"
    }
}

class ErrorSync(msg: String, val requiereLogin: Boolean = false, val transitorio: Boolean = false) : Exception(msg)

object Http {
    val cliente: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .readTimeout(java.time.Duration.ofSeconds(30))
            .build()
    }
}
