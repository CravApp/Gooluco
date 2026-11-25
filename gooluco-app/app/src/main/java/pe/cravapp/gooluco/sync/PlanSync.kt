package pe.cravapp.gooluco.sync

import pe.cravapp.gooluco.data.EmaRespuestaEntity
import pe.cravapp.gooluco.data.FindriscEntity
import pe.cravapp.gooluco.data.NordicoEntity
import pe.cravapp.gooluco.data.PerfilEntity
import pe.cravapp.gooluco.data.PesajeEntity
import pe.cravapp.gooluco.data.SyncEstadoEntity
import pe.cravapp.gooluco.data.TallaHistorialEntity
import pe.cravapp.gooluco.domain.EstadoEma
import pe.cravapp.gooluco.fhir.Fhir
import pe.cravapp.gooluco.fhir.MapeadorFhir
import pe.cravapp.gooluco.fhir.MapeadorFhir.Clase
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

enum class EstadoItem { SINCRONIZADO, ERROR }

/** Un recurso por subir. [construir] recibe el paciente del token y los ids remotos ya conocidos (para derivedFrom). */
class RecursoPendiente(
    val clave: String,
    val tipo: String, // "Observation" | "QuestionnaireResponse"
    val tsUtc: Long,
    val dependeDe: String? = null,
    val construir: (paciente: String, remotos: Map<String, String>) -> String?,
)

/** Clasificación de la respuesta HTTP del servidor FHIR. */
enum class Desenlace { CREADO, NO_AUTORIZADO, RECHAZADO, REINTENTAR }

object PlanSync {

    fun desenlace(codigoHttp: Int): Desenlace = when (codigoHttp) {
        200, 201 -> Desenlace.CREADO
        401 -> Desenlace.NO_AUTORIZADO
        408, 425, 429 -> Desenlace.REINTENTAR
        in 400..499 -> Desenlace.RECHAZADO // 400 validación, 403 scope/compartimento, 422 regla de negocio
        else -> Desenlace.REINTENTAR       // 5xx o códigos inesperados
    }

    /**
     * Construye la lista de recursos candidatos a partir de los datos locales.
     * Solo entra lo registrado desde [desdeUtc] (activación), salvo la talla vigente, que siempre se envía.
     */
    fun candidatos(
        mapeador: MapeadorFhir,
        zona: String,
        perfil: PerfilEntity?,
        pesajes: List<PesajeEntity>,
        tallas: List<TallaHistorialEntity>,
        emas: List<EmaRespuestaEntity>,
        findrisc: List<FindriscEntity>,
        nordico: List<NordicoEntity>,
        desdeUtc: Long,
    ): List<RecursoPendiente> {
        val out = mutableListOf<RecursoPendiente>()
        fun json(o: Any?): String? = when (o) {
            null -> null
            is pe.cravapp.gooluco.fhir.Observation -> Fhir.json.encodeToString(pe.cravapp.gooluco.fhir.Observation.serializer(), o)
            is pe.cravapp.gooluco.fhir.QuestionnaireResponse -> Fhir.json.encodeToString(pe.cravapp.gooluco.fhir.QuestionnaireResponse.serializer(), o)
            else -> error("tipo no soportado")
        }
        val tallaVigente = tallas.maxByOrNull { it.desdeUtc }
        tallas.filter { it.desdeUtc >= desdeUtc || it.id == tallaVigente?.id }.forEach { t ->
            out += RecursoPendiente(MapeadorFhir.claveDe(Clase.TALLA, t.id), "Observation", t.desdeUtc) { pac, _ -> json(mapeador.talla(t, pac, zona)) }
        }
        pesajes.filter { it.timestampUtc >= desdeUtc }.forEach { p ->
            val clavePeso = MapeadorFhir.claveDe(Clase.PESO, p.id)
            out += RecursoPendiente(clavePeso, "Observation", p.timestampUtc) { pac, _ -> json(mapeador.peso(p, pac)) }
            if (p.imc != null) out += RecursoPendiente(MapeadorFhir.claveDe(Clase.IMC, p.id), "Observation", p.timestampUtc, dependeDe = clavePeso) { pac, rem ->
                json(mapeador.imc(p, pac, rem[clavePeso]))
            }
        }
        emas.filter { it.estado == EstadoEma.RESPONDIDA.name && it.pssTotal != null && (it.respondidaUtc ?: 0) >= desdeUtc }.forEach { e ->
            out += RecursoPendiente(MapeadorFhir.claveDe(Clase.PSS4, e.id), "QuestionnaireResponse", e.respondidaUtc ?: e.programadaUtc) { pac, _ ->
                json(mapeador.pss4(e, pac, zona))
            }
        }
        findrisc.filter { it.timestampUtc >= desdeUtc }.forEach { f ->
            out += RecursoPendiente(MapeadorFhir.claveDe(Clase.CINTURA, f.id), "Observation", f.timestampUtc) { pac, _ -> json(mapeador.cintura(f, pac, zona)) }
            val edad = perfil?.let { edadEn(it.fechaNacimiento, f.timestampUtc, zona) }
            if (edad != null) out += RecursoPendiente(MapeadorFhir.claveDe(Clase.FINDRISC, f.id), "QuestionnaireResponse", f.timestampUtc) { pac, _ ->
                json(mapeador.findrisc(f, edad, pac, zona))
            }
        }
        nordico.filter { it.timestampUtc >= desdeUtc }.forEach { n ->
            out += RecursoPendiente(MapeadorFhir.claveDe(Clase.NORDICO, n.id), "QuestionnaireResponse", n.timestampUtc) { pac, _ -> json(mapeador.nordico(n, pac, zona)) }
        }
        return out.sortedBy { it.tsUtc }
    }

    /**
     * Pendientes = candidatos sin estado SINCRONIZADO. Los ERROR (rechazo permanente) solo se reintentan si
     * [reintentarErrores]. Un recurso que depende de otro (IMC → peso) espera a que el otro esté sincronizado,
     * salvo que el otro haya sido rechazado (entonces se envía sin derivedFrom).
     */
    fun pendientes(candidatos: List<RecursoPendiente>, estados: Map<String, SyncEstadoEntity>, reintentarErrores: Boolean): List<RecursoPendiente> =
        candidatos.filter { c ->
            when (estados[c.clave]?.estado) {
                EstadoItem.SINCRONIZADO.name -> false
                EstadoItem.ERROR.name -> reintentarErrores
                else -> true
            }
        }

    fun contarPendientes(candidatos: List<RecursoPendiente>, estados: Map<String, SyncEstadoEntity>): Int =
        pendientes(candidatos, estados, reintentarErrores = false).size

    fun edadEn(fechaNacimiento: String, tsUtc: Long, zona: String): Int? = runCatching {
        Period.between(LocalDate.parse(fechaNacimiento), Instant.ofEpochMilli(tsUtc).atZone(ZoneId.of(zona)).toLocalDate()).years
    }.getOrNull()
}
