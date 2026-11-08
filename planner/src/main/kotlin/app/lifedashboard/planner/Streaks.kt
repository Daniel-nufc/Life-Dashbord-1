package app.lifedashboard.planner

import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * Streaks für tägliche Gewohnheiten – mit Joker: Pro Kalenderwoche darf ein Tag fehlen,
 * ohne dass die Serie reißt („nicht bestrafen“).
 */
object Streaks {
    data class Result(
        val current: Int,
        val best: Int,
        /** Tage, die durch einen Joker gerettet wurden */
        val jokerDays: Set<LocalDate>,
    )

    private fun weekKey(date: LocalDate) = date.get(IsoFields.WEEK_BASED_YEAR) * 100 + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    fun compute(done: Set<LocalDate>, today: LocalDate, jokersPerWeek: Int = 1): Result {
        if (done.isEmpty()) return Result(0, 0, emptySet())
        val earliest = done.min()

        // Aktuelle Serie: rückwärts ab heute (heute noch offen zählt nicht als Bruch)
        val jokers = mutableSetOf<LocalDate>()
        val usedPerWeek = mutableMapOf<Int, Int>()
        var day = if (today in done) today else today.minusDays(1)
        var current = 0
        while (!day.isBefore(earliest)) {
            if (day in done) {
                current++
            } else {
                val week = weekKey(day)
                val used = usedPerWeek[week] ?: 0
                // Joker nur, wenn davor noch erledigte Tage kommen (sonst ist die Serie hier zu Ende)
                if (used < jokersPerWeek && day.minusDays(1) in done) {
                    usedPerWeek[week] = used + 1
                    jokers += day
                    current++
                } else {
                    break
                }
            }
            day = day.minusDays(1)
        }

        // Beste Serie: vorwärts über die ganze Historie
        var best = 0
        var run = 0
        val bestUsed = mutableMapOf<Int, Int>()
        var d = earliest
        val last = if (today in done) today else today.minusDays(1)
        while (!d.isAfter(last)) {
            if (d in done) {
                run++
            } else {
                val week = weekKey(d)
                val used = bestUsed[week] ?: 0
                if (used < jokersPerWeek && run > 0 && d.plusDays(1) in done) {
                    bestUsed[week] = used + 1
                    run++
                } else {
                    run = 0
                }
            }
            best = maxOf(best, run)
            d = d.plusDays(1)
        }
        return Result(current, maxOf(best, current), jokers)
    }
}
