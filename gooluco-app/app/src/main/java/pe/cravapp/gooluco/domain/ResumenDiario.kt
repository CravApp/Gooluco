package pe.cravapp.gooluco.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class PesajeDia(val momento: LocalDateTime, val pesoKg: Double)

enum class CalidadDia { COMPLETO, PARCIAL, INSUFICIENTE }

data class ResumenDia(
    val fecha: LocalDate,
    val emaRespondidas: Int,
    val estresMedio: Double?,
    val estresElevado: Boolean?,
    val pesoManana: Double?,
    val pesoNoche: Double?,
    val deltaIntradia: Double?,
    val delta24h: Double?,
    val calidad: CalidadDia,
)

/** Correlación diaria estrés ↔ peso (RF-24..RF-26). Sin imputación. */
object ResumenDiario {
    const val MIN_EMA_DIA_COMPLETO = 2
    const val MIN_DIAS_LINEA_BASE = 7

    /** MAÑANA = primer pesaje estable de la franja; NOCHE = último (RF-24). */
    fun pesosDelDia(d: LocalDate, pesajes: List<PesajeDia>, cfg: ConfigAgenda = ConfigAgenda()): Pair<Double?, Double?> {
        val asignados = pesajes.map { Agenda.franja(it.momento, cfg) to it }.filter { it.first.first == d }
        val manana = asignados.filter { it.first.second == Franja.MANANA }.minByOrNull { it.second.momento }?.second?.pesoKg
        val noche = asignados.filter { it.first.second == Franja.NOCHE }.maxByOrNull { it.second.momento }?.second?.pesoKg
        return manana to noche
    }

    /** Línea base personal: media de los estrés diarios válidos; null si hay < 7 días (RF-26). */
    fun lineaBase(estresDiarios: List<Double>, minDias: Int = MIN_DIAS_LINEA_BASE): Double? =
        if (estresDiarios.size < minDias) null else estresDiarios.average()

    fun resumir(
        d: LocalDate,
        totalesPss: List<Int>,
        pesajes: List<PesajeDia>,
        lineaBase: Double?,
        cfg: ConfigAgenda = ConfigAgenda(),
    ): ResumenDia {
        val (m, n) = pesosDelDia(d, pesajes, cfg)
        val (m1, _) = pesosDelDia(d.plusDays(1), pesajes, cfg)
        val estres = if (totalesPss.isEmpty()) null else totalesPss.average()
        val emaOk = totalesPss.size >= MIN_EMA_DIA_COMPLETO
        val calidad = when {
            emaOk && m != null -> CalidadDia.COMPLETO
            emaOk || m != null -> CalidadDia.PARCIAL
            else -> CalidadDia.INSUFICIENTE
        }
        return ResumenDia(
            fecha = d,
            emaRespondidas = totalesPss.size,
            estresMedio = estres,
            estresElevado = if (estres != null && lineaBase != null) estres > lineaBase else null,
            pesoManana = m,
            pesoNoche = n,
            deltaIntradia = if (m != null && n != null) Imc.redondear1(n - m) else null,
            delta24h = if (m != null && m1 != null) Imc.redondear1(m1 - m) else null,
            calidad = calidad,
        )
    }
}
