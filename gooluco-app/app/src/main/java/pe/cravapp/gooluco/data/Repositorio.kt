package pe.cravapp.gooluco.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.cravapp.gooluco.ble.LecturaOkok
import pe.cravapp.gooluco.domain.Agenda
import pe.cravapp.gooluco.domain.ConfigAgenda
import pe.cravapp.gooluco.domain.EstadoEma
import pe.cravapp.gooluco.domain.FindriscResultado
import pe.cravapp.gooluco.domain.Imc
import pe.cravapp.gooluco.domain.Nordico
import pe.cravapp.gooluco.domain.Pss4
import pe.cravapp.gooluco.domain.RegionNordica
import pe.cravapp.gooluco.domain.RespuestaRegion
import pe.cravapp.gooluco.domain.SlotAgenda
import pe.cravapp.gooluco.domain.TipoSlot
import pe.cravapp.gooluco.notif.Agendador
import pe.cravapp.gooluco.notif.aDominio
import pe.cravapp.gooluco.sync.AlmacenSeguro
import pe.cravapp.gooluco.sync.ProgramadorSync
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import kotlin.math.abs

/** Acceso único a los datos locales. Nada sale del dispositivo. */
class Repositorio(private val ctx: Context, private val db: BaseDatos, val cfg: ConfigAgenda = ConfigAgenda()) {

    val perfil = db.perfil().observar()
    val pesajes = db.pesajes().observarTodos()
    val emas = db.ema().observarTodas()
    val findrisc = db.cuestionarios().observarFindrisc()
    val nordico = db.cuestionarios().observarNordico()
    val agenda = db.agenda().observar()
    val tallas = db.lecturaSync().observarTallas()
    val estadosSync = db.sync().observar()

    private fun avisarSync() = ProgramadorSync.siActiva(ctx)

    suspend fun guardarPerfil(nuevo: PerfilEntity) = withContext(Dispatchers.IO) {
        val anterior = db.perfil().obtener()
        require(Imc.tallaValida(nuevo.tallaCm)) { "La talla debe estar entre 100 y 230 cm." }
        db.perfil().guardar(nuevo)
        if (anterior?.tallaCm != nuevo.tallaCm) {
            db.perfil().registrarTalla(TallaHistorialEntity(tallaCm = nuevo.tallaCm, desdeUtc = System.currentTimeMillis()))
            avisarSync()
        }
        if (db.agenda().todos().isEmpty()) guardarAgendaInterno(Agenda.SLOTS_DEFECTO)
        else Agendador.programar(ctx, db.agenda().todos().map { it.aDominio() }, cfg)
    }

    suspend fun vincularBalanza(mac: String?, variante: String?) = withContext(Dispatchers.IO) {
        db.perfil().obtener()?.let { db.perfil().guardar(it.copy(balanzaMac = mac, balanzaVariante = variante)) }
    }

    /** RF-09: true si es repetición (misma MAC y peso ±0,05 kg en 60 s). */
    suspend fun esDuplicado(l: LecturaOkok): Boolean = withContext(Dispatchers.IO) {
        val u = db.pesajes().ultimo() ?: return@withContext false
        u.mac.equals(l.mac, true) && abs(u.pesoKg - l.frame.weightKg) <= 0.05 && abs(l.timestampMs - u.timestampUtc) < 60_000
    }

    suspend fun ultimoPeso(): Double? = withContext(Dispatchers.IO) { db.pesajes().ultimo()?.pesoKg }

    /** RF-10, RF-14, RF-24: guarda el pesaje estable con IMC calculado con la talla vigente. */
    suspend fun guardarPesaje(l: LecturaOkok): Long = withContext(Dispatchers.IO) {
        val p = db.perfil().obtener()
        val talla = p?.tallaCm
        val imc = talla?.let { Imc.calcular(l.frame.weightKg, it) }
        val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(l.timestampMs), cfg.zona)
        val (fecha, franja) = Agenda.franja(local, cfg)
        db.pesajes().insertar(
            PesajeEntity(
                timestampUtc = l.timestampMs,
                zona = cfg.zona.id,
                pesoKg = l.frame.weightKg,
                impedanciaOhm = l.frame.impedanceOhm,
                unidadOriginal = l.frame.unit?.label,
                variante = l.frame.variant.label,
                mac = l.mac,
                rssi = l.rssi,
                fuente = l.fuente.name,
                tramaHex = l.dataHex,
                tallaCmUsada = talla,
                imc = imc,
                claseImc = imc?.let { Imc.clasificar(it).name },
                fechaLocal = fecha.toString(),
                franja = franja.name,
            )
        ).also { avisarSync() }
    }

    suspend fun fijarImcExterno(id: Long, imc: Double?) = withContext(Dispatchers.IO) { db.pesajes().fijarImcExterno(id, imc) }
    suspend fun borrarPesaje(id: Long) = withContext(Dispatchers.IO) { db.pesajes().borrar(id) }

    /** RF-21: guarda respuesta EMA (o su omisión). [slot] null = registro libre fuera de agenda. */
    suspend fun guardarEma(
        slot: SlotAgenda?,
        respuestas: List<Int>?,
        actividad: String?,
        lugar: String?,
        social: Boolean?,
        estado: EstadoEma,
    ) = withContext(Dispatchers.IO) {
        val ahora = ZonedDateTime.now(cfg.zona)
        val programada = slot?.let { ahora.toLocalDate().atTime(it.hora).atZone(cfg.zona) } ?: ahora
        val p = respuestas?.let { Pss4.puntuados(it) }
        db.ema().insertar(
            EmaRespuestaEntity(
                slotId = slot?.id ?: SLOT_LIBRE,
                fechaProgramada = programada.toLocalDate().toString(),
                programadaUtc = programada.toInstant().toEpochMilli(),
                respondidaUtc = if (estado == EstadoEma.RESPONDIDA) ahora.toInstant().toEpochMilli() else null,
                latenciaSeg = if (estado == EstadoEma.RESPONDIDA) Agenda.latenciaSeg(programada, ahora) else null,
                estado = estado.name,
                i1 = p?.get(0), i2 = p?.get(1), i3 = p?.get(2), i4 = p?.get(3),
                pssTotal = p?.sum(),
                actividad = actividad, lugar = lugar, social = social,
                version = Pss4.VERSION,
            )
        )
        avisarSync()
    }

    /** RF-19: marca como EXPIRADA cada EMA de hoy/ayer cuya ventana cerró sin registro (desde el consentimiento). */
    suspend fun cerrarEmaVencidas() = withContext(Dispatchers.IO) {
        val p = db.perfil().obtener() ?: return@withContext
        val desde = p.consentimientoUtc ?: return@withContext
        val ahora = ZonedDateTime.now(cfg.zona)
        val slots = db.agenda().todos().map { it.aDominio() }.filter { it.activo && it.tipo == TipoSlot.EMA }
        for (d in listOf(ahora.toLocalDate().minusDays(1), ahora.toLocalDate())) {
            for (s in slots) {
                val prog = d.atTime(s.hora).atZone(cfg.zona)
                if (prog.toInstant().toEpochMilli() < desde) continue
                if (prog.plusMinutes(cfg.ventanaEmaMin).isAfter(ahora)) continue
                if (db.ema().existe(s.id, d.toString()) > 0) continue
                db.ema().insertar(
                    EmaRespuestaEntity(
                        slotId = s.id, fechaProgramada = d.toString(), programadaUtc = prog.toInstant().toEpochMilli(),
                        respondidaUtc = null, latenciaSeg = null, estado = EstadoEma.EXPIRADA.name, version = Pss4.VERSION,
                    )
                )
            }
        }
    }

    suspend fun emaYaRegistrada(slot: SlotAgenda, fecha: LocalDate) = withContext(Dispatchers.IO) {
        db.ema().existe(slot.id, fecha.toString()) > 0
    }

    suspend fun guardarFindrisc(r: FindriscResultado, imc: Double, cintura: Double) = withContext(Dispatchers.IO) {
        db.cuestionarios().insertarFindrisc(
            FindriscEntity(
                timestampUtc = System.currentTimeMillis(), puntos = r.puntos.joinToString(","), total = r.total,
                categoria = r.categoria.name, imcUsado = imc, cinturaCm = cintura,
            )
        )
        db.perfil().obtener()?.let { if (it.cinturaCm != cintura) db.perfil().guardar(it.copy(cinturaCm = cintura)) }
        avisarSync()
    }

    suspend fun guardarNordico(r: Map<RegionNordica, RespuestaRegion>) = withContext(Dispatchers.IO) {
        db.cuestionarios().insertarNordico(NordicoEntity(timestampUtc = System.currentTimeMillis(), respuestas = Nordico.codificar(r), version = Nordico.VERSION))
        avisarSync()
    }

    suspend fun guardarAgenda(slots: List<SlotAgenda>) = withContext(Dispatchers.IO) { guardarAgendaInterno(slots) }

    private suspend fun guardarAgendaInterno(slots: List<SlotAgenda>) {
        db.agenda().guardar(slots.map { SlotEntity(it.id, it.tipo.name, it.hora.hour * 60 + it.hora.minute, it.activo) })
        Agendador.programar(ctx, slots, cfg) // RF-23: solo afecta a los disparos futuros
    }

    /** RF-39: elimina todos los datos locales y cancela las notificaciones. */
    suspend fun borrarTodo() = withContext(Dispatchers.IO) {
        Agendador.cancelarTodo(ctx, db.agenda().todos().map { it.aDominio() })
        ProgramadorSync.cancelar(ctx)
        AlmacenSeguro.get(ctx).desconectar(desactivar = true) // los datos ya subidos al servidor de prueba no se borran desde aquí
        db.clearAllTables()
    }

    companion object {
        const val SLOT_LIBRE = "LIBRE"
    }
}
