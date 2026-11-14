package app.lifedashboard.planner

import java.time.DayOfWeek
import java.time.LocalDate

/** Ein Block aus dem Tagesplan für Auswertungen: geschafft (true) oder verpasst (false). */
data class PlanResult(val date: LocalDate, val startMinute: Int, val minutes: Int, val done: Boolean)

/** Wann schafft Daniel seine Blöcke wirklich? */
data class BestTime(val fromHour: Int, val toHour: Int, val rate: Double, val samples: Int)

object Insights {
    /**
     * Heatmap: geschaffte Minuten je Tag, [weeks] Wochen (Montag–Sonntag), älteste Woche zuerst.
     * Tage in der Zukunft sind null.
     */
    fun heatmap(outcomes: List<PlanResult>, today: LocalDate, weeks: Int = 8): List<List<Int?>> {
        val byDay = outcomes.filter { it.done }.groupBy { it.date }.mapValues { (_, l) -> l.sumOf { it.minutes } }
        val thisMonday = today.with(DayOfWeek.MONDAY)
        return (weeks - 1 downTo 0).map { w ->
            val monday = thisMonday.minusWeeks(w.toLong())
            (0..6).map { d ->
                val day = monday.plusDays(d.toLong())
                if (day.isAfter(today)) null else byDay[day] ?: 0
            }
        }
    }

    /**
     * Beste Zeit: das 2-Stunden-Fenster (nach Startzeit), in dem die meisten Blöcke wirklich geschafft werden.
     * Braucht mindestens [minSamples] Blöcke im Fenster, sonst null (noch zu wenig Daten).
     */
    fun bestTime(outcomes: List<PlanResult>, minSamples: Int = 4): BestTime? =
        (6..20).mapNotNull { h ->
            val inWindow = outcomes.filter { it.startMinute / 60 in h..h + 1 }
            if (inWindow.size < minSamples) null
            else BestTime(h, h + 2, inWindow.count { it.done }.toDouble() / inWindow.size, inWindow.size)
        }.maxWithOrNull(compareBy<BestTime> { it.rate }.thenBy { it.samples })

    /** Längste Serie von Tagen hintereinander mit mindestens einem geschafften Block (bis heute). */
    fun currentRunDays(outcomes: List<PlanResult>, today: LocalDate): Int {
        val days = outcomes.filter { it.done }.map { it.date }.toSet()
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }
}
