package app.lifedashboard.ui.today

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lifedashboard.data.db.PlanBlockEntity
import app.lifedashboard.data.db.PlanStatus
import app.lifedashboard.planner.ProgressState
import app.lifedashboard.ui.components.GradientCard
import app.lifedashboard.ui.components.MiniAction
import app.lifedashboard.ui.components.Pill
import app.lifedashboard.ui.components.ProgressRing
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.TileHeader
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.theme.LocalAppColors

private fun hm(minutes: Int) = if (minutes >= 60) "%d:%02d Std".format(minutes / 60, minutes % 60) else "$minutes Min"
private fun clock(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

/**
 * Kopfkarte von „Heute“: oben Datum und Coach, darunter groß der nächste Block mit „Jetzt starten“.
 * Rechts der Tagesring: wie viel vom heutigen Plan schon geschafft ist.
 */
@Composable
fun NextUpHero(
    s: TodayUiState.Ready,
    progress: ProgressState?,
    onStart: (PlanBlockEntity) -> Unit,
    onLater: (PlanBlockEntity) -> Unit,
    /** „Keine Lust“: nur 5 Minuten anfangen */
    onStartSmall: ((PlanBlockEntity) -> Unit)? = null,
) {
    val tones = LocalAppColors.current
    val block = s.nextBlock
    val glow = block?.type?.area()?.color ?: MaterialTheme.colorScheme.primary
    val minuteNow = s.now.hour * 60 + s.now.minute
    GradientCard(listOf(glow, tones.glow), modifier = Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(s.dateLabel, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Pill("Woche ${s.weekType.name}", Color.White.copy(alpha = 0.2f))
            s.holiday?.let { Pill(it.take(18), Color.White.copy(alpha = 0.2f)) }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (block != null) {
                    val running = block.startMinute <= minuteNow
                    Text(
                        if (running) "JETZT DRAN" else "ALS NÄCHSTES · ${clock(block.startMinute)}",
                        color = glow,
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.4.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        block.title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${hm(block.endMinute - block.startMinute)}" + if (block.status == PlanStatus.PROPOSED) " · Vorschlag" else "",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text("FÜR HEUTE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (s.missedToday > 0) "${s.missedToday} ${if (s.missedToday == 1) "Block" else "Blöcke"} verpasst" else if (s.doneBlocks > 0) "Alles erledigt" else "Nichts mehr geplant",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        when {
                            s.missedToday > 0 -> "Das kostet Punkte. Hol morgen auf – der Plan ist schon angepasst."
                            s.doneBlocks > 0 -> "Freizeit verdient. Genieß sie ohne schlechtes Gewissen."
                            else -> "Freie Zeit – nutz sie bewusst."
                        },
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            val planned = (s.doneBlocks + s.schoolBlocksLeft + s.missedToday).coerceAtLeast(s.plannedBlocks)
            val dayProgress = if (planned == 0) 0f else s.doneBlocks.toFloat() / planned
            ProgressRing(dayProgress, MaterialTheme.colorScheme.primary, size = 82.dp, stroke = 7.dp, trackColor = Color.White.copy(alpha = 0.10f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${s.doneBlocks}/$planned", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text("Blöcke", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (block != null) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onStart(block) }
                        .padding(start = 16.dp, end = 22.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                    Text("Jetzt starten", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    "Später",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                        .clickable { onLater(block) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                )
                if (onStartSmall != null && block.endMinute - block.startMinute > 10) {
                    Text(
                        "Nur 5 Min",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                            .clickable { onStartSmall(block) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        }
        // Strenger Coach: Rückstand und Coach-Satz unten in der Karte
        val backlog = progress?.backlogMinutes ?: 0
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        Spacer(Modifier.height(12.dp))
        if (backlog > 0) {
            Text(
                "Rückstand diese Woche: ${hm(backlog)} – das hat dich ${progress?.penaltyXp ?: 0} Punkte gekostet." +
                    if (progress?.reward?.locked == true) " Zockzeit gesperrt, bis du aufholst." else "",
                color = tones.warning,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(s.coachLine, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Abends: der Plan für morgen auf einen Blick, mit einem Tipp bestätigen. */
@Composable
fun TomorrowCard(blocks: List<PlanBlockEntity>, onConfirm: () -> Unit) {
    val visible = blocks.filter { it.status == PlanStatus.PROPOSED || it.status == PlanStatus.ACCEPTED }.sortedBy { it.startMinute }
    val open = visible.count { it.status == PlanStatus.PROPOSED }
    SectionCard {
        TileHeader(Icons.Rounded.WbTwilight, "Plan für morgen", MaterialTheme.colorScheme.primary) {
            Text(hm(visible.sumOf { it.endMinute - it.startMinute }), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (visible.isEmpty()) {
            Text("Morgen ist nichts geplant.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        visible.forEach { b ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(clock(b.startMinute), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(46.dp))
                Box(Modifier.size(8.dp).background(b.type.area().color, CircleShape))
                Text(b.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${b.endMinute - b.startMinute} Min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (open > 0) {
            MiniAction("Plan bestätigen", filled = true, onClick = onConfirm)
        } else {
            Text("Bestätigt. Morgen musst du nur noch anfangen.", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.positive)
        }
    }
}
