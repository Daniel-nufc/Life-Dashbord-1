package app.lifedashboard.ui.sport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.repository.WorkoutWithSets
import app.lifedashboard.planner.Activity
import app.lifedashboard.planner.RunningProgress
import app.lifedashboard.ui.components.Meter
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.TileHeader
import app.lifedashboard.ui.components.color
import java.time.LocalDate

fun List<WorkoutWithSets>.activities(): List<Activity> =
    map { Activity(it.date, it.workout.type.name, it.workout.durationMinutes, it.workout.distanceMeters) }

private fun km(v: Double) = "%.1f km".format(v).replace('.', ',')

/** Erstes Ironman-Ziel: 10 km am Stück. Nur Auswertung – was und wann Daniel läuft, entscheidet er selbst. */
@Composable
fun TenKCard(workouts: List<WorkoutWithSets>) {
    val p = RunningProgress.tenK(workouts.activities(), LocalDate.now())
    SectionCard {
        TileHeader(Icons.Rounded.DirectionsRun, "Weg zu 10 km", LifeArea.SPORT.color)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (p.longestRecentKm > 0) km(p.longestRecentKm) else "–", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("  längster Lauf (4 Wochen)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
        Meter((p.longestRecentKm / 10.0).toFloat().coerceIn(0f, 1f), color = LifeArea.SPORT.color, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Text(
            when (p.weeksTo10k) {
                null -> "Noch kein Lauf mit Strecke in den letzten 4 Wochen. Ein lockerer Lauf in einem Sport-Zeitfenster reicht für den Start."
                0 -> "10 km geschafft! Nächstes Ziel auf dem Ironman-Weg: länger oder schneller – du entscheidest."
                else -> "Mit +10 % pro Woche bist du in etwa ${p.weeksTo10k} Wochen bei 10 km. Nächster langer Lauf: ${km(p.nextLongRunKm ?: 0.0)}."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        // Wochen-km als kleine Balken
        val max = (p.weeklyKm.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            p.weeklyKm.forEachIndexed { i, v ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().height((6 + 50 * v / max).dp).clip(RoundedCornerShape(6.dp))
                            .background(LifeArea.SPORT.color.copy(alpha = if (i == p.weeklyKm.lastIndex) 1f else 0.45f)),
                    )
                    Text(if (v > 0) "%.0f".format(v) else "0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("km je Woche (diese Woche rechts)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Persönliche Bestleistungen aus dem Trainings-Log. */
@Composable
fun PersonalBestsCard(workouts: List<WorkoutWithSets>) {
    val b = RunningProgress.bests(workouts.activities())
    if (b.longestSessionMinutes == null) return
    SectionCard {
        TileHeader(Icons.Rounded.EmojiEvents, "Deine Bestleistungen", LifeArea.SPORT.color)
        @Composable
        fun line(label: String, value: String?, date: LocalDate? = null) {
            if (value == null) return
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    value + (date?.let { " · ${it.dayOfMonth}.${it.monthValue}." } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        line("Längster Lauf", b.longestRunKm?.let(::km), b.longestRunDate)
        line("Schnellste Pace (ab 3 km)", b.fastestPaceSecPerKm?.let(RunningProgress::pace), b.fastestPaceDate)
        line("Längste Radfahrt", b.longestRideKm?.let(::km))
        line("Längstes Training", b.longestSessionMinutes?.let { "${it / 60}:%02d Std".format(it % 60) })
    }
}
