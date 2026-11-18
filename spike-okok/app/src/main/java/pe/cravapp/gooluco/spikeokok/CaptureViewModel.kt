package pe.cravapp.gooluco.spikeokok

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pe.cravapp.gooluco.okok.Capture
import pe.cravapp.gooluco.okok.CaptureSource
import pe.cravapp.gooluco.okok.CsvLog
import pe.cravapp.gooluco.okok.DecodeResult
import pe.cravapp.gooluco.okok.OkokDecoder
import pe.cravapp.gooluco.okok.OkokTestVectors
import pe.cravapp.gooluco.okok.StableReadingDetector
import pe.cravapp.gooluco.okok.toHex
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Estado y lógica del spike: escaneo BLE, simulador, conexión GATT opcional y exportación CSV.
 * Toda mutación de estado ocurre en el hilo principal.
 */
class CaptureViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val UI_LIMIT = 1_000
        private const val LOG_LIMIT = 50_000
        val SERVICE_FFF0: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val CHAR_FFF1: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val main = Handler(Looper.getMainLooper())
    private val detector = StableReadingDetector()
    private val fullLog = ArrayList<Capture>()
    private var nextId = 1L

    /** Lista visible (más reciente primero). */
    val captures = mutableStateListOf<Capture>()
    var scanning by mutableStateOf(false); private set
    var simulating by mutableStateOf(false); private set
    var onlyOkok by mutableStateOf(true)
    var lastStable by mutableStateOf<Capture?>(null); private set
    var status by mutableStateOf("Listo. Pulsa «Escanear» (balanza real) o «Simular».")
    var totalCount by mutableIntStateOf(0); private set
    var okokCount by mutableIntStateOf(0); private set
    var stableCount by mutableIntStateOf(0); private set
    var gattState by mutableStateOf<String?>(null); private set

    private var simJob: Job? = null
    private var gatt: BluetoothGatt? = null

    private val adapter: BluetoothAdapter?
        get() = (getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true

    // ------------------------------------------------------------------ registro

    private fun record(
        source: CaptureSource,
        mac: String,
        name: String?,
        rssi: Int?,
        companyId: Int?,
        data: ByteArray,
        scanRecord: ByteArray?,
        result: DecodeResult,
    ) {
        if (onlyOkok && result == DecodeResult.NotOkok && source != CaptureSource.GATT_NOTIFY) return
        val stable = (result as? DecodeResult.Ok)?.let { detector.accept(mac, it.frame) } ?: false
        val c = Capture(
            id = nextId++,
            timestampMs = System.currentTimeMillis(),
            source = source,
            mac = mac,
            deviceName = name,
            rssi = rssi,
            companyId = companyId,
            dataHex = data.toHex(),
            scanRecordHex = scanRecord?.toHex(),
            result = result,
            stableReading = stable,
        )
        totalCount++
        if (result !is DecodeResult.NotOkok) okokCount++
        if (stable) { stableCount++; lastStable = c }
        if (fullLog.size >= LOG_LIMIT) fullLog.removeAt(0)
        fullLog.add(c)
        captures.add(0, c)
        if (captures.size > UI_LIMIT) captures.removeRange(UI_LIMIT, captures.size)
    }

    fun clear() {
        captures.clear(); fullLog.clear(); detector.reset()
        lastStable = null; totalCount = 0; okokCount = 0; stableCount = 0
        status = "Log borrado."
    }

    // ------------------------------------------------------------------ escaneo BLE

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            main.post { handle(result) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            main.post { results.forEach { handle(it) } }
        }

        override fun onScanFailed(errorCode: Int) {
            main.post {
                scanning = false
                status = "Error de escaneo BLE: código $errorCode" +
                    if (errorCode == SCAN_FAILED_APPLICATION_REGISTRATION_FAILED) " (reinicia Bluetooth)" else ""
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handle(r: ScanResult) {
        val record = r.scanRecord ?: return
        val mac = r.device.address
        val name = record.deviceName ?: runCatching { r.device.name }.getOrNull()
        val m = record.manufacturerSpecificData
        if (m == null || m.size() == 0) {
            if (!onlyOkok) record(CaptureSource.BLE_ADV, mac, name, r.rssi, null, ByteArray(0), record.bytes, DecodeResult.NotOkok)
            return
        }
        for (i in 0 until m.size()) {
            val id = m.keyAt(i)
            val data = m.valueAt(i) ?: ByteArray(0)
            record(CaptureSource.BLE_ADV, mac, name, r.rssi, id, data, record.bytes, OkokDecoder.decode(id, data, mac))
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || !isBluetoothOn()) {
            status = "Bluetooth apagado o no disponible."
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setLegacy(false) }
            .build()
        try {
            // Sin filtros: el Company ID de C0 cambia en cada trama (contador), así que se filtra en software.
            scanner.startScan(null, settings, scanCallback)
            scanning = true
            status = "Escaneando… súbete a la balanza."
        } catch (e: SecurityException) {
            status = "Falta permiso de Bluetooth: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try { adapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) { }
        scanning = false
        status = "Escaneo detenido."
    }

    // ------------------------------------------------------------------ simulador

    fun toggleSimulator() {
        if (simulating) { simJob?.cancel(); simulating = false; status = "Simulador detenido."; return }
        simulating = true
        status = "Simulador: inyectando TV-01..TV-04 y tramas sintéticas V20/V11/VF0…"
        simJob = viewModelScope.launch {
            try {
                for (v in OkokTestVectors.simulatorSequence()) {
                    record(CaptureSource.SIMULATOR, v.mac, "${v.name.ifEmpty { "SIM" }} · ${v.id}", (-72..-48).random(),
                        v.companyId, v.data, null, OkokDecoder.decode(v.companyId, v.data, v.mac))
                    delay(700)
                }
                status = "Simulador terminó la secuencia."
            } finally {
                simulating = false
            }
        }
    }

    // ------------------------------------------------------------------ GATT opcional (Chipsea FFF0)

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, st: Int, newState: Int) {
            main.post {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> { gattState = "Conectado a ${g.device.address}; descubriendo servicios…"; g.discoverServices() }
                    BluetoothProfile.STATE_DISCONNECTED -> { gattState = "Desconectado (status $st)"; g.close(); if (gatt === g) gatt = null }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, st: Int) {
            main.post {
                val list = g.services.joinToString("; ") { s ->
                    shortUuid(s.uuid) + "[" + s.characteristics.joinToString(",") { shortUuid(it.uuid) } + "]"
                }
                val ch = g.getService(SERVICE_FFF0)?.getCharacteristic(CHAR_FFF1)
                if (ch == null) {
                    gattState = "Sin servicio FFF0/FFF1. Servicios: $list"
                    return@post
                }
                g.setCharacteristicNotification(ch, true)
                val d = ch.getDescriptor(CCCD)
                if (d != null) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        g.writeDescriptor(d)
                    }
                }
                gattState = "Suscrito a FFF1 (notificaciones crudas). Servicios: $list"
            }
        }

        @Deprecated("API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val v = c.value ?: return
            onNotify(g, c, v)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            onNotify(g, c, value)
        }

        private fun onNotify(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            val copy = value.copyOf()
            main.post {
                record(CaptureSource.GATT_NOTIFY, g.device.address, "GATT ${shortUuid(c.uuid)}", null, null, copy, null,
                    DecodeResult.NotOkok)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectGatt(mac: String) {
        disconnectGatt()
        val dev: BluetoothDevice = adapter?.getRemoteDevice(mac) ?: return
        gattState = "Conectando a $mac…"
        gatt = dev.connectGatt(getApplication(), false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnectGatt() {
        gatt?.let { runCatching { it.disconnect() }; runCatching { it.close() } }
        gatt = null
        gattState = null
    }

    private fun shortUuid(u: UUID): String {
        val s = u.toString()
        return if (s.endsWith("-0000-1000-8000-00805f9b34fb")) s.substring(4, 8).uppercase() else s
    }

    // ------------------------------------------------------------------ exportación

    /** Escribe el log completo a un CSV en cache/exports y devuelve su Uri para compartir. */
    fun exportCsv(): Uri? {
        if (fullLog.isEmpty()) { status = "No hay tramas para exportar."; return null }
        val ctx = getApplication<Application>()
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
        val model = "${Build.MANUFACTURER}_${Build.MODEL}".replace(Regex("[^A-Za-z0-9_-]"), "")
        val file = File(dir, "okok_capturas_${model}_$stamp.csv")
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT).apply { timeZone = TimeZone.getDefault() }
        file.bufferedWriter().use { w ->
            w.write(CsvLog.HEADER); w.write("\n")
            for (c in fullLog) { w.write(CsvLog.row(c, iso.format(Date(c.timestampMs)))); w.write("\n") }
        }
        status = "Exportadas ${fullLog.size} filas a ${file.name}"
        return FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
    }

    override fun onCleared() {
        stopScan(); disconnectGatt(); super.onCleared()
    }
}
