package app.lifedashboard.ui.progress

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.planner.Badge
import app.lifedashboard.planner.IronmanPath
import app.lifedashboard.planner.ProgressState
import app.lifedashboard.planner.WeekRing
import app.lifedashboard.ui.components.Meter
import app.lifedashboard.ui.components.ProgressRing
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.TileHeader
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.components.readable
import app.lifedashboard.ui.theme.LocalAppColors

private fun minutesText(minutes: Int): String = if (minutes >= 60) "%d:%02d h".format(minutes / 60, minutes % 60) else "$minutes Min"

private fun km(meters: Int): String = if (meters % 1000 == 0) "${meters / 1000}" else "%.1f".format(meters / 1000.0).replace('.', ',')

/** Kompakte Karte für „Heute“: Level, Punkte, Serie und die beiden Wochenringe. */
@Composable
fun ProgressCard(
    state: ProgressState,
    onClick: () -> Unit,
    onRedeem: ((Int) -> Unit)? = null,
    /** Spiegel am Abend: Social-Media-Zeit heute (null = nicht anzeigen) */
    socialMinutesToday: Int? = null,
) {
    SectionCard(onClick = onClick) {
        TileHeader(Icons.Rounded.Bolt, "Fortschritt", MaterialTheme.colorScheme.primary) {
            if (state.xpToday > 0) {
                Text("+${state.xpToday} heute", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        LevelRow(state)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WeekRingTile("Sport", state.sportWeek, LifeArea.SPORT.color, Modifier.weight(1f))
            WeekRingTile("Lernen", state.studyWeek, LifeArea.SCHOOL.color, Modifier.weight(1f))
            StreakTile(state, Modifier.weight(1f))
        }
        if (onRedeem != null) {
            Spacer(Modifier.height(18.dp))
            GamingRow(state, onRedeem)
        }
        socialMinutesToday?.let { social ->
            Spacer(Modifier.height(14.dp))
            MirrorRow(social, state.schoolMinutesToday)
        }
    }
}

/** Zockzeit: verdient mit Lernen und Sport, einlösbar in 30-Minuten-Schritten. */
@Composable
private fun GamingRow(state: ProgressState, onRedeem: (Int) -> Unit) {
    val tones = LocalAppColors.current
    val r = state.reward
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tones.energy.copy(alpha = if (r.locked) 0.06f else 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.SportsEsports, null, tint = if (r.locked) MaterialTheme.colorScheme.outline else tones.energy, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (r.locked) "Zockzeit gesperrt" else "Zockzeit: ${minutesText(r.availableMinutes)}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                if (r.locked) "Erst ${minutesText(state.backlogMinutes)} Rückstand aufholen."
                else "Verdient diese Woche: ${minutesText(r.earnedMinutes)}" + if (r.redeemedMinutes > 0) " · eingelöst ${minutesText(r.redeemedMinutes)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!r.locked && r.availableMinutes >= 30) {
            Text(
                "30 Min einlösen",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(tones.energy).clickable { onRedeem(30) }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** Ehrlicher Spiegel: Handy gegen Schule, heute. */
@Composable
private fun MirrorRow(socialMinutes: Int, schoolMinutes: Int) {
    val tones = LocalAppColors.current
    val total = (socialMinutes + schoolMinutes).coerceAtLeast(1)
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text("Heute: Handy ${minutesText(socialMinutes)}", style = MaterialTheme.typography.labelMedium, color = tones.warning, modifier = Modifier.weight(1f))
            Text("Schule ${minutesText(schoolMinutes)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))) {
            if (socialMinutes > 0) Box(Modifier.weight(socialMinutes.toFloat() / total).height(6.dp).background(tones.warning))
            if (schoolMinutes > 0) Box(Modifier.weight(schoolMinutes.toFloat() / total).height(6.dp).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
fun LevelRow(state: ProgressState) {
    Row(verticalAlignment = Alignment.Bottom) {
        app.lifedashboard.ui.components.AnimatedNumber(state.level.level, MaterialTheme.typography.displaySmall, Modifier.alignByBaseline())
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).alignByBaseline()) {
            Text("Level · ${state.level.title}", style = MaterialTheme.typography.titleSmall)
        }
        Text(
            "${state.level.xpTotal} Punkte · noch ${state.level.xpToNext}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
        )
    }
    Spacer(Modifier.height(10.dp))
    Meter(state.level.fraction)
}

@Composable
private fun WeekRingTile(label: String, ring: WeekRing, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ProgressRing(ring.fraction, color.readable(), size = 58.dp, stroke = 5.dp) {
            if (ring.reached) {
                Icon(Icons.Rounded.Check, contentDescription = "Wochenziel erreicht", tint = color.readable(), modifier = Modifier.size(22.dp))
            } else {
                Text("${(ring.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            "${minutesText(ring.minutes)} / ${minutesText(ring.goalMinutes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StreakTile(state: ProgressState, modifier: Modifier = Modifier) {
    val app = LocalAppColors.current
    val alive = state.streak.current > 0
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = if (state.activeToday) app.energy else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
                Text("${state.streak.current}", style = MaterialTheme.typography.headlineSmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Serie", style = MaterialTheme.typography.labelMedium)
        Text(
            when {
                state.activeToday -> "heute gesichert"
                alive -> "heute noch offen"
                else -> "heute starten"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (alive && !state.activeToday) app.energy else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Die Etappen-Strecke bis zum Ironman. */
@Composable
fun IronmanPathCard(path: IronmanPath, modifier: Modifier = Modifier) {
    val sport = LifeArea.SPORT.color.readable()
    val app = LocalAppColors.current
    SectionCard(modifier = modifier) {
        TileHeader(Icons.Rounded.Route, "Ironman-Weg", LifeArea.SPORT.color) {
            Text("${path.currentIndex}/${path.stages.size} Etappen", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val current = path.current
        Text(current?.stage?.name ?: "Ironman geschafft", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (current != null) "Aktuelle Etappe · ${(current.fraction * 100).toInt()} %" else "Alle Etappen erledigt",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        // Strecke: ein Punkt pro Etappe, die Linie füllt sich bis zum aktuellen Stand
        val progress by animateFloatAsState(path.fraction, tween(1100, easing = FastOutSlowInEasing), label = "path")
        val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
        val cardColor = app.card
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val n = path.stages.size
            val inset = 8.dp.toPx()
            val y = size.height / 2
            val w = size.width - inset * 2
            drawLine(track, Offset(inset, y), Offset(inset + w, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawLine(sport, Offset(inset, y), Offset(inset + w * progress, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            for (i in 1..n) {
                val x = inset + w * i / n
                val reached = i <= path.currentIndex
                drawCircle(cardColor, 7.dp.toPx(), Offset(x, y))
                drawCircle(if (reached) sport else track, if (i == n) 6.dp.toPx() else 4.5.dp.toPx(), Offset(x, y))
            }
            // Deine Position
            drawCircle(cardColor, 8.dp.toPx(), Offset(inset + w * progress, y))
            drawCircle(sport, 5.5.dp.toPx(), Offset(inset + w * progress, y))
            drawCircle(cardColor, 2.dp.toPx(), Offset(inset + w * progress, y))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Start", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Ironman", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (current != null) {
            Spacer(Modifier.height(16.dp))
            val s = current.stage
            if (s.swimMeters > 0) DisciplineRow(Icons.Rounded.Pool, "Schwimmen", path.bestSwimMeters, s.swimMeters, current.swim ?: 0f, sport)
            if (s.bikeMeters > 0) DisciplineRow(Icons.Rounded.DirectionsBike, "Rad", path.bestBikeMeters, s.bikeMeters, current.bike ?: 0f, sport)
            if (s.runMeters > 0) DisciplineRow(Icons.Rounded.DirectionsRun, "Laufen", path.bestRunMeters, s.runMeters, current.run ?: 0f, sport)
            Text(
                "Es zählt deine längste Strecke am Stück. Was du trainierst, entscheidest du.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun DisciplineRow(icon: ImageVector, label: String, best: Int, required: Int, fraction: Float, color: Color) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = if (fraction >= 1f) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "${km(minOf(best, required))} / ${km(required)} km",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Meter(fraction, color = color, height = 4.dp)
        }
    }
}

private fun badgeIcon(id: String): ImageVector = when {
    id.startsWith("run") -> Icons.Rounded.DirectionsRun
    id.startsWith("swim") -> Icons.Rounded.Pool
    id.startsWith("bike") -> Icons.Rounded.DirectionsBike
    id == "tri_week" -> Icons.Rounded.Flag
    id == "dist_100k" -> Icons.Rounded.Route
    id.startsWith("streak") -> Icons.Rounded.LocalFireDepartment
    id == "focus_day" -> Icons.Rounded.Timer
    id.startsWith("study") -> Icons.Rounded.MenuBook
    id.startsWith("tasks") -> Icons.Rounded.TaskAlt
    id.startsWith("level") -> Icons.Rounded.EmojiEvents
    id == "first_workout" -> Icons.Rounded.FitnessCenter
    else -> Icons.Rounded.School
}

/** Alle Abzeichen: erreichte in Akzentfarbe, offene blass mit Fortschritt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgeGrid(badges: List<Badge>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
        badges.sortedByDescending { it.earned }.forEach { BadgeTile(it, Modifier.weight(1f)) }
        // letzte Reihe auffüllen, damit alle Kacheln gleich breit bleiben
        repeat((3 - badges.size % 3) % 3) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun BadgeTile(badge: Badge, modifier: Modifier = Modifier) {
    val app = LocalAppColors.current
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (badge.earned) accent.copy(alpha = 0.10f) else Color.Transparent)
            .border(1.dp, if (badge.earned) accent.copy(alpha = 0.35f) else app.hairline, RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProgressRing(badge.progress, if (badge.earned) accent else MaterialTheme.colorScheme.outline, size = 46.dp, stroke = 3.dp) {
            Icon(badgeIcon(badge.id), null, tint = if (badge.earned) accent else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            badge.title,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = if (badge.earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            badge.description,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 2,
            maxLines = 2,
        )
    }
}

/** Level-Aufstieg: Ring zieht sich einmal ganz zu, die Zahl federt herein. Ein Tipp schließt. */
@Composable
fun LevelUpOverlay(level: Int, title: String, onDismiss: () -> Unit) {
    val view = LocalView.current
    val appear = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val number = remember { Animatable(0.6f) }
    LaunchedEffect(level) {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        appear.animateTo(1f, tween(220))
        ring.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(level) {
        number.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow))
    }
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(196.dp)) {
                    val stroke = 6.dp.toPx()
                    drawCircle(Color.White.copy(alpha = 0.10f), radius = size.minDimension / 2 - stroke, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                    drawArc(
                        accent, -90f, 360f * ring.value, false,
                        topLeft = Offset(stroke, stroke),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke * 2, size.height - stroke * 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = number.value; scaleY = number.value }) {
                    Text("LEVEL", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, letterSpacing = 3.sp)
                    Text("$level", color = Color.White, fontSize = 76.sp, fontWeight = FontWeight.Light, lineHeight = 80.sp)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("Level $level erreicht", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            if (title.isNotBlank()) Text(title, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onDismiss) { Text("Weiter", color = accent) }
        }
    }
}
