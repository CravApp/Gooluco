package pe.cravapp.gooluco.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class TipoSlot(val etiqueta: String) {
    EMA("EMA de estrés"),
    PESAJE_MANANA("Pesaje mañana"),
    PESAJE_NOCHE("Pesaje noche"),
}

data class SlotAgenda(
    val id: String,
    val tipo: TipoSlot,
    val hora: LocalTime,
    val activo: Boolean = true,
)

enum class EstadoEma { RESPONDIDA, OMITIDA, EXPIRADA, CANCELADA }

enum class Franja { MANANA, NOCHE, OTRA }

/** Configuración de la agenda diaria (RF-16) con los valores por defecto de la Tabla 3 del SRS. */
data class ConfigAgenda(
    val ventanaEmaMin: Long = 60,
    val recordatorioMin: Long = 20,
    val mananaInicio: LocalTime = LocalTime.of(5, 0),
    val mananaFin: LocalTime = LocalTime.of(11, 0),
    val nocheInicio: LocalTime = LocalTime.of(18, 0),
    val nocheFin: LocalTime = LocalTime.of(23, 59, 59),
    val graciaNocturna: LocalTime = LocalTime.of(1, 0),
    val zona: ZoneId = ZoneId.of("America/Lima"),
)

object Agenda {
    val SLOTS_DEFECTO = listOf(
        SlotAgenda("P-M", TipoSlot.PESAJE_MANANA, LocalTime.of(6, 30)),
        SlotAgenda("EMA-1", TipoSlot.EMA, LocalTime.of(9, 0)),
        SlotAgenda("EMA-2", TipoSlot.EMA, LocalTime.of(13, 0)),
        SlotAgenda("EMA-3", TipoSlot.EMA, LocalTime.of(17, 0)),
        SlotAgenda("P-N", TipoSlot.PESAJE_NOCHE, LocalTime.of(21, 0)),
        SlotAgenda("EMA-4", TipoSlot.EMA, LocalTime.of(21, 0)),
    )

    /** Próxima hora de disparo estrictamente posterior a [ahora] (hoy o mañana). */
    fun proximoDisparo(slot: SlotAgenda, ahora: ZonedDateTime): ZonedDateTime {
        val hoy = ahora.toLocalDate().atTime(slot.hora).atZone(ahora.zone)
        return if (hoy.isAfter(ahora)) hoy else ahora.toLocalDate().plusDays(1).atTime(slot.hora).atZone(ahora.zone)
    }

    /** RF-18: una EMA solo se responde dentro de su ventana [hora, hora + ventana). */
    fun dentroDeVentana(programada: LocalDateTime, ahora: LocalDateTime, ventanaMin: Long): Boolean =
        !ahora.isBefore(programada) && ahora.isBefore(programada.plusMinutes(ventanaMin))

    /** Slot EMA cuya ventana está abierta ahora, si existe. */
    fun emaAbierta(slots: List<SlotAgenda>, ahora: LocalDateTime, cfg: ConfigAgenda = ConfigAgenda()): SlotAgenda? =
        slots.filter { it.activo && it.tipo == TipoSlot.EMA }
            .firstOrNull { dentroDeVentana(ahora.toLocalDate().atTime(it.hora), ahora, cfg.ventanaEmaMin) }

    /** RF-24: fecha local y franja de un pesaje. 00:00–gracia → NOCHE del día anterior. */
    fun franja(t: LocalDateTime, cfg: ConfigAgenda = ConfigAgenda()): Pair<LocalDate, Franja> {
        val h = t.toLocalTime()
        return when {
            h.isBefore(cfg.graciaNocturna) -> t.toLocalDate().minusDays(1) to Franja.NOCHE
            !h.isBefore(cfg.mananaInicio) && !h.isAfter(cfg.mananaFin) -> t.toLocalDate() to Franja.MANANA
            !h.isBefore(cfg.nocheInicio) && !h.isAfter(cfg.nocheFin) -> t.toLocalDate() to Franja.NOCHE
            else -> t.toLocalDate() to Franja.OTRA
        }
    }

    /** RF-19: adherencia = respondidas / programadas (las CANCELADAS no cuentan). Null si no hay programadas. */
    fun adherencia(estados: List<EstadoEma>): Double? {
        val programadas = estados.count { it != EstadoEma.CANCELADA }
        if (programadas == 0) return null
        return estados.count { it == EstadoEma.RESPONDIDA }.toDouble() / programadas
    }

    fun latenciaSeg(notificada: ZonedDateTime, respondida: ZonedDateTime): Long =
        Duration.between(notificada, respondida).seconds.coerceAtLeast(0)
}
