package pe.cravapp.gooluco.ble

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import pe.cravapp.gooluco.okok.OkokFrame

enum class TipoFuente { REAL, SIMULADA }

/** Una trama OKOK decodificada, con la marca de lectura estable (RF-07). */
data class LecturaOkok(
    val frame: OkokFrame,
    val mac: String,
    val rssi: Int?,
    val estable: Boolean,
    val fuente: TipoFuente,
    val timestampMs: Long,
    val dataHex: String,
)

sealed interface EstadoFuente {
    data object Inactiva : EstadoFuente
    data object Escaneando : EstadoFuente
    data class Recibiendo(val ultimaMac: String) : EstadoFuente
    /** RF-13: errores con código. */
    data class Error(val codigo: String, val mensaje: String) : EstadoFuente
}

/** Interfaz común de adquisición (RF-03), implementada por el adaptador BLE y el simulador. */
interface FuenteBalanza {
    val tipo: TipoFuente
    val estado: StateFlow<EstadoFuente>
    val lecturas: SharedFlow<LecturaOkok>
    fun iniciar()
    fun detener()
}
