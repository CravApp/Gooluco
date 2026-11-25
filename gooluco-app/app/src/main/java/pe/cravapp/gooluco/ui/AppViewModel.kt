package pe.cravapp.gooluco.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.cravapp.gooluco.ble.AdaptadorBleOkok
import pe.cravapp.gooluco.ble.EstadoFuente
import pe.cravapp.gooluco.ble.FuenteBalanza
import pe.cravapp.gooluco.ble.LecturaOkok
import pe.cravapp.gooluco.ble.SimuladorOkok
import pe.cravapp.gooluco.data.BaseDatos
import pe.cravapp.gooluco.data.EmaRespuestaEntity
import pe.cravapp.gooluco.data.FindriscEntity
import pe.cravapp.gooluco.data.NordicoEntity
import pe.cravapp.gooluco.data.PerfilEntity
import pe.cravapp.gooluco.data.PesajeEntity
import pe.cravapp.gooluco.data.Repositorio
import pe.cravapp.gooluco.domain.EstadoEma
import pe.cravapp.gooluco.domain.FindriscResultado
import pe.cravapp.gooluco.domain.RegionNordica
import pe.cravapp.gooluco.domain.RespuestaRegion
import pe.cravapp.gooluco.domain.SlotAgenda
import pe.cravapp.gooluco.ml.ModeloLiteRt
import pe.cravapp.gooluco.ml.ModeloRiesgo
import pe.cravapp.gooluco.notif.aDominio
import androidx.work.WorkInfo
import androidx.work.WorkManager
import pe.cravapp.gooluco.data.SyncEstadoEntity
import pe.cravapp.gooluco.fhir.Observation
import pe.cravapp.gooluco.fhir.MapeadorFhir
import pe.cravapp.gooluco.sync.AlmacenSeguro
import pe.cravapp.gooluco.sync.ClienteFhir
import pe.cravapp.gooluco.sync.ClienteSmart
import pe.cravapp.gooluco.sync.EstadoConexion
import pe.cravapp.gooluco.sync.EstadoItem
import pe.cravapp.gooluco.sync.ErrorSync
import pe.cravapp.gooluco.sync.PlanSync
import pe.cravapp.gooluco.sync.ProgramadorSync
import pe.cravapp.gooluco.sync.TrabajoSync
import java.time.ZonedDateTime
import kotlin.math.abs

data class Datos(
    val cargado: Boolean = false,
    val perfil: PerfilEntity? = null,
    val pesajes: List<PesajeEntity> = emptyList(),
    val emas: List<EmaRespuestaEntity> = emptyList(),
    val findrisc: List<FindriscEntity> = emptyList(),
    val nordico: List<NordicoEntity> = emptyList(),
    val slots: List<SlotAgenda> = emptyList(),
    val panel: Panel = Panel(),
)

/** Estado de la sincronización con el servidor FHIR de prueba. */
data class EstadoSyncUi(
    val conexion: EstadoConexion = EstadoConexion(),
    val pendientes: Int = 0,
    val sincronizados: Int = 0,
    val rechazados: List<SyncEstadoEntity> = emptyList(),
    val enCurso: Boolean = false,
    val servidor: List<Observation>? = null,
    val mensajeServidor: String? = null,
)

/** Estado de la sesión de pesaje en la pestaña Balanza. */
data class SesionPesaje(
    val simulador: Boolean = false,
    val enVivo: LecturaOkok? = null,
    val tramas: Int = 0,
    val porConfirmar: LecturaOkok? = null,
    val guardadoId: Long? = null,
    val mensaje: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repositorio(app, BaseDatos.get(app))
    val modelo: ModeloRiesgo = ModeloLiteRt.cargar(app)

    private val reloj = MutableStateFlow(ZonedDateTime.now(repo.cfg.zona))

    val datos: StateFlow<Datos> = combine(
        combine(repo.perfil, repo.pesajes, repo.emas) { p, w, e -> Triple(p, w, e) },
        combine(repo.findrisc, repo.nordico, repo.agenda) { f, n, a -> Triple(f, n, a) },
        reloj,
    ) { (p, w, e), (f, n, a), ahora ->
        Datos(true, p, w, e, f, n, a.map { it.aDominio() }, CalculoPanel.calcular(p, w, e, f, n, ahora, repo.cfg))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Datos())

    val almacen = AlmacenSeguro.get(app)
    private val servidor = MutableStateFlow<Pair<List<Observation>?, String?>>(null to null)

    val sync: StateFlow<EstadoSyncUi> = combine(
        almacen.estado,
        combine(datos, repo.tallas, repo.estadosSync) { d, t, e -> Triple(d, t, e) },
        WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(TrabajoSync.UNICO),
        servidor,
    ) { con, (d, tallas, estados), trabajos, srv ->
        val cands = PlanSync.candidatos(
            MapeadorFhir(almacen.idInstalacion), repo.cfg.zona.id, d.perfil, d.pesajes, tallas, d.emas, d.findrisc, d.nordico, con.desdeUtc,
        )
        val mapa = estados.associateBy { it.clave }
        EstadoSyncUi(
            conexion = con,
            pendientes = if (con.activa) PlanSync.contarPendientes(cands, mapa) else 0,
            sincronizados = estados.count { it.estado == EstadoItem.SINCRONIZADO.name },
            rechazados = estados.filter { it.estado == EstadoItem.ERROR.name }.sortedByDescending { it.actualizadoUtc },
            enCurso = trabajos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED },
            servidor = srv.first,
            mensajeServidor = srv.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoSyncUi())

    fun activarSync(incluirAnteriores: Boolean) {
        almacen.activar(if (incluirAnteriores) 0L else System.currentTimeMillis())
    }

    fun desactivarSync() {
        ProgramadorSync.cancelar(getApplication())
        almacen.desconectar(desactivar = true)
        servidor.value = null to null
    }

    /** Cierra la sesión: borra los tokens cifrados; la opción de sincronizar queda activada. */
    fun desconectarServidor() {
        ProgramadorSync.cancelar(getApplication())
        almacen.desconectar(desactivar = false)
        servidor.value = null to null
    }

    fun alConectar() {
        ProgramadorSync.periodica(getApplication())
        ProgramadorSync.ahora(getApplication())
    }

    fun sincronizarAhora(reintentarErrores: Boolean) = ProgramadorSync.ahora(getApplication(), reintentarErrores)

    fun verServidor() = viewModelScope.launch {
        val pac = almacen.estado.value.paciente ?: return@launch
        servidor.value = null to "Consultando…"
        servidor.value = try {
            val token = ClienteSmart(getApplication(), almacen).tokenVigente()
            ClienteFhir().pesos(pac, token) to null
        } catch (e: ErrorSync) { null to e.message } catch (e: Exception) { null to "Error: ${e.message}" }
    }

    private val ble = AdaptadorBleOkok(app, viewModelScope)
    private val sim = SimuladorOkok(viewModelScope)
    private val _sesion = MutableStateFlow(SesionPesaje())
    val sesion: StateFlow<SesionPesaje> = _sesion
    private var recolector: Job? = null

    private val fuente: FuenteBalanza get() = if (_sesion.value.simulador) sim else ble
    val estadoBle: StateFlow<EstadoFuente> = combine(ble.estado, sim.estado, _sesion.map { it.simulador }) { b, s, usaSim ->
        if (usaSim) s else b
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EstadoFuente.Inactiva)

    init {
        viewModelScope.launch {
            repo.cerrarEmaVencidas()
            while (true) { delay(60_000); reloj.value = ZonedDateTime.now(repo.cfg.zona); repo.cerrarEmaVencidas() }
        }
    }

    fun refrescar() { reloj.value = ZonedDateTime.now(repo.cfg.zona) }

    fun bluetoothEncendido() = ble.bluetoothEncendido()

    fun usarSimulador(v: Boolean) {
        detenerPesaje()
        _sesion.value = SesionPesaje(simulador = v)
    }

    fun pesoSimulado(kg: Double) { sim.pesoObjetivoKg = kg }

    fun iniciarPesaje() {
        detenerPesaje()
        ble.macVinculada = datos.value.perfil?.balanzaMac
        _sesion.value = _sesion.value.copy(enVivo = null, tramas = 0, guardadoId = null, porConfirmar = null, mensaje = null)
        val f = fuente
        recolector = viewModelScope.launch {
            f.lecturas.collect { l ->
                _sesion.value = _sesion.value.copy(enVivo = l, tramas = _sesion.value.tramas + 1)
                if (l.estable && l.frame.weightKg > 0) alEstable(l)
            }
        }
        f.iniciar()
    }

    fun detenerPesaje() {
        recolector?.cancel(); recolector = null
        ble.detener(); sim.detener()
    }

    private suspend fun alEstable(l: LecturaOkok) {
        if (repo.esDuplicado(l)) return
        val anterior = repo.ultimoPeso()
        if (anterior != null && abs(anterior - l.frame.weightKg) > UMBRAL_CONFIRMACION_KG) {
            _sesion.value = _sesion.value.copy(porConfirmar = l) // RF-09: «¿es usted?»
            return
        }
        guardar(l)
    }

    private suspend fun guardar(l: LecturaOkok) {
        val id = repo.guardarPesaje(l)
        _sesion.value = _sesion.value.copy(guardadoId = id, porConfirmar = null, mensaje = "Pesaje guardado: %.2f kg".format(l.frame.weightKg))
        if (!_sesion.value.simulador) detenerPesaje()
    }

    fun confirmarPesaje(aceptar: Boolean) {
        val l = _sesion.value.porConfirmar ?: return
        viewModelScope.launch {
            if (aceptar) guardar(l) else _sesion.value = _sesion.value.copy(porConfirmar = null, mensaje = "Pesaje descartado.")
        }
    }

    fun vincularBalanza(l: LecturaOkok?) = viewModelScope.launch {
        repo.vincularBalanza(l?.mac, l?.frame?.variant?.label)
    }

    fun fijarImcExterno(id: Long, imc: Double?) = viewModelScope.launch { repo.fijarImcExterno(id, imc) }
    fun borrarPesaje(id: Long) = viewModelScope.launch { repo.borrarPesaje(id) }

    fun guardarPerfil(p: PerfilEntity, alTerminar: (String?) -> Unit) = viewModelScope.launch {
        val err = runCatching { repo.guardarPerfil(p) }.exceptionOrNull()?.message
        alTerminar(err)
    }

    fun guardarEma(slot: SlotAgenda?, r: List<Int>?, actividad: String?, lugar: String?, social: Boolean?, estado: EstadoEma) =
        viewModelScope.launch { repo.guardarEma(slot, r, actividad, lugar, social, estado) }

    suspend fun emaYaRegistrada(slot: SlotAgenda) = repo.emaYaRegistrada(slot, reloj.value.toLocalDate())

    fun guardarFindrisc(r: FindriscResultado, imc: Double, cintura: Double) = viewModelScope.launch { repo.guardarFindrisc(r, imc, cintura) }
    fun guardarNordico(r: Map<RegionNordica, RespuestaRegion>) = viewModelScope.launch { repo.guardarNordico(r) }
    fun guardarAgenda(s: List<SlotAgenda>) = viewModelScope.launch { repo.guardarAgenda(s) }
    fun borrarTodo(alTerminar: () -> Unit) = viewModelScope.launch { detenerPesaje(); repo.borrarTodo(); alTerminar() }

    override fun onCleared() { detenerPesaje() }

    companion object { const val UMBRAL_CONFIRMACION_KG = 5.0 }
}
