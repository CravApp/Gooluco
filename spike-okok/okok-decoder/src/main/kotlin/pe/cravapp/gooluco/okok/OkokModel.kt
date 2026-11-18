package pe.cravapp.gooluco.okok

/** Variantes de anuncio OKOK soportadas por el spike (ver README, tabla de protocolo). */
enum class OkokVariant(val label: String) {
    C0("OKOK C0"),
    V20("OKOK V20"),
    V11("OKOK V11"),
    VF0("OKOK VF0"),
}

/** Unidad que la balanza indica en sus flags (bits 3-4 del byte de atributos). */
enum class ScaleUnit(val label: String) {
    KG("kg"),
    JIN("jin"),
    LB("lb"),
    ST_LB("st:lb"),
}

/**
 * Trama OKOK decodificada.
 *
 * @property companyId    Company ID de 16 bits tal como lo entrega Android (little-endian ya resuelto).
 * @property payload      Bytes de manufacturer data que siguen al Company ID.
 * @property rawWeight    Valor crudo del peso (u16) antes de aplicar divisor/unidad.
 * @property divisor      Divisor de decimales aplicado (1, 10 o 100).
 * @property unit         Unidad indicada por la trama (null si la variante no la informa).
 * @property weightKg     Peso convertido a kg.
 * @property impedanceOhm Impedancia en ohmios (null si la variante no la trae, viene cifrada o es 0).
 * @property stable       Flag de lectura final/estable (null si la variante no lo informa).
 * @property checksumOk   Resultado del checksum XOR (null si la variante no tiene checksum).
 * @property encrypted    Solo C0: bit 7 del byte 2 activo (impedancia cifrada según okokscale.py).
 * @property embeddedMac  Solo C0: MAC incluida en bytes 7..12.
 * @property productId    Solo C0: bytes 4..5 (big-endian).
 * @property counter      Solo C0: byte alto del Company ID (contador que cambia entre tramas).
 * @property notes        Observaciones para el log (supuestos no confirmados, longitudes inesperadas, etc.).
 */
data class OkokFrame(
    val variant: OkokVariant,
    val companyId: Int,
    val payload: ByteArray,
    val rawWeight: Int,
    val divisor: Int,
    val unit: ScaleUnit?,
    val weightKg: Double,
    val impedanceOhm: Double?,
    val stable: Boolean?,
    val checksumOk: Boolean?,
    val encrypted: Boolean = false,
    val embeddedMac: String? = null,
    val productId: Int? = null,
    val counter: Int? = null,
    val notes: List<String> = emptyList(),
) {
    val payloadHex: String get() = payload.toHex()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OkokFrame) return false
        return variant == other.variant && companyId == other.companyId &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * (31 * variant.hashCode() + companyId) + payload.contentHashCode()
}

/** Resultado de intentar decodificar un bloque de manufacturer data. */
sealed interface DecodeResult {
    /** Trama OKOK válida. */
    data class Ok(val frame: OkokFrame) : DecodeResult

    /** Parece OKOK (Company ID conocido) pero no pasa la validación. */
    data class Rejected(val variant: OkokVariant, val reason: String) : DecodeResult

    /** El Company ID no corresponde a ninguna variante OKOK conocida. */
    data object NotOkok : DecodeResult
}

fun ByteArray.toHex(separator: String = " "): String =
    joinToString(separator) { "%02X".format(it.toInt() and 0xFF) }

fun String.hexToBytes(): ByteArray {
    val clean = replace(" ", "").replace(":", "").replace("-", "")
    require(clean.length % 2 == 0) { "Hex con longitud impar: $this" }
    return ByteArray(clean.length / 2) { i -> clean.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
}
