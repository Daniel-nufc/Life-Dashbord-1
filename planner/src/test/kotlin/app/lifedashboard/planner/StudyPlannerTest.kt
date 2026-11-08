package app.lifedashboard.planner

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudyPlannerTest {
    private val today = LocalDate.of(2026, 11, 9)

    private fun exam(id: Long, date: LocalDate, difficulty: Int, vararg efforts: Int) = StudyExam(
        id = id,
        subjectName = "Fach$id",
        date = date,
        difficulty = difficulty,
        topics = efforts.mapIndexed { i, e -> StudyTopic(id * 100 + i, "Thema ${i + 1}", e, 0) },
    )

    @Test
    fun `Restaufwand beruecksichtigt Fortschritt und Schwierigkeit`() {
        val e = StudyExam(1, "Mathe", today.plusDays(10), 5, listOf(StudyTopic(1, "A", 100, 50), StudyTopic(2, "B", 100, 100)))
        assertEquals(70, StudyPlanner.remainingMinutes(e)) // 50 × 1,4
    }

    @Test
    fun `gesamter Aufwand wird vor der Klausur verteilt und Vortag ist Wiederholung`() {
        val mathe = exam(1, today.plusDays(10), 3, 120, 120, 60) // 300 min
        val plan = StudyPlanner.plan(listOf(mathe), today)
        val learning = plan.filter { !it.isReview }
        assertTrue(learning.sumOf { it.minutes } >= 300, "alles verteilt: ${learning.sumOf { it.minutes }}")
        assertTrue(learning.all { it.date.isBefore(mathe.date.minusDays(1)) })
        val review = plan.single { it.isReview }
        assertEquals(mathe.date.minusDays(1), review.date)
        assertTrue(plan.all { it.minutes % 25 == 0 })
    }

    @Test
    fun `mehr Zeit kurz vor der Klausur als am Anfang`() {
        val plan = StudyPlanner.plan(listOf(exam(1, today.plusDays(10), 3, 600)), today).filter { !it.isReview }
        assertTrue(plan.first().minutes <= plan.last().minutes)
    }

    @Test
    fun `Tageslimit wird ueber mehrere Klausuren eingehalten`() {
        val plan = StudyPlanner.plan(
            listOf(exam(1, today.plusDays(6), 5, 600), exam(2, today.plusDays(7), 4, 600)),
            today,
            dailyCapMinutes = 100,
        )
        plan.groupBy { it.date }.forEach { (date, list) ->
            assertTrue(list.sumOf { it.minutes } <= 100, "Limit überschritten am $date")
        }
    }

    @Test
    fun `vergangene Klausuren werden ignoriert`() {
        assertEquals(0, StudyPlanner.plan(listOf(exam(1, today.minusDays(1), 3, 100)), today).size)
    }

    @Test
    fun `Spaced Repetition Intervalle`() {
        assertEquals(1, SpacedRepetition.nextInterval(0, ReviewRating.OK))
        assertEquals(3, SpacedRepetition.nextInterval(1, ReviewRating.OK))
        assertEquals(7, SpacedRepetition.nextInterval(1, ReviewRating.EASY))
        assertEquals(1, SpacedRepetition.nextInterval(14, ReviewRating.HARD))
        assertEquals(30, SpacedRepetition.nextInterval(30, ReviewRating.EASY))
    }

    @Test
    fun `Notenpunkte Durchschnitt und Umrechnung`() {
        assertEquals(11.0, Grades.average(listOf(12 to 2.0, 9 to 1.0))!!, 0.001)
        assertEquals(1.7, Grades.pointsToGrade(11.9), 0.001)
        assertEquals(11.9, Grades.pointsForGrade(1.7), 0.001)
        assertEquals(1.0, Grades.pointsToGrade(15.0), 0.001)
    }
}
