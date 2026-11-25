package pe.cravapp.gooluco.ble

import kotlin.math.roundToInt

/**
 * Genera tramas C0 byte a byte con el mismo formato que la balanza real (RC-01..RC-03):
 * [0-1] peso u16 BE (÷100), [2-3] impedancia u16 BE (÷10), [4-5] product id, [6] atributos
 * (bit 0 = estable), [7-12] MAC. El Company ID lleva 0xC0 en el byte bajo y un contador en el alto.
 */
object TramaC0 {
    const val MAC_SIMULADA = "5E:00:00:00:0E:01"
    private const val ATTR_PROVISIONAL = 0x24
    private const val ATTR_ESTABLE = 0x25

    fun companyId(contador: Int) = ((contador and 0xFF) shl 8) or 0xC0

    fun payload(pesoKg: Double, estable: Boolean, impedanciaOhm: Double? = null, mac: String = MAC_SIMULADA): ByteArray {
        val raw = (pesoKg * 100).roundToInt().coerceIn(0, 0xFFFF)
        val imp = ((impedanciaOhm ?: 0.0) * 10).roundToInt().coerceIn(0, 0xFFFF)
        val macBytes = mac.split(":").map { it.toInt(16).toByte() }
        require(macBytes.size == 6)
        return byteArrayOf(
            (raw shr 8).toByte(), raw.toByte(),
            (imp shr 8).toByte(), imp.toByte(),
            0x0A, 0x01,
            (if (estable) ATTR_ESTABLE else ATTR_PROVISIONAL).toByte(),
        ) + macBytes.toByteArray()
    }

    /** Secuencia de un pesaje: reposo → subida provisional → lecturas estables repetidas. */
    fun sesion(pesoObjetivoKg: Double, pasosSubida: Int = 5): List<Pair<Double, Boolean>> {
        val subida = (1..pasosSubida).map { i -> pesoObjetivoKg * i / (pasosSubida + 1) + (if (i == pasosSubida) 0.05 else 0.0) to false }
        return listOf(0.0 to false) + subida + List(3) { pesoObjetivoKg to true }
    }
}
