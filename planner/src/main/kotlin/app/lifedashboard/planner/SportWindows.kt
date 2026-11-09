package app.lifedashboard.planner

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.max

/**
 * Alte Einheiten-Typen bleiben nur, damit gespeicherte Ergebnisse älterer Versionen lesbar sind.
 * Neu geplant wird ausschließlich [WINDOW] – ein freies Sport-Zeitfenster.
 */
enum class SessionType(val label: String) {
    EASY_RUN("Lockerer Lauf"),
    INTERVALS("Intervalle"),
    TEMPO("Tempolauf"),
    LONG_RUN("Langer Lauf"),
    STRENGTH("Kraft & Stabi"),
    BIKE("Radfahren"),
    SWIM("Schwimmen"),
    WINDOW("Sport-Zeitfenster"),
}

/** Wie anstrengend war es? (optional, nur für dich) */
enum class Effort { EASY, OK, HARD }

data class SportProfile(
    /** Wie viele freie Sport-Zeitfenster pro Woche (zusätzlich zu festen Trainings wie Floorball) */
    val windowsPerWeek: Int = 3,
    /** Länge eines Zeitfensters in Minuten */
    val minutes: Int = 60,
    /** Tage mit festem Training (z. B. Floorball Mo/Mi) – dort kein extra Fenster */
    val fixedSportDays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
    /** Ruhetag (null = keiner) */
    val restDay: DayOfWeek? = DayOfWeek.SUNDAY,
)

/** Ein freies Zeitfenster für Sport. WAS du machst, entscheidest du. */
data class SportWindow(
    val date: LocalDate,
    val minutes: Int,
    /** gekürzt (z. B. wegen Klausurphase oder weil du es selbst kürzer wolltest) */
    val reduced: Boolean = false,
) {
    val type: SessionType get() = SessionType.WINDOW
    val key: String get() = "train:${date.toEpochDay()}:WINDOW"
    val title: String get() = "Sport-Zeitfenster"
}

/**
 * Verteilt Sport-Zeitfenster über die Woche – ohne vorzuschreiben, welcher Sport.
 *
 * Regeln:
 * - nicht an Tagen mit festem Training (Floorball) und nicht am Ruhetag
 * - möglichst verteilt (nicht direkt neben einem anderen Sporttag)
 * - nicht an Tagen ohne Zeit (lange Job-Schicht)
 * - Klausur-Tag und Vortag nur, wenn sonst nichts frei ist
 * - Klausurphase: ein Fenster weniger (mind. 1) und kürzer – Bewegung bleibt, aber Lernen geht vor
 */
object SportWindowPlanner {
    private val preference = listOf(
        DayOfWeek.SATURDAY, DayOfWeek.THURSDAY, DayOfWeek.TUESDAY, DayOfWeek.FRIDAY,
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY,
    )

    fun planWeek(
        profile: SportProfile,
        monday: LocalDate,
        examDays: Set<LocalDate> = emptySet(),
        examPhase: Boolean = false,
        blockedDays: Set<LocalDate> = emptySet(),
    ): List<SportWindow> {
        val wanted = profile.windowsPerWeek.coerceIn(0, 7)
        if (wanted == 0) return emptyList()
        val count = if (examPhase) max(1, wanted - 1) else wanted
        val minutes = if (examPhase) roundTo15(profile.minutes * 0.75).coerceAtLeast(30) else profile.minutes
        val days = (0L..6L).map { monday.plusDays(it) }
        val candidates = days.filter { d ->
            d.dayOfWeek != profile.restDay && d.dayOfWeek !in profile.fixedSportDays && d !in blockedDays
        }
        val chosen = mutableListOf<LocalDate>()
        repeat(count) {
            val sportDays = profile.fixedSportDays + chosen.map { it.dayOfWeek }
            val best = candidates.filter { it !in chosen }.minByOrNull { d ->
                var score = preference.indexOf(d.dayOfWeek).toDouble()
                if (d.dayOfWeek.plus(1) in sportDays || d.dayOfWeek.minus(1) in sportDays) score += 10
                if (d in examDays) score += 30
                if (d.plusDays(1) in examDays) score += 20
                score
            } ?: return@repeat
            chosen += best
        }
        return chosen.sorted().map { SportWindow(it, minutes, reduced = examPhase) }
    }

    /** Selbst gekürzt (z. B. nach schlechtem Schlaf): halbe Zeit, mind. 20 Min. */
    fun shorten(window: SportWindow): SportWindow = window.copy(minutes = max(20, roundTo15(window.minutes * 0.5)), reduced = true)

    private fun roundTo15(v: Double) = ((v / 15.0) + 0.5).toInt() * 15
}
