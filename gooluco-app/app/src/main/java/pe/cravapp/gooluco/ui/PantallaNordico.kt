package pe.cravapp.gooluco.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.cravapp.gooluco.domain.Nordico
import pe.cravapp.gooluco.domain.RegionNordica
import pe.cravapp.gooluco.domain.RespuestaRegion
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Cuestionario Nórdico (Kuorinka): 9 regiones × molestias 12 meses / impedimento / últimos 7 días. */
@Composable
fun PantallaNordico(d: Datos, guardar: (Map<RegionNordica, RespuestaRegion>) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val previo = d.nordico.maxByOrNull { it.timestampUtc }
    val resp = remember(previo?.id) {
        mutableStateMapOf<RegionNordica, RespuestaRegion>().apply { previo?.let { putAll(Nordico.decodificar(it.respuestas)) } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Cuestionario Nórdico", style = MaterialTheme.typography.headlineMedium)
        Text("Marca las zonas del cuerpo donde has tenido molestias (dolor, incomodidad o adormecimiento).",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        previo?.let {
            val f = LocalDateTime.ofInstant(Instant.ofEpochMilli(it.timestampUtc), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
            Text("Última respuesta: $f · ${Nordico.resumen(Nordico.decodificar(it.respuestas))}", style = MaterialTheme.typography.labelMedium, color = cs.primary)
        }
        RegionNordica.entries.forEach { reg ->
            val r = resp[reg] ?: RespuestaRegion()
            Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(reg.etiqueta, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(r.molestias12m, { resp[reg] = r.copy(molestias12m = !r.molestias12m, impidio12m = if (r.molestias12m) false else r.impidio12m) },
                            label = { Text("12 meses") })
                        FilterChip(r.impidio12m, { resp[reg] = r.copy(impidio12m = !r.impidio12m, molestias12m = true) }, label = { Text("Impidió actividades") })
                        FilterChip(r.molestias7d, { resp[reg] = r.copy(molestias7d = !r.molestias7d) }, label = { Text("7 días") })
                    }
                }
            }
        }
        Text("Vista previa: ${Nordico.resumen(resp)}", style = MaterialTheme.typography.titleSmall)
        Button(onClick = { guardar(resp.toMap()) }, modifier = Modifier.fillMaxWidth()) { Text("Guardar cuestionario") }
        AvisoNoDiagnostico("El Cuestionario Nórdico es de tamizaje de molestias musculoesqueléticas; no reemplaza una evaluación profesional.")
        Spacer(Modifier.height(24.dp))
    }
}
