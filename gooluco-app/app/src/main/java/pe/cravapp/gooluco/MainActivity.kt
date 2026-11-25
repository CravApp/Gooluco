package pe.cravapp.gooluco

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import pe.cravapp.gooluco.domain.TipoSlot
import pe.cravapp.gooluco.notif.Agendador
import pe.cravapp.gooluco.ui.AppEquilibra
import pe.cravapp.gooluco.ui.AppViewModel
import pe.cravapp.gooluco.ui.theme.TemaEquilibra

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var trasPermisos: (() -> Unit)? = null

    private val permisosBle = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) continuarBle() else trasPermisos = null
    }
    private val activarBt = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (vm.bluetoothEncendido()) trasPermisos?.invoke()
        trasPermisos = null
    }
    private val permisoNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun permisosBleRequeridos(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION,
        ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun faltantes() = permisosBleRequeridos().filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    private fun pedirBle(despues: () -> Unit) {
        trasPermisos = despues
        val f = faltantes()
        if (f.isNotEmpty()) permisosBle.launch(f.toTypedArray()) else continuarBle()
    }

    private fun continuarBle() {
        if (vm.bluetoothEncendido()) { trasPermisos?.invoke(); trasPermisos = null; return }
        try { activarBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) } catch (_: SecurityException) { trasPermisos = null }
    }

    private fun pedirNotificaciones() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permisoNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun destinoDesde(i: Intent?): String? {
        val slot = i?.getStringExtra(Agendador.EXTRA_SLOT) ?: return null
        return when (i.getStringExtra(Agendador.EXTRA_TIPO)?.let { runCatching { TipoSlot.valueOf(it) }.getOrNull() }) {
            TipoSlot.EMA -> "ema?slot=$slot"
            TipoSlot.PESAJE_MANANA, TipoSlot.PESAJE_NOCHE -> "balanza"
            null -> null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Agendador.crearCanal(this)
        val destino = destinoDesde(intent)
        setContent {
            TemaEquilibra {
                Surface {
                    AppEquilibra(vm, destino, ::pedirBle, ::pedirNotificaciones)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onResume() {
        super.onResume()
        vm.refrescar()
    }
}
