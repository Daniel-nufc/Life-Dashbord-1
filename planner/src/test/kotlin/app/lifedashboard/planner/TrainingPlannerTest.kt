package app.lifedashboard.planner

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrainingPlannerTest {
    private val start = LocalDate.of(2026, 11, 9)
    private val profile = TrainingProfile(planStart = start)

    private fun doneWeek(w: Int, effort: Effort = Effort.OK): List<SessionResult> =
        TrainingPlanner.planWeek(profile, start.plusWeeks(w.toLong()), emptyList()).map { SessionResult(it.date, it.type, true, effort, it.distanceKm) }

    @Test
    fun `nie zwei harte Tage hintereinander und Floorball zaehlt als hart`() {
        repeat(12) { w ->
            val week = TrainingPlanner.planWeek(profile, start.plusWeeks(w.toLong()), emptyList())
            val hardDays = week.filter { it.type.hard }.map { it.date.dayOfWeek }.toSet() + profile.hardDays
            hardDays.forEach { d -> assertFalse(d.plus(1) in hardDays, "Woche $w: $d und ${d.plus(1)} beide hart") }
            assertTrue(week.none { it.date.dayOfWeek == DayOfWeek.SUNDAY }, "Ruhetag Sonntag")
            assertTrue(week.none { it.date.dayOfWeek in profile.hardDays }, "keine Einheit an Floorball-Tagen")
        }
    }

    @Test
    fun `Steigerung max 10 Prozent und jede 4 Woche Erholung`() {
        val results = (0 until 12).flatMap { doneWeek(it) }
        val long = TrainingPlanner.longRunProgression(profile, results, 11)
        assertEquals(5.0, long[0])
        long.zipWithNext().forEachIndexed { i, (a, b) ->
            if (!TrainingPlanner.isRecoveryWeek(i + 1) && !TrainingPlanner.isRecoveryWeek(i)) assertTrue(b <= a * 1.1 + 0.5, "Woche ${i + 1}: $a → $b")
        }
        assertTrue(TrainingPlanner.isRecoveryWeek(3))
        assertTrue(long[3] < long[2], "Erholungswoche kürzer")
        assertTrue(long.max() <= 10.5, "nie mehr als ~10,5 km beim 10-km-Ziel")
    }

    @Test
    fun `verpasster langer Lauf wird nicht nachgeholt und nicht gesteigert`() {
        val week0 = doneWeek(0).filter { it.type != SessionType.LONG_RUN }
        val long = TrainingPlanner.longRunProgression(profile, week0, 1)
        assertEquals(long[0], long[1])
    }

    @Test
    fun `zu hart haelt den Umfang`() {
        val long = TrainingPlanner.longRunProgression(profile, doneWeek(0, Effort.HARD), 1)
        assertEquals(long[0], long[1])
    }

    @Test
    fun `Klausurphase weniger Umfang aber nicht null und keine Intervalle`() {
        val monday = start.plusWeeks(1)
        val normal = TrainingPlanner.planWeek(profile, monday, doneWeek(0))
        val exam = TrainingPlanner.planWeek(profile, monday, doneWeek(0), examPhase = true)
        assertTrue(exam.isNotEmpty())
        assertTrue(exam.sumOf { it.minutes } < normal.sumOf { it.minutes })
        assertTrue(exam.none { it.type == SessionType.INTERVALS || it.type == SessionType.TEMPO })
    }

    @Test
    fun `kein hartes Training am Tag vor der Klausur`() {
        val monday = start.plusWeeks(1)
        val examSaturday = setOf(monday.plusDays(6))
        val week = TrainingPlanner.planWeek(profile, monday, doneWeek(0), examDays = examSaturday)
        assertTrue(week.none { it.type.hard && it.date == monday.plusDays(5) })
    }

    @Test
    fun `Zieldatum wird geschaetzt und Kraft steigert sich`() {
        val goal = TrainingPlanner.estimatedGoalDate(profile, emptyList(), start)
        assertTrue(goal.isAfter(start.plusWeeks(6)) && goal.isBefore(start.plusWeeks(16)), "10 km in ca. 8–12 Wochen: $goal")
        val base = TrainingPlanner.strengthProgram(0).first()
        val harder = TrainingPlanner.strengthProgram(3).first()
        assertTrue(harder.reps.toInt() > base.reps.toInt())
    }

    @Test
    fun `schlechter Schlaf macht Intervalle zu lockerem Lauf`() {
        val s = TrainingSession(start, SessionType.INTERVALS, 40, null, "Intervalle", "", 1, false)
        val r = TrainingPlanner.reduce(s)
        assertEquals(SessionType.EASY_RUN, r.type)
        assertTrue(r.minutes < 40)
    }
}
