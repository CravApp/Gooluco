package pe.cravapp.gooluco.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

val LocaleEs: Locale = Locale.forLanguageTag("es-PE")

fun kg(x: Double?, dec: Int = 1): String = x?.let { String.format(LocaleEs, "%.${dec}f", it) } ?: "—"
fun num1(x: Double?): String = kg(x, 1)
fun pct(x: Double?): String = x?.let { "${(it * 100).toInt()} %" } ?: "—"

/** Chip de estado (tonal) como los badges del mockup. */
@Composable
fun ChipEstado(icono: ImageVector, texto: String, fondo: Color, contenido: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(fondo).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, null, tint = contenido, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(texto, style = MaterialTheme.typography.labelMedium, color = contenido)
    }
}

/** Píldora de métrica (icono en círculo + etiqueta + valor). */
@Composable
fun PildoraMetrica(icono: ImageVector, etiqueta: String, valor: String, fondo: Color, acento: Color, contenido: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(fondo).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(acento), contentAlignment = Alignment.Center) {
            Icon(icono, null, tint = fondo, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = contenido.copy(alpha = 0.8f))
            Text(valor, style = MaterialTheme.typography.titleMedium, color = contenido, maxLines = 1)
        }
    }
}

/** Anillo de progreso hacia la meta (peso actual vs meta). */
@Composable
fun AnilloPeso(progreso: Float, tam: Dp = 160.dp, contenido: @Composable () -> Unit) {
    val pista = MaterialTheme.colorScheme.surfaceVariant
    val arco = MaterialTheme.colorScheme.primaryContainer
    Box(Modifier.size(tam), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(tam)) {
            val grosor = 14.dp.toPx()
            drawArc(pista, 0f, 360f, false, style = Stroke(grosor), topLeft = Offset(grosor / 2, grosor / 2),
                size = androidx.compose.ui.geometry.Size(size.width - grosor, size.height - grosor))
            drawArc(arco, -90f, 360f * progreso.coerceIn(0f, 1f), false, style = Stroke(grosor, cap = StrokeCap.Round),
                topLeft = Offset(grosor / 2, grosor / 2), size = androidx.compose.ui.geometry.Size(size.width - grosor, size.height - grosor))
        }
        contenido()
    }
}

/** Gráfico de línea simple (peso por día). */
@Composable
fun GraficoLinea(valores: List<Double>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    if (valores.size < 2) {
        Box(modifier.height(120.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Se necesitan al menos 2 días con pesaje.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val min = valores.min(); val max = valores.max()
    val rango = (max - min).takeIf { it > 0.0 } ?: 1.0
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.height(120.dp).fillMaxWidth()) {
        val pasoX = size.width / (valores.size - 1)
        fun y(v: Double) = (size.height - 8f) - ((v - min) / rango).toFloat() * (size.height - 16f)
        drawLine(grid, Offset(0f, size.height - 1), Offset(size.width, size.height - 1))
        val path = Path()
        valores.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * pasoX, y(v)) }
        drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        valores.forEachIndexed { i, v -> drawCircle(color, 4.dp.toPx(), Offset(i * pasoX, y(v))) }
    }
}

@Composable
fun Seccion(titulo: String, modifier: Modifier = Modifier, accion: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(titulo, style = MaterialTheme.typography.titleLarge)
        accion?.invoke()
    }
}

/** Aviso permanente «apoyo, no diagnóstico» (RF-32). */
@Composable
fun AvisoNoDiagnostico(texto: String = "Apoyo, no diagnóstico. Ante dudas, consulte a su profesional de salud.") {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FilaDato(etiqueta: String, valor: String, destacado: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = if (destacado) FontWeight.SemiBold else FontWeight.Normal)
    }
}
