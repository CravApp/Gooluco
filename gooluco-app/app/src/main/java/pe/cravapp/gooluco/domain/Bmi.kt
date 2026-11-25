package pe.cravapp.gooluco.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/** Clases de IMC de la OMS (RF-14, regla A1). */
enum class ClaseImc(val etiqueta: String) {
    BAJO_PESO("Bajo peso"),
    NORMAL("Normal"),
    SOBREPESO("Sobrepeso"),
    OBESIDAD_I("Obesidad I"),
    OBESIDAD_II("Obesidad II"),
    OBESIDAD_III("Obesidad III");

    val esObesidad: Boolean get() = this >= OBESIDAD_I
}

/**
 * IMC = peso OKOK / talla² (RF-14). La balanza OKOK no envía IMC: se calcula siempre en la app
 * con la talla del perfil. Un IMC externo (smartwatch, pantalla, ingreso manual) solo se contrasta (RF-15).
 */
object Imc {
    const val TALLA_MIN_CM = 100.0
    const val TALLA_MAX_CM = 230.0
    const val UMBRAL_CONTRASTE_DEFECTO = 0.5

    fun tallaValida(tallaCm: Double): Boolean = tallaCm in TALLA_MIN_CM..TALLA_MAX_CM

    /** IMC redondeado a 1 decimal. */
    fun calcular(pesoKg: Double, tallaCm: Double): Double {
        require(pesoKg > 0) { "Peso inválido: $pesoKg" }
        require(tallaValida(tallaCm)) { "Talla fuera de rango: $tallaCm cm" }
        val m = tallaCm / 100.0
        return redondear1(pesoKg / (m * m))
    }

    /** Se clasifica sobre el valor ya redondeado a 1 decimal (como se muestra al paciente). */
    fun clasificar(imc: Double): ClaseImc = when {
        imc < 18.5 -> ClaseImc.BAJO_PESO
        imc < 25.0 -> ClaseImc.NORMAL
        imc < 30.0 -> ClaseImc.SOBREPESO
        imc < 35.0 -> ClaseImc.OBESIDAD_I
        imc < 40.0 -> ClaseImc.OBESIDAD_II
        else -> ClaseImc.OBESIDAD_III
    }

    /** RF-15: true si el IMC externo difiere del calculado más que el umbral → «verifique su talla». */
    fun contrasteAlerta(imcCalculado: Double, imcExterno: Double, umbral: Double = UMBRAL_CONTRASTE_DEFECTO): Boolean =
        abs(imcCalculado - imcExterno) > umbral + 1e-9

    fun redondear1(x: Double): Double = (x * 10.0).roundToInt() / 10.0
}
