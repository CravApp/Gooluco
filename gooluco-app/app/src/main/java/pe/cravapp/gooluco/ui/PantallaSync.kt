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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import pe.cravapp.gooluco.sync.ClienteSmart
import pe.cravapp.gooluco.ui.theme.Extra
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AccionesSync(
    val activar: (Boolean) -> Unit = {},
    val desactivar: () -> Unit = {},
    val conectar: () -> Unit = {},
    val desconectar: () -> Unit = {},
    val sincronizar: (Boolean) -> Unit = {},
    val verServidor: () -> Unit = {},
)

private val FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private fun fecha(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.of("America/Lima")).format(FMT)

/** Sincronización opcional con el servidor FHIR R4 + SMART de prueba. Desactivada por defecto. */
@Composable
fun PantallaSync(s: EstadoSyncUi, a: AccionesSync) {
    val cs = MaterialTheme.colorScheme
    val c = s.conexion
    var dialogo by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sincronización con servidor", style = MaterialTheme.typography.headlineMedium)
        Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (c.activa) Icons.Filled.Cloud else Icons.Filled.CloudOff, null, tint = cs.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Enviar mis registros al servidor", style = MaterialTheme.typography.titleSmall)
                    Text(if (c.activa) "Activada" else "Desactivada: la app funciona 100 % sin conexión.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Switch(c.activa, { if (it) dialogo = true else a.desactivar() })
            }
        }
        AvisoNoDiagnostico(
            "Servidor de PRUEBA (${ClienteSmart.API_BASE.removePrefix("https://")}). Úsalo solo con datos ficticios o de prueba. " +
                "Se envían peso, IMC, talla, cintura y respuestas a cuestionarios (FHIR R4). Nunca se envían la MAC de la balanza, las tramas BLE ni la impedancia."
        )

        if (c.activa) {
            Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!c.conectado) {
                        Text("Sin sesión", style = MaterialTheme.typography.titleMedium)
                        Text("Inicia sesión en el servidor (SMART on FHIR). En la página de prueba elige el usuario ficticio «ana» o «beto» y pulsa «Autorizar».",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        Button(onClick = a.conectar, modifier = Modifier.fillMaxWidth()) { Text("Conectar e iniciar sesión") }
                    } else {
                        Text("Conectado como Patient/${c.paciente}", style = MaterialTheme.typography.titleMedium)
                        FilaDato("Última sincronización", c.ultimaSyncUtc?.let { fecha(it) } ?: "Aún no")
                        FilaDato("Pendientes por enviar", "${s.pendientes}", destacado = true)
                        FilaDato("Enviados", "${s.sincronizados}")
                        FilaDato("Rechazados por el servidor", "${s.rechazados.size}")
                        FilaDato("Desde", if (c.desdeUtc == 0L) "todos los registros" else fecha(c.desdeUtc))
                        c.ultimoResultado?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                        if (s.enCurso) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Button(onClick = { a.sincronizar(false) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Sync, null); Spacer(Modifier.width(8.dp)); Text("Sincronizar ahora")
                        }
                        if (s.rechazados.isNotEmpty()) OutlinedButton(onClick = { a.sincronizar(true) }, modifier = Modifier.fillMaxWidth()) { Text("Reintentar rechazados") }
                        OutlinedButton(onClick = a.verServidor, modifier = Modifier.fillMaxWidth()) { Text("Ver mis pesos en el servidor") }
                        TextButton(onClick = a.desconectar) { Text("Cerrar sesión (borrar tokens)", color = cs.error) }
                    }
                    c.ultimoError?.let { Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (s.rechazados.isNotEmpty()) {
                Seccion("Rechazados")
                s.rechazados.take(10).forEach { r ->
                    Text("${r.clave} · ${r.error}", style = MaterialTheme.typography.bodySmall, color = cs.error)
                }
            }
            if (s.servidor != null || s.mensajeServidor != null) {
                Seccion("En el servidor (solo lectura)")
                s.mensajeServidor?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                s.servidor?.let { lista ->
                    if (lista.isEmpty()) Text("Sin pesos registrados en el servidor.", color = cs.onSurfaceVariant)
                    lista.forEach { o ->
                        FilaDato(o.effectiveDateTime?.replace('T', ' ')?.take(16) ?: "—", "${kg(o.valueQuantity?.value, 2)} ${o.valueQuantity?.unit ?: ""} · ${o.id}")
                    }
                }
            }
        }
        Text("Datos para soporte: cliente ${ClienteSmart.CLIENT_ID}, scopes «${c.scopes ?: ClienteSmart.SCOPES}».",
            style = MaterialTheme.typography.bodySmall, color = Extra.colores.lavanda)
        Spacer(Modifier.height(24.dp))
    }

    if (dialogo) {
        var incluir by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { dialogo = false },
            title = { Text("Activar sincronización") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Acepto que la app envíe al servidor de PRUEBA de Gooluco mis pesajes (peso, IMC calculado), talla, cintura " +
                            "y respuestas a los cuestionarios de estrés, FINDRISC y Nórdico, asociados al usuario de prueba con el que inicie sesión. " +
                            "Es un entorno de pruebas: solo debo usar datos ficticios o de prueba, no datos reales de pacientes. " +
                            "Puedo desactivarlo y cerrar sesión cuando quiera."
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(incluir, { incluir = it })
                        Text("Enviar también los registros anteriores a hoy", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialogo = false; a.activar(incluir) }) { Text("Acepto y activo") } },
            dismissButton = { TextButton(onClick = { dialogo = false }) { Text("Cancelar") } },
        )
    }
}
