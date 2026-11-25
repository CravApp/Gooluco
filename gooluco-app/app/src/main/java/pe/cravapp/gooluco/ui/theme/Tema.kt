package pe.cravapp.gooluco.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paleta del mockup «Equilibra» (tailwind config): Material 3, claro y oscuro.
private val Claro = lightColorScheme(
    primary = Color(0xFF0049BE), onPrimary = Color.White,
    primaryContainer = Color(0xFF1D61E7), onPrimaryContainer = Color(0xFFE8EBFF),
    secondary = Color(0xFF006A61), onSecondary = Color.White,
    secondaryContainer = Color(0xFF86F2E4), onSecondaryContainer = Color(0xFF006F66),
    tertiary = Color(0xFF365C00), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB1F661), onTertiaryContainer = Color(0xFF1E3700),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFAF8FF), onBackground = Color(0xFF191B23),
    surface = Color(0xFFFAF8FF), onSurface = Color(0xFF191B23),
    surfaceDim = Color(0xFFD9D9E4), surfaceBright = Color(0xFFFAF8FF),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F3FE),
    surfaceContainer = Color(0xFFEDEDF8), surfaceContainerHigh = Color(0xFFE7E7F3),
    surfaceContainerHighest = Color(0xFFE1E2ED),
    surfaceVariant = Color(0xFFDFE2F3), onSurfaceVariant = Color(0xFF424654),
    outline = Color(0xFF737686), outlineVariant = Color(0xFFC3C6D7),
    inverseSurface = Color(0xFF2E3039), inverseOnSurface = Color(0xFFF0F0FB), inversePrimary = Color(0xFFB3C5FF),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFB3C5FF), onPrimary = Color(0xFF002A76),
    primaryContainer = Color(0xFF1D61E7), onPrimaryContainer = Color(0xFFE8EBFF),
    secondary = Color(0xFF6BD8CB), onSecondary = Color(0xFF003732),
    secondaryContainer = Color(0xFF29A195), onSecondaryContainer = Color(0xFF001D1A),
    tertiary = Color(0xFF96D947), onTertiary = Color(0xFF1E3700),
    tertiaryContainer = Color(0xFF477700), onTertiaryContainer = Color(0xFFBFFF74),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF11131B), onBackground = Color(0xFFE1E2ED),
    surface = Color(0xFF11131B), onSurface = Color(0xFFE1E2ED),
    surfaceDim = Color(0xFF11131B), surfaceBright = Color(0xFF373942),
    surfaceContainerLowest = Color(0xFF0C0E15), surfaceContainerLow = Color(0xFF191B23),
    surfaceContainer = Color(0xFF1D1F27), surfaceContainerHigh = Color(0xFF282A32),
    surfaceContainerHighest = Color(0xFF32343D),
    surfaceVariant = Color(0xFF424654), onSurfaceVariant = Color(0xFFC3C6D7),
    outline = Color(0xFF8D90A0), outlineVariant = Color(0xFF424654),
    inverseSurface = Color(0xFFE1E2ED), inverseOnSurface = Color(0xFF2E3039), inversePrimary = Color(0xFF0054D7),
)

/** Colores extra del diseño que no existen en el esquema M3 (lavanda, éxito, advertencia). */
@Immutable
data class ColoresExtra(
    val lavanda: Color, val onLavanda: Color, val lavandaContainer: Color, val onLavandaContainer: Color,
    val exito: Color, val exitoContainer: Color, val onExitoContainer: Color,
    val advertencia: Color, val advertenciaContainer: Color, val onAdvertenciaContainer: Color,
)

private val ExtraClaro = ColoresExtra(
    Color(0xFF732EE4), Color.White, Color(0xFFEADDFF), Color(0xFF25005A),
    Color(0xFF006E2D), Color(0xFF7FFC97), Color(0xFF002109),
    Color(0xFF904D00), Color(0xFFFFDCC3), Color(0xFF2F1500),
)
private val ExtraOscuro = ColoresExtra(
    Color(0xFFD2BBFF), Color(0xFF3F008E), Color(0xFF5A00C6), Color(0xFFEADDFF),
    Color(0xFF62DF7D), Color(0xFF005320), Color(0xFF7FFC97),
    Color(0xFFFFB77D), Color(0xFF6E3900), Color(0xFFFFDCC3),
)

val LocalColoresExtra = staticCompositionLocalOf { ExtraClaro }

// Tipografía: escala del mockup. Las fuentes Red Hat Display / Radio Canada Big no se empaquetan (offline): se usa la del sistema.
private val Tipografia = Typography(
    displayLarge = TextStyle(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineLarge = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TemaEquilibra(oscuro: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalColoresExtra provides if (oscuro) ExtraOscuro else ExtraClaro) {
        MaterialTheme(colorScheme = if (oscuro) Oscuro else Claro, typography = Tipografia, shapes = Formas, content = content)
    }
}

object Extra {
    val colores: ColoresExtra @Composable get() = LocalColoresExtra.current
}
