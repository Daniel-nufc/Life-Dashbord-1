package app.lifedashboard.ui.focus

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.lifedashboard.data.db.PlanBlockEntity
import app.lifedashboard.planner.DemandType
import app.lifedashboard.planner.Progress
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.theme.LocalAppColors
import app.lifedashboard.ui.today.area
import app.lifedashboard.ui.today.isSchoolType
import kotlinx.coroutines.delay

/**
 * Fokus-Modus: Vollbild mit großer Uhr und genau einer Aufgabe. Die Zeit läuft weiter, auch wenn
 * du kurz die App verlässt. Am Ende: „Geschafft“ (Punkte + Zockzeit) oder noch eine Runde.
 */
@Composable
fun FocusMode(block: PlanBlockEntity, onDone: () -> Unit, onClose: () -> Unit) {
    val view = LocalView.current
    val tones = LocalAppColors.current
    val accent = block.type.area().color
    val plannedMinutes = (block.endMinute - block.startMinute).coerceAtLeast(5)
    var totalSeconds by rememberSaveable(block.id) { mutableStateOf(plannedMinutes * 60) }
    var startedAt by rememberSaveable(block.id) { mutableLongStateOf(System.currentTimeMillis()) }
    var pausedElapsed by rememberSaveable(block.id) { mutableLongStateOf(-1L) }
    var finished by rememberSaveable(block.id) { mutableStateOf(false) }
    var confirmQuit by rememberSaveable(block.id) { mutableStateOf(false) }
    var now by androidx.compose.runtime.remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Bildschirm bleibt an, solange der Fokus läuft
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(block.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val elapsed = if (pausedElapsed >= 0) pausedElapsed else (now - startedAt) / 1000
    val left = (totalSeconds - elapsed).coerceAtLeast(0)
    val progress = (elapsed.toFloat() / totalSeconds).coerceIn(0f, 1f)
    LaunchedEffect(left == 0L) {
        if (left == 0L && !finished) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
        0.85f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b",
    )
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "s",
    )

    Dialog(onDismissRequest = { confirmQuit = true }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF07070A))
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.30f * breathe), Color.Transparent), radius = 1400f))
                .safeDrawingPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(finished, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut() }, label = "focus") { done ->
                if (done) {
                    val minutes = (elapsed / 60).toInt().coerceAtLeast(1)
                    val points = minutes * (if (block.type == DemandType.SPORT) Progress.XP_PER_SPORT_MINUTE else Progress.XP_PER_STUDY_MINUTE)
                    val gaming = if (block.type.isSchoolType()) Progress.gamingMinutes(minutes, 0) else Progress.gamingMinutes(0, minutes)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("GESCHAFFT", color = accent, style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp)
                        Text("+$points", color = Color.White, fontSize = 84.sp, fontWeight = FontWeight.Bold, lineHeight = 88.sp)
                        Text("Punkte", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium)
                        if (gaming > 0) {
                            Text(
                                "+$gaming Min Zockzeit verdient",
                                color = tones.energy,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        Text(
                            "${if (minutes == 1) "1 Minute" else "$minutes Minuten"} konzentriert. Genau so geht das.",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Spacer(Modifier.height(24.dp))
                        BigButton("Weiter", accent, filled = true) { onClose() }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 520.dp)) {
                        Text("FOKUS", color = accent, style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            block.title,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                        )
                        Spacer(Modifier.height(36.dp))
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(280.dp)) {
                                val stroke = 10.dp.toPx()
                                val arc = Size(size.width - stroke, size.height - stroke)
                                val tl = Offset(stroke / 2, stroke / 2)
                                drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, tl, arc, style = Stroke(stroke))
                                rotate(-90f) {
                                    drawArc(
                                        Brush.sweepGradient(0f to accent.copy(alpha = 0.5f), progress.coerceAtLeast(0.01f) to accent, 1f to accent),
                                        0f, 360f * progress, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round),
                                    )
                                }
                                // langsam kreisender Lichtpunkt außen – zeigt: die Zeit läuft
                                rotate(spin) {
                                    drawCircle(accent.copy(alpha = 0.55f), 3.dp.toPx(), Offset(center.x, stroke / 2 - 9.dp.toPx() + 6.dp.toPx()))
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "%d:%02d".format(left / 60, left % 60),
                                    color = Color.White,
                                    fontSize = 68.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-1).sp,
                                )
                                Text(
                                    if (pausedElapsed >= 0) "pausiert" else if (left == 0L) "Zeit um" else "noch übrig",
                                    color = Color.White.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        Spacer(Modifier.height(40.dp))
                        if (left == 0L) {
                            BigButton("Geschafft", accent, filled = true) { finished = true; onDone() }
                            Spacer(Modifier.height(12.dp))
                            BigButton("Noch 25 Minuten", accent, filled = false) { totalSeconds += 25 * 60 }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                BigButton(if (pausedElapsed >= 0) "Weiter" else "Pause", accent, filled = false) {
                                    if (pausedElapsed >= 0) {
                                        startedAt = System.currentTimeMillis() - pausedElapsed * 1000
                                        pausedElapsed = -1
                                    } else {
                                        pausedElapsed = elapsed
                                    }
                                }
                                BigButton("Fertig", accent, filled = true) { finished = true; onDone() }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Abbrechen",
                            color = Color.White.copy(alpha = 0.45f),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { confirmQuit = true }.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
        if (confirmQuit) {
            AlertDialog(
                onDismissRequest = { confirmQuit = false },
                title = { Text("Wirklich aufhören?") },
                text = { Text("Wer jetzt abbricht, verliert den Block. Noch 5 Minuten schaffst du.") },
                confirmButton = { TextButton(onClick = { confirmQuit = false }) { Text("Weitermachen") } },
                dismissButton = { TextButton(onClick = { confirmQuit = false; onClose() }) { Text("Abbrechen", color = MaterialTheme.colorScheme.error) } },
            )
        }
    }
}

@Composable
private fun BigButton(label: String, accent: Color, filled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (filled) Color(0xFF04140D) else Color.White,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .widthIn(min = 150.dp)
            .clip(RoundedCornerShape(50))
            .then(if (filled) Modifier.background(accent) else Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50)))
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 16.dp),
    )
}

/** „Nur 15 Minuten?“ – statt Sport ganz ausfallen zu lassen. */
@Composable
fun MiniSportDialog(block: PlanBlockEntity, onMini: () -> Unit, onDecline: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nur 15 Minuten?") },
        text = {
            Text(
                "Statt ${block.endMinute - block.startMinute} Minuten ganz ausfallen zu lassen: Mach 15. " +
                    "Locker laufen, Rad oder Kraft – egal. Kurz ist besser als nichts, und deine Serie bleibt.",
            )
        },
        confirmButton = { TextButton(onClick = onMini) { Text("15 Min machen") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Heute nicht", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
}
