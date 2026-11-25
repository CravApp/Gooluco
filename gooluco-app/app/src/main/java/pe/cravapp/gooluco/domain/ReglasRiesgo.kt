package pe.cravapp.gooluco.domain

import kotlin.math.abs

enum class NivelRiesgo(val etiqueta: String) {
    SIN_DATOS("Datos insuficientes"),
    BAJO("Bajo"),
    LIGERAMENTE_ELEVADO("Ligeramente elevado"),
    MODERADO("Moderado"),
    ALTO("Alto"),
    MUY_ALTO("Muy alto"),
}

enum class ClaseCintura { NORMAL, AUMENTADA, MUY_AUMENTADA }

/**
 * Umbrales de alertas. Valores provisionales: TBD-07 (deben validarlos profesionales de salud).
 */
data class UmbralesAlerta(
    val cambioPeso7dKg: Double = 2.0,
    val cambioPeso28dPct: Double = 5.0,
    val diasEstresConsecutivos: Int = 3,
    val adherenciaMinima: Double = 0.5,
)

data class EntradaRiesgo(
    val condicion: Condicion,
    val sexo: Sexo,
    val imc: Double? = null,
    val cinturaCm: Double? = null,
    val findriscTotal: Int? = null,
    val cambioPeso7dKg: Double? = null,
    val cambioPeso28dPct: Double? = null,
    val diasEstresElevadoConsecutivos: Int = 0,
    val adherenciaEma7d: Double? = null,
)

data class ResultadoRiesgo(
    val nivel: NivelRiesgo,
    val claseImc: ClaseImc?,
    val categoriaFindrisc: CategoriaFindrisc?,
    val cintura: ClaseCintura?,
    val reglas: List<String>,
    val alertas: List<String>,
    val derivar: Boolean,
) {
    companion object {
        const val AVISO = "Apoyo, no diagnóstico."
        const val DERIVACION = "Consulte a un profesional de salud y considere pruebas de laboratorio (HbA1c / glucosa)."
    }
}

/** Capa A (reglas, RF-29, RF-33, Tabla 4). Siempre disponible; es el nivel que se muestra. */
object ReglasRiesgo {

    fun claseCintura(cm: Double, sexo: Sexo): ClaseCintura = when (sexo) {
        Sexo.MASCULINO -> when { cm < 94 -> ClaseCintura.NORMAL; cm <= 102 -> ClaseCintura.AUMENTADA; else -> ClaseCintura.MUY_AUMENTADA }
        Sexo.FEMENINO -> when { cm < 80 -> ClaseCintura.NORMAL; cm <= 88 -> ClaseCintura.AUMENTADA; else -> ClaseCintura.MUY_AUMENTADA }
    }

    fun evaluar(e: EntradaRiesgo, u: UmbralesAlerta = UmbralesAlerta()): ResultadoRiesgo {
        val reglas = mutableListOf<String>()
        val alertas = mutableListOf<String>()
        var nivel: NivelRiesgo? = null
        fun alMenos(n: NivelRiesgo) { nivel = maxOf(nivel ?: n, n) }

        // A2 FINDRISC (no aplica a DM2)
        val catF = if (e.findriscTotal != null && Findrisc.aplica(e.condicion)) Findrisc.categoria(e.findriscTotal) else null
        if (catF != null) {
            reglas += "A2 FINDRISC ${e.findriscTotal} → ${catF.etiqueta.lowercase()}" +
                if (e.findriscTotal!! >= Findrisc.CORTE_PERU) " (≥ ${Findrisc.CORTE_PERU}, corte Perú)" else ""
            alMenos(
                when (catF) {
                    CategoriaFindrisc.BAJO -> NivelRiesgo.BAJO
                    CategoriaFindrisc.LIGERAMENTE_ELEVADO -> NivelRiesgo.LIGERAMENTE_ELEVADO
                    CategoriaFindrisc.MODERADO -> NivelRiesgo.MODERADO
                    CategoriaFindrisc.ALTO -> NivelRiesgo.ALTO
                    CategoriaFindrisc.MUY_ALTO -> NivelRiesgo.MUY_ALTO
                }
            )
        }

        // A1 IMC
        val claseImc = e.imc?.let { Imc.clasificar(it) }
        if (claseImc != null) {
            reglas += "A1 IMC ${e.imc} → ${claseImc.etiqueta.lowercase()}"
            alMenos(
                when (claseImc) {
                    ClaseImc.BAJO_PESO, ClaseImc.NORMAL -> NivelRiesgo.BAJO
                    ClaseImc.SOBREPESO -> NivelRiesgo.LIGERAMENTE_ELEVADO
                    ClaseImc.OBESIDAD_I -> NivelRiesgo.MODERADO
                    ClaseImc.OBESIDAD_II, ClaseImc.OBESIDAD_III -> NivelRiesgo.ALTO
                }
            )
        }

        // A3 Cintura
        val cint = e.cinturaCm?.let { claseCintura(it, e.sexo) }
        when (cint) {
            ClaseCintura.AUMENTADA -> { reglas += "A3 Cintura ${fmt(e.cinturaCm!!)} cm → aumentada"; alMenos(NivelRiesgo.LIGERAMENTE_ELEVADO) }
            ClaseCintura.MUY_AUMENTADA -> { reglas += "A3 Cintura ${fmt(e.cinturaCm!!)} cm → obesidad abdominal"; alMenos(NivelRiesgo.MODERADO) }
            else -> {}
        }

        // Con DM2 diagnosticada el riesgo físico asociado parte al menos de moderado.
        if (e.condicion == Condicion.DM2 && nivel != null) {
            reglas += "Condición: DM2 diagnosticada"
            alMenos(NivelRiesgo.MODERADO)
        }

        // A4 Tendencia de peso
        var alertaSostenida = false
        e.cambioPeso7dKg?.let {
            if (abs(it) > u.cambioPeso7dKg) { alertas += "A4 Cambio de peso de ${fmt(it)} kg en 7 días"; alertaSostenida = true }
        }
        e.cambioPeso28dPct?.let {
            if (abs(it) > u.cambioPeso28dPct) { alertas += "A4 Cambio de peso de ${fmt(it)} % en 28 días"; alertaSostenida = true }
        }
        // A5 Estrés sostenido (alerta psicosocial, no cambia el nivel)
        if (e.diasEstresElevadoConsecutivos >= u.diasEstresConsecutivos) {
            alertas += "A5 ${e.diasEstresElevadoConsecutivos} días seguidos de estrés elevado"
            alertaSostenida = true
        }
        // A6 Adherencia
        e.adherenciaEma7d?.let {
            if (it < u.adherenciaMinima) alertas += "A6 Adherencia EMA ${(it * 100).toInt()} % en 7 días: datos insuficientes"
        }

        val n = nivel ?: NivelRiesgo.SIN_DATOS
        val derivar = n >= NivelRiesgo.ALTO || claseImc?.esObesidad == true || alertaSostenida
        return ResultadoRiesgo(n, claseImc, catF, cint, reglas, alertas, derivar)
    }

    private fun fmt(x: Double) = if (x == x.toLong().toDouble()) x.toLong().toString() else "%.1f".format(java.util.Locale.ROOT, x)
}
