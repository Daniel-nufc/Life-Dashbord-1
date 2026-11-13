package app.lifedashboard.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Ruhiges, fast einfarbiges Erscheinungsbild: tiefes Schwarz bzw. warmes Hellgrau,
 * feine Linien statt Schatten und genau eine Akzentfarbe. Bereichsfarben sind nur kleine Markierungen.
 */
@Immutable
data class AppColors(
    val screenBackground: Color,
    val card: Color,
    /** Feine Trennlinie / Kartenrand */
    val hairline: Color,
    /** Dunkle Fläche für die Kopfkarte – in beiden Modi mit heller Schrift */
    val ink: Color,
    val onInk: Color,
    val onInkMuted: Color,
    val positive: Color,
    val warning: Color,
    /** Energie-Farbe für Serie, Zockzeit und Belohnungen */
    val energy: Color,
    /** Zweite Leuchtfarbe für Verläufe (zusammen mit primary) */
    val glow: Color,
    val isDark: Boolean,
)

private val DarkAppColors = AppColors(
    screenBackground = Color(0xFF0A0A0B),
    card = Color(0xFF141416),
    hairline = Color(0xFF27272B),
    ink = Color(0xFF1A1A1D),
    onInk = Color(0xFFF3F2EE),
    onInkMuted = Color(0xFFA3A3A8),
    positive = Color(0xFF3EE29A),
    warning = Color(0xFFF2B84B),
    energy = Color(0xFFFF8A4C),
    glow = Color(0xFF3BC9F0),
    isDark = true,
)

private val LightAppColors = AppColors(
    screenBackground = Color(0xFFF3F1EC),
    card = Color(0xFFFFFFFF),
    hairline = Color(0xFFE4E1D9),
    ink = Color(0xFF17171A),
    onInk = Color(0xFFF6F4EF),
    onInkMuted = Color(0xFFB0AFAA),
    positive = Color(0xFF07875A),
    warning = Color(0xFFB07400),
    energy = Color(0xFFE8622A),
    glow = Color(0xFF0B9CC4),
    isDark = false,
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF3EE29A),
    onPrimary = Color(0xFF032317),
    primaryContainer = Color(0xFF143427),
    onPrimaryContainer = Color(0xFFC4EEDB),
    secondary = Color(0xFFC9C8C3),
    onSecondary = Color(0xFF1A1A1D),
    secondaryContainer = Color(0xFF242427),
    onSecondaryContainer = Color(0xFFE6E5E0),
    tertiary = Color(0xFFFF8A4C),
    onTertiary = Color(0xFF2E1104),
    tertiaryContainer = Color(0xFF3A2C12),
    onTertiaryContainer = Color(0xFFF6DDB0),
    error = Color(0xFFFF7A6B),
    onError = Color(0xFF3A0A05),
    errorContainer = Color(0xFF4A1711),
    onErrorContainer = Color(0xFFFFD2CB),
    background = Color(0xFF0A0A0B),
    onBackground = Color(0xFFF3F2EE),
    surface = Color(0xFF0A0A0B),
    onSurface = Color(0xFFF3F2EE),
    surfaceVariant = Color(0xFF202023),
    onSurfaceVariant = Color(0xFF9D9DA3),
    outline = Color(0xFF4A4A50),
    outlineVariant = Color(0xFF27272B),
    surfaceContainerLowest = Color(0xFF070708),
    surfaceContainerLow = Color(0xFF101012),
    surfaceContainer = Color(0xFF141416),
    surfaceContainerHigh = Color(0xFF1A1A1D),
    surfaceContainerHighest = Color(0xFF222225),
    inverseSurface = Color(0xFFF3F2EE),
    inverseOnSurface = Color(0xFF17171A),
    inversePrimary = Color(0xFF07875A),
    scrim = Color(0xFF000000),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF07875A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9ECE3),
    onPrimaryContainer = Color(0xFF0A3D2C),
    secondary = Color(0xFF3A3A3D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9E6DF),
    onSecondaryContainer = Color(0xFF1F1F22),
    tertiary = Color(0xFFE8622A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF4E6C8),
    onTertiaryContainer = Color(0xFF3F2A04),
    error = Color(0xFFB93A2C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DCD7),
    onErrorContainer = Color(0xFF5A130B),
    background = Color(0xFFF3F1EC),
    onBackground = Color(0xFF17171A),
    surface = Color(0xFFF3F1EC),
    onSurface = Color(0xFF17171A),
    surfaceVariant = Color(0xFFE9E6DF),
    onSurfaceVariant = Color(0xFF66655F),
    outline = Color(0xFF9A988F),
    outlineVariant = Color(0xFFE4E1D9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF8F4),
    surfaceContainer = Color(0xFFF7F5F0),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFECE9E2),
    inverseSurface = Color(0xFF17171A),
    inverseOnSurface = Color(0xFFF6F4EF),
    inversePrimary = Color(0xFF3EE29A),
    scrim = Color(0xFF000000),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Zahlen stehen sauber untereinander (gleich breite Ziffern). */
private const val TABULAR = "tnum"

private fun appTypography(): Typography {
    val base = Typography()
    return base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = 42.sp, lineHeight = 46.sp, letterSpacing = (-0.03).em, fontFeatureSettings = TABULAR),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em, fontFeatureSettings = TABULAR),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.015).em, fontFeatureSettings = TABULAR),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em, fontFeatureSettings = TABULAR),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.em, fontFeatureSettings = TABULAR),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.em),
        bodyLarge = base.bodyLarge.copy(letterSpacing = 0.em, lineHeight = 23.sp),
        bodyMedium = base.bodyMedium.copy(letterSpacing = 0.em, lineHeight = 20.sp),
        bodySmall = base.bodySmall.copy(letterSpacing = 0.01.em, lineHeight = 17.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.01.em, fontFeatureSettings = TABULAR),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.02.em, fontFeatureSettings = TABULAR),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.06.em),
    )
}

@Composable
fun LifeDashboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    // Symbole in Status- und Navigationsleiste passend zum gewählten Modus (auch bei manueller Wahl)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = AppShapes,
            typography = appTypography(),
            content = content,
        )
    }
}
