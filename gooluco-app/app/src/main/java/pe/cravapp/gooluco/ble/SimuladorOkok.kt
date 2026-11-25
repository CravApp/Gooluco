package pe.cravapp.gooluco.ble

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

/** Simulador OKOK (RF-11): emite tramas C0 reales byte a byte y las pasa por el mismo decodificador. */
class SimuladorOkok(
    private val scope: CoroutineScope,
    var pesoObjetivoKg: Double = 72.4,
    private val intervaloMs: Long = 600,
) : FuenteBalanza {
    override val tipo = TipoFuente.SIMULADA
    private val _estado = MutableStateFlow<EstadoFuente>(EstadoFuente.Inactiva)
    override val estado: StateFlow<EstadoFuente> = _estado
    private val _lecturas = MutableSharedFlow<LecturaOkok>(extraBufferCapacity = 64)
    override val lecturas: SharedFlow<LecturaOkok> = _lecturas
    private var job: Job? = null

    override fun iniciar() {
        detener()
        val detector = StableReadingDetector()
        _estado.value = EstadoFuente.Escaneando
        job = scope.launch {
            var contador = 1
            for ((peso, estable) in TramaC0.sesion(pesoObjetivoKg)) {
                delay(intervaloMs)
                val cid = TramaC0.companyId(contador++)
                val data = TramaC0.payload(peso, estable, if (estable) 520.0 else null)
                val r = OkokDecoder.decode(cid, data, TramaC0.MAC_SIMULADA)
                if (r is DecodeResult.Ok) {
                    _estado.value = EstadoFuente.Recibiendo(TramaC0.MAC_SIMULADA)
                    _lecturas.emit(
                        LecturaOkok(r.frame, TramaC0.MAC_SIMULADA, -60, detector.accept(TramaC0.MAC_SIMULADA, r.frame),
                            TipoFuente.SIMULADA, System.currentTimeMillis(), data.toHex())
                    )
                }
            }
            _estado.value = EstadoFuente.Inactiva
        }
    }

    override fun detener() {
        job?.cancel(); job = null
        _estado.value = EstadoFuente.Inactiva
    }
}
