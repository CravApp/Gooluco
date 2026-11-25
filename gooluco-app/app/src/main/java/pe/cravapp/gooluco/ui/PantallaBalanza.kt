package pe.cravapp.gooluco.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pe.cravapp.gooluco.ble.EstadoFuente
import pe.cravapp.gooluco.ble.LecturaOkok
import pe.cravapp.gooluco.data.PesajeEntity
import pe.cravapp.gooluco.domain.ClaseImc
import pe.cravapp.gooluco.domain.Imc
import pe.cravapp.gooluco.ui.theme.Extra
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm")

data class AccionesBalanza(
    val iniciar: () -> Unit = {},
    val detener: () -> Unit = {},
    val simulador: (Boolean) -> Unit = {},
    val pesoSimulado: (Double) -> Unit = {},
    val confirmar: (Boolean) -> Unit = {},
    val vincular: (LecturaOkok?) -> Unit = {},
    val imcExterno: (Long, Double?) -> Unit = { _, _ -> },
    val borrar: (Long) -> Unit = {},
)

@Composable
fun PantallaBalanza(d: Datos, s: SesionPesaje, estado: EstadoFuente, a: AccionesBalanza) {
    val cs = MaterialTheme.colorScheme
    val talla = d.perfil?.tallaCm
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Balanza OKOK", style = MaterialTheme.typography.headlineMedium)
        Text("La balanza envía solo el peso. El IMC se calcula aquí con tu talla (${talla?.let { "${num1(it)} cm" } ?: "sin talla"}).",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)

        // Lectura en vivo
        Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val activo = estado is EstadoFuente.Escaneando || estado is EstadoFuente.Recibiendo
                Text(
                    when (estado) {
                        EstadoFuente.Inactiva -> "En espera"
                        EstadoFuente.Escaneando -> "Buscando balanza… súbete descalzo"
                        is EstadoFuente.Recibiendo -> "Recibiendo de ${estado.ultimaMac}"
                        is EstadoFuente.Error -> "${estado.mensaje} (${estado.codigo})"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (estado is EstadoFuente.Error) cs.error else cs.onSurfaceVariant,
                )
                val l = s.enVivo
                Text(if (l != null) kg(l.frame.weightKg, 2) else "—", style = MaterialTheme.typography.displayLarge)
                Text(
                    when {
                        l == null -> "kg"
                        l.frame.weightKg == 0.0 -> "kg · balanza en reposo"
                        l.estable -> "kg · estable"
                        l.frame.stable == true -> "kg · estable (repetición)"
                        else -> "kg · provisional"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                if (l != null && l.frame.weightKg > 0 && talla != null) {
                    val imc = Imc.calcular(l.frame.weightKg, talla)
                    Text("IMC ${num1(imc)} · ${Imc.clasificar(imc).etiqueta}", style = MaterialTheme.typography.titleMedium, color = cs.primary)
                }
                Text("${s.tramas} tramas · ${if (s.simulador) "simulador" else "balanza real"}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                s.mensaje?.let { Text(it, color = Extra.colores.exito, style = MaterialTheme.typography.titleSmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!activo) Button(onClick = a.iniciar) { Icon(Icons.AutoMirrored.Filled.BluetoothSearching, null); Spacer(Modifier.width(8.dp)); Text("Pesarme") }
                    else OutlinedButton(onClick = a.detener) { Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(8.dp)); Text("Detener") }
                }
            }
        }

        // Fuente y vinculación (RF-08, RF-11)
        Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Science, null, tint = cs.tertiary); Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Simulador OKOK", style = MaterialTheme.typography.titleSmall)
                        Text("Tramas C0 sintéticas (marcadas como SIMULADA)", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Switch(checked = s.simulador, onCheckedChange = a.simulador)
                }
                if (s.simulador) {
                    var txt by remember { mutableStateOf("72.4") }
                    OutlinedTextField(txt, { txt = it; it.replace(',', '.').toDoubleOrNull()?.takeIf { v -> v in 10.0..250.0 }?.let(a.pesoSimulado) },
                        label = { Text("Peso a simular (kg)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                }
                HorizontalDivider()
                val mac = d.perfil?.balanzaMac
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (mac != null) Icons.Filled.Link else Icons.Filled.LinkOff, null, tint = cs.primary); Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (mac != null) "Balanza vinculada" else "Sin balanza vinculada", style = MaterialTheme.typography.titleSmall)
                        Text(mac?.let { "$it · ${d.perfil.balanzaVariante ?: ""}" } ?: "Se aceptan todas las balanzas OKOK cercanas",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    if (mac != null) TextButton(onClick = { a.vincular(null) }) { Text("Desvincular") }
                    else if (s.enVivo != null && !s.simulador) TextButton(onClick = { a.vincular(s.enVivo) }) { Text("Vincular esta") }
                }
            }
        }

        // Historial
        Seccion("Pesajes guardados")
        if (d.pesajes.isEmpty()) Text("Aún no hay pesajes.", color = cs.onSurfaceVariant)
        d.pesajes.take(30).forEach { p -> FilaPesaje(p, a) }
        AvisoNoDiagnostico("El IMC del smartwatch es opcional y solo sirve para contrastar: si difiere más de 0,5 kg/m², revisa tu talla.")
        Spacer(Modifier.height(24.dp))
    }

    // RF-09: confirmación «¿es usted?»
    s.porConfirmar?.let { l ->
        AlertDialog(
            onDismissRequest = { a.confirmar(false) },
            icon = { Icon(Icons.Filled.Warning, null) },
            title = { Text("¿Es usted?") },
            text = { Text("El peso ${kg(l.frame.weightKg, 2)} kg difiere más de 5 kg de tu último pesaje. ¿Guardarlo en tu perfil?") },
            confirmButton = { TextButton(onClick = { a.confirmar(true) }) { Text("Sí, soy yo") } },
            dismissButton = { TextButton(onClick = { a.confirmar(false) }) { Text("No, descartar") } },
        )
    }
}

@Composable
private fun FilaPesaje(p: PesajeEntity, a: AccionesBalanza) {
    val cs = MaterialTheme.colorScheme
    var editar by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLowest)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${kg(p.pesoKg, 2)} kg · IMC ${num1(p.imc)} ${p.claseImc?.let { "(" + ClaseImc.valueOf(it).etiqueta + ")" } ?: ""}",
                        style = MaterialTheme.typography.titleSmall)
                    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(p.timestampUtc), ZoneId.of(p.zona)).format(FECHA_HORA)
                    Text("$t · ${p.franja.lowercase().replace("manana", "mañana")} · ${p.variante} · ${p.fuente.lowercase()}",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    p.imcExterno?.let { ext ->
                        val alerta = p.imc != null && Imc.contrasteAlerta(p.imc, ext)
                        Text("Smartwatch: IMC ${num1(ext)}" + if (alerta) " · difiere: verifica tu talla" else " · coincide",
                            style = MaterialTheme.typography.bodySmall, color = if (alerta) cs.error else Extra.colores.exito)
                    }
                }
                TextButton(onClick = { editar = true }) { Text("IMC reloj") }
                IconButton(onClick = { a.borrar(p.id) }) { Icon(Icons.Filled.Delete, "Borrar pesaje") }
            }
        }
    }
    if (editar) {
        var txt by remember { mutableStateOf(p.imcExterno?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { editar = false },
            title = { Text("IMC del smartwatch (opcional)") },
            text = {
                OutlinedTextField(txt, { txt = it }, label = { Text("IMC (kg/m²)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            },
            confirmButton = {
                TextButton(onClick = { a.imcExterno(p.id, txt.replace(',', '.').toDoubleOrNull()?.takeIf { it in 10.0..80.0 }); editar = false }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editar = false }) { Text("Cancelar") } },
        )
    }
}
