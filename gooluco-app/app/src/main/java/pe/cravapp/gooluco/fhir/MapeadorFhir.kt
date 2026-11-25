package pe.cravapp.gooluco.fhir

import pe.cravapp.gooluco.data.EmaRespuestaEntity
import pe.cravapp.gooluco.data.FindriscEntity
import pe.cravapp.gooluco.data.NordicoEntity
import pe.cravapp.gooluco.data.PesajeEntity
import pe.cravapp.gooluco.data.TallaHistorialEntity
import pe.cravapp.gooluco.domain.AntecedenteFamiliar
import pe.cravapp.gooluco.domain.EstadoEma
import pe.cravapp.gooluco.domain.Nordico
import pe.cravapp.gooluco.domain.RegionNordica
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/**
 * Convierte los registros locales en recursos FHIR R4 aceptados por gooluco-api.
 * Privacidad: NUNCA se envían la MAC de la balanza, la trama cruda (tramaHex), la impedancia ni el RSSI.
 */
class MapeadorFhir(private val idInstalacion: String) {

    private fun sujeto(paciente: String) = Reference("Patient/$paciente")
    private fun ident(clave: String) = Identifier(Fhir.SID_REGISTRO, "$idInstalacion/$clave")
    private val signosVitales = listOf(CodeableConcept(listOf(Coding(Fhir.CATEGORIA_OBS, "vital-signs", "Vital Signs"))))

    private fun medida(
        clave: String, paciente: String, loinc: String, display: String, valor: Double, ucum: String,
        tsUtc: Long, zona: String, derivadoDe: String? = null, nota: String? = null,
    ) = Observation(
        identifier = listOf(ident(clave)),
        category = signosVitales,
        code = CodeableConcept(listOf(Coding(Fhir.LOINC, loinc, display)), display),
        subject = sujeto(paciente),
        effectiveDateTime = fechaHora(tsUtc, zona),
        valueQuantity = Quantity(valor, ucum, Fhir.UCUM, ucum),
        derivedFrom = derivadoDe?.let { listOf(Reference("Observation/$it")) },
        note = nota?.let { listOf(Annotation(it)) },
    )

    /** 29463-7 Body weight (kg, 2 decimales). */
    fun peso(p: PesajeEntity, paciente: String): Observation = medida(
        claveDe(Clase.PESO, p.id), paciente, "29463-7", "Body weight", redondear(p.pesoKg, 2), "kg", p.timestampUtc, p.zona,
        nota = notaFuente(p),
    )

    /** 39156-5 BMI calculado en la app (peso OKOK / talla²). Null si el pesaje no tiene IMC. */
    fun imc(p: PesajeEntity, paciente: String, pesoRemoto: String?): Observation? {
        val imc = p.imc ?: return null
        return medida(
            claveDe(Clase.IMC, p.id), paciente, "39156-5", "Body mass index (BMI) [Ratio]", redondear(imc, 1), "kg/m2",
            p.timestampUtc, p.zona, derivadoDe = pesoRemoto,
            nota = "IMC calculado en la app con talla ${p.tallaCmUsada?.let { redondear(it, 1) }} cm." + (notaFuente(p)?.let { " $it" } ?: ""),
        )
    }

    /** 8302-2 Body height (cm), un recurso por cada cambio de talla del perfil. */
    fun talla(t: TallaHistorialEntity, paciente: String, zona: String): Observation = medida(
        claveDe(Clase.TALLA, t.id), paciente, "8302-2", "Body height", redondear(t.tallaCm, 1), "cm", t.desdeUtc, zona,
    )

    /** 8280-0 Waist circumference (cm), medida ingresada al responder FINDRISC. */
    fun cintura(f: FindriscEntity, paciente: String, zona: String): Observation = medida(
        claveDe(Clase.CINTURA, f.id), paciente, "8280-0", "Waist Circumference at umbilicus by Tape measure",
        redondear(f.cinturaCm, 1), "cm", f.timestampUtc, zona,
    )

    /** PSS-4 + contexto EMA. Se envían las respuestas tal como las marcó el paciente (sin invertir) y el total. */
    fun pss4(e: EmaRespuestaEntity, paciente: String, zona: String): QuestionnaireResponse? {
        if (e.estado != EstadoEma.RESPONDIDA.name) return null
        val p = listOf(e.i1, e.i2, e.i3, e.i4)
        if (p.any { it == null }) return null
        // Room guarda los puntajes ya invertidos (ítems 2 y 3): se recupera la opción marcada.
        val marcadas = p.mapIndexed { i, v -> if (i == 1 || i == 2) 4 - v!! else v!! }
        val likert = "${Fhir.Q_BASE}/pss4-ema/likert"
        val items = mutableListOf<QRItem>()
        marcadas.forEachIndexed { i, v -> items += QRItem("pss4-${i + 1}", answer = listOf(QRAnswer(valueCoding = Coding(likert, v.toString(), OPCIONES[v])))) }
        items += QRItem("pss4-total", answer = listOf(QRAnswer(valueInteger = e.pssTotal ?: p.sumOf { it!! })))
        e.actividad?.let { items += QRItem("actividad", answer = listOf(QRAnswer(valueCoding = Coding("${Fhir.Q_BASE}/pss4-ema/actividad", it)))) }
        e.lugar?.let { items += QRItem("lugar", answer = listOf(QRAnswer(valueCoding = Coding("${Fhir.Q_BASE}/pss4-ema/lugar", it)))) }
        e.social?.let { items += QRItem("social", answer = listOf(QRAnswer(valueBoolean = it))) }
        return QuestionnaireResponse(
            identifier = ident(claveDe(Clase.PSS4, e.id)),
            questionnaire = "${Fhir.Q_BASE}/pss4-ema",
            subject = sujeto(paciente),
            authored = fechaHora(e.respondidaUtc ?: e.programadaUtc, zona),
            item = items,
        )
    }

    /** FINDRISC: las respuestas se reconstruyen a partir de los puntos guardados por ítem. */
    fun findrisc(f: FindriscEntity, edad: Int, paciente: String, zona: String): QuestionnaireResponse {
        val pts = f.puntos.split(",").map { it.trim().toInt() }
        require(pts.size == 8) { "FINDRISC con ${pts.size} ítems" }
        val familia = AntecedenteFamiliar.entries.first { it.puntos == pts[7] }
        fun b(v: Boolean) = listOf(QRAnswer(valueBoolean = v))
        return QuestionnaireResponse(
            identifier = ident(claveDe(Clase.FINDRISC, f.id)),
            questionnaire = "${Fhir.Q_BASE}/findrisc",
            subject = sujeto(paciente),
            authored = fechaHora(f.timestampUtc, zona),
            item = listOf(
                QRItem("edad", answer = listOf(QRAnswer(valueInteger = edad))),
                QRItem("imc", answer = listOf(QRAnswer(valueDecimal = redondear(f.imcUsado, 1)))),
                QRItem("cintura", answer = listOf(QRAnswer(valueDecimal = redondear(f.cinturaCm, 1)))),
                QRItem("actividad", answer = b(pts[3] == 0)),
                QRItem("verduras", answer = b(pts[4] == 0)),
                QRItem("antihipertensivos", answer = b(pts[5] > 0)),
                QRItem("glucosa", answer = b(pts[6] > 0)),
                QRItem("familia", answer = listOf(QRAnswer(valueCoding = Coding("${Fhir.Q_BASE}/findrisc/familia", familia.name, familia.etiqueta)))),
                QRItem("total", answer = listOf(QRAnswer(valueInteger = f.total))),
            ),
        )
    }

    /** Cuestionario Nórdico: un grupo por región con sus 3 preguntas sí/no. */
    fun nordico(n: NordicoEntity, paciente: String, zona: String): QuestionnaireResponse {
        val r = Nordico.decodificar(n.respuestas)
        return QuestionnaireResponse(
            identifier = ident(claveDe(Clase.NORDICO, n.id)),
            questionnaire = "${Fhir.Q_BASE}/nordico",
            subject = sujeto(paciente),
            authored = fechaHora(n.timestampUtc, zona),
            item = RegionNordica.entries.map { reg ->
                val x = r[reg] ?: pe.cravapp.gooluco.domain.RespuestaRegion()
                QRItem(
                    reg.name, text = reg.etiqueta,
                    item = listOf(
                        QRItem("${reg.name}.molestias12m", answer = listOf(QRAnswer(valueBoolean = x.molestias12m))),
                        QRItem("${reg.name}.impidio12m", answer = listOf(QRAnswer(valueBoolean = x.impidio12m))),
                        QRItem("${reg.name}.molestias7d", answer = listOf(QRAnswer(valueBoolean = x.molestias7d))),
                    ),
                )
            },
        )
    }

    private fun notaFuente(p: PesajeEntity): String? = when (p.fuente) {
        "SIMULADA" -> "Pesaje SIMULADO (simulador OKOK de la app)."
        else -> null
    }

    enum class Clase { PESO, IMC, TALLA, CINTURA, PSS4, FINDRISC, NORDICO }

    companion object {
        private val OPCIONES = listOf("Nunca", "Casi nunca", "A veces", "Bastante a menudo", "Muy a menudo")
        private val ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        fun claveDe(c: Clase, idLocal: Long) = "${c.name}:$idLocal"

        /** Fecha-hora con zona (p. ej. 2026-11-25T07:05:00-05:00), a segundos. */
        fun fechaHora(tsUtc: Long, zona: String): String =
            Instant.ofEpochMilli(tsUtc).truncatedTo(ChronoUnit.SECONDS).atZone(runCatching { ZoneId.of(zona) }.getOrDefault(ZoneId.of("America/Lima")))
                .toOffsetDateTime().format(ISO)

        fun redondear(x: Double, dec: Int): Double {
            var f = 1.0; repeat(dec) { f *= 10 }
            return (x * f).roundToLong() / f
        }
    }
}
