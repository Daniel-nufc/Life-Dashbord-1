package app.lifedashboard.planner

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressTest {
    private val today = LocalDate.of(2026, 11, 13) // Freitag

    private fun input(
        activities: List<ActivityLog> = emptyList(),
        study: List<StudyLog> = emptyList(),
        tasks: List<LocalDate> = emptyList(),
        habits: List<LocalDate> = emptyList(),
    ) = ProgressInput(today, activities, study, tasks, habits, weeklySportGoalMinutes = 180, weeklyStudyGoalMinutes = 240)

    @Test
    fun `level thresholds grow step by step`() {
        assertEquals(0, Progress.xpToReach(1))
        assertEquals(200, Progress.xpToReach(2))
        assertEquals(500, Progress.xpToReach(3))
        assertEquals(900, Progress.xpToReach(4))
        assertEquals(1, Progress.levelFor(199).level)
        val l = Progress.levelFor(260)
        assertEquals(2, l.level)
        assertEquals(60, l.xpIntoLevel)
        assertEquals(300, l.xpForLevel)
        assertEquals(240, l.xpToNext)
    }

    @Test
    fun `empty data gives level one and no streak`() {
        val s = Progress.compute(input())
        assertEquals(1, s.level.level)
        assertEquals(0, s.streak.current)
        assertEquals(0, s.earnedBadges)
        assertEquals(0, s.ironman.currentIndex)
        assertEquals(0f, s.ironman.fraction)
    }

    @Test
    fun `points come from sport, study, tasks and habits`() {
        val s = Progress.compute(
            input(
                activities = listOf(ActivityLog(today, Discipline.RUN, 30, 5_000)),
                study = listOf(StudyLog(today, 25), StudyLog(today.minusDays(1), 50)),
                tasks = listOf(today),
                habits = listOf(today, today.minusDays(1)),
            ),
        )
        // 60 + (50 + 100) + 15 + 10
        assertEquals(235, s.level.xpTotal)
        assertEquals(60 + 50 + 15 + 5, s.xpToday)
        assertTrue(s.activeToday)
        assertEquals(2, s.streak.current)
        assertEquals(30, s.sportWeek.minutes)
        assertEquals(75, s.studyWeek.minutes)
    }

    @Test
    fun `very long sessions are capped`() {
        val s = Progress.compute(input(activities = listOf(ActivityLog(today, Discipline.BIKE, 600, 150_000))))
        assertEquals(Progress.MAX_MINUTES_COUNTED * Progress.XP_PER_SPORT_MINUTE, s.level.xpTotal)
    }

    @Test
    fun `week ring only counts the current week`() {
        val s = Progress.compute(
            input(
                activities = listOf(
                    ActivityLog(today.minusDays(4), Discipline.OTHER, 90, null), // Montag dieser Woche
                    ActivityLog(today.minusDays(5), Discipline.OTHER, 90, null), // Sonntag davor
                ),
            ),
        )
        assertEquals(90, s.sportWeek.minutes)
        assertEquals(0.5f, s.sportWeek.fraction)
        assertFalse(s.sportWeek.reached)
    }

    @Test
    fun `ironman path moves with the longest single distance`() {
        val half = Progress.ironman(listOf(ActivityLog(today, Discipline.RUN, 34, 5_000)))
        assertEquals(0, half.currentIndex)
        assertEquals(0.5f, half.current!!.fraction)

        val first = Progress.ironman(listOf(ActivityLog(today, Discipline.RUN, 60, 10_000)))
        assertEquals(1, first.currentIndex)
        assertEquals("Sprint-Triathlon", first.current!!.stage.name)
        // Laufen ist für den Sprint schon erfüllt, Schwimmen und Rad fehlen
        assertEquals(1f, first.current!!.run)
        assertEquals(0f, first.current!!.swim)
        assertTrue(first.fraction > 1f / 7)
    }

    @Test
    fun `several short runs do not replace one long run`() {
        val path = Progress.ironman((1..5).map { ActivityLog(today.minusDays(it.toLong()), Discipline.RUN, 20, 3_000) })
        assertEquals(0, path.currentIndex)
        assertEquals(15_000, path.totalRunMeters)
        assertEquals(3_000, path.bestRunMeters)
    }

    @Test
    fun `badges are earned from real entries`() {
        val s = Progress.compute(
            input(
                activities = listOf(
                    ActivityLog(today, Discipline.RUN, 60, 10_000),
                    ActivityLog(today.minusDays(1), Discipline.SWIM, 30, 1_000),
                    ActivityLog(today.minusDays(2), Discipline.BIKE, 60, 20_000),
                ),
                study = listOf(StudyLog(today, 125)),
            ),
        )
        val earned = s.badges.filter { it.earned }.map { it.id }.toSet()
        assertTrue(earned.containsAll(setOf("first_workout", "run_5k", "run_10k", "swim_1k", "tri_week", "focus_day")))
        assertFalse("run_hm" in earned)
        assertEquals(0.5f, s.badges.first { it.id == "bike_40k" }.progress)
    }

    @Test
    fun `nudge saves a streak in the evening`() {
        val s = Progress.compute(
            input(study = listOf(StudyLog(today.minusDays(1), 30), StudyLog(today.minusDays(2), 30), StudyLog(today.minusDays(3), 30))),
        )
        assertFalse(s.activeToday)
        val nudge = assertNotNull(Progress.nudge(s, today))
        assertTrue(nudge.first.contains("3 Tage Serie"))
    }

    @Test
    fun `no nudge when nothing is at stake`() {
        assertNull(Progress.nudge(Progress.compute(input()), LocalDate.of(2026, 11, 10)))
    }

    @Test
    fun `nudge for weekly goal late in the week`() {
        val s = Progress.compute(input(activities = listOf(ActivityLog(today.minusDays(3), Discipline.OTHER, 120, null))))
        val nudge = assertNotNull(Progress.nudge(s, today))
        assertTrue(nudge.first.contains("60 Min"))
    }
}

class StrictCoachTest {
    private val today = LocalDate.of(2026, 11, 13) // Freitag

    private fun input(blocks: List<BlockLog>, study: List<StudyLog> = emptyList(), activities: List<ActivityLog> = emptyList(), strict: Boolean = true, redeemed: Int = 0) =
        ProgressInput(today, activities, study, emptyList(), emptyList(), 180, 240, blocks, strict, redeemed)

    @Test
    fun `missed blocks cost points and build backlog`() {
        val s = Progress.compute(
            input(
                blocks = listOf(BlockLog(today.minusDays(1), 50, BlockOutcome.MISSED), BlockLog(today.minusDays(2), 50, BlockOutcome.DONE)),
                study = listOf(StudyLog(today.minusDays(2), 50)),
            ),
        )
        assertEquals(50, s.backlogMinutes)
        assertEquals(50, s.penaltyXp)
        assertEquals(100 - 50, s.level.xpTotal)
    }

    @Test
    fun `missed day breaks the streak in strict mode`() {
        val blocks = listOf(
            BlockLog(today.minusDays(1), 50, BlockOutcome.MISSED),
            BlockLog(today.minusDays(2), 50, BlockOutcome.DONE),
            BlockLog(today.minusDays(3), 50, BlockOutcome.DONE),
        )
        assertEquals(0, Progress.compute(input(blocks)).streak.current)
        // ohne strengen Modus hilft der Joker
        assertEquals(3, Progress.compute(input(blocks, strict = false)).streak.current)
    }

    @Test
    fun `gaming time is earned and locked by backlog`() {
        val done = (0..3).map { BlockLog(today.minusDays(it.toLong()), 50, BlockOutcome.DONE) }
        val free = Progress.compute(input(done, activities = listOf(ActivityLog(today, Discipline.RUN, 45, 6000)), redeemed = 30))
        // 200 Min Schule → 120, 45 Min Sport → 15 → 135
        assertEquals(135, free.reward.earnedMinutes)
        assertEquals(105, free.reward.availableMinutes)
        assertFalse(free.reward.locked)

        val locked = Progress.compute(input(done + BlockLog(today.minusDays(1), 75, BlockOutcome.MISSED)))
        assertTrue(locked.reward.locked)
        assertEquals(0, locked.reward.availableMinutes)
    }
}
