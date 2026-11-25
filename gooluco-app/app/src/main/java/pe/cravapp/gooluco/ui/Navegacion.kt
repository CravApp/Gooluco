package pe.cravapp.gooluco.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pe.cravapp.gooluco.ml.EstadoModelo
import java.time.LocalDateTime

private enum class Pestana(val ruta: String, val etiqueta: String, val icono: ImageVector) {
    HOY("hoy", "Hoy", Icons.Filled.WbSunny),
    BALANZA("balanza", "Balanza", Icons.Filled.MonitorWeight),
    NORDICO("nordico", "Nórdico", Icons.Filled.AccessibilityNew),
    ANALISIS("analisis", "Análisis", Icons.Filled.Psychology),
}

private val TITULOS = mapOf("ema" to "Estrés", "findrisc" to "FINDRISC", "perfil" to "Perfil", "agenda" to "Agenda", "sync" to "Servidor")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppEquilibra(
    vm: AppViewModel,
    destinoInicial: String?,
    pedirPermisosBle: (() -> Unit) -> Unit,
    pedirPermisoNotificaciones: () -> Unit,
    conectarServidor: () -> Unit = {},
) {
    val d by vm.datos.collectAsStateWithLifecycle()
    val sesion by vm.sesion.collectAsStateWithLifecycle()
    val estadoBle by vm.estadoBle.collectAsStateWithLifecycle()
    val estadoSync by vm.sync.collectAsStateWithLifecycle()
    val nav = rememberNavController()

    if (!d.cargado) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (d.perfil?.consentimientoUtc == null) {
        Scaffold { pad ->
            Box(Modifier.padding(pad)) {
                PantallaPerfil(null, vm::guardarPerfil, {}, alGuardar = pedirPermisoNotificaciones)
            }
        }
        return
    }

    LaunchedEffect(destinoInicial) { destinoInicial?.let { nav.navigate(it) } }

    val entrada by nav.currentBackStackEntryAsState()
    val ruta = entrada?.destination?.route?.substringBefore("?")
    val esPestana = Pestana.entries.any { it.ruta == ruta }
    val ahora = LocalDateTime.now(vm.repo.cfg.zona)
    val textoModelo = when (vm.modelo.estado) { is EstadoModelo.Disponible -> "Modelo listo"; is EstadoModelo.NoDisponible -> "Solo reglas" }
    val acciones = AccionesHoy(
        perfil = { nav.navigate("perfil") },
        balanza = { nav.irAPestana(Pestana.BALANZA.ruta) },
        nordico = { nav.irAPestana(Pestana.NORDICO.ruta) },
        analisis = { nav.irAPestana(Pestana.ANALISIS.ruta) },
        ema = { slot -> nav.navigate("ema" + (slot?.let { "?slot=$it" } ?: "")) },
        findrisc = { nav.navigate("findrisc") },
        agenda = { nav.navigate("agenda") },
    )

    Scaffold(
        topBar = {
            if (!esPestana && ruta != null) TopAppBar(
                title = { Text(TITULOS[ruta] ?: "") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            )
        },
        bottomBar = {
            if (esPestana) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Pestana.entries.forEach { p ->
                    NavigationBarItem(
                        selected = ruta == p.ruta,
                        onClick = { nav.irAPestana(p.ruta) },
                        icon = { Icon(p.icono, null) },
                        label = { Text(p.etiqueta) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Pestana.HOY.ruta, modifier = Modifier.padding(pad)) {
            composable(Pestana.HOY.ruta) { PantallaHoy(d, estadoBle, textoModelo, ahora, acciones) }
            composable(Pestana.BALANZA.ruta) {
                PantallaBalanza(d, sesion, estadoBle, AccionesBalanza(
                    iniciar = { if (sesion.simulador) vm.iniciarPesaje() else pedirPermisosBle { vm.iniciarPesaje() } },
                    detener = vm::detenerPesaje,
                    simulador = vm::usarSimulador,
                    pesoSimulado = vm::pesoSimulado,
                    confirmar = vm::confirmarPesaje,
                    vincular = { vm.vincularBalanza(it) },
                    imcExterno = { id, imc -> vm.fijarImcExterno(id, imc) },
                    borrar = { vm.borrarPesaje(it) },
                ))
            }
            composable(Pestana.NORDICO.ruta) {
                PantallaNordico(d) { vm.guardarNordico(it); nav.irAPestana(Pestana.HOY.ruta) }
            }
            composable(Pestana.ANALISIS.ruta) { PantallaAnalisis(d, vm.modelo.estado, acciones) }
            composable(
                "ema?slot={slot}",
                arguments = listOf(navArgument("slot") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { e ->
                PantallaEma(d, e.arguments?.getString("slot"), ahora, vm::emaYaRegistrada,
                    { s, r, a, l, so, est -> vm.guardarEma(s, r, a, l, so, est) }, { nav.popBackStack() })
            }
            composable("findrisc") { PantallaFindrisc(d, { r, i, c -> vm.guardarFindrisc(r, i, c) }, { nav.popBackStack() }) }
            composable("perfil") {
                PantallaPerfil(d.perfil, vm::guardarPerfil, { vm.borrarTodo {} }, alGuardar = { nav.popBackStack() }, abrirSync = { nav.navigate("sync") })
            }
            composable("sync") {
                PantallaSync(estadoSync, AccionesSync(
                    activar = vm::activarSync,
                    desactivar = vm::desactivarSync,
                    conectar = conectarServidor,
                    desconectar = vm::desconectarServidor,
                    sincronizar = { vm.sincronizarAhora(it) },
                    verServidor = { vm.verServidor() },
                ))
            }
            composable("agenda") { PantallaAgenda(d.slots, { vm.guardarAgenda(it) }, { nav.popBackStack() }) }
        }
    }
}

private fun NavHostController.irAPestana(ruta: String) = navigate(ruta) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

