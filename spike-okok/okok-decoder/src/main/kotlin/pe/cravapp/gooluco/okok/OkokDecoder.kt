package pe.cravapp.gooluco.okok

/**
 * Decodificador de anuncios BLE de balanzas con protocolo OKOK (chip Chipsea y marcas OEM).
 *
 * Implementación propia, escrita a partir de la especificación resumida en el SRS v2
 * (SRS-OKOK-DM-002, §2.2, Tabla 1) y contrastada con estas fuentes abiertas (no se copió código):
 *  - openScale, OkOkHandler.kt (GPLv3): https://github.com/oliexdev/openScale
 *  - okokscale.py, parser.py (Apache-2.0): https://github.com/rrooggiieerr/okokscale.py
 *  - okok-bia-formulas (formato C0): https://github.com/100nandoo/okok-bia-formulas
 *
 * Todos los índices son sobre los bytes que siguen al Company ID de 2 bytes del AD 0xFF.
 * Android ya entrega el Company ID como entero (los dos bytes little-endian).
 */
object OkokDecoder {

    const val COMPANY_V20 = 0x20CA
    const val COMPANY_V11 = 0x11CA
    const val COMPANY_VF0 = 0xF0FF

    const val V20_LENGTH = 19
    const val V11_LENGTH = 23
    const val C0_LENGTH = 13
    const val VF0_EXPECTED_LENGTH = 18

    private const val LB_TO_KG = 0.45359237

    /** Identifica la variante por el Company ID, sin validar el contenido. */
    fun variantOf(companyId: Int): OkokVariant? = when {
        companyId == COMPANY_V20 -> OkokVariant.V20
        companyId == COMPANY_V11 -> OkokVariant.V11
        companyId == COMPANY_VF0 -> OkokVariant.VF0
        (companyId and 0xFF) == 0xC0 -> OkokVariant.C0
        else -> null
    }

    /**
     * Decodifica un bloque de manufacturer data.
     *
     * @param companyId     Company ID (clave del SparseArray de ScanRecord.manufacturerSpecificData).
     * @param data          Bytes que siguen al Company ID.
     * @param advertiserMac MAC del anunciante (opcional), para verificar la MAC embebida en C0.
     */
    fun decode(companyId: Int, data: ByteArray, advertiserMac: String? = null): DecodeResult =
        when (variantOf(companyId)) {
            OkokVariant.V20 -> decodeV20(companyId, data)
            OkokVariant.V11 -> decodeV11(companyId, data)
            OkokVariant.VF0 -> decodeVF0(companyId, data)
            OkokVariant.C0 -> decodeC0(companyId, data, advertiserMac)
            null -> DecodeResult.NotOkok
        }

    /** Decodifica todos los bloques de manufacturer data de un anuncio. */
    fun decodeAll(manufacturerData: Map<Int, ByteArray>, advertiserMac: String? = null): List<DecodeResult> =
        manufacturerData.map { (id, bytes) -> decode(id, bytes, advertiserMac) }

    // ---------------------------------------------------------------- V20 (0x20CA)

    /**
     * V20: 19 bytes. [6] bit0 = final/estable, bit2 = dos decimales (si no, uno);
     * [8..9] peso u16 BE; [10..11] impedancia u16 BE en décimas de ohmio;
     * [12] XOR que arranca en 0x20 (versión implícita). Unas firmwares lo calculan sobre
     * [0..11] y otras sobre [0..10]; se aceptan ambas y se anota cuál coincidió.
     */
    private fun decodeV20(companyId: Int, d: ByteArray): DecodeResult {
        if (d.size != V20_LENGTH) {
            return DecodeResult.Rejected(OkokVariant.V20, "longitud ${d.size} != $V20_LENGTH")
        }
        val expectedFull = xorSeeded(0x20, d, 0, 12)
        val expectedShort = xorSeeded(0x20, d, 0, 11)
        val got = d.u8(12)
        val notes = mutableListOf<String>()
        when (got) {
            expectedFull -> notes += "checksum XOR sobre bytes 0..11"
            expectedShort -> notes += "checksum XOR sobre bytes 0..10 (variante de firmware)"
            else -> return DecodeResult.Rejected(
                OkokVariant.V20,
                "checksum inválido: recibido %02X, esperado %02X o %02X".format(got, expectedFull, expectedShort),
            )
        }
        val flags = d.u8(6)
        val divisor = if (flags and 0x04 != 0) 100 else 10
        val raw = d.u16be(8)
        val impRaw = d.u16be(10)
        return DecodeResult.Ok(
            OkokFrame(
                variant = OkokVariant.V20,
                companyId = companyId,
                payload = d.copyOf(),
                rawWeight = raw,
                divisor = divisor,
                unit = ScaleUnit.KG,
                weightKg = raw.toDouble() / divisor,
                impedanceOhm = if (impRaw == 0) null else impRaw / 10.0,
                stable = flags and 0x01 != 0,
                checksumOk = true,
                notes = notes,
            ),
        )
    }

    // ---------------------------------------------------------------- V11 (0x11CA)

    /**
     * V11: 23 bytes. [3..4] peso u16 BE; [9] propiedades: bits1-2 decimales, bits3-4 unidad;
     * [16] XOR que arranca en 0xCA xor 0x11. Bit 0 de [1] = final (solo según okokscale.py).
     */
    private fun decodeV11(companyId: Int, d: ByteArray): DecodeResult {
        if (d.size != V11_LENGTH) {
            return DecodeResult.Rejected(OkokVariant.V11, "longitud ${d.size} != $V11_LENGTH")
        }
        val expected = xorSeeded(0xCA xor 0x11, d, 0, 15)
        val got = d.u8(16)
        if (got != expected) {
            return DecodeResult.Rejected(
                OkokVariant.V11,
                "checksum inválido: recibido %02X, esperado %02X".format(got, expected),
            )
        }
        val props = d.u8(9)
        val notes = mutableListOf("flag estable tomado de byte 1 bit 0 (no confirmado)")
        val (divisor, divNote) = divisorFromBits((props shr 1) and 0x3)
        divNote?.let { notes += it }
        val unit = unitFromBits((props shr 3) and 0x3)
        val raw = d.u16be(3)
        return DecodeResult.Ok(
            OkokFrame(
                variant = OkokVariant.V11,
                companyId = companyId,
                payload = d.copyOf(),
                rawWeight = raw,
                divisor = divisor,
                unit = unit,
                weightKg = toKg(raw, d.u8(3), d.u8(4), divisor, unit),
                impedanceOhm = null,
                stable = d.u8(1) and 0x01 != 0,
                checksumOk = true,
                notes = notes,
            ),
        )
    }

    // ---------------------------------------------------------------- VF0 (0xF0FF)

    /**
     * VF0: peso u16 con el byte alto en [3] y el bajo en [2] (orden invertido), en décimas de kg.
     * Sin checksum, estabilidad ni impedancia documentados. okokscale.py espera 18 bytes.
     */
    private fun decodeVF0(companyId: Int, d: ByteArray): DecodeResult {
        if (d.size < 4) {
            return DecodeResult.Rejected(OkokVariant.VF0, "longitud ${d.size} < 4")
        }
        val notes = mutableListOf("sin flag de estabilidad: se usa repetición de peso")
        if (d.size != VF0_EXPECTED_LENGTH) notes += "longitud ${d.size} (okokscale.py espera $VF0_EXPECTED_LENGTH)"
        val raw = (d.u8(3) shl 8) or d.u8(2)
        if (raw == 0) return DecodeResult.Rejected(OkokVariant.VF0, "peso cero")
        return DecodeResult.Ok(
            OkokFrame(
                variant = OkokVariant.VF0,
                companyId = companyId,
                payload = d.copyOf(),
                rawWeight = raw,
                divisor = 10,
                unit = ScaleUnit.KG,
                weightKg = raw / 10.0,
                impedanceOhm = null,
                stable = null,
                checksumOk = null,
                notes = notes,
            ),
        )
    }

    // ---------------------------------------------------------------- C0 (0x??C0)

    /**
     * C0: Company ID con byte bajo 0xC0 (el byte alto es un contador). 13 bytes:
     * [0..1] peso u16 BE; [2..3] impedancia u16 BE en décimas de ohmio (bit 7 de [2] = cifrada);
     * [4..5] product ID; [6] atributos: bit0 estable, bits1-2 decimales (0→÷10, 1→÷1, 2→÷100),
     * bits3-4 unidad (0 kg, 1 jin, 2 lb, 3 st:lb); [7..12] MAC de la balanza.
     */
    private fun decodeC0(companyId: Int, d: ByteArray, advertiserMac: String?): DecodeResult {
        if (d.size < C0_LENGTH) {
            return DecodeResult.Rejected(OkokVariant.C0, "longitud ${d.size} < $C0_LENGTH")
        }
        val notes = mutableListOf<String>()
        if (d.size != C0_LENGTH) notes += "longitud ${d.size} (se esperaban $C0_LENGTH)"
        val raw = d.u16be(0)
        if (raw == 0) return DecodeResult.Rejected(OkokVariant.C0, "peso cero")
        val attrib = d.u8(6)
        val (divisor, divNote) = divisorFromBits((attrib shr 1) and 0x3)
        divNote?.let { notes += it }
        val unit = unitFromBits((attrib shr 3) and 0x3)
        val encrypted = d.u8(2) and 0x80 != 0
        val impRaw = d.u16be(2)
        val impedance = when {
            encrypted -> { notes += "impedancia cifrada (bit 7 de byte 2)"; null }
            impRaw == 0 -> null
            else -> impRaw / 10.0
        }
        val mac = (7..12).joinToString(":") { "%02X".format(d.u8(it)) }
        if (advertiserMac != null) {
            notes += if (advertiserMac.equals(mac, ignoreCase = true)) {
                "MAC embebida = MAC del anunciante"
            } else {
                "MAC embebida distinta a la del anunciante"
            }
        }
        return DecodeResult.Ok(
            OkokFrame(
                variant = OkokVariant.C0,
                companyId = companyId,
                payload = d.copyOf(),
                rawWeight = raw,
                divisor = divisor,
                unit = unit,
                weightKg = toKg(raw, d.u8(0), d.u8(1), divisor, unit),
                impedanceOhm = impedance,
                stable = attrib and 0x01 != 0,
                checksumOk = null,
                encrypted = encrypted,
                embeddedMac = mac,
                productId = d.u16be(4),
                counter = (companyId shr 8) and 0xFF,
                notes = notes,
            ),
        )
    }

    // ---------------------------------------------------------------- utilidades

    private fun divisorFromBits(bits: Int): Pair<Int, String?> = when (bits) {
        0 -> 10 to null
        1 -> 1 to null
        2 -> 100 to null
        else -> 10 to "bits de decimales = 3 (no documentado), se asume ÷10"
    }

    private fun unitFromBits(bits: Int): ScaleUnit = when (bits) {
        0 -> ScaleUnit.KG
        1 -> ScaleUnit.JIN
        2 -> ScaleUnit.LB
        else -> ScaleUnit.ST_LB
    }

    /** Convierte a kg. En st:lb el byte alto son stones y el bajo libras (con el divisor). */
    private fun toKg(raw: Int, msb: Int, lsb: Int, divisor: Int, unit: ScaleUnit): Double = when (unit) {
        ScaleUnit.KG -> raw.toDouble() / divisor
        ScaleUnit.JIN -> raw.toDouble() / divisor / 2.0
        ScaleUnit.LB -> raw.toDouble() / divisor * LB_TO_KG
        ScaleUnit.ST_LB -> (msb * 14.0 + lsb.toDouble() / divisor) * LB_TO_KG
    }

    internal fun xorSeeded(seed: Int, d: ByteArray, from: Int, toInclusive: Int): Int {
        var x = seed and 0xFF
        for (i in from..toInclusive) x = x xor d.u8(i)
        return x and 0xFF
    }

    private fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF
    private fun ByteArray.u16be(i: Int): Int = (u8(i) shl 8) or u8(i + 1)
}
