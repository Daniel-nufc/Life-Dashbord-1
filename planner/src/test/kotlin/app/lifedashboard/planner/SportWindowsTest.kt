package app.lifedashboard.planner

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SportWindowsTest {
    private val monday = LocalDate.of(2026, 11, 9)
    private val profile = SportProfile()

    @Test
    fun `drei Fenster nicht an Floorball-Tagen und nicht am Ruhetag`() {
        repeat(52) { w ->
            val week = SportWindowPlanner.planWeek(profile, monday.plusWeeks(w.toLong()))
            assertEquals(3, week.size, "Woche $w")
            assertTrue(week.none { it.date.dayOfWeek in profile.fixedSportDays })
            assertTrue(week.none { it.date.dayOfWeek == DayOfWeek.SUNDAY })
            assertTrue(week.all { it.minutes == 60 && it.type == SessionType.WINDOW })
            assertEquals(3, week.map { it.date }.toSet().size, "nie zwei Fenster am selben Tag")
        }
    }

    @Test
    fun `Fenster sind verteilt - Samstag zuerst`() {
        val week = SportWindowPlanner.planWeek(profile.copy(windowsPerWeek = 1), monday)
        assertEquals(DayOfWeek.SATURDAY, week.single().date.dayOfWeek)
    }

    @Test
    fun `keine Sportart wird vorgeschrieben`() {
        val w = SportWindowPlanner.planWeek(profile, monday).first()
        assertEquals("Sport-Zeitfenster", w.title)
        assertTrue(w.key.endsWith(":WINDOW"))
    }

    @Test
    fun `Klausurphase - weniger und kuerzer, Klausur-Vortag gemieden`() {
        val exam = monday.plusDays(4) // Freitag
        val week = SportWindowPlanner.planWeek(profile, monday, examDays = setOf(exam), examPhase = true)
        assertEquals(2, week.size)
        assertTrue(week.all { it.minutes == 45 && it.reduced })
        assertFalse(week.any { it.date == exam || it.date == exam.minusDays(1) })
    }

    @Test
    fun `lange Schicht blockiert den Tag`() {
        val sat = monday.plusDays(5)
        val week = SportWindowPlanner.planWeek(profile, monday, blockedDays = setOf(sat))
        assertTrue(week.none { it.date == sat })
        assertEquals(3, week.size)
    }

    @Test
    fun `null Fenster gewuenscht ergibt leere Woche und kuerzen halbiert`() {
        assertTrue(SportWindowPlanner.planWeek(profile.copy(windowsPerWeek = 0), monday).isEmpty())
        val w = SportWindow(monday, 60)
        assertEquals(30, SportWindowPlanner.shorten(w).minutes)
        assertEquals(20, SportWindowPlanner.shorten(SportWindow(monday, 30)).minutes)
    }

    @Test
    fun `ohne feste Trainings und ohne Ruhetag auch 6 Fenster moeglich`() {
        val week = SportWindowPlanner.planWeek(SportProfile(windowsPerWeek = 6, fixedSportDays = emptySet(), restDay = null), monday)
        assertEquals(6, week.size)
    }
}
