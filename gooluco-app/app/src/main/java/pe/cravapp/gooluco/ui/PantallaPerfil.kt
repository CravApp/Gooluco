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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import pe.cravapp.gooluco.data.PerfilEntity
import pe.cravapp.gooluco.domain.Condicion
import pe.cravapp.gooluco.domain.Imc
import pe.cravapp.gooluco.domain.Sexo
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Consentimiento (RF-37), perfil (RF-01, RF-02) y eliminación de datos (RF-39). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaPerfil(
    actual: PerfilEntity?,
    guardar: (PerfilEntity, (String?) -> Unit) -> Unit,
    borrarTodo: () -> Unit,
    alGuardar: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val onboarding = actual?.consentimientoUtc == null
    var talla by remember { mutableStateOf(actual?.tallaCm?.toString() ?: "") }
    var nacimiento by remember { mutableStateOf(actual?.fechaNacimiento ?: "") }
    var sexo by remember { mutableStateOf(actual?.sexo?.let { Sexo.valueOf(it) }) }
    var condicion by remember { mutableStateOf(actual?.condicion?.let { Condicion.valueOf(it) } ?: Condicion.NO_DEFINIDA) }
    var cintura by remember { mutableStateOf(actual?.cinturaCm?.toString() ?: "") }
    var meta by remember { mutableStateOf(actual?.metaPesoKg?.toString() ?: "") }
    var consiento by remember { mutableStateOf(!onboarding) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmarBorrado by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (onboarding) "Bienvenido a Equilibra" else "Perfil", style = MaterialTheme.typography.headlineMedium)
        if (onboarding) Text(
            "Equilibra registra tu peso desde una balanza OKOK, calcula tu IMC con tu talla y te hace preguntas breves " +
                "sobre estrés durante el día para estimar tu riesgo físico. Todo se guarda solo en este teléfono.",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
        )
        fun dec(s: String) = s.replace(',', '.').toDoubleOrNull()
        OutlinedTextField(talla, { talla = it }, label = { Text("Talla (cm)") }, singleLine = true,
            supportingText = { Text("Entre 100 y 230 cm. Se usa para calcular el IMC.") },
            isError = talla.isNotEmpty() && dec(talla)?.let { Imc.tallaValida(it) } != true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(nacimiento, { nacimiento = it }, label = { Text("Fecha de nacimiento (AAAA-MM-DD)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        Text("Sexo", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sexo.entries.forEach { s -> FilterChip(sexo == s, { sexo = s }, label = { Text(s.etiqueta) }) }
        }
        Text("Condición", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Condicion.entries.forEach { c -> FilterChip(condicion == c, { condicion = c }, label = { Text(c.etiqueta) }) }
        }
        OutlinedTextField(cintura, { cintura = it }, label = { Text("Perímetro de cintura (cm, opcional)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(meta, { meta = it }, label = { Text("Meta de peso (kg, opcional)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

        if (onboarding) Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(consiento, { consiento = it })
            Text("Doy mi consentimiento expreso e informado para que la app registre y procese mis datos de salud " +
                "(peso, IMC, respuestas a cuestionarios) solo en este dispositivo. Puedo revocarlo y borrar mis datos cuando quiera.",
                style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = cs.error) }
        Button(
            enabled = consiento,
            onClick = {
                val t = dec(talla)
                val f = try { LocalDate.parse(nacimiento.trim()) } catch (_: DateTimeParseException) { null }
                error = when {
                    t == null || !Imc.tallaValida(t) -> "La talla debe estar entre 100 y 230 cm."
                    f == null || f.isAfter(LocalDate.now()) || f.isBefore(LocalDate.of(1900, 1, 1)) -> "Fecha de nacimiento inválida (formato AAAA-MM-DD)."
                    sexo == null -> "Selecciona el sexo."
                    else -> null
                }
                if (error == null) {
                    guardar(
                        (actual ?: PerfilEntity(tallaCm = t!!, fechaNacimiento = "", sexo = "", condicion = "")).copy(
                            tallaCm = t!!, fechaNacimiento = f.toString(), sexo = sexo!!.name, condicion = condicion.name,
                            cinturaCm = dec(cintura), metaPesoKg = dec(meta),
                            consentimientoUtc = actual?.consentimientoUtc ?: System.currentTimeMillis(),
                        )
                    ) { err -> error = err; if (err == null) alGuardar() }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (onboarding) "Aceptar y comenzar" else "Guardar perfil") }

        if (!onboarding) {
            HorizontalDivider()
            Text("Privacidad", style = MaterialTheme.typography.titleMedium)
            Text("Tus datos no salen del teléfono. Revocar el consentimiento elimina todos los datos locales y detiene la agenda.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            OutlinedButton(onClick = { confirmarBorrado = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                modifier = Modifier.fillMaxWidth()) { Text("Revocar consentimiento y borrar mis datos") }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmarBorrado) AlertDialog(
        onDismissRequest = { confirmarBorrado = false },
        title = { Text("¿Borrar todos tus datos?") },
        text = { Text("Se eliminarán perfil, pesajes, cuestionarios y agenda de este teléfono. No se puede deshacer.") },
        confirmButton = { TextButton(onClick = { confirmarBorrado = false; borrarTodo() }) { Text("Borrar todo", color = cs.error) } },
        dismissButton = { TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") } },
    )
}
