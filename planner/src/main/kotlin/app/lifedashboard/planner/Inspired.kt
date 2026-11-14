package app.lifedashboard.planner

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.IsoFields
import kotlin.math.roundToInt

/**
 * Ideen, die von beliebten Apps abgeschaut sind (Forest, Habitica, Duolingo, Strava, Sleep Cycle, YNAB …) –
 * hier nur die Rechenlogik, alles aus vorhandenen Daten. Siehe docs/IDEEN.md, Runde 2.
 */
object Inspired {

    // ---------- Fokus-Wald (Forest): jeder geschaffte 25-Min-Abschnitt pflanzt einen Baum ----------

    data class ForestDay(val date: LocalDate, val trees: Int, val withered: Int)

    /** Wald dieser Woche (Mo–So): Bäume = geschaffte Minuten / 25 (mind. 1 je Block), verwelkt = verpasste Blöcke. */
    fun forest(results: List<PlanResult>, today: LocalDate): List<ForestDay> {
        val monday = today.with(DayOfWeek.MONDAY)
        return (0..6).map { i ->
            val day = monday.plusDays(i.toLong())
            val mine = results.filter { it.date == day }
            ForestDay(day, mine.filter { it.done }.sumOf { maxOf(1, it.minutes / 25) }, mine.count { !it.done })
        }
    }

    // ---------- Wochen-Boss (Habitica): das Wochenziel als Gegner mit Lebenspunkten ----------

    data class Boss(val name: String, val hp: Int, val damage: Int) {
        val left: Int get() = (hp - damage).coerceAtLeast(0)
        val defeated: Boolean get() = hp > 0 && damage >= hp
        val fraction: Float get() = if (hp <= 0) 0f else (damage.toFloat() / hp).coerceIn(0f, 1f)
    }

    private val bossNames = listOf("Der Aufschieber", "Graf Ablenkung", "Das Sofa-Monster", "Captain Keine-Lust", "Die Montags-Hydra", "Lord Morgen-mach-ich's")

    /** Lebenspunkte = Wochenziel (Lern- + Sport-Minuten), Schaden = was schon geschafft ist. Jede Woche ein anderer Gegner. */
    fun weekBoss(goalMinutes: Int, doneMinutes: Int, today: LocalDate): Boss =
        Boss(bossNames[today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) % bossNames.size], goalMinutes.coerceAtLeast(0), doneMinutes.coerceAtLeast(0))

    // ---------- Liga gegen dich selbst (Duolingo): diese Woche gegen letzte Woche ----------

    data class WeekCompare(val thisWeek: Int, val lastWeekSamePoint: Int, val lastWeekTotal: Int) {
        val ahead: Int get() = thisWeek - lastWeekSamePoint
    }

    /** Geschaffte Minuten dieser Woche gegen die letzte Woche – bis zum selben Wochentag und insgesamt. */
    fun weekCompare(results: List<PlanResult>, today: LocalDate): WeekCompare {
        val monday = today.with(DayOfWeek.MONDAY)
        val lastMonday = monday.minusWeeks(1)
        val done = results.filter { it.done }
        return WeekCompare(
            thisWeek = done.filter { !it.date.isBefore(monday) && !it.date.isAfter(today) }.sumOf { it.minutes },
            lastWeekSamePoint = done.filter { !it.date.isBefore(lastMonday) && !it.date.isAfter(today.minusWeeks(1)) }.sumOf { it.minutes },
            lastWeekTotal = done.filter { !it.date.isBefore(lastMonday) && it.date.isBefore(monday) }.sumOf { it.minutes },
        )
    }

    // ---------- Trainingsbelastung (Strava): letzte 7 Tage gegen den Schnitt der 4 Wochen davor ----------

    enum class LoadLevel { NO_DATA, LOW, STEADY, BUILDING, TOO_MUCH }

    data class TrainingLoad(val last7: Int, val weeklyAverage: Int, val level: LoadLevel)

    fun trainingLoad(activities: List<Activity>, today: LocalDate): TrainingLoad {
        val last7 = activities.filter { !it.date.isBefore(today.minusDays(6)) && !it.date.isAfter(today) }.sumOf { it.minutes }
        val before = activities.filter { !it.date.isBefore(today.minusDays(34)) && it.date.isBefore(today.minusDays(6)) }.sumOf { it.minutes }
        val avg = before / 4
        val level = when {
            avg < 30 && last7 < 30 -> LoadLevel.NO_DATA
            avg < 30 -> LoadLevel.BUILDING
            last7 > avg * 1.5 -> LoadLevel.TOO_MUCH
            last7 > avg * 1.1 -> LoadLevel.BUILDING
            last7 < avg * 0.6 -> LoadLevel.LOW
            else -> LoadLevel.STEADY
        }
        return TrainingLoad(last7, avg, level)
    }

    // ---------- Sport-Monat (Strava-Monatsrückblick) ----------

    data class SportMonth(val sessions: Int, val minutes: Int, val km: Double)

    fun sportMonth(activities: List<Activity>, anyDayInMonth: LocalDate): SportMonth {
        val mine = activities.filter { it.date.year == anyDayInMonth.year && it.date.month == anyDayInMonth.month }
        return SportMonth(mine.size, mine.sumOf { it.minutes }, mine.sumOf { it.meters ?: 0 } / 1000.0)
    }

    // ---------- Schlaf (Sleep Cycle): Schnitt, Schlafschuld, Zubettgeh-Zeit ----------

    data class SleepAdvice(val averageMinutes: Int?, val debtMinutes: Int, val bedtime: LocalTime, val nights: Int)

    /**
     * [nights]: Schlaf der letzten Nächte (null = keine Daten). Ziel 8 Std (Jugendliche brauchen 8–10).
     * Zubettgehen = Aufstehen − 8 Std − 15 Min zum Einschlafen.
     */
    fun sleepAdvice(nights: List<Duration?>, wakeUp: LocalTime, targetMinutes: Int = 480): SleepAdvice {
        val known = nights.filterNotNull().map { it.toMinutes().toInt() }
        return SleepAdvice(
            averageMinutes = known.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            debtMinutes = known.sumOf { (targetMinutes - it).coerceAtLeast(0) },
            bedtime = wakeUp.minusMinutes(targetMinutes + 15L),
            nights = known.size,
        )
    }

    // ---------- Geld ----------

    /** Tage am Stück ohne Ausgabe (bis heute; heute zählt nur, wenn noch nichts ausgegeben wurde). */
    fun noSpendStreak(spendDays: Set<LocalDate>, today: LocalDate, firstDay: LocalDate): Int {
        var d = today
        var n = 0
        while (d !in spendDays && !d.isBefore(firstDay)) { n++; d = d.minusDays(1) }
        return n
    }

    /** „Was kostet das in Arbeit?“ – Minuten im Minijob für einen Preis. null ohne Stundenlohn. */
    fun workMinutes(priceCents: Long, wageCentsPerHour: Long): Int? =
        if (wageCentsPerHour <= 0 || priceCents <= 0) null else (priceCents * 60.0 / wageCentsPerHour).roundToInt()

    /** Frei verfügbar pro Tag bis zum nächsten Geld-Eingang (z. B. Taschengeld am 21.). */
    fun perDay(availableCents: Long, today: LocalDate, nextIncome: LocalDate): Long {
        val days = (nextIncome.toEpochDay() - today.toEpochDay()).coerceAtLeast(1)
        return (availableCents.coerceAtLeast(0)) / days
    }

    /** Nächster Tag mit festem Geld-Eingang aus den Monatstagen (z. B. 21 und 1). */
    fun nextIncomeDay(daysOfMonth: List<Int>, today: LocalDate): LocalDate? = daysOfMonth.mapNotNull { dom ->
        (0..1).map { today.plusMonths(it.toLong()) }.map { m -> m.withDayOfMonth(minOf(dom.coerceAtLeast(1), m.lengthOfMonth())) }.firstOrNull { it.isAfter(today) }
    }.minOrNull()

    // ---------- Noten-Trend ----------

    enum class Trend { UP, DOWN, FLAT }

    /** Letzte Note gegen den Schnitt der Noten davor ([points] in zeitlicher Reihenfolge). null bei weniger als 2 Noten. */
    fun gradeTrend(points: List<Int>): Trend? {
        if (points.size < 2) return null
        val before = points.dropLast(1).average()
        val diff = points.last() - before
        return when {
            diff >= 1.0 -> Trend.UP
            diff <= -1.0 -> Trend.DOWN
            else -> Trend.FLAT
        }
    }

    // ---------- Wiederkehrende Aufgaben (Todoist) ----------

    /** Nächster Termin einer wiederkehrenden Aufgabe: ab dem alten Termin, aber nie in der Vergangenheit. */
    fun nextRepeat(due: LocalDate?, doneOn: LocalDate, everyDays: Int): LocalDate {
        var next = (due ?: doneOn).plusDays(everyDays.toLong())
        while (!next.isAfter(doneOn)) next = next.plusDays(everyDays.toLong())
        return next
    }

    // ---------- Energie (Daylio): Verlauf und Zusammenhang mit geschafften Blöcken ----------

    /** Anteil geschaffter Blöcke an Tagen mit Energie ≥ 4 und ≤ 2 (null bei zu wenig Daten: je mind. 3 Blöcke). */
    fun energyEffect(energy: Map<LocalDate, Int>, results: List<PlanResult>): Pair<Int, Int>? {
        fun rate(days: Set<LocalDate>): Int? {
            val r = results.filter { it.date in days }
            return if (r.size < 3) null else (r.count { it.done } * 100.0 / r.size).roundToInt()
        }
        val fit = rate(energy.filterValues { it >= 4 }.keys) ?: return null
        val tired = rate(energy.filterValues { it <= 2 }.keys) ?: return null
        return fit to tired
    }
}
