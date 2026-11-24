package pe.cravapp.gooluco.okok

/**
 * Tramas de prueba para tests y para el modo simulador.
 *
 * TV-01..TV-04 son capturas reales publicadas (SRS v2, Tabla 2):
 *  - TV-01: okok-bia-formulas (ejemplo documentado del formato C0).
 *  - TV-02: openScale issue #1177 («Yoda0»).
 *  - TV-03, TV-04: openScale issue #950.
 * Las tramas SYN-* son SINTÉTICAS: se construyeron con este mismo decodificador a partir de la
 * especificación (no son capturas reales) y sirven solo para ejercitar V20/V11/VF0.
 */
object OkokTestVectors {

    data class Vector(
        val id: String,
        val companyId: Int,
        val dataHex: String,
        val mac: String,
        val name: String,
        val real: Boolean,
        val description: String,
    ) {
        val data: ByteArray get() = dataHex.hexToBytes()
    }

    val TV01 = Vector("TV-01", 0x07C0, "1A 31 17 70 0A 01 25 24 16 51 4B 12 3C", "24:16:51:4B:12:3C", "Chipsea-BLE", true,
        "C0 67,05 kg estable, 600,0 Ω")
    val TV02 = Vector("TV-02", 0x3FC0, "1D CE 13 88 00 00 25 00 00 00 00 00 00", "A1:B2:C3:00:00:02", "Yoda0", true,
        "C0 76,30 kg estable, 500,0 Ω")
    val TV03 = Vector("TV-03", 0xB1C0, "1F D6 17 70 0A 01 25 5D 02 49 A6 3D E8", "5D:02:49:A6:3D:E8", "ADV", true,
        "C0 81,50 kg estable")
    val TV04 = Vector("TV-04", 0x08C0, "13 D3 17 70 0A 01 24 5D 02 49 A6 3D E8", "5D:02:49:A6:3D:E8", "ADV", true,
        "C0 50,75 kg NO estable")

    // Captura real propia (moto g86 power 5G, 23/11/2026, balanza C0 product ID 0x0A01, sin nombre BLE).
    // MAC enmascarada: se conserva el OUI 78:66:A5 y se reemplazan los 3 últimos bytes por 00:00:01,
    // tanto en la MAC del anunciante como en los bytes 7..12 de la trama.
    private const val REAL_MAC = "78:66:A5:00:00:01"

    /** Real: balanza encendida sin carga (peso 0). Debe descartarse como reposo. */
    val RC01 = Vector("RC-01", 0x01C0, "00 00 00 00 0A 01 24 78 66 A5 00 00 01", REAL_MAC, "", true,
        "C0 real en reposo, peso 0")

    /** Real: pesaje provisional 92,85 kg, sin impedancia, no estable. */
    val RC02 = Vector("RC-02", 0x06C0, "24 45 00 00 0A 01 24 78 66 A5 00 00 01", REAL_MAC, "", true,
        "C0 real provisional 92,85 kg")

    /** Real: lectura final 92,80 kg, estable, impedancia 0x1770 (600,0 Ω, sospechosa de valor por defecto). */
    val RC03 = Vector("RC-03", 0x04C0, "24 40 17 70 0A 01 25 78 66 A5 00 00 01", REAL_MAC, "", true,
        "C0 real estable 92,80 kg, 600,0 Ω")

    /** V20 sintética: 72,40 kg (÷100), estable, 512,0 Ω. Checksum XOR(0x20, bytes 0..11). */
    val SYN_V20 = Vector("SYN-V20", OkokDecoder.COMPANY_V20, withV20Checksum("00 00 00 00 00 00 05 00 1C 48 14 00 00 00 00 00 00 00 00"),
        "C8:47:8C:00:20:20", "", false, "V20 sintética 72,40 kg estable 512,0 Ω")

    /** V20 sintética con checksum alterado: debe descartarse (RF-05). */
    val SYN_V20_BAD = SYN_V20.copy(id = "SYN-V20-BAD", dataHex = corruptByte(SYN_V20.dataHex, 12),
        description = "V20 sintética con checksum alterado")

    /** V11 sintética: 65,3 kg (÷10, kg), final. Checksum XOR(0xCA^0x11, bytes 0..15). */
    val SYN_V11 = Vector("SYN-V11", OkokDecoder.COMPANY_V11, withV11Checksum("00 01 00 02 8D 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"),
        "C8:47:8C:00:11:11", "", false, "V11 sintética 65,3 kg")

    /** VF0 sintética: 58,6 kg (0x024A con LSB en [2] y MSB en [3]). */
    val SYN_VF0 = Vector("SYN-VF0", OkokDecoder.COMPANY_VF0, "00 00 4A 02 00 00 00 00 00 00 00 00 00 00 00 00 00 00",
        "C8:47:8C:00:F0:F0", "", false, "VF0 sintética 58,6 kg")

    val REAL = listOf(TV01, TV02, TV03, TV04, RC01, RC02, RC03)
    val ALL = REAL + listOf(SYN_V20, SYN_V20_BAD, SYN_V11, SYN_VF0)

    /**
     * Secuencia para el simulador: un pesaje C0 que sube de forma provisional (no estable)
     * y se estabiliza en TV-01, más las demás variantes.
     */
    fun simulatorSequence(): List<Vector> {
        val rising = listOf(0x0BB8, 0x16A8, 0x1A0E, 0x1A2A).mapIndexed { i, raw ->
            val bytes = TV01.data.copyOf()
            bytes[0] = (raw shr 8).toByte(); bytes[1] = raw.toByte()
            bytes[6] = 0x24 // ÷100, kg, no estable
            bytes[2] = 0; bytes[3] = 0 // sin impedancia mientras sube
            TV01.copy(id = "SIM-${i + 1}", companyId = ((i + 3) shl 8) or 0xC0, dataHex = bytes.toHex(), real = false,
                description = "C0 provisional ${raw / 100.0} kg")
        }
        return listOf(RC01) + rising + listOf(TV01, TV01.copy(id = "TV-01 (repetida)"), TV04, TV03, TV02, SYN_V20, SYN_V20_BAD, SYN_V11,
            SYN_VF0, SYN_VF0.copy(id = "SYN-VF0 #2"), SYN_VF0.copy(id = "SYN-VF0 #3"), RC02, RC03, RC03.copy(id = "RC-03 (repetida)"))
    }

    private fun withV20Checksum(hex: String): String {
        val b = hex.hexToBytes()
        b[12] = OkokDecoder.xorSeeded(0x20, b, 0, 11).toByte()
        return b.toHex()
    }

    private fun withV11Checksum(hex: String): String {
        val b = hex.hexToBytes()
        b[16] = OkokDecoder.xorSeeded(0xCA xor 0x11, b, 0, 15).toByte()
        return b.toHex()
    }

    private fun corruptByte(hex: String, index: Int): String {
        val b = hex.hexToBytes()
        b[index] = (b[index].toInt() xor 0x5A).toByte()
        return b.toHex()
    }
}
