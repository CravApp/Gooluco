package pe.cravapp.gooluco.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pe.cravapp.gooluco.domain.AntecedenteFamiliar
import pe.cravapp.gooluco.domain.Condicion
import pe.cravapp.gooluco.domain.Findrisc
import pe.cravapp.gooluco.domain.FindriscEntrada
import pe.cravapp.gooluco.domain.FindriscResultado
import pe.cravapp.gooluco.domain.Sexo

/** FINDRISC (RF-22): edad del perfil e IMC del último pesaje se completan solos; cintura manual. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaFindrisc(d: Datos, guardar: (FindriscResultado, Double, Double) -> Unit, cerrar: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val perfil = d.perfil
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("FINDRISC", style = MaterialTheme.typography.headlineMedium)
        if (perfil == null) { Text("Completa tu perfil primero."); return@Column }
        if (perfil.condicion == Condicion.DM2.name) {
            Text("FINDRISC estima el riesgo de desarrollar diabetes tipo 2; no aplica a personas con DM2 diagnosticada.")
            return@Column
        }
        val edad = d.panel.edad ?: 0
        var imcTxt by remember { mutableStateOf(d.panel.imc?.toString() ?: "") }
        var cinturaTxt by remember { mutableStateOf(perfil.cinturaCm?.toString() ?: "") }
        var actividad by remember { mutableStateOf<Boolean?>(null) }
        var verduras by remember { mutableStateOf<Boolean?>(null) }
        var antihip by remember { mutableStateOf<Boolean?>(null) }
        var glucosa by remember { mutableStateOf<Boolean?>(null) }
        var familia by remember { mutableStateOf<AntecedenteFamiliar?>(null) }

        Text("Edad (del perfil): $edad años", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(imcTxt, { imcTxt = it }, readOnly = d.panel.imc != null, singleLine = true,
            label = { Text(if (d.panel.imc != null) "IMC (último pesaje OKOK + talla)" else "IMC (sin pesaje: ingrésalo)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(cinturaTxt, { cinturaTxt = it }, singleLine = true, label = { Text("Perímetro de cintura (cm), a la altura del ombligo") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

        @Composable
        fun SiNo(titulo: String, valor: Boolean?, set: (Boolean) -> Unit) {
            Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(titulo, style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(valor == true, { set(true) }, label = { Text("Sí") })
                        FilterChip(valor == false, { set(false) }, label = { Text("No") })
                    }
                }
            }
        }
        SiNo("¿Realiza al menos 30 minutos de actividad física al día (trabajo o tiempo libre)?", actividad) { actividad = it }
        SiNo("¿Come verduras o frutas todos los días?", verduras) { verduras = it }
        SiNo("¿Toma o ha tomado medicamentos para la presión alta?", antihip) { antihip = it }
        SiNo("¿Le han encontrado alguna vez glucosa alta (control médico, enfermedad, embarazo)?", glucosa) { glucosa = it }
        Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("¿Algún familiar ha sido diagnosticado con diabetes (tipo 1 o 2)?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AntecedenteFamiliar.entries.forEach { f -> FilterChip(familia == f, { familia = f }, label = { Text(f.etiqueta) }) }
                }
            }
        }
        val imc = imcTxt.replace(',', '.').toDoubleOrNull()
        val cintura = cinturaTxt.replace(',', '.').toDoubleOrNull()
        val listo = imc != null && cintura != null && actividad != null && verduras != null && antihip != null && glucosa != null && familia != null
        val res = if (listo) Findrisc.evaluar(
            FindriscEntrada(edad, imc!!, cintura!!, Sexo.valueOf(perfil.sexo), actividad!!, verduras!!, antihip!!, glucosa!!, familia!!)
        ) else null
        res?.let {
            Text("Puntaje: ${it.total} / 26 · ${it.categoria.etiqueta} (riesgo a 10 años ${it.categoria.riesgo10Anios})",
                style = MaterialTheme.typography.titleMedium, color = cs.primary)
            if (it.positivoCortePeru) Text("Supera el punto de corte peruano (≥ ${Findrisc.CORTE_PERU}): conviene una prueba de glucosa o HbA1c.",
                style = MaterialTheme.typography.bodyMedium)
        }
        Button(enabled = res != null, onClick = { guardar(res!!, imc!!, cintura!!); cerrar() }, modifier = Modifier.fillMaxWidth()) { Text("Guardar FINDRISC") }
        AvisoNoDiagnostico()
        Spacer(Modifier.height(24.dp))
    }
}
