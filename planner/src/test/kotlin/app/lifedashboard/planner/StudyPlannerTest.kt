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
fun `Lernzeit - locker vorlernen, dann gleichmaessige Lernwoche`() {
        val examDay = today.plusDays(10)
        val plan = StudyPlanner.plan(listOf(exam(1, examDay, 3, 600)), today).filter { !it.isReview }
        val perDay = plan.groupBy { it.date }.mapValues { (_, l) -> l.sumOf { it.minutes } }
        assertEquals(600, perDay.values.sum())
        val week = perDay.filterKeys { !it.isBefore(examDay.minusDays(7)) }
        assertTrue(week.values.sum() >= 400, "Hauptteil in der Lernwoche: $perDay")
        assertTrue(week.size >= 5, "Lernwoche auf fast alle Tage verteilt: $perDay")
        assertTrue(week.values.max() - week.values.min() <= 50, "kein Tag viel voller als ein anderer: $perDay")
    }

    @Test
    fun `mehrere Klausuren - kein Tag mit sehr wenig neben einem vollen`() {
        val exams = listOf(exam(1, today.plusDays(8), 3, 240), exam(2, today.plusDays(9), 3, 240), exam(3, today.plusDays(10), 3, 240))
        val perDay = StudyPlanner.plan(exams, today, dailyCapMinutes = 150).filter { !it.isReview }
            .groupBy { it.date }.mapValues { (_, l) -> l.sumOf { it.minutes } }
        // 720 Min auf 7–9 Tage: überall rund 1,5 Std statt 2,5 Std am Anfang und nichts am Ende
        assertTrue(perDay.values.max() <= 125, "zu voller Tag: $perDay")
        assertTrue(perDay.values.max() - perDay.values.min() <= 75, "ungleich verteilt: $perDay")
    }

    @Test
    fun `Tage mit wenig freier Zeit bekommen weniger`() {
        val busy = today.plusDays(2)
        val plan = StudyPlanner.plan(listOf(exam(1, today.plusDays(10), 3, 600)), today, capacity = { if (it == busy) 25 else 120 })
        assertTrue(plan.filter { it.date == busy }.sumOf { it.minutes } <= 25)
        assertEquals(600, plan.filter { !it.isReview }.sumOf { it.minutes })
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

    @Test
    fun `Klausur ohne Themen bekommt trotzdem Lernzeit`() {
        val e = StudyExam(1, "Bio", today.plusDays(10), 3, emptyList())
        assertEquals(240, StudyPlanner.remainingMinutes(e))
        assertTrue(StudyPlanner.plan(listOf(e), today).any { !it.isReview })
    }

    @Test
    fun `Klausur morgen - heute wird richtig gelernt, nicht nur wiederholt`() {
        val e = StudyExam(1, "Mathe", today.plusDays(1), 3, listOf(StudyTopic(1, "Integrale", 90, 0)))
        val plan = StudyPlanner.plan(listOf(e), today)
        assertTrue(plan.all { it.date == today })
        assertTrue(plan.any { !it.isReview && it.minutes >= 50 }, "Lernzeit erwartet: $plan")
        assertTrue(plan.sumOf { it.minutes } <= 120, "Tageslimit")
    }

    @Test
    fun `Klausur in 4 Wochen - alle 3 Tage locker, in der letzten Woche richtig`() {
        val examDay = today.plusDays(28)
        val topics = (1..4).map { StudyTopic(it.toLong(), "Thema $it", 75, 0) }
        val e = StudyExam(1, "Englisch", examDay, 3, topics, minimumMinutes = 480)
        assertEquals(480, StudyPlanner.remainingMinutes(e)) // nicht nur 4 × 75 Min = 5 Std
        val learn = StudyPlanner.plan(listOf(e), today).filter { !it.isReview }
        assertTrue(learn.sumOf { it.minutes } in 480..500, "Blöcke à 25 Min: ${learn.sumOf { it.minutes }}")
        val early = learn.filter { it.date.isBefore(examDay.minusDays(7)) }.map { it.date }.sorted()
        assertTrue(early.size >= 3, "vorher schon locker anfangen: $early")
        early.zipWithNext().forEach { (a, b) -> assertTrue(b.toEpochDay() - a.toEpochDay() >= 2, "nur alle paar Tage: $early") }
        assertTrue(learn.filter { !it.date.isBefore(examDay.minusDays(7)) }.sumOf { it.minutes } >= 300)
    }

    @Test
    fun `Fortschritt ist Verstaendnis - weniger Restzeit`() {
        val half = StudyExam(1, "Englisch", today.plusDays(20), 3, (1..4).map { StudyTopic(it.toLong(), "T$it", 75, 50) }, minimumMinutes = 480)
        assertEquals(240, StudyPlanner.remainingMinutes(half))
        val done = half.copy(topics = half.topics.map { it.copy(progress = 100) })
        assertEquals(0, StudyPlanner.remainingMinutes(done))
    }

    @Test
    fun `was brauche ich in der naechsten Klausur`() {
        // bisher 9 und 11 Punkte (gleich gewichtet), Ziel 12 → nächste Note 16 nötig → allein nicht zu schaffen
        assertEquals(16, Grades.neededNext(listOf(9 to 1.0, 11 to 1.0), 12.0))
        // Klausur zählt doppelt: (9 + 11 + 2x) / 4 ≥ 12 → x ≥ 14
        assertEquals(14, Grades.neededNext(listOf(9 to 1.0, 11 to 1.0), 12.0, nextWeight = 2.0))
        assertEquals(0, Grades.neededNext(listOf(15 to 1.0, 15 to 1.0), 5.0))
        assertEquals(12, Grades.neededNext(emptyList(), 11.9))
    }

    @Test
    fun `groesster Hebel ist das schwaechste Fach unter dem Ziel`() {
        val lever = Grades.biggestLever(mapOf("Mathe" to 7.0, "Deutsch" to 11.0, "Bio" to 13.0, "Englisch" to 9.0), 12.0)!!
        assertEquals("Mathe", lever.subject)
        assertEquals(1.25, lever.gainOverall, 0.001)
        assertEquals(null, Grades.biggestLever(mapOf("Bio" to 13.0), 12.0))
    }
}
