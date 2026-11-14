package app.lifedashboard.planner

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.ln

/** Ein Training (aus dem Log, Strava oder Health Connect). [kind]: RUN, BIKE, SWIM, FLOORBALL … */
data class Activity(val date: LocalDate, val kind: String, val minutes: Int, val meters: Int?)

/** Stand auf dem Weg zu den ersten 10 km – ohne eine Sportart vorzuschreiben: nur Auswertung. */
data class TenKProgress(
    /** Längster Lauf der letzten 4 Wochen in km (0 = noch keiner) */
    val longestRecentKm: Double,
    /** Lauf-km je Woche, älteste zuerst (4 Wochen) */
    val weeklyKm: List<Double>,
    /** Wochen bis 10 km, wenn der längste Lauf jede Woche um 10 % wächst; 0 = geschafft, null = noch kein Lauf */
    val weeksTo10k: Int?,
    /** Vorschlag für den nächsten langen Lauf (+10 %, gerundet auf 0,5 km) */
    val nextLongRunKm: Double?,
)

data class PersonalBests(
    val longestRunKm: Double?,
    /** Schnellste Pace bei Läufen ab 3 km, Sekunden pro km */
    val fastestPaceSecPerKm: Int?,
    val longestRideKm: Double?,
    val longestSessionMinutes: Int?,
    /** Wann der jeweilige Rekord aufgestellt wurde */
    val longestRunDate: LocalDate?,
    val fastestPaceDate: LocalDate?,
)

object RunningProgress {
    fun tenK(activities: List<Activity>, today: LocalDate): TenKProgress {
        val runs = activities.filter { it.kind == "RUN" && (it.meters ?: 0) > 0 }
        val recent = runs.filter { !it.date.isBefore(today.minusDays(27)) && !it.date.isAfter(today) }
        val longest = (recent.maxOfOrNull { it.meters!! } ?: 0) / 1000.0
        val weekly = (3 downTo 0).map { w ->
            val end = today.minusDays(7L * w)
            val start = end.minusDays(6)
            runs.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }.sumOf { it.meters!! } / 1000.0
        }
        val weeks = when {
            longest <= 0.0 -> null
            longest >= 10.0 -> 0
            else -> ceil(ln(10.0 / longest) / ln(1.1)).toInt()
        }
        val next = if (longest <= 0.0 || longest >= 10.0) null else minOf(10.0, Math.round(longest * 1.1 * 2) / 2.0).coerceAtLeast(longest + 0.5)
        return TenKProgress(longest, weekly, weeks, next)
    }

    fun bests(activities: List<Activity>): PersonalBests {
        val runs = activities.filter { it.kind == "RUN" && (it.meters ?: 0) > 0 }
        val longestRun = runs.maxByOrNull { it.meters!! }
        val paced = runs.filter { it.meters!! >= 3000 && it.minutes > 0 }.minByOrNull { it.minutes * 60.0 / (it.meters!! / 1000.0) }
        return PersonalBests(
            longestRunKm = longestRun?.let { it.meters!! / 1000.0 },
            fastestPaceSecPerKm = paced?.let { (it.minutes * 60.0 / (it.meters!! / 1000.0)).toInt() },
            longestRideKm = activities.filter { it.kind == "BIKE" && (it.meters ?: 0) > 0 }.maxOfOrNull { it.meters!! }?.div(1000.0),
            longestSessionMinutes = activities.maxOfOrNull { it.minutes },
            longestRunDate = longestRun?.date,
            fastestPaceDate = paced?.date,
        )
    }

    fun pace(secPerKm: Int): String = "%d:%02d /km".format(secPerKm / 60, secPerKm % 60)
}
