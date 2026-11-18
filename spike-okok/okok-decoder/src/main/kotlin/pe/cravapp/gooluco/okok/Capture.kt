package pe.cravapp.gooluco.okok

/** Origen de una captura. */
enum class CaptureSource { BLE_ADV, SIMULATOR, GATT_NOTIFY }

/**
 * Una entrada del log del spike: un bloque de manufacturer data (o una notificación GATT)
 * con su contexto y el resultado del decodificador.
 */
data class Capture(
    val id: Long,
    val timestampMs: Long,
    val source: CaptureSource,
    val mac: String,
    val deviceName: String?,
    val rssi: Int?,
    val companyId: Int?,
    val dataHex: String,
    val scanRecordHex: String?,
    val result: DecodeResult,
    val stableReading: Boolean = false,
) {
    val frame: OkokFrame? get() = (result as? DecodeResult.Ok)?.frame
}

/** Exportación CSV (separador coma, RFC 4180, números con punto decimal). */
object CsvLog {
    val HEADER = listOf(
        "id", "timestamp_ms", "timestamp_iso", "source", "mac", "name", "rssi",
        "company_id", "variant", "status", "reason", "weight_raw", "divisor", "unit",
        "weight_kg", "impedance_ohm", "stable_flag", "stable_reading", "checksum_ok",
        "encrypted", "embedded_mac", "product_id", "counter", "data_hex", "scan_record_hex", "notes",
    ).joinToString(",")

    fun row(c: Capture, isoTime: String): String {
        val f = c.frame
        val (variant, status, reason) = when (val r = c.result) {
            is DecodeResult.Ok -> Triple(r.frame.variant.name, "OK", "")
            is DecodeResult.Rejected -> Triple(r.variant.name, "RECHAZADA", r.reason)
            DecodeResult.NotOkok ->
                if (c.source == CaptureSource.GATT_NOTIFY) Triple("GATT", "GATT_RAW", "") else Triple("", "NO_OKOK", "")
        }
        return listOf(
            c.id.toString(),
            c.timestampMs.toString(),
            isoTime,
            c.source.name,
            c.mac,
            c.deviceName ?: "",
            c.rssi?.toString() ?: "",
            c.companyId?.let { "0x%04X".format(it) } ?: "",
            variant,
            status,
            reason,
            f?.rawWeight?.toString() ?: "",
            f?.divisor?.toString() ?: "",
            f?.unit?.label ?: "",
            f?.weightKg?.let { "%.3f".format(java.util.Locale.ROOT, it) } ?: "",
            f?.impedanceOhm?.let { "%.1f".format(java.util.Locale.ROOT, it) } ?: "",
            f?.stable?.toString() ?: "",
            c.stableReading.toString(),
            f?.checksumOk?.toString() ?: "",
            f?.encrypted?.toString() ?: "",
            f?.embeddedMac ?: "",
            f?.productId?.let { "0x%04X".format(it) } ?: "",
            f?.counter?.toString() ?: "",
            c.dataHex,
            c.scanRecordHex ?: "",
            f?.notes?.joinToString("; ") ?: "",
        ).joinToString(",") { escape(it) }
    }

    private fun escape(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
