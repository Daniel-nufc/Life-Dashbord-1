package app.lifedashboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Zusätzliche Farben im One-UI-Stil:
 * Hell = hellgrauer Hintergrund mit weißen Karten, Dunkel = schwarzer Hintergrund mit dunkelgrauen Karten.
 */
@Immutable
data class AppColors(
    val screenBackground: Color,
    val card: Color,
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(screenBackground = Color(0xFFF2F2F5), card = Color.White)
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private fun appTypography(): Typography {
    val base = Typography()
    return base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Normal, fontSize = 38.sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun LifeDashboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Dynamic Color: Farben passen sich deinem Hintergrundbild an (Android 12+, bei uns immer).
    val context = LocalContext.current
    val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    val appColors = if (darkTheme) {
        AppColors(screenBackground = Color.Black, card = colorScheme.surfaceContainerHigh)
    } else {
        AppColors(screenBackground = colorScheme.surfaceContainer, card = colorScheme.surfaceContainerLowest)
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
