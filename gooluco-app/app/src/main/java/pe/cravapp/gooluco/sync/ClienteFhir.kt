package pe.cravapp.gooluco.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import pe.cravapp.gooluco.fhir.Bundle
import pe.cravapp.gooluco.fhir.Fhir
import pe.cravapp.gooluco.fhir.Observation
import pe.cravapp.gooluco.fhir.RecursoMinimo
import java.io.IOException

data class RespuestaHttp(val codigo: Int, val cuerpo: String) {
    val recurso: RecursoMinimo? get() = runCatching { Fhir.json.decodeFromString(RecursoMinimo.serializer(), cuerpo) }.getOrNull()
    val diagnostico: String get() = recurso?.issue?.mapNotNull { it.diagnostics }?.joinToString("; ")?.ifEmpty { null } ?: cuerpo.take(200)
}

/** REST FHIR R4 mínimo sobre OkHttp (solo HTTPS). */
class ClienteFhir(private val base: String = ClienteSmart.FHIR_BASE, private val http: OkHttpClient = Http.cliente) {

    @Throws(IOException::class)
    suspend fun crear(tipo: String, json: String, token: String): RespuestaHttp = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$base/$tipo")
            .header("Authorization", "Bearer $token")
            .header("Accept", FHIR_JSON)
            .post(json.toRequestBody(FHIR_JSON.toMediaType()))
            .build()
        http.newCall(req).execute().use { RespuestaHttp(it.code, it.body?.string().orEmpty()) }
    }

    /** Lectura de solo consulta: pesos del paciente en el servidor (más recientes primero según el servidor). */
    suspend fun pesos(paciente: String, token: String, cantidad: Int = 30): List<Observation> = withContext(Dispatchers.IO) {
        val url = "$base/Observation?patient=$paciente&code=http://loinc.org%7C29463-7&_count=200"
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept", FHIR_JSON).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw ErrorSync("Consulta al servidor: HTTP ${r.code}", requiereLogin = r.code == 401)
            val b = Fhir.json.decodeFromString(Bundle.serializer(Observation.serializer()), r.body!!.string())
            // El servidor no soporta _sort: se ordenan aquí, del más reciente al más antiguo.
            b.entry.orEmpty().map { it.resource }
                .sortedByDescending { o -> o.effectiveDateTime?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull() } }
                .take(cantidad)
        }
    }

    companion object { const val FHIR_JSON = "application/fhir+json" }
}
