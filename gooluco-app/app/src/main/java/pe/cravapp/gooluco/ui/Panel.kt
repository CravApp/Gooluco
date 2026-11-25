package pe.cravapp.gooluco.ui

import pe.cravapp.gooluco.data.EmaRespuestaEntity
import pe.cravapp.gooluco.data.FindriscEntity
import pe.cravapp.gooluco.data.NordicoEntity
import pe.cravapp.gooluco.data.PerfilEntity
import pe.cravapp.gooluco.data.PesajeEntity
import pe.cravapp.gooluco.data.Repositorio
import pe.cravapp.gooluco.domain.Agenda
import pe.cravapp.gooluco.domain.ClaseImc
import pe.cravapp.gooluco.domain.Condicion
import pe.cravapp.gooluco.domain.ConfigAgenda
import pe.cravapp.gooluco.domain.EntradaRiesgo
import pe.cravapp.gooluco.domain.EstadoEma
import pe.cravapp.gooluco.domain.Imc
import pe.cravapp.gooluco.domain.Nordico
import pe.cravapp.gooluco.domain.PesajeDia
import pe.cravapp.gooluco.domain.ReglasRiesgo
import pe.cravapp.gooluco.domain.ResultadoRiesgo
import pe.cravapp.gooluco.domain.ResumenDia
import pe.cravapp.gooluco.domain.ResumenDiario
import pe.cravapp.gooluco.domain.Sexo
import pe.cravapp.gooluco.domain.Tendencia
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZonedDateTime

/** Indicadores derivados que muestran «Hoy» y «Análisis». Todo se calcula en el dispositivo. */
data class Panel(
    val pesoActual: Double? = null,
    val metaPeso: Double? = null,
    val deltaSemanal: Double? = null,
    val imc: Double? = null,
    val claseImc: ClaseImc? = null,
    val edad: Int? = null,
    val diasRegistro7: Int = 0,
    val nordicoResumen: String? = null,
    val nordicoFecha: LocalDate? = null,
    val ultimoPesajeManana: PesajeEntity? = null,
    val proyeccion14: Double? = null,
    val nPuntosProyeccion: Int = 0,
    val adherencia7d: Double? = null,
    val estres7d: Double? = null,
    val lineaBaseEstres: Double? = null,
    val diasEstresSeguidos: Int = 0,
    val spearman: Double? = null,
    val nParesSpearman: Int = 0,
    val ultimoFindrisc: FindriscEntity? = null,
    val riesgo: ResultadoRiesgo? = null,
    val resumenes: List<ResumenDia> = emptyList(),
    val serie28d: List<Pair<LocalDate, Double>> = emptyList(),
)

object CalculoPanel {
    private const val DIA_MS = 86_400_000.0

    fun calcular(
        perfil: PerfilEntity?,
        pesajes: List<PesajeEntity>,
        emas: List<EmaRespuestaEntity>,
        findrisc: List<FindriscEntity>,
        nordico: List<NordicoEntity>,
        ahora: ZonedDateTime,
        cfg: ConfigAgenda = ConfigAgenda(),
    ): Panel {
        val hoy = ahora.toLocalDate()
        val nowMs = ahora.toInstant().toEpochMilli()
        val asc = pesajes.sortedBy { it.timestampUtc }
        val ultimo = asc.lastOrNull()

        val ult7 = asc.filter { it.timestampUtc >= nowMs - 7 * DIA_MS }
        val delta = if (ult7.size >= 2) Imc.redondear1(ult7.last().pesoKg - ult7.first().pesoKg) else null
        val ult28 = asc.filter { it.timestampUtc >= nowMs - 28 * DIA_MS }
        val cambio28Pct = if (ult28.size >= 2) (ult28.last().pesoKg - ult28.first().pesoKg) / ult28.first().pesoKg * 100 else null

        val imc = ultimo?.let { p -> perfil?.tallaCm?.let { Imc.calcular(p.pesoKg, it) } ?: p.imc }
        val edad = perfil?.let { runCatching { Period.between(LocalDate.parse(it.fechaNacimiento), hoy).years }.getOrNull() }

        val respondidas = emas.filter { it.estado == EstadoEma.RESPONDIDA.name && it.pssTotal != null }
        val diasConDatos = (0..6).map { hoy.minusDays(it.toLong()).toString() }.count { d ->
            asc.any { it.fechaLocal == d } || respondidas.any { it.fechaProgramada == d }
        }

        // Proyección lineal sobre los pesajes de los últimos 28 días (x en días).
        val puntos = ult28.map { (it.timestampUtc - nowMs) / DIA_MS to it.pesoKg }
        val proy = Tendencia.proyeccion(puntos)

        // Adherencia EMA 7 días (solo slots de agenda; el registro libre no cuenta).
        val desde7 = hoy.minusDays(6).toString()
        val ema7 = emas.filter { it.slotId != Repositorio.SLOT_LIBRE && it.fechaProgramada >= desde7 }
        val adh = Agenda.adherencia(ema7.map { EstadoEma.valueOf(it.estado) })

        // Estrés diario, línea base y días seguidos de estrés elevado.
        val porDia = respondidas.groupBy { LocalDate.parse(it.fechaProgramada) }.mapValues { e -> e.value.mapNotNull { it.pssTotal } }
        val mediasDiarias = porDia.mapValues { it.value.average() }
        val base = ResumenDiario.lineaBase(mediasDiarias.values.toList())
        val estres7 = mediasDiarias.filterKeys { it >= hoy.minusDays(6) }.values.takeIf { it.isNotEmpty() }?.average()
        var seguidos = 0
        if (base != null) {
            for (d in mediasDiarias.keys.sortedDescending()) { if (mediasDiarias.getValue(d) > base) seguidos++ else break }
        }

        // Resúmenes diarios (RF-25) y correlación exploratoria (RF-27).
        val pesajesDia = asc.map { PesajeDia(LocalDateTime.ofInstant(Instant.ofEpochMilli(it.timestampUtc), cfg.zona), it.pesoKg) }
        val resumenes = (0 until 28).map { hoy.minusDays(it.toLong()) }.map { d ->
            ResumenDiario.resumir(d, porDia[d].orEmpty(), pesajesDia, base, cfg)
        }
        val pares = resumenes.filter { it.estresMedio != null && it.delta24h != null }.map { it.estresMedio!! to it.delta24h!! }
        val rho = Tendencia.spearman(pares)

        val serie = asc.filter { it.timestampUtc >= nowMs - 28 * DIA_MS }
            .groupBy { LocalDate.parse(it.fechaLocal) }.map { (d, l) -> d to l.first().pesoKg }.sortedBy { it.first }

        val uf = findrisc.maxByOrNull { it.timestampUtc }
        val un = nordico.maxByOrNull { it.timestampUtc }

        val riesgo = perfil?.let {
            ReglasRiesgo.evaluar(
                EntradaRiesgo(
                    condicion = Condicion.valueOf(it.condicion),
                    sexo = Sexo.valueOf(it.sexo),
                    imc = imc,
                    cinturaCm = it.cinturaCm,
                    findriscTotal = uf?.total,
                    cambioPeso7dKg = delta,
                    cambioPeso28dPct = cambio28Pct,
                    diasEstresElevadoConsecutivos = seguidos,
                    adherenciaEma7d = adh,
                )
            )
        }

        return Panel(
            pesoActual = ultimo?.pesoKg,
            metaPeso = perfil?.metaPesoKg,
            deltaSemanal = delta,
            imc = imc,
            claseImc = imc?.let { Imc.clasificar(it) },
            edad = edad,
            diasRegistro7 = diasConDatos,
            nordicoResumen = un?.let { Nordico.resumen(Nordico.decodificar(it.respuestas)) },
            nordicoFecha = un?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.timestampUtc), cfg.zona).toLocalDate() },
            ultimoPesajeManana = asc.lastOrNull { it.franja == "MANANA" },
            proyeccion14 = proy,
            nPuntosProyeccion = puntos.size,
            adherencia7d = adh,
            estres7d = estres7,
            lineaBaseEstres = base,
            diasEstresSeguidos = seguidos,
            spearman = rho,
            nParesSpearman = pares.size,
            ultimoFindrisc = uf,
            riesgo = riesgo,
            resumenes = resumenes,
            serie28d = serie,
        )
    }
}
