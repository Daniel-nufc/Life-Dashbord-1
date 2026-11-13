package app.lifedashboard.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import app.lifedashboard.data.db.AppDatabase
import app.lifedashboard.data.db.IcalSourceEntity
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.seed.SeedDataInitializer
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * FamilyWall-Kalender über einen echten (lokalen) HTTP-Server holen und in die echte Datenbank übernehmen.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class FamilyWallSyncTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var ical: IcalSyncRepository
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var seed: SeedDataInitializer

    private lateinit var server: ServerSocket

    @Volatile private var feed = ""

    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))

    private val originalZone = java.util.TimeZone.getDefault()

    @Before
    fun setUp() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin")) // wie auf Daniels Handy
        WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext<Context>())
        hilt.inject()
        // Mini-HTTP-Server: beantwortet jede Anfrage mit dem aktuellen Kalender
        server = ServerSocket(0, 10, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use { s ->
                    val reader = s.getInputStream().bufferedReader()
                    while (reader.readLine()?.isNotEmpty() == true) Unit // Kopfzeilen überspringen
                    val body = feed.toByteArray()
                    val head = "HTTP/1.1 200 OK\r\nContent-Type: text/calendar\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                    s.getOutputStream().apply { write(head.toByteArray()); write(body); flush() }
                }
            }
        }
    }

    @After
    fun tearDown() {
        server.close()
        java.util.TimeZone.setDefault(originalZone)
    }

    private fun calendar(withDentist: Boolean) = buildString {
        append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n")
        append("BEGIN:VEVENT\r\nUID:gitarre\r\nSUMMARY:Gitarre\r\nLOCATION:Musikschule\r\n")
        append("ATTENDEE;CN=Daniel:mailto:d@example.org\r\n")
        append("DTSTART;TZID=Europe/Berlin:${nextMonday.format(fmt)}T160000\r\nDTEND;TZID=Europe/Berlin:${nextMonday.format(fmt)}T170000\r\n")
        append("RRULE:FREQ=WEEKLY;COUNT=3\r\nEND:VEVENT\r\n")
        if (withDentist) {
            val day = nextMonday.plusDays(2).format(fmt)
            append("BEGIN:VEVENT\r\nUID:zahnarzt\r\nSUMMARY:Test beim Zahnarzt\r\n")
            append("ATTENDEE;CN=Daniel:mailto:d@example.org\r\n")
            append("DTSTART;TZID=Europe/Berlin:${day}T090000\r\nDTEND;TZID=Europe/Berlin:${day}T093000\r\nEND:VEVENT\r\n")
        }
        // Termin nur für Mama – darf nicht übernommen werden
        append("BEGIN:VEVENT\r\nUID:mama\r\nSUMMARY:Friseur\r\nATTENDEE;CN=Mama:mailto:m@example.org\r\n")
        append("DTSTART;TZID=Europe/Berlin:${nextMonday.format(fmt)}T100000\r\nEND:VEVENT\r\n")
        append("END:VCALENDAR\r\n")
    }

    private fun url() = "http://127.0.0.1:${server.localPort}/familywall/ical/abc"

    @Test
    fun `FamilyWall-Termine landen privat im Kalender und folgen Aenderungen`() = runBlocking {
        seed.seedIfNeeded()
        feed = calendar(withDentist = true)
        val source = IcalSourceEntity(name = "FamilyWall", url = url(), importAsTasks = false, lastSyncMillis = null, lastError = null, area = LifeArea.PRIVATE, filterName = "Daniel")
        val id = ical.save(source)
        assertTrue(ical.sync(source.copy(id = id)))

        val events = db.eventDao().byExternalPrefix("familywall:")
        val guitar = events.filter { it.title == "Gitarre" }
        assertEquals(listOf(nextMonday, nextMonday.plusWeeks(1), nextMonday.plusWeeks(2)), guitar.map { LocalDate.ofEpochDay(it.dateEpochDay) }.sorted())
        assertTrue(events.all { it.area == LifeArea.PRIVATE })
        assertTrue(events.none { it.title == "Friseur" }) // nur meine Termine
        assertTrue(db.icalSourceDao().all().single().lastNote!!.startsWith("2 von 3 Terminen sind für dich"))
        assertEquals("Musikschule", guitar.first().location)
        assertEquals(16 * 60, guitar.first().startMinute)
        // „Test“ im Familienkalender ist keine Klausur
        assertTrue(db.studyDao().observeExams().first().none { it.title.contains("Zahnarzt") })

        // In FamilyWall gelöscht → verschwindet auch hier
        feed = calendar(withDentist = false)
        assertTrue(ical.sync(db.icalSourceDao().all().single()))
        assertEquals(3, db.eventDao().byExternalPrefix("familywall:").size)
    }

    @Test
    fun `ungueltiger Link meldet einen verstaendlichen Fehler`() = runBlocking {
        feed = "<html>Not found</html>"
        val source = IcalSourceEntity(name = "FamilyWall", url = url(), importAsTasks = false, lastSyncMillis = null, lastError = null, area = LifeArea.PRIVATE)
        val id = ical.save(source)
        assertEquals(false, ical.sync(source.copy(id = id)))
        assertTrue(db.icalSourceDao().all().single().lastError!!.contains("keinen Kalender"))
    }
}
