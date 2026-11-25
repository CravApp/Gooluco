package pe.cravapp.gooluco.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.cravapp.gooluco.domain.CategoriaFindrisc
import pe.cravapp.gooluco.domain.NivelRiesgo
import pe.cravapp.gooluco.domain.ResultadoRiesgo
import pe.cravapp.gooluco.ml.EstadoModelo
import pe.cravapp.gooluco.ui.theme.Extra
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** «Mi riesgo» + «Tendencias» (RF-25..RF-33). */
@Composable
fun PantallaAnalisis(d: Datos, modelo: EstadoModelo, acciones: AccionesHoy) {
    val p = d.panel
    val cs = MaterialTheme.colorScheme
    val r = p.riesgo
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Análisis", style = MaterialTheme.typography.headlineMedium)

        val (fondo, texto) = when (r?.nivel) {
            NivelRiesgo.ALTO, NivelRiesgo.MUY_ALTO -> cs.errorContainer to cs.onErrorContainer
            NivelRiesgo.MODERADO, NivelRiesgo.LIGERAMENTE_ELEVADO -> Extra.colores.advertenciaContainer to Extra.colores.onAdvertenciaContainer
            NivelRiesgo.BAJO -> Extra.colores.exitoContainer to Extra.colores.onExitoContainer
            else -> cs.surfaceContainerHigh to cs.onSurface
        }
        Card(colors = CardDefaults.cardColors(containerColor = fondo, contentColor = texto), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipEstado(Icons.Filled.Psychology, "Capa A · reglas", cs.surface.copy(alpha = 0.6f), texto)
                Text("Riesgo físico: ${r?.nivel?.etiqueta ?: "—"}", style = MaterialTheme.typography.headlineMedium)
                r?.reglas?.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                if (r != null && r.reglas.isEmpty()) Text("Registra tu peso y completa FINDRISC para evaluar.", style = MaterialTheme.typography.bodyMedium)
                r?.alertas?.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodyMedium) }
                if (r?.derivar == true) Text(ResultadoRiesgo.DERIVACION, style = MaterialTheme.typography.titleSmall)
                Text(ResultadoRiesgo.AVISO, style = MaterialTheme.typography.labelMedium)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Extra.colores.lavandaContainer, contentColor = Extra.colores.onLavandaContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ChipEstado(Icons.Filled.Memory, "Capa B · LiteRT", cs.surface.copy(alpha = 0.6f), Extra.colores.onLavandaContainer)
                when (modelo) {
                    is EstadoModelo.Disponible -> Text("Modelo cargado (${modelo.version}, ${modelo.entradas} entradas). Estimación complementaria: pendiente de contrato (TBD-04/05).")
                    is EstadoModelo.NoDisponible -> Text("${modelo.motivo} Se usa solo la Capa A (RF-31).")
                }
            }
        }

        Seccion("Indicadores")
        Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                FilaDato("Peso actual", p.pesoActual?.let { "${kg(it, 2)} kg" } ?: "—", true)
                FilaDato("IMC (peso OKOK / talla²)", p.imc?.let { "${num1(it)} · ${p.claseImc?.etiqueta}" } ?: "—")
                FilaDato("Cambio de peso 7 días", p.deltaSemanal?.let { "${kg(it)} kg" } ?: "—")
                FilaDato("Adherencia EMA 7 días", pct(p.adherencia7d))
                FilaDato("Estrés medio 7 días (PSS-4)", p.estres7d?.let { "${num1(it)} / 16" } ?: "—")
                FilaDato("Línea base de estrés", p.lineaBaseEstres?.let { num1(it) } ?: "Se requieren ≥ 7 días")
                FilaDato("Días seguidos con estrés elevado", "${p.diasEstresSeguidos}")
                FilaDato("Último FINDRISC", p.ultimoFindrisc?.let {
                    val f = Instant.ofEpochMilli(it.timestampUtc).atZone(ZoneId.systemDefault()).toLocalDate()
                    "${it.total} pts · ${CategoriaFindrisc.valueOf(it.categoria).etiqueta} · $f"
                } ?: if (d.perfil?.condicion == "DM2") "No aplica (DM2)" else "Pendiente")
            }
        }

        Seccion("Peso 28 días")
        Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                GraficoLinea(p.serie28d.map { it.second })
                Text("Primer pesaje de cada día.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }

        Seccion("Estrés ↔ peso")
        Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                if (p.spearman != null) {
                    FilaDato("ρ de Spearman (estrés diario vs Δ24h)", num1(p.spearman), true)
                    FilaDato("Pares válidos (n)", "${p.nParesSpearman}")
                } else {
                    Text("Se necesitan ≥ 14 días con estrés y Δ24h (hay ${p.nParesSpearman}).", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Exploratorio, no diagnóstico.", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Últimos días", style = MaterialTheme.typography.titleSmall)
                p.resumenes.take(7).forEach { r ->
                    FilaDato(
                        r.fecha.diaCorto(),
                        listOfNotNull(
                            r.estresMedio?.let { "estrés ${num1(it)}" + if (r.estresElevado == true) " ↑" else "" },
                            r.pesoManana?.let { "mañana ${kg(it)}" },
                            r.pesoNoche?.let { "noche ${kg(it)}" },
                        ).joinToString(" · ").ifEmpty { "sin datos" } + " · " + r.calidad.name.lowercase(),
                    )
                }
            }
        }

        Seccion("Cuestionarios y agenda")
        OutlinedButton(onClick = { acciones.ema(null) }, modifier = Modifier.fillMaxWidth()) { Text("Cuestionario de estrés (PSS-4 + contexto)") }
        OutlinedButton(onClick = acciones.findrisc, modifier = Modifier.fillMaxWidth()) { Text("FINDRISC (riesgo de diabetes)") }
        OutlinedButton(onClick = acciones.agenda, modifier = Modifier.fillMaxWidth()) { Text("Agenda diaria y notificaciones") }
        OutlinedButton(onClick = acciones.perfil, modifier = Modifier.fillMaxWidth()) { Text("Perfil, privacidad y sincronización") }
        AvisoNoDiagnostico()
        Spacer(Modifier.height(24.dp))
    }
}

private fun LocalDate.diaCorto() = "%02d/%02d".format(dayOfMonth, monthValue)
