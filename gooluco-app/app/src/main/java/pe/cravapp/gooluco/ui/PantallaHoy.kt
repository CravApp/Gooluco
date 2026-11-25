package pe.cravapp.gooluco.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.cravapp.gooluco.ble.EstadoFuente
import pe.cravapp.gooluco.data.EmaRespuestaEntity
import pe.cravapp.gooluco.data.PesajeEntity
import pe.cravapp.gooluco.domain.Agenda
import pe.cravapp.gooluco.domain.NivelRiesgo
import pe.cravapp.gooluco.domain.SlotAgenda
import pe.cravapp.gooluco.domain.TipoSlot
import pe.cravapp.gooluco.ui.theme.Extra
import pe.cravapp.gooluco.ui.theme.TemaEquilibra
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

data class AccionesHoy(
    val perfil: () -> Unit = {},
    val balanza: () -> Unit = {},
    val nordico: () -> Unit = {},
    val analisis: () -> Unit = {},
    val ema: (String?) -> Unit = {},
    val findrisc: () -> Unit = {},
    val agenda: () -> Unit = {},
)

@Composable
fun PantallaHoy(d: Datos, estadoBle: EstadoFuente, textoModelo: String, ahora: LocalDateTime, acciones: AccionesHoy) {
    val p = d.panel
    val ex = Extra.colores
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Encabezado: chip BLE, nombre, perfil
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val (icono, texto) = when (estadoBle) {
                    is EstadoFuente.Recibiendo -> Icons.Filled.BluetoothConnected to "BLE recibiendo"
                    EstadoFuente.Escaneando -> Icons.AutoMirrored.Filled.BluetoothSearching to "BLE buscando"
                    is EstadoFuente.Error -> Icons.Filled.Bluetooth to "BLE con error"
                    EstadoFuente.Inactiva -> Icons.Filled.Bluetooth to if (d.perfil?.balanzaMac != null) "Balanza vinculada" else "BLE en espera"
                }
                ChipEstado(icono, texto, cs.secondaryContainer, cs.onSecondaryContainer, Modifier.clickable { acciones.balanza() })
                Text("Equilibra", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = acciones.perfil) { Icon(Icons.Filled.AccountCircle, "Perfil de usuario", Modifier.size(32.dp)) }
            }

            // Héroe: anillo de peso + 3 píldoras
            Row(verticalAlignment = Alignment.CenterVertically) {
                val prog = if (p.pesoActual != null && p.metaPeso != null) {
                    (if (p.pesoActual > p.metaPeso) p.metaPeso / p.pesoActual else p.pesoActual / p.metaPeso).toFloat()
                } else 0f
                Box(contentAlignment = Alignment.TopCenter) {
                    AnilloPeso(prog, 156.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Peso actual", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            Text(kg(p.pesoActual), style = MaterialTheme.typography.displayMedium)
                            Text(p.metaPeso?.let { "Meta ${kg(it)} kg" } ?: "Sin meta", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                    p.deltaSemanal?.let {
                        Surface(color = cs.primary, shape = RoundedCornerShape(50), modifier = Modifier.padding(top = 0.dp)) {
                            Text((if (it > 0) "+" else "") + kg(it) + " kg", color = cs.onPrimary,
                                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PildoraMetrica(Icons.Filled.CalendarToday, "Registro diario", "${p.diasRegistro7}/7 días",
                        cs.secondaryContainer, cs.secondary, cs.onSecondaryContainer)
                    PildoraMetrica(Icons.Filled.AccessibilityNew, "Nórdico hoy", p.nordicoResumen ?: "Pendiente",
                        cs.tertiaryContainer, cs.tertiary, cs.onTertiaryContainer, Modifier.clickable { acciones.nordico() })
                    PildoraMetrica(Icons.Filled.Memory, "ML local", textoModelo,
                        ex.lavandaContainer, ex.lavanda, ex.onLavandaContainer, Modifier.clickable { acciones.analisis() })
                }
            }

            // Tarjeta de acción + validación
            Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow), shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChipEstado(Icons.Filled.AccessibilityNew, "Riesgo (reglas): ${p.riesgo?.nivel?.etiqueta ?: "—"}",
                        cs.tertiaryContainer, cs.onTertiaryContainer)
                    Text(tituloAccion(p), style = MaterialTheme.typography.headlineMedium)
                    Text(textoAccion(p), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    // Pesaje matutino BLE
                    Surface(color = cs.surfaceContainerLowest, shape = RoundedCornerShape(20.dp), modifier = Modifier.clickable { acciones.balanza() }) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.MonitorWeight, null, tint = cs.primary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Pesaje matutino BLE", style = MaterialTheme.typography.titleSmall)
                                Text(p.ultimoPesajeManana?.let { "${kg(it.pesoKg)} kg · ${horaLocal(it)} · ${it.fechaLocal} · Offline" }
                                    ?: "Aún sin pesaje de mañana", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.ChevronRight, null)
                        }
                    }
                    Button(onClick = acciones.nordico, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.Assignment, null); Spacer(Modifier.width(8.dp)); Text("Actualizar Cuestionario Nórdico")
                    }
                }
            }

            // Agenda del día (EMA de estrés + pesajes)
            Seccion("Agenda de hoy") { TextButton(onClick = acciones.agenda) { Text("Editar") } }
            TarjetaAgenda(d.slots, d.emas, d.pesajes, ahora, acciones)

            // Tu día hasta ahora
            Seccion("Tu día hasta ahora") { ChipEstado(Icons.Filled.CloudOff, "Offline", cs.surfaceContainerHigh, cs.onSurfaceVariant) }
            ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChipEstado(Icons.Filled.Memory, "Predicción local", ex.lavandaContainer, ex.onLavandaContainer)
                        Spacer(Modifier.weight(1f))
                        Text(if (p.proyeccion14 != null) "Tendencia lineal" else "Sin datos", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(p.proyeccion14?.let { "Proyección a 14 días: ${kg(it)} kg" } ?: "Proyección a 14 días: —",
                        style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (p.proyeccion14 != null) "Regresión lineal sobre ${p.nPuntosProyeccion} pesajes locales de los últimos 28 días. " +
                            "Es una extrapolación de tendencia, no un modelo entrenado; nada sale del teléfono."
                        else "Se necesitan al menos 4 pesajes en los últimos 28 días para proyectar la tendencia.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Cuestionarios", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { acciones.ema(null) }) { Icon(Icons.Filled.Mood, null); Spacer(Modifier.width(8.dp)); Text("Estrés (PSS-4)") }
                    TextButton(onClick = acciones.findrisc) {
                        Icon(Icons.AutoMirrored.Filled.Assignment, null); Spacer(Modifier.width(8.dp))
                        Text("FINDRISC" + (p.ultimoFindrisc?.let { " · último: ${it.total} pts" } ?: " · pendiente"))
                    }
                }
            }
            AvisoNoDiagnostico()
            Spacer(Modifier.height(88.dp))
        }

        // FAB «añadir» con menú rápido
        var menu by remember { mutableStateOf(false) }
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            FloatingActionButton(onClick = { menu = true }, containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer) {
                Icon(Icons.Filled.Add, "Nuevo registro diario")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Pesarme (balanza OKOK)") }, leadingIcon = { Icon(Icons.Filled.MonitorWeight, null) },
                    onClick = { menu = false; acciones.balanza() })
                DropdownMenuItem(text = { Text("Cuestionario de estrés") }, leadingIcon = { Icon(Icons.Filled.Mood, null) },
                    onClick = { menu = false; acciones.ema(null) })
                DropdownMenuItem(text = { Text("Cuestionario Nórdico") }, leadingIcon = { Icon(Icons.Filled.AccessibilityNew, null) },
                    onClick = { menu = false; acciones.nordico() })
                DropdownMenuItem(text = { Text("FINDRISC") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Assignment, null) },
                    onClick = { menu = false; acciones.findrisc() })
            }
        }
    }
}

private fun horaLocal(p: PesajeEntity): String =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(p.timestampUtc), ZoneId.of(p.zona)).format(HHMM)

private fun tituloAccion(p: Panel): String = when {
    p.pesoActual == null -> "¡Bienvenido a Equilibra!"
    p.riesgo?.derivar == true -> "Conviene una consulta"
    p.riesgo?.nivel == NivelRiesgo.BAJO && p.nordicoResumen == "Sin dolor" -> "¡Buen equilibrio postural!"
    else -> "Sigue registrando tu día"
}

private fun textoAccion(p: Panel): String {
    if (p.pesoActual == null) return "Súbete a tu balanza OKOK para registrar tu primer pesaje. El IMC se calcula con la talla de tu perfil."
    val partes = mutableListOf<String>()
    p.deltaSemanal?.let { partes += if (it < 0) "Tu peso bajó ${kg(-it)} kg esta semana" else if (it > 0) "Tu peso subió ${kg(it)} kg esta semana" else "Tu peso se mantuvo esta semana" }
    p.imc?.let { partes += "IMC ${num1(it)} (${p.claseImc?.etiqueta?.lowercase()})" }
    partes += when (p.nordicoResumen) {
        null -> "aún no completas el Cuestionario Nórdico"
        "Sin dolor" -> "el Cuestionario Nórdico no muestra molestias"
        else -> "Nórdico: ${p.nordicoResumen.lowercase()}"
    }
    var t = partes.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
    if (p.riesgo?.derivar == true) t += " " + pe.cravapp.gooluco.domain.ResultadoRiesgo.DERIVACION
    return t
}

@Composable
private fun TarjetaAgenda(slots: List<SlotAgenda>, emas: List<EmaRespuestaEntity>, pesajes: List<PesajeEntity>, ahora: LocalDateTime, acciones: AccionesHoy) {
    val cs = MaterialTheme.colorScheme
    val hoy = ahora.toLocalDate().toString()
    Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            if (slots.isEmpty()) Text("Sin agenda configurada.", Modifier.padding(16.dp))
            slots.filter { it.activo }.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
                val estado: String
                var accion: (() -> Unit)? = null
                when (s.tipo) {
                    TipoSlot.EMA -> {
                        val reg = emas.firstOrNull { it.slotId == s.id && it.fechaProgramada == hoy }
                        val abierta = Agenda.dentroDeVentana(ahora.toLocalDate().atTime(s.hora), ahora, 60)
                        estado = when {
                            reg != null -> reg.estado.lowercase().replaceFirstChar { it.uppercase() } + (reg.pssTotal?.let { " · PSS $it/16" } ?: "")
                            abierta -> "Abierta ahora"
                            ahora.toLocalTime().isBefore(s.hora) -> "Programada"
                            else -> "Ventana cerrada"
                        }
                        if (reg == null && abierta) accion = { acciones.ema(s.id) }
                    }
                    else -> {
                        val franja = if (s.tipo == TipoSlot.PESAJE_MANANA) "MANANA" else "NOCHE"
                        val pz = pesajes.filter { it.fechaLocal == hoy && it.franja == franja }
                        estado = if (pz.isNotEmpty()) "Registrado · ${kg(pz.first().pesoKg)} kg" else "Pendiente"
                        if (pz.isEmpty()) accion = acciones.balanza
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (s.tipo == TipoSlot.EMA) Icons.Filled.Schedule else Icons.Filled.MonitorWeight, null, tint = cs.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${s.hora.format(HHMM)} · ${s.tipo.etiqueta}", style = MaterialTheme.typography.titleSmall)
                        Text(estado, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    accion?.let { FilledTonalButton(onClick = it, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(if (s.tipo == TipoSlot.EMA) "Responder" else "Pesarme") } }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- vista previa (datos de ejemplo del mockup, no reales)

internal fun datosDeEjemplo(): Datos = Datos(
    cargado = true,
    slots = Agenda.SLOTS_DEFECTO,
    panel = Panel(
        pesoActual = 72.4, metaPeso = 70.0, deltaSemanal = -0.4, imc = 24.9,
        claseImc = pe.cravapp.gooluco.domain.ClaseImc.NORMAL, diasRegistro7 = 6, nordicoResumen = "Sin dolor",
        proyeccion14 = 71.8, nPuntosProyeccion = 42,
    ),
)

@Preview(showBackground = true, heightDp = 1400, name = "Hoy · ejemplo")
@Composable
private fun VistaPreviaHoy() {
    TemaEquilibra(oscuro = false) {
        Surface { PantallaHoy(datosDeEjemplo(), EstadoFuente.Inactiva, "Solo reglas", LocalDateTime.of(2026, 11, 25, 13, 20), AccionesHoy()) }
    }
}

@Preview(showBackground = true, heightDp = 1400, name = "Hoy · oscuro")
@Composable
private fun VistaPreviaHoyOscuro() {
    TemaEquilibra(oscuro = true) {
        Surface { PantallaHoy(datosDeEjemplo(), EstadoFuente.Inactiva, "Solo reglas", LocalDateTime.of(2026, 11, 25, 7, 42), AccionesHoy()) }
    }
}

