package app.lifedashboard.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
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
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp))
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
