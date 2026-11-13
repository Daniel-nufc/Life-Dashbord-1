package app.lifedashboard.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lifedashboard.ui.theme.LocalAppColors

/** Bereichsfarbe so anpassen, dass sie als Schrift auf hellem Grund gut lesbar bleibt. */
@Composable
fun Color.readable(): Color = if (LocalAppColors.current.isDark) this else lerp(this, Color.Black, 0.28f)

/** Fortschrittsring. Füllt sich beim Erscheinen weich von null auf den Wert. */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 6.dp,
    trackColor: Color = color.copy(alpha = 0.16f),
    content: @Composable () -> Unit = {},
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val animated by animateFloatAsState(if (shown) progress.coerceIn(0f, 1f) else 0f, tween(900, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(strokePx / 2, strokePx / 2)
            drawArc(trackColor, -90f, 360f, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/**
 * Kopfkarte: dunkle, ruhige Fläche mit heller Schrift – in beiden Modi. Die übergebene Farbe
 * erscheint nur als ganz leichter Schimmer in der Ecke.
 */
@Composable
fun GradientCard(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val app = LocalAppColors.current
    val glow = colors.firstOrNull() ?: MaterialTheme.colorScheme.primary
    CompositionLocalProvider(LocalContentColor provides app.onInk) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .focusOnScroll()
                .clip(MaterialTheme.shapes.large)
                .background(app.ink)
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(glow.copy(alpha = 0.20f), Color.Transparent),
                            center = Offset(size.width, 0f),
                            radius = size.width * 0.75f,
                        ),
                    )
                }
                .then(if (app.isDark) Modifier.border(1.dp, app.hairline, MaterialTheme.shapes.large) else Modifier)
                .padding(horizontal = 22.dp, vertical = 22.dp),
            content = content,
        )
    }
}

/** Kleines Symbolfeld für einen Bereich. */
@Composable
fun IconBadge(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(color.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color.readable(), modifier = Modifier.size(size * 0.54f))
    }
}

/** Kleines Etikett, z. B. „Woche A“: getönte Fläche, farbige Schrift. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = Color.Unspecified) {
    // Halbtransparente Farbe = Etikett auf der dunklen Kopfkarte → Schrift bleibt hell
    val onHero = color.alpha < 1f
    val foreground = when {
        textColor != Color.Unspecified && textColor != Color.White -> textColor
        onHero -> LocalAppColors.current.onInk
        else -> color.readable()
    }
    Text(
        text = text,
        color = foreground,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (onHero) Color.White.copy(alpha = 0.10f) else color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Überschrift einer Kachel: kleines Symbol in Bereichsfarbe, Titel als ruhige Zeile in Großbuchstaben. */
@Composable
fun TileHeader(icon: ImageVector, title: String, color: Color, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color.readable(), modifier = Modifier.size(18.dp))
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}
