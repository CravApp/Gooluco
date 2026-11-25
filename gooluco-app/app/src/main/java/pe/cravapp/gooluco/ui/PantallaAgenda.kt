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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.cravapp.gooluco.domain.Agenda
import pe.cravapp.gooluco.domain.SlotAgenda
import pe.cravapp.gooluco.domain.TipoSlot
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Agenda diaria (RF-16, RF-23): horarios de EMA (1–12) y avisos de pesaje. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAgenda(actual: List<SlotAgenda>, guardar: (List<SlotAgenda>) -> Unit, cerrar: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val slots = remember(actual) { mutableStateListOf<SlotAgenda>().apply { addAll(actual.ifEmpty { Agenda.SLOTS_DEFECTO }) } }
    var editando by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Agenda diaria", style = MaterialTheme.typography.headlineMedium)
        Text("Recibirás una notificación en cada horario. Cada cuestionario se puede responder durante 60 min; a los 20 min llega un recordatorio. " +
            "Ventanas de pesaje: mañana 05:00–11:00, noche 18:00–23:59 (hora de Lima).",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        slots.forEachIndexed { i, s ->
            Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${s.id} · ${s.tipo.etiqueta}", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { editando = i }) { Text(s.hora.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.headlineMedium) }
                    }
                    if (s.tipo == TipoSlot.EMA && slots.count { it.tipo == TipoSlot.EMA } > 1) {
                        TextButton(onClick = { slots.removeAt(i) }) { Text("Quitar") }
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(s.activo, { slots[i] = s.copy(activo = it) })
                }
            }
        }
        val nEma = slots.count { it.tipo == TipoSlot.EMA }
        OutlinedButton(enabled = nEma < 12, onClick = {
            val id = (1..12).map { "EMA-$it" }.first { c -> slots.none { it.id == c } }
            slots.add(SlotAgenda(id, TipoSlot.EMA, LocalTime.of(19, 0)))
        }, modifier = Modifier.fillMaxWidth()) { Text("Agregar cuestionario de estrés ($nEma/12)") }
        TextButton(onClick = { slots.clear(); slots.addAll(Agenda.SLOTS_DEFECTO) }) { Text("Restaurar valores por defecto") }
        Button(onClick = { guardar(slots.sortedBy { it.hora }.toList()); cerrar() }, modifier = Modifier.fillMaxWidth()) { Text("Guardar agenda") }
        Spacer(Modifier.height(24.dp))
    }
    editando?.let { i ->
        val st = rememberTimePickerState(slots[i].hora.hour, slots[i].hora.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { editando = null },
            title = { Text("Hora de ${slots[i].id}") },
            text = { TimePicker(st) },
            confirmButton = { TextButton(onClick = { slots[i] = slots[i].copy(hora = LocalTime.of(st.hour, st.minute)); editando = null }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { editando = null }) { Text("Cancelar") } },
        )
    }
}
