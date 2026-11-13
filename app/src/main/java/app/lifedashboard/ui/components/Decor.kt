package app.lifedashboard.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lifedashboard.ui.theme.LocalAppColors

/** Bereichsfarbe so anpassen, dass sie als Schrift auf hellem Grund gut lesbar bleibt. */
@Composable
fun Color.readable(): Color = if (LocalAppColors.current.isDark) this else lerp(this, Color.Black, 0.28f)

/**
 * Fortschrittsring mit leuchtendem Verlauf: Der Bogen wird zum Ende hin heller und hat
 * einen kleinen Lichtpunkt an der Spitze. Füllt sich beim Erscheinen weich.
 */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 6.dp,
    trackColor: Color = color.copy(alpha = 0.14f),
    endColor: Color = lerp(color, Color.White, 0.35f),
    content: @Composable () -> Unit = {},
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val animated by animateFloatAsState(if (shown) progress.coerceIn(0f, 1f) else 0f, tween(1000, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(strokePx / 2, strokePx / 2)
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokePx))
            if (animated > 0f) {
                rotate(-90f) {
                    val sweep = 360f * animated
                    drawArc(
                        Brush.sweepGradient(0f to color.copy(alpha = 0.65f), animated.coerceAtLeast(0.01f) to endColor, 1f to endColor),
                        0f, sweep, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round),
                    )
                    // Lichtpunkt an der Spitze
                    val r = arcSize.width / 2
                    val angle = Math.toRadians(sweep.toDouble())
                    val tip = Offset(center.x + (r * kotlin.math.cos(angle)).toFloat(), center.y + (r * kotlin.math.sin(angle)).toFloat())
                    drawCircle(endColor.copy(alpha = 0.35f), strokePx * 1.1f, tip)
                }
            }
        }
        content()
    }
}

/** Zahl, die beim Erscheinen und bei Änderungen hochzählt. */
@Composable
fun AnimatedNumber(value: Int, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified, format: (Int) -> String = { "$it" }) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val animated by animateIntAsState(if (shown) value else 0, tween(900, easing = FastOutSlowInEasing), label = "number")
    Text(format(animated), style = style, color = color, modifier = modifier)
}

/**
 * Kopfkarte: dunkle Fläche mit zwei langsam wandernden Lichtflecken (Polarlicht) und heller Schrift –
 * in beiden Modi. Die erste Farbe bestimmt das Hauptlicht.
 */
@Composable
fun GradientCard(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tones = LocalAppColors.current
    val main = colors.firstOrNull() ?: MaterialTheme.colorScheme.primary
    val second = colors.getOrNull(1) ?: tones.glow
    val drift by rememberInfiniteTransition(label = "aurora").animateFloat(
        0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "drift",
    )
    CompositionLocalProvider(LocalContentColor provides tones.onInk) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .focusOnScroll()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(tones.ink)
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(main.copy(alpha = 0.42f), Color.Transparent),
                            center = Offset(size.width * (0.75f + 0.2f * drift), size.height * (0.05f + 0.15f * drift)),
                            radius = size.width * 0.75f,
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            listOf(second.copy(alpha = 0.24f), Color.Transparent),
                            center = Offset(size.width * (0.1f + 0.15f * (1 - drift)), size.height * 1.05f),
                            radius = size.width * 0.6f,
                        ),
                    )
                }
                .border(1.dp, Color.White.copy(alpha = 0.07f), MaterialTheme.shapes.extraLarge)
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
            .clip(RoundedCornerShape(size * 0.32f))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.10f)))),
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
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (onHero) Color.White.copy(alpha = 0.12f) else color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Überschrift einer Kachel: Symbol in getöntem Feld, Titel als ruhige Zeile in Großbuchstaben. */
@Composable
fun TileHeader(icon: ImageVector, title: String, color: Color, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBadge(icon, color, size = 28.dp)
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}
