package app.lifedashboard.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fokus beim Scrollen: Was in der Bildschirmmitte liegt, ist voll da – Rubriken am Rand werden
 * minimal kleiner und blasser. Läuft ohne Neuaufbau der Oberfläche (nur auf der Zeichen-Ebene).
 */
fun Modifier.focusOnScroll(enabled: Boolean = true): Modifier = if (!enabled) this else composed {
    val distance = remember { mutableFloatStateOf(0f) }
    this
        .onGloballyPositioned { coordinates ->
            val viewport = coordinates.findRootCoordinates().size.height.toFloat()
            if (viewport <= 0f) return@onGloballyPositioned
            val top = coordinates.positionInRoot().y
            val bottom = top + coordinates.size.height
            val center = viewport / 2f
            val away = when {
                center < top -> top - center
                center > bottom -> center - bottom
                else -> 0f
            }
            distance.floatValue = (away / center).coerceIn(0f, 1f)
        }
        .graphicsLayer {
            val eased = distance.floatValue * distance.floatValue
            val scale = 1f - 0.045f * eased
            scaleX = scale
            scaleY = scale
            alpha = 1f - 0.22f * eased
        }
}

/**
 * Haken zum Abhaken: Der Kreis füllt sich, der Haken zeichnet sich, ein Ring pulsiert kurz nach außen,
 * dazu ein leichtes Vibrieren. Ersetzt das eckige Standard-Kästchen.
 */
@Composable
fun CheckMark(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 24.dp,
) {
    val view = LocalView.current
    val fill by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), label = "fill")
    val stroke by animateFloatAsState(if (checked) 1f else 0f, tween(if (checked) 260 else 120, delayMillis = if (checked) 90 else 0, easing = FastOutSlowInEasing), label = "stroke")
    val pulse = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        if (checked && !seen) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
        seen = checked
    }
    val onCheck = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline

    Box(
        modifier
            .size(44.dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 22.dp),
                onValueChange = {
                    view.performHapticFeedback(if (it) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK)
                    onCheckedChange(it)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val line = 1.6.dp.toPx()
            // Ring, der beim Abhaken nach außen läuft
            if (pulse.value < 1f) {
                drawCircle(color.copy(alpha = (1f - pulse.value) * 0.45f), radius = r * (1f + pulse.value * 0.9f), center = c, style = Stroke(line))
            }
            drawCircle(outline.copy(alpha = (1f - fill).coerceIn(0f, 1f)), radius = r - line / 2f, center = c, style = Stroke(line))
            if (fill > 0f) drawCircle(color, radius = r * fill.coerceAtMost(1.12f), center = c)
            if (stroke > 0f) {
                val path = Path().apply {
                    moveTo(c.x - r * 0.42f, c.y + r * 0.02f)
                    lineTo(c.x - r * 0.10f, c.y + r * 0.34f)
                    lineTo(c.x + r * 0.46f, c.y - r * 0.30f)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val part = Path()
                measure.getSegment(0f, measure.length * stroke, part, true)
                drawPath(part, onCheck, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Dünner Fortschrittsbalken, der sich weich füllt. */
@Composable
fun Meter(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 6.dp,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "meter")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(RoundedCornerShape(50))
                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), androidx.compose.ui.graphics.lerp(color, Color.White, 0.25f)))),
        )
    }
}
