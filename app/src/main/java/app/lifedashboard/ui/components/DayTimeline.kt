package app.lifedashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.PlanBlockEntity
import app.lifedashboard.data.db.PlanStatus
import app.lifedashboard.planner.TimeRange
import app.lifedashboard.ui.theme.LocalAppColors
import app.lifedashboard.ui.today.EntryKind
import app.lifedashboard.ui.today.TimelineEntry
import java.time.LocalTime

/** Was man mit einem Vorschlag aus dem Tagesplan tun kann. */
class PlanActions(
    val accept: (PlanBlockEntity) -> Unit,
    val done: (PlanBlockEntity) -> Unit,
    val decline: (PlanBlockEntity) -> Unit,
    val later: (PlanBlockEntity) -> Unit,
)

@Composable
fun entryColor(entry: TimelineEntry): Color = when (entry.kind) {
    EntryKind.LESSON -> entry.colorArgb?.let { Color(it) } ?: LifeArea.SCHOOL.color
    EntryKind.COMMUTE, EntryKind.BREAK -> MaterialTheme.colorScheme.outline
    EntryKind.FREE -> MaterialTheme.colorScheme.outlineVariant
    EntryKind.EVENT -> entry.colorArgb?.let { Color(it) } ?: entry.area?.color ?: MaterialTheme.colorScheme.primary
    else -> entry.area?.color ?: MaterialTheme.colorScheme.primary
}

/** Unterricht eines Tages zu einem einzigen Eintrag „Schule“ zusammenfassen, Wege weglassen (für die Wochenansicht). */
fun List<TimelineEntry>.compactSchool(): List<TimelineEntry> {
    val lessons = filter { it.kind == EntryKind.LESSON }
    if (lessons.isEmpty()) return filter { it.kind != EntryKind.COMMUTE }
    val school = TimelineEntry(
        range = TimeRange(lessons.minOf { it.range.start }, lessons.maxOf { it.range.end }),
        title = "Schule",
        subtitle = lessons.mapNotNull { it.subtitle?.substringBefore(" · ") }.distinct().joinToString(" · "),
        kind = EntryKind.LESSON,
        area = LifeArea.SCHOOL,
    )
    return (filter { it.kind != EntryKind.LESSON && it.kind != EntryKind.COMMUTE && !(it.kind == EntryKind.FREE && it.range.start < school.range.end && it.range.end > school.range.start) } + school)
        .sortedWith(compareBy({ it.range.start }, { it.range.end }))
}

/**
 * Der Tag als Zeitstrahl: links die Uhrzeit, in der Mitte eine durchgehende Linie mit einem Punkt
 * pro Eintrag, rechts der Inhalt. Feste Termine sind gefüllt, Vorschläge nur umrandet –
 * so sieht man sofort, was fest steht und was noch offen ist.
 */
@Composable
fun DayTimeline(
    entries: List<TimelineEntry>,
    now: LocalTime?,
    actions: PlanActions?,
    modifier: Modifier = Modifier,
    onEntryClick: ((TimelineEntry) -> Unit)? = null,
) {
    // Freizeit nach dem letzten geplanten Block: verdient, wenn alles erledigt ist
    val plans = entries.filter { it.plan != null && it.plan.status != PlanStatus.DECLINED && it.plan.status != PlanStatus.MOVED }
    val lastPlanEnd = plans.maxOfOrNull { it.range.end }
    val openBlocks = plans.count { it.plan!!.status != PlanStatus.DONE }
    Column(modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, entry ->
            val reward = entry.kind == EntryKind.FREE && lastPlanEnd != null && !entry.range.start.isBefore(lastPlanEnd) &&
                entries.subList(index, entries.size).none { it.plan != null }
            TimelineRow(
                entry = entry,
                freeLabel = if (reward) (if (openBlocks == 0) "Freizeit – verdient" else "Freizeit · noch $openBlocks ${if (openBlocks == 1) "Block" else "Blöcke"} davor") else null,
                isNow = now != null && now in entry.range,
                isPast = now != null && !entry.range.end.isAfter(now),
                first = index == 0,
                last = index == entries.lastIndex,
                actions = actions,
                onClick = if (onEntryClick != null && entry.refId != null && entry.plan == null) ({ onEntryClick(entry) }) else null,
            )
        }
    }
}

@Composable
private fun TimelineRow(
    entry: TimelineEntry,
    isNow: Boolean,
    isPast: Boolean,
    first: Boolean,
    last: Boolean,
    actions: PlanActions?,
    onClick: (() -> Unit)?,
    freeLabel: String? = null,
) {
    val tones = LocalAppColors.current
    val accent = entryColor(entry)
    val fade = if (isPast) 0.45f else 1f
    val free = entry.kind == EntryKind.FREE
    val status = entry.plan?.status
    val proposal = status == PlanStatus.PROPOSED
    val done = status == PlanStatus.DONE
    val minutes = entry.range.duration.toMinutes().toInt()
    // längere Einträge bekommen etwas mehr Höhe – die Länge ist auf einen Blick sichtbar
    val extra = if (free) 0.dp else (minutes / 30 * 3).coerceAtMost(18).dp

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick) else Modifier),
    ) {
        // Uhrzeit
        Column(Modifier.width(46.dp).padding(top = 9.dp), horizontalAlignment = Alignment.End) {
            Text(
                entry.range.start.hhmm(),
                style = MaterialTheme.typography.labelMedium,
                color = (if (isNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface).copy(alpha = if (free) 0.5f else fade),
            )
        }
        // Schiene mit Punkt
        Box(Modifier.width(44.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .padding(top = if (first) 12.dp else 0.dp, bottom = if (last) 12.dp else 0.dp)
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .background(tones.hairline),
            )
            when {
                free -> Box(Modifier.padding(top = 13.dp).size(7.dp).background(tones.card, CircleShape).border(1.5.dp, tones.hairline, CircleShape))
                entry.kind == EntryKind.LESSON && entry.title != "Schule" ->
                    Box(Modifier.padding(top = 11.dp).size(11.dp).background(tones.card, CircleShape).padding(2.dp).background(accent.copy(alpha = fade), CircleShape))
                else -> {
                    val icon = when {
                        done -> Icons.Rounded.Check
                        entry.kind == EntryKind.COMMUTE -> Icons.Rounded.DirectionsBus
                        entry.kind == EntryKind.BREAK -> Icons.Rounded.LocalCafe
                        entry.kind == EntryKind.LESSON -> Icons.Rounded.School
                        else -> entry.area?.icon ?: Icons.Rounded.Event
                    }
                    Box(
                        Modifier
                            .padding(top = 3.dp)
                            .size(28.dp)
                            .background(tones.card, CircleShape)
                            .then(
                                if (proposal) Modifier.border(1.5.dp, accent.copy(alpha = 0.7f * fade), CircleShape)
                                else Modifier.background(accent.copy(alpha = (if (isNow || done) 1f else 0.16f) * fade), CircleShape),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon, null,
                            tint = if ((isNow || done) && !proposal) tones.card else accent.readable().copy(alpha = fade),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
        // Inhalt
        Column(Modifier.weight(1f).padding(top = 7.dp, bottom = 9.dp + extra)) {
            if (free) {
                Text(
                    (freeLabel ?: "Frei") + " · ${entry.range.duration.pretty()}",
                    style = if (freeLabel != null) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                    color = when {
                        freeLabel?.startsWith("Freizeit –") == true -> tones.energy
                        freeLabel != null -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = fade)
                    },
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = fade),
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isNow) Pill("jetzt", MaterialTheme.colorScheme.primary)
                }
                val detail = listOfNotNull(
                    when (status) {
                        PlanStatus.PROPOSED -> "Vorschlag"
                        PlanStatus.ACCEPTED -> "Eingeplant"
                        PlanStatus.DONE -> "Erledigt"
                        else -> null
                    },
                    "bis ${entry.range.end.hhmm()}",
                    if (entry.plan != null) "$minutes Min" else entry.subtitle?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = fade),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val block = entry.plan
                if (block != null && actions != null && !done) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (proposal) {
                            MiniAction("Passt", filled = true) { actions.accept(block) }
                        } else {
                            MiniAction("Erledigt", filled = true) { actions.done(block) }
                        }
                        MiniAction("Später", filled = false) { actions.later(block) }
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).clickable { actions.decline(block) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, "Ablehnen", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }
    }
}

/** Kleiner, flacher Knopf für Vorschläge. */
@Composable
fun MiniAction(label: String, filled: Boolean, onClick: () -> Unit) {
    val tones = LocalAppColors.current
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (filled) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier.border(1.dp, tones.hairline, RoundedCornerShape(50)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
