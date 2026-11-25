package pe.cravapp.gooluco.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.openid.appauth.AuthState
import java.util.UUID

/** Estado visible de la sincronización (sin tokens). */
data class EstadoConexion(
    val activa: Boolean = false,
    val conectado: Boolean = false,
    val paciente: String? = null,
    val scopes: String? = null,
    val desdeUtc: Long = 0,
    val ultimaSyncUtc: Long? = null,
    val ultimoResultado: String? = null,
    val ultimoError: String? = null,
)

/**
 * Preferencias cifradas con Android Keystore (EncryptedSharedPreferences, AES-256-GCM).
 * Guarda el AuthState de AppAuth (access + refresh token), el paciente del token y el estado de la sincronización.
 * La copia de seguridad de la app está desactivada, así que los tokens no salen del teléfono.
 */
class AlmacenSeguro private constructor(private val prefs: SharedPreferences) {

    private val _estado = MutableStateFlow(leer())
    val estado: StateFlow<EstadoConexion> = _estado

    @Synchronized
    fun authState(): AuthState? = prefs.getString(K_AUTH, null)?.let { runCatching { AuthState.jsonDeserialize(it) }.getOrNull() }

    @Synchronized
    fun guardarAuthState(a: AuthState?) {
        prefs.edit().apply { if (a == null) remove(K_AUTH) else putString(K_AUTH, a.jsonSerializeString()) }.apply()
        refrescar()
    }

    val idInstalacion: String
        @Synchronized get() = prefs.getString(K_INSTALACION, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(K_INSTALACION, it).apply()
        }

    fun activar(desdeUtc: Long) {
        prefs.edit().putBoolean(K_ACTIVA, true).putLong(K_DESDE, desdeUtc).remove(K_ERROR).apply(); refrescar()
    }

    fun fijarSesion(paciente: String?, scopes: String?) {
        prefs.edit().putString(K_PACIENTE, paciente).putString(K_SCOPES, scopes).remove(K_ERROR).apply(); refrescar()
    }

    fun registrarResultado(resultado: String, error: String?, exito: Boolean) {
        prefs.edit().apply {
            putString(K_RESULTADO, resultado)
            if (error == null) remove(K_ERROR) else putString(K_ERROR, error)
            if (exito) putLong(K_ULTIMA, System.currentTimeMillis())
        }.apply()
        refrescar()
    }

    fun registrarError(error: String) { prefs.edit().putString(K_ERROR, error).apply(); refrescar() }

    /** Desconectar: borra tokens y paciente (la sincronización sigue activa si [desactivar] es false). */
    fun desconectar(desactivar: Boolean) {
        prefs.edit().remove(K_AUTH).remove(K_PACIENTE).remove(K_SCOPES).apply {
            if (desactivar) { putBoolean(K_ACTIVA, false); remove(K_RESULTADO); remove(K_ERROR); remove(K_ULTIMA) }
        }.apply()
        refrescar()
    }

    private fun refrescar() { _estado.value = leer() }

    private fun leer() = EstadoConexion(
        activa = prefs.getBoolean(K_ACTIVA, false),
        conectado = prefs.contains(K_AUTH) && prefs.getString(K_PACIENTE, null) != null,
        paciente = prefs.getString(K_PACIENTE, null),
        scopes = prefs.getString(K_SCOPES, null),
        desdeUtc = prefs.getLong(K_DESDE, 0),
        ultimaSyncUtc = prefs.getLong(K_ULTIMA, 0).takeIf { it > 0 },
        ultimoResultado = prefs.getString(K_RESULTADO, null),
        ultimoError = prefs.getString(K_ERROR, null),
    )

    companion object {
        private const val ARCHIVO = "sync_seguro"
        private const val K_AUTH = "auth_state"
        private const val K_PACIENTE = "paciente"
        private const val K_SCOPES = "scopes"
        private const val K_ACTIVA = "activa"
        private const val K_DESDE = "desde_utc"
        private const val K_ULTIMA = "ultima_sync_utc"
        private const val K_RESULTADO = "ultimo_resultado"
        private const val K_ERROR = "ultimo_error"
        private const val K_INSTALACION = "id_instalacion"

        @Volatile private var instancia: AlmacenSeguro? = null

        fun get(ctx: Context): AlmacenSeguro = instancia ?: synchronized(this) {
            instancia ?: AlmacenSeguro(abrir(ctx.applicationContext)).also { instancia = it }
        }

        private fun abrir(ctx: Context): SharedPreferences {
            val llave = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                ctx, ARCHIVO, llave,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
