package pe.cravapp.gooluco.spikeokok

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import pe.cravapp.gooluco.okok.Capture
import pe.cravapp.gooluco.okok.CaptureSource
import pe.cravapp.gooluco.okok.DecodeResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val vm: CaptureViewModel by viewModels()

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun missingPermissions(): List<String> = requiredPermissions().filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val permLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions(),
                    ) { granted ->
                        val missing = granted.filterValues { !it }.keys
                        if (missing.isEmpty()) startScanChecked() else {
                            vm.status = "Permisos denegados: ${missing.joinToString { it.substringAfterLast('.') }}. " +
                                "Sin ellos no se puede escanear (el simulador sí funciona)."
                        }
                    }
                    val enableBtLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult(),
                    ) { if (vm.isBluetoothOn()) vm.startScan() else vm.status = "Bluetooth sigue apagado." }

                    SpikeScreen(
                        vm = vm,
                        onScan = {
                            if (vm.scanning) vm.stopScan()
                            else {
                                val missing = missingPermissions()
                                if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
                                else if (!vm.isBluetoothOn()) {
                                    try { enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                                    catch (e: SecurityException) { vm.status = "Activa Bluetooth manualmente." }
                                } else vm.startScan()
                            }
                        },
                        onExport = {
                            vm.exportCsv()?.let { uri ->
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Capturas OKOK (spike)")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(Intent.createChooser(send, "Compartir CSV"))
                            }
                        },
                        onGatt = { mac ->
                            if (missingPermissions().isEmpty()) vm.connectGatt(mac)
                            else permLauncher.launch(missingPermissions().toTypedArray())
                        },
                    )
                }
            }
        }
    }

    private fun startScanChecked() {
        if (vm.isBluetoothOn()) vm.startScan() else vm.status = "Permisos OK. Activa Bluetooth y vuelve a pulsar «Escanear»."
    }
}

private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
private val Green = Color(0xFF2E7D32)
private val Orange = Color(0xFFEF6C00)
private val Red = Color(0xFFC62828)

@Composable
fun SpikeScreen(vm: CaptureViewModel, onScan: () -> Unit, onExport: () -> Unit, onGatt: (String) -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("Spike OKOK · captura BLE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            Button(onClick = onScan) { Text(if (vm.scanning) "Detener" else "Escanear") }
            OutlinedButton(onClick = vm::toggleSimulator) { Text(if (vm.simulating) "Parar sim." else "Simular") }
            OutlinedButton(onClick = onExport) { Text("CSV") }
            TextButton(onClick = vm::clear) { Text("Limpiar") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.onlyOkok, onCheckedChange = { vm.onlyOkok = it })
            Spacer(Modifier.width(6.dp))
            Text("Solo OKOK", fontSize = 13.sp)
            Spacer(Modifier.width(12.dp))
            Text("${vm.totalCount} tramas · ${vm.okokCount} OKOK · ${vm.stableCount} estables", fontSize = 13.sp)
        }
        Text(vm.status, fontSize = 12.sp, color = Color.DarkGray)
        vm.gattState?.let { g ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("GATT: $g", fontSize = 12.sp, color = Color(0xFF1565C0), modifier = Modifier.weight(1f))
                TextButton(onClick = vm::disconnectGatt) { Text("Cerrar") }
            }
        }
        LastStableCard(vm.lastStable)
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vm.captures, key = { it.id }) { c -> CaptureRow(c, onGatt) }
        }
    }
}

@Composable
private fun LastStableCard(c: Capture?) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Última lectura estable", fontSize = 12.sp, color = Green)
            val f = c?.frame
            if (c == null || f == null) {
                Text("—", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("%.2f kg".format(f.weightKg), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(
                        f.impedanceOhm?.let { "%.1f Ω".format(it) },
                        f.variant.label, c.mac, timeFmt.format(Date(c.timestampMs)),
                    ).joinToString(" · "),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun CaptureRow(c: Capture, onGatt: (String) -> Unit) {
    val (badge, color) = when (val r = c.result) {
        is DecodeResult.Ok -> when {
            c.stableReading -> "LECTURA ESTABLE" to Green
            r.frame.stable == true -> "estable (repetida)" to Green
            r.frame.stable == null -> "sin flag" to Color.Gray
            else -> "provisional" to Orange
        }
        is DecodeResult.Rejected -> "RECHAZADA: ${r.reason}" to Red
        DecodeResult.NotOkok -> (if (c.source == CaptureSource.GATT_NOTIFY) "GATT crudo" else "no OKOK") to Color.Gray
    }
    Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Text(
                "${timeFmt.format(Date(c.timestampMs))} · ${c.source.name} · ${c.mac}" +
                    (c.rssi?.let { " · $it dBm" } ?: "") + (c.deviceName?.let { " · $it" } ?: ""),
                fontSize = 11.sp, color = Color.DarkGray,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val variant = (c.result as? DecodeResult.Ok)?.frame?.variant?.label
                    ?: (c.result as? DecodeResult.Rejected)?.variant?.label ?: ""
                Text(
                    "$variant ${c.companyId?.let { "0x%04X".format(it) } ?: ""}",
                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    badge, color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.background(color, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(c.dataHex.ifEmpty { "(sin manufacturer data)" }, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            c.frame?.let { f ->
                Text(
                    buildString {
                        append("%.2f kg".format(f.weightKg))
                        append(" (crudo ${f.rawWeight} ÷${f.divisor}, ${f.unit?.label ?: "?"})")
                        f.impedanceOhm?.let { append(" · %.1f Ω".format(it)) }
                        f.checksumOk?.let { append(" · checksum ${if (it) "OK" else "MAL"}") }
                        if (f.encrypted) append(" · cifrada")
                        f.embeddedMac?.let { append(" · MAC trama $it") }
                        f.productId?.let { append(" · prod 0x%04X".format(it)) }
                        f.counter?.let { append(" · cnt $it") }
                    },
                    fontSize = 12.sp,
                )
                if (f.notes.isNotEmpty()) Text(f.notes.joinToString("; "), fontSize = 10.sp, color = Color.Gray)
            }
            if (c.source == CaptureSource.BLE_ADV && c.frame != null) {
                TextButton(onClick = { onGatt(c.mac) }) { Text("Probar GATT FFF0 con esta balanza", fontSize = 12.sp) }
            }
        }
    }
}
