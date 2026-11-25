package pe.cravapp.gooluco.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.cravapp.gooluco.domain.Agenda
import pe.cravapp.gooluco.domain.EstadoEma
import pe.cravapp.gooluco.domain.Pss4
import pe.cravapp.gooluco.domain.SlotAgenda
import pe.cravapp.gooluco.domain.TipoSlot
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** EMA de estrés (RF-20): PSS-4 referido a la última hora + contexto (actividad, lugar, social). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaEma(
    d: Datos,
    slotId: String?,
    ahora: LocalDateTime,
    yaRegistrada: suspend (SlotAgenda) -> Boolean,
    guardar: (SlotAgenda?, List<Int>?, String?, String?, Boolean?, EstadoEma) -> Unit,
    cerrar: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val candidato = (slotId?.let { id -> d.slots.firstOrNull { it.id == id && it.tipo == TipoSlot.EMA } } ?: Agenda.emaAbierta(d.slots, ahora))
        ?.takeIf { Agenda.dentroDeVentana(ahora.toLocalDate().atTime(it.hora), ahora, 60) }
    var slot by remember(candidato) { mutableStateOf(candidato) }
    LaunchedEffect(candidato) { if (candidato != null && yaRegistrada(candidato)) slot = null }

    val r = remember { mutableStateListOf<Int?>(null, null, null, null) }
    var actividad by remember { mutableStateOf<String?>(null) }
    var lugar by remember { mutableStateOf<String?>(null) }
    var social by remember { mutableStateOf<Boolean?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("¿Cómo va tu estrés?", style = MaterialTheme.typography.headlineMedium)
        Text(
            slot?.let { "Cuestionario ${it.id} de las ${it.hora.format(DateTimeFormatter.ofPattern("HH:mm"))} (ventana de 60 min)." }
                ?: "No hay un cuestionario de la agenda abierto ahora. Este será un registro libre: cuenta para tu estrés diario, pero no para la adherencia.",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
        )
        Pss4.ITEMS.forEachIndexed { i, q ->
            Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("${i + 1}. $q", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pss4.OPCIONES.forEachIndexed { v, o -> FilterChip(r[i] == v, { r[i] = v }, label = { Text(o) }) }
                    }
                }
            }
        }
        Text("¿Qué estabas haciendo?", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pss4.ACTIVIDADES.forEach { (k, v) -> FilterChip(actividad == k, { actividad = k }, label = { Text(v) }) }
        }
        Text("¿Dónde estabas?", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pss4.LUGARES.forEach { (k, v) -> FilterChip(lugar == k, { lugar = k }, label = { Text(v) }) }
        }
        Text("¿Estabas con otras personas?", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(social == true, { social = true }, label = { Text("Sí (social)") })
            FilterChip(social == false, { social = false }, label = { Text("No (solo/a)") })
        }
        val completo = r.all { it != null }
        if (completo) Text("Puntaje PSS-4: ${Pss4.total(r.map { it!! })} / 16", style = MaterialTheme.typography.titleMedium, color = cs.primary)
        Button(enabled = completo, onClick = {
            guardar(slot, r.map { it!! }, actividad, lugar, social, EstadoEma.RESPONDIDA); cerrar()
        }, modifier = Modifier.fillMaxWidth()) { Text("Guardar respuesta") }
        if (slot != null) OutlinedButton(onClick = { guardar(slot, null, null, null, null, EstadoEma.OMITIDA); cerrar() }, modifier = Modifier.fillMaxWidth()) {
            Text("Omitir este cuestionario")
        }
        Text("Versión del cuestionario: ${Pss4.VERSION}. Traducción provisional (TBD-06).", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}
