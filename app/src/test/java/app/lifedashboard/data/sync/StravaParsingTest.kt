package app.lifedashboard.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.lifedashboard.data.db.WorkoutEntity
import app.lifedashboard.data.db.WorkoutType
import java.time.LocalDate
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class StravaParsingTest {
    private val sample = """
        [
          {"id": 111, "name": "Winterberg Hügel", "sport_type": "Run", "start_date_local": "2026-08-03T09:12:00Z",
           "distance": 12034.5, "moving_time": 4380, "average_heartrate": 158.4},
          {"id": 112, "name": "Feierabendrunde", "sport_type": "Ride", "start_date_local": "2026-08-05T18:00:00Z",
           "distance": 25500.0, "moving_time": 3600},
          {"id": 113, "name": "Kurz Brötchen holen", "sport_type": "Walk", "start_date_local": "2026-08-06T08:00:00Z",
           "distance": 800.0, "moving_time": 600}
        ]
    """.trimIndent()

    @Test
    fun `activities are mapped to workouts, short walks are skipped`() {
        val list = StravaRepository.parseActivities(JSONArray(sample))
        assertEquals(2, list.size)
        val run = list[0]
        assertEquals(WorkoutType.RUN, run.type)
        assertEquals(LocalDate.of(2026, 8, 3), run.date)
        assertEquals(12034, run.meters)
        assertEquals(73, run.minutes)
        assertEquals(158, run.avgHeartRate)
        assertEquals(WorkoutType.BIKE, list[1].type)
        assertNull(list[1].avgHeartRate)
    }

    @Test
    fun `code is read from the redirect link or pasted alone`() {
        assertEquals("abc123", StravaRepository.extractCode("lifedashboard://localhost/strava?state=&code=abc123&scope=read,activity:read_all"))
        assertEquals("abc123", StravaRepository.extractCode("http://localhost/strava?code=abc123"))
        assertEquals("a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0", StravaRepository.extractCode(" a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0 "))
        assertNull(StravaRepository.extractCode("lifedashboard://localhost/strava?error=access_denied"))
        assertNull(StravaRepository.extractCode(""))
    }

    @Test
    fun `same workout from health connect is recognised`() {
        val a = StravaRepository.parseActivities(JSONArray(sample)).first()
        val fromHealth = WorkoutEntity(dateEpochDay = a.date.toEpochDay(), type = WorkoutType.RUN, durationMinutes = 75, distanceMeters = 12000, note = null, externalId = "hc:1")
        assertTrue(StravaRepository.isSameWorkout(fromHealth, a))
        assertFalse(StravaRepository.isSameWorkout(fromHealth.copy(dateEpochDay = fromHealth.dateEpochDay + 1), a))
    }
}
