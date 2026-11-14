package app.lifedashboard.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.lifedashboard.planner.BestTime
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.TileHeader

/** Was die App aus den letzten 8 Wochen gelernt hat. */
data class InsightState(
    val heatmap: List<List<Int?>>,
    val bestTime: BestTime?,
    val runDays: Int,
    val forest: List<app.lifedashboard.planner.Inspired.ForestDay> = emptyList(),
    val compare: app.lifedashboard.planner.Inspired.WeekCompare? = null,
    /** Energie der letzten 14 Tage (Abend-Check), ältester Tag zuerst; null = nicht angegeben */
    val energy: List<Int?> = emptyList(),
    /** Geschaffte Blöcke in % an fitten / an platten Tagen */
    val energyEffect: Pair<Int, Int>? = null,
)

/** Geschaffte Minuten je Tag der letzten 8 Wochen (je dunkler, desto mehr). */
@Composable
fun HeatmapCard(s: InsightState) {
    val accent = MaterialTheme.colorScheme.primary
    SectionCard {
        TileHeader(Icons.Rounded.CalendarMonth, "Deine letzten 8 Wochen", accent) {
            if (s.runDays > 1) Text("${s.runDays} Tage am Stück", style = MaterialTheme.typography.labelMedium, color = accent)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            s.heatmap.forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { minutes ->
                        val alpha = when {
                            minutes == null -> 0f
                            minutes == 0 -> 0.08f
                            minutes < 30 -> 0.3f
                            minutes < 60 -> 0.5f
                            minutes < 120 -> 0.75f
                            else -> 1f
                        }
                        Box(
                            Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(4.dp))
                                .background(if (minutes == null) MaterialTheme.colorScheme.surface else accent.copy(alpha = alpha)),
                        )
                    }
                }
            }
        }
        Text(
            "Jedes Kästchen ist ein Tag – je dunkler, desto mehr geschafft (Lernen, Hausaufgaben, Sport aus dem Tagesplan).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Zu welcher Uhrzeit Blöcke wirklich geschafft werden – danach plant die App bevorzugt. */
@Composable
fun BestTimeCard(best: BestTime?) {
    SectionCard {
        TileHeader(Icons.Rounded.Schedule, "Deine beste Zeit", MaterialTheme.colorScheme.primary)
        Text(
            if (best == null) "Noch zu wenig Daten. Nach ein paar geschafften oder verpassten Blöcken siehst du hier, wann es bei dir am besten läuft."
            else "Zwischen ${best.fromHour} und ${best.toHour} Uhr schaffst du ${(best.rate * 100).toInt()} % deiner Blöcke (${best.samples} Blöcke). Leg Schweres in diese Zeit – die App lernt das auch aus deinen Entscheidungen.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun hm(min: Int) = if (min >= 60) "%d:%02d Std".format(min / 60, min % 60) else "$min Min"

/** Fokus-Wald (Idee: Forest): Für je 25 geschaffte Minuten wächst ein Baum, verpasste Blöcke verwelken. */
@Composable
fun ForestCard(s: InsightState) {
    val green = MaterialTheme.colorScheme.primary
    val trees = s.forest.sumOf { it.trees }
    SectionCard {
        TileHeader(androidx.compose.material.icons.Icons.Rounded.Park, "Dein Wald diese Woche", green) {
            Text("$trees ${if (trees == 1) "Baum" else "Bäume"}", style = MaterialTheme.typography.labelMedium, color = green)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            s.forest.forEachIndexed { i, day ->
                Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(76.dp), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            repeat(minOf(day.withered, 2)) {
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Park, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.height(18.dp))
                            }
                            repeat(minOf(day.trees, 4 - minOf(day.withered, 2))) {
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Park, null, tint = green, modifier = Modifier.height(18.dp))
                            }
                        }
                    }
                    Text(listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")[i], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (day.trees > 0) "${day.trees}" else "·", style = MaterialTheme.typography.labelSmall, color = green)
                }
            }
        }
        Text(
            if (trees == 0) "Noch kahl. Ein Fokus-Block von 25 Minuten pflanzt deinen ersten Baum."
            else "Je 25 geschaffte Minuten wächst ein Baum. Verpasste Blöcke verwelken (grau).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Wochen-Boss (Idee: Habitica): das Wochenziel als Gegner – jede geschaffte Minute ist ein Treffer. */
@Composable
fun BossCard(boss: app.lifedashboard.planner.Inspired.Boss) {
    val color = if (boss.defeated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    SectionCard {
        TileHeader(androidx.compose.material.icons.Icons.Rounded.Shield, "Wochen-Boss", color) {
            Text(if (boss.defeated) "Besiegt" else "${boss.left} LP", style = MaterialTheme.typography.labelMedium, color = color)
        }
        Text(boss.name, style = MaterialTheme.typography.titleLarge)
        app.lifedashboard.ui.components.Meter(boss.fraction, color = color, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Text(
            when {
                boss.hp <= 0 -> "Diese Woche gibt es kein Wochenziel – kein Gegner in Sicht."
                boss.defeated -> "Erledigt! Wochenziel aus Lernen und Sport geschafft. Nächste Woche wartet ein neuer Gegner."
                else -> "Jede Minute Lernen oder Sport ist ein Treffer. Noch ${hm(boss.left)} bis zum Sieg (${hm(boss.damage)} von ${hm(boss.hp)})."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Liga gegen dich selbst (Idee: Duolingo): diese Woche gegen letzte Woche – zum selben Zeitpunkt. */
@Composable
fun WeekCompareCard(c: app.lifedashboard.planner.Inspired.WeekCompare) {
    val ahead = c.ahead >= 0
    val color = if (ahead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    SectionCard {
        TileHeader(androidx.compose.material.icons.Icons.Rounded.CompareArrows, "Du gegen letzte Woche", color)
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(hm(c.thisWeek), style = MaterialTheme.typography.headlineSmall, color = color)
                Text("diese Woche", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(hm(c.lastWeekSamePoint), style = MaterialTheme.typography.headlineSmall)
                Text("letzte Woche bis heute", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            when {
                c.thisWeek == 0 && c.lastWeekTotal == 0 -> "Noch keine geschafften Blöcke – diese Woche legst du die Messlatte."
                ahead -> "Du liegst ${hm(c.ahead)} vorn. Letzte Woche waren es am Ende ${hm(c.lastWeekTotal)} – das schlägst du."
                else -> "Dir fehlen ${hm(-c.ahead)} auf dein Ich von letzter Woche. Ein Block reicht oft zum Aufholen."
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Energie-Verlauf (Idee: Daylio): der Abend-Check der letzten 14 Tage und was er mit deinen Blöcken macht. */
@Composable
fun EnergyCard(s: InsightState) {
    if (s.energy.count { it != null } < 2) return
    val accent = MaterialTheme.colorScheme.primary
    SectionCard {
        TileHeader(androidx.compose.material.icons.Icons.Rounded.Bolt, "Deine Energie", accent)
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            s.energy.forEach { e ->
                Box(
                    Modifier.weight(1f).height(((e ?: 0) * 10 + 4).dp).clip(RoundedCornerShape(3.dp))
                        .background(if (e == null) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f) else accent.copy(alpha = 0.35f + 0.13f * e)),
                )
            }
        }
        Text("letzte 14 Tage · hoch = fit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.energyEffect?.let { (fit, tired) ->
            Text(
                "An fitten Tagen schaffst du $fit % deiner Blöcke, an platten $tired %. " +
                    if (fit - tired >= 15) "Schlaf und Pausen lohnen sich bei dir messbar." else "Du ziehst auch an müden Tagen durch – stark.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
