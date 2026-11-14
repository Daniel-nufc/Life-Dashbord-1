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
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Insights
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

private fun hm(min: Int) = if (min >= 60) "%d:%02d Std".format(min / 60, min % 60) else "$min Min"

/** Schlaf (Idee: Sleep Cycle): Schnitt der Woche, Schlafschuld und wann du heute ins Bett solltest. */
@Composable
fun SleepCard(nights: List<java.time.Duration?>) {
    val tomorrow = LocalDate.now().plusDays(1).dayOfWeek
    val weekend = tomorrow == java.time.DayOfWeek.SATURDAY || tomorrow == java.time.DayOfWeek.SUNDAY
    val wake = if (weekend) java.time.LocalTime.of(9, 30) else java.time.LocalTime.of(6, 0)
    val a = app.lifedashboard.planner.Inspired.sleepAdvice(nights, wake)
    val color = androidx.compose.ui.graphics.Color(0xFF6B5BD2)
    SectionCard {
        TileHeader(androidx.compose.material.icons.Icons.Rounded.Bedtime, "Schlaf", color)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(a.averageMinutes?.let(::hm) ?: "–", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("  im Schnitt (${a.nights} Nächte)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(
            when {
                a.averageMinutes == null -> "Noch keine Schlafdaten. Mit Samsung Health über Health Connect kommen sie automatisch."
                a.debtMinutes >= 120 -> "Dir fehlen diese Woche ${hm(a.debtMinutes)} Schlaf auf 8 Std pro Nacht. Das merkst du beim Lernen und beim Sport."
                a.debtMinutes > 0 -> "Fast im Soll – nur ${hm(a.debtMinutes)} unter 8 Std pro Nacht."
                else -> "Ausgeschlafen. So lernt es sich am leichtesten."
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "Heute bis %d:%02d Uhr ins Bett, dann sind es 8 Std bis %d:%02d.".format(a.bedtime.hour, a.bedtime.minute, wake.hour, wake.minute),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Trainingsbelastung (Idee: Strava): letzte 7 Tage gegen deinen Schnitt – steigern ja, aber nicht sprunghaft. */
@Composable
fun TrainingLoadCard(workouts: List<WorkoutWithSets>) {
    val load = app.lifedashboard.planner.Inspired.trainingLoad(workouts.activities(), LocalDate.now())
    if (load.level == app.lifedashboard.planner.Inspired.LoadLevel.NO_DATA) return
    val (label, text, color) = when (load.level) {
        app.lifedashboard.planner.Inspired.LoadLevel.TOO_MUCH -> Triple("Sehr viel", "Deutlich mehr als gewohnt. Gönn dir einen ruhigen Tag – Verletzungen kosten mehr Zeit als eine Pause.", MaterialTheme.colorScheme.error)
        app.lifedashboard.planner.Inspired.LoadLevel.BUILDING -> Triple("Im Aufbau", "Etwas mehr als gewohnt – genau so wird man fitter. Weiter so, aber nicht sprunghaft steigern.", LifeArea.SPORT.color)
        app.lifedashboard.planner.Inspired.LoadLevel.LOW -> Triple("Wenig", "Weniger als sonst. Ein Sport-Zeitfenster diese Woche bringt dich wieder in den Rhythmus.", MaterialTheme.colorScheme.tertiary)
        else -> Triple("Gleichmäßig", "So viel wie gewohnt. Stabil dranbleiben ist die halbe Ironman-Vorbereitung.", LifeArea.SPORT.color)
    }
    SectionCard {
        TileHeader(androidx.compose.material.icons.Icons.Rounded.Insights, "Trainingsbelastung", color) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        }
        Text("${hm(load.last7)} in 7 Tagen · sonst ${hm(load.weeklyAverage)} pro Woche", style = MaterialTheme.typography.titleSmall)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Sport-Monat (Idee: Strava-Monatsrückblick): dieser Monat gegen den letzten. */
@Composable
fun SportMonthCard(workouts: List<WorkoutWithSets>) {
    val today = LocalDate.now()
    val now = app.lifedashboard.planner.Inspired.sportMonth(workouts.activities(), today)
    val last = app.lifedashboard.planner.Inspired.sportMonth(workouts.activities(), today.minusMonths(1))
    if (now.sessions == 0 && last.sessions == 0) return
    SectionCard {
        TileHeader(Icons.Rounded.EmojiEvents, "Dein Sport-Monat", LifeArea.SPORT.color)
        @Composable
        fun row(label: String, a: String, b: String) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(a, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(b, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
        row("", "dieser Monat", "letzter Monat")
        row("Trainings", "${now.sessions}", "${last.sessions}")
        row("Zeit", hm(now.minutes), hm(last.minutes))
        row("Strecke", km(now.km), km(last.km))
        if (today.dayOfMonth >= 20 && now.minutes < last.minutes) {
            Text("Noch ${hm(last.minutes - now.minutes)} bis zum Stand vom letzten Monat – das geht noch.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
