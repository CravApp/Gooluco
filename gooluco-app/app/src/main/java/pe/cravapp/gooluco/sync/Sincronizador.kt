package pe.cravapp.gooluco.sync

import android.content.Context
import pe.cravapp.gooluco.data.BaseDatos
import pe.cravapp.gooluco.data.SyncEstadoEntity
import pe.cravapp.gooluco.domain.ConfigAgenda
import pe.cravapp.gooluco.fhir.MapeadorFhir
import java.io.IOException

data class ResumenSync(val enviados: Int, val rechazados: Int, val pendientes: Int, val transitorio: Boolean, val mensaje: String)

/** Sube a gooluco-api los registros locales pendientes (un recurso por POST, en orden cronológico). */
class Sincronizador(
    private val ctx: Context,
    private val db: BaseDatos = BaseDatos.get(ctx),
    private val almacen: AlmacenSeguro = AlmacenSeguro.get(ctx),
    private val smart: ClienteSmart = ClienteSmart(ctx, almacen),
    private val fhir: ClienteFhir = ClienteFhir(),
) {
    suspend fun candidatos(): List<RecursoPendiente> {
        val l = db.lecturaSync()
        return PlanSync.candidatos(
            MapeadorFhir(almacen.idInstalacion), ConfigAgenda().zona.id, db.perfil().obtener(),
            l.pesajes(), l.tallas(), l.emas(), l.findrisc(), l.nordico(), almacen.estado.value.desdeUtc,
        )
    }

    suspend fun ejecutar(reintentarErrores: Boolean): ResumenSync {
        val est = almacen.estado.value
        if (!est.activa) return ResumenSync(0, 0, 0, false, "Sincronización desactivada.")
        val paciente = est.paciente ?: throw ErrorSync("Sin sesión. Conéctate al servidor.", requiereLogin = true)
        val estados = db.sync().todos().associateBy { it.clave }.toMutableMap()
        val cola = PlanSync.pendientes(candidatos(), estados, reintentarErrores)
        var token = smart.tokenVigente()
        var enviados = 0; var rechazados = 0
        for ((i, r) in cola.withIndex()) {
            val remotos = estados.values.filter { it.remoteId != null }.associate { it.clave to it.remoteId!! }
            if (r.dependeDe != null && estados[r.dependeDe]?.estado != EstadoItem.SINCRONIZADO.name &&
                estados[r.dependeDe]?.estado != EstadoItem.ERROR.name) continue // se envía en la próxima pasada
            val json = r.construir(paciente, remotos) ?: continue
            var resp = try { fhir.crear(r.tipo, json, token) } catch (e: IOException) {
                return cerrar(enviados, rechazados, cola.size - i, true, "Sin conexión con el servidor: ${e.message}")
            }
            if (PlanSync.desenlace(resp.codigo) == Desenlace.NO_AUTORIZADO) {
                token = smart.tokenVigente(forzarRenovacion = true)
                resp = try { fhir.crear(r.tipo, json, token) } catch (e: IOException) {
                    return cerrar(enviados, rechazados, cola.size - i, true, "Sin conexión con el servidor: ${e.message}")
                }
            }
            val previo = estados[r.clave]
            when (PlanSync.desenlace(resp.codigo)) {
                Desenlace.CREADO -> {
                    val s = SyncEstadoEntity(r.clave, r.tipo, resp.recurso?.id, EstadoItem.SINCRONIZADO.name, (previo?.intentos ?: 0) + 1, null, System.currentTimeMillis())
                    db.sync().guardar(s); estados[r.clave] = s; enviados++
                }
                Desenlace.RECHAZADO -> {
                    val s = SyncEstadoEntity(r.clave, r.tipo, null, EstadoItem.ERROR.name, (previo?.intentos ?: 0) + 1,
                        "HTTP ${resp.codigo}: ${resp.diagnostico}", System.currentTimeMillis())
                    db.sync().guardar(s); estados[r.clave] = s; rechazados++
                }
                Desenlace.NO_AUTORIZADO -> throw ErrorSync("El servidor rechazó la sesión (401). Vuelve a conectarte.", requiereLogin = true)
                Desenlace.REINTENTAR -> return cerrar(enviados, rechazados, cola.size - i, true, "El servidor respondió HTTP ${resp.codigo}; se reintentará.")
            }
        }
        val quedan = PlanSync.contarPendientes(candidatos(), estados)
        return cerrar(enviados, rechazados, quedan, false, null)
    }

    private fun cerrar(env: Int, rech: Int, pend: Int, transitorio: Boolean, error: String?): ResumenSync {
        val msg = "Enviados $env" + (if (rech > 0) " · rechazados $rech" else "") + " · pendientes $pend"
        almacen.registrarResultado(msg, error ?: if (rech > 0) "$rech registro(s) rechazado(s) por el servidor; ver detalle." else null, exito = !transitorio)
        return ResumenSync(env, rech, pend, transitorio, error ?: msg)
    }
}
