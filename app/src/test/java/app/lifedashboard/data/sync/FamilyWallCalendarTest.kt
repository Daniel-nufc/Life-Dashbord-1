package app.lifedashboard.data.sync

import android.content.Context
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import app.lifedashboard.data.repository.AgendaRepository
import app.lifedashboard.data.repository.TitleChoiceRepository
import app.lifedashboard.data.settings.SettingsRepository
import app.lifedashboard.testing.FakeCalendarProvider
import app.lifedashboard.testing.FakeCalendarProvider.Cal
import app.lifedashboard.testing.FakeCalendarProvider.Ev
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Daniels Lage nachgestellt: FamilyWall überträgt den Familienkalender ins Handy (ohne Teilnehmer, ohne Farben).
 * Was er als „meins“ markiert, muss im Kalender erscheinen; Termine anderer nicht.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class FamilyWallCalendarTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var agenda: AgendaRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var choices: TitleChoiceRepository
    @Inject lateinit var calendar: SystemCalendarRepository

    private val today = LocalDate.now()
    private val tomorrow = today.plusDays(1)

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        shadowOf(app as android.app.Application).grantPermissions(android.Manifest.permission.READ_CALENDAR)
        Robolectric.setupContentProvider(FakeCalendarProvider::class.java, CalendarContract.AUTHORITY)
        FakeCalendarProvider.reset()
        FakeCalendarProvider.calendars += Cal(5, "FamilyWall Familie Friedrich", "Daniel_Sohn")
        FakeCalendarProvider.events += listOf(
            Ev(1, 5, "Floorball Mixed Turnier Falkensee", tomorrow),
            Ev(2, 5, "Augentropfen Papa", tomorrow, LocalTime.of(20, 0), LocalTime.of(20, 15)),
            Ev(3, 5, "Spieltag", today.plusDays(3)),
            Ev(4, 5, "Spieltag Marie W", today.plusDays(3)),
            Ev(5, 5, "Zahnarzt", tomorrow, LocalTime.of(16, 0), LocalTime.of(17, 0)),
        )
        hilt.inject()
    }

    private fun day(date: LocalDate) = runBlocking { agenda.observe(date, 1).first().days.single() }

    @Test
    fun `nur meine FamilyWall-Termine erscheinen - auch ganztaegige und selbst zugeordnete`() = runBlocking {
        val fw = calendar.calendars().single { it.isFamilyWall }
        settings.setSystemCalendarIds(setOf(fw.id))
        settings.setOnlyMineCalendarIds(setOf(fw.id))
        choices.ensureSeeded()
        // Daniel tippt im Dialog „Meins“ beim Zahnarzt
        choices.set("Zahnarzt", true)

        val t = day(tomorrow).externalEvents.map { it.title }.toSet()
        assertTrue("Turnier fehlt: $t", "Floorball Mixed Turnier Falkensee" in t) // erkannt an „Turnier“
        assertTrue("Zahnarzt fehlt: $t", "Zahnarzt" in t)
        assertTrue("Papas Termin darf nicht rein: $t", "Augentropfen Papa" !in t)
        assertTrue(day(tomorrow).reservedDay) // eigenes Turnier → Tag reserviert

        val match = day(today.plusDays(3)).externalEvents.map { it.title }
        assertEquals(listOf("Spieltag"), match)
    }
}
