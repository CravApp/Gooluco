package pe.cravapp.gooluco.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pe.cravapp.gooluco.okok.DecodeResult
import pe.cravapp.gooluco.okok.OkokDecoder
import pe.cravapp.gooluco.okok.StableReadingDetector
import pe.cravapp.gooluco.okok.toHex

/**
 * Adaptador BLE OKOK (RF-04, RF-05, RF-07, RF-08, RF-13): escaneo pasivo de anuncios C0/V20/V11/VF0,
 * mismo enfoque validado en el spike (sin filtros de hardware: el Company ID de C0 cambia con el contador).
 */
class AdaptadorBleOkok(
    private val ctx: Context,
    private val scope: CoroutineScope,
    /** RF-08: si hay balanza vinculada, se ignoran las demás. */
    var macVinculada: String? = null,
    private val timeoutMs: Long = 60_000,
) : FuenteBalanza {
    override val tipo = TipoFuente.REAL
    private val _estado = MutableStateFlow<EstadoFuente>(EstadoFuente.Inactiva)
    override val estado: StateFlow<EstadoFuente> = _estado
    private val _lecturas = MutableSharedFlow<LecturaOkok>(extraBufferCapacity = 128)
    override val lecturas: SharedFlow<LecturaOkok> = _lecturas
    private val detector = StableReadingDetector()
    private var timeoutJob: Job? = null
    private var ultimaTramaMs = 0L

    private val adapter get() = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    fun bluetoothEncendido(): Boolean = adapter?.isEnabled == true

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = manejar(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { manejar(it) }
        override fun onScanFailed(errorCode: Int) {
            _estado.value = EstadoFuente.Error("BLE-SCAN-$errorCode", "Error de escaneo BLE (código $errorCode). Reinicia el Bluetooth.")
        }
    }

    private fun manejar(r: ScanResult) {
        val record = r.scanRecord ?: return
        val mac = r.device.address
        if (macVinculada != null && !macVinculada.equals(mac, ignoreCase = true)) return
        val m = record.manufacturerSpecificData ?: return
        for (i in 0 until m.size()) {
            val id = m.keyAt(i)
            val data = m.valueAt(i) ?: continue
            val res = OkokDecoder.decode(id, data, mac)
            if (res is DecodeResult.Ok) {
                ultimaTramaMs = System.currentTimeMillis()
                _estado.value = EstadoFuente.Recibiendo(mac)
                _lecturas.tryEmit(
                    LecturaOkok(res.frame, mac, r.rssi, detector.accept(mac, res.frame), TipoFuente.REAL, ultimaTramaMs, data.toHex())
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun iniciar() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || !bluetoothEncendido()) {
            _estado.value = EstadoFuente.Error("BLE-OFF", "Bluetooth apagado o no disponible.")
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .setLegacy(false)
            .build()
        try {
            detector.reset()
            scanner.startScan(null, settings, callback)
            _estado.value = EstadoFuente.Escaneando
            ultimaTramaMs = System.currentTimeMillis()
            timeoutJob?.cancel()
            timeoutJob = scope.launch {
                while (true) {
                    delay(5_000)
                    if (System.currentTimeMillis() - ultimaTramaMs > timeoutMs) {
                        detenerEscaneo()
                        _estado.value = EstadoFuente.Error("BLE-TIMEOUT", "Sin tramas OKOK en ${timeoutMs / 1000} s. ¿La balanza está encendida?")
                        break
                    }
                }
            }
        } catch (e: SecurityException) {
            _estado.value = EstadoFuente.Error("BLE-PERM", "Falta permiso de Bluetooth.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun detenerEscaneo() {
        try { adapter?.bluetoothLeScanner?.stopScan(callback) } catch (_: Exception) { }
    }

    override fun detener() {
        timeoutJob?.cancel(); timeoutJob = null
        detenerEscaneo()
        _estado.value = EstadoFuente.Inactiva
    }
}
