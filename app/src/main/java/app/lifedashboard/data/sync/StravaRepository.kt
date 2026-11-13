package app.lifedashboard.data.sync

import android.net.Uri
import app.lifedashboard.core.security.SecretStore
import app.lifedashboard.data.db.SportDao
import app.lifedashboard.data.db.WorkoutEntity
import app.lifedashboard.data.db.WorkoutType
import app.lifedashboard.data.settings.SettingsRepository
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Eine Aktivität aus Strava, schon in unser Format übersetzt. */
data class StravaActivity(
    val id: Long,
    val date: LocalDate,
    val type: WorkoutType,
    val minutes: Int,
    val meters: Int?,
    val avgHeartRate: Int?,
    val name: String,
)

/**
 * Strava-Anbindung ohne eigenen Server: Du legst unter strava.com/settings/api einen kostenlosen
 * API-Zugang an (Callback-Domain „localhost“) und trägst Client-ID und Client-Secret in der App ein.
 * Alle Schlüssel liegen verschlüsselt im Android Keystore, die Läufe landen im Trainings-Log.
 */
@Singleton
class StravaRepository @Inject constructor(
    private val secrets: SecretStore,
    private val sportDao: SportDao,
    private val settings: SettingsRepository,
) {
    private val mutex = Mutex()

    val hasClient: Boolean get() = !secrets.get(CLIENT_ID).isNullOrBlank() && !secrets.get(CLIENT_SECRET).isNullOrBlank()
    val lastSyncMillis: Long? get() = secrets.get(LAST_SYNC)?.toLongOrNull()

    fun saveClient(clientId: String, clientSecret: String) {
        secrets.put(CLIENT_ID, clientId.trim())
        secrets.put(CLIENT_SECRET, clientSecret.trim())
    }

    /** Link, der Strava (App oder Browser) zum Erlauben öffnet */
    fun authorizeUri(): Uri = Uri.parse("https://www.strava.com/oauth/mobile/authorize").buildUpon()
        .appendQueryParameter("client_id", secrets.get(CLIENT_ID).orEmpty())
        .appendQueryParameter("redirect_uri", REDIRECT)
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("approval_prompt", "auto")
        .appendQueryParameter("scope", "activity:read_all")
        .build()

    /** Rückkehr von Strava (oder eingefügter Link/Code): Code gegen Schlüssel tauschen, dann alle Läufe holen. */
    suspend fun handleRedirect(input: String): Result<Int> = runCatching {
        val code = extractCode(input) ?: error("Kein Code gefunden – bitte den ganzen Link einfügen.")
        val json = post("https://www.strava.com/oauth/token", mapOf(
            "client_id" to secrets.get(CLIENT_ID).orEmpty(),
            "client_secret" to secrets.get(CLIENT_SECRET).orEmpty(),
            "code" to code,
            "grant_type" to "authorization_code",
        ))
        storeTokens(json)
        settings.setStravaConnected(true)
        sync(full = true)
    }

    suspend fun disconnect() {
        listOf(ACCESS, REFRESH, EXPIRES, LAST_SYNC).forEach { secrets.put(it, null) }
        settings.setStravaConnected(false)
    }

    /** Alle 3 Stunden im Hintergrund – nur wenn verbunden. */
    suspend fun syncIfDue(): Int {
        if (secrets.get(REFRESH) == null) return 0
        val last = lastSyncMillis ?: 0
        if (System.currentTimeMillis() - last < 3 * 3600_000L) return 0
        return sync(full = false)
    }

    /** Holt neue Aktivitäten (beim ersten Mal die ganze Geschichte) und trägt sie ins Trainings-Log ein. */
    suspend fun sync(full: Boolean): Int = mutex.withLock {
        val token = accessToken()
        val after = if (full) 0L else ((lastSyncMillis ?: 0L) / 1000 - 3 * 24 * 3600).coerceAtLeast(0)
        val all = mutableListOf<StravaActivity>()
        for (page in 1..30) {
            val arr = get("https://www.strava.com/api/v3/athlete/activities?per_page=100&page=$page&after=$after", token)
            val parsed = parseActivities(arr)
            all += parsed
            if (arr.length() < 100) break
        }
        val added = importAll(all)
        secrets.put(LAST_SYNC, System.currentTimeMillis().toString())
        added
    }

    private suspend fun importAll(list: List<StravaActivity>): Int {
        var added = 0
        val existing = sportDao.observeWorkouts().first()
        for (a in list) {
            val ext = "strava:${a.id}"
            if (sportDao.countExternal(ext) > 0) continue
            // dasselbe Training kam schon über Health Connect oder wurde von Hand eingetragen
            if (existing.any { isSameWorkout(it, a) }) continue
            sportDao.upsertWorkout(
                WorkoutEntity(
                    dateEpochDay = a.date.toEpochDay(),
                    type = a.type,
                    durationMinutes = a.minutes,
                    distanceMeters = a.meters,
                    note = "Strava: ${a.name}",
                    externalId = ext,
                    avgHeartRate = a.avgHeartRate,
                ),
            )
            added++
        }
        return added
    }

    private suspend fun accessToken(): String {
        val expires = secrets.get(EXPIRES)?.toLongOrNull() ?: 0
        val access = secrets.get(ACCESS)
        if (access != null && System.currentTimeMillis() / 1000 < expires - 300) return access
        val refresh = secrets.get(REFRESH) ?: error("Strava ist nicht verbunden.")
        val json = post("https://www.strava.com/oauth/token", mapOf(
            "client_id" to secrets.get(CLIENT_ID).orEmpty(),
            "client_secret" to secrets.get(CLIENT_SECRET).orEmpty(),
            "grant_type" to "refresh_token",
            "refresh_token" to refresh,
        ))
        storeTokens(json)
        return json.getString("access_token")
    }

    private fun storeTokens(json: JSONObject) {
        secrets.put(ACCESS, json.getString("access_token"))
        // Strava macht den alten Refresh-Token sofort ungültig – immer den neuesten speichern
        secrets.put(REFRESH, json.getString("refresh_token"))
        secrets.put(EXPIRES, json.getLong("expires_at").toString())
    }

    private suspend fun post(url: String, form: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val body = form.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        c.outputStream.use { it.write(body.toByteArray()) }
        JSONObject(read(c))
    }

    private suspend fun get(url: String, token: String): JSONArray = withContext(Dispatchers.IO) {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
        }
        JSONArray(read(c))
    }

    private fun read(c: HttpURLConnection): String {
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code == 401) error("Strava hat den Zugriff abgelehnt (401). Client-ID/Secret prüfen und neu verbinden.")
        if (code == 429) error("Strava: zu viele Anfragen – in 15 Minuten nochmal.")
        if (code !in 200..299) error("Strava antwortet mit Fehler $code")
        return text
    }

    companion object {
        /** Muss zur „Authorization Callback Domain“ localhost passen */
        const val REDIRECT = "lifedashboard://localhost/strava"
        private const val CLIENT_ID = "strava_client_id"
        private const val CLIENT_SECRET = "strava_client_secret"
        private const val ACCESS = "strava_access"
        private const val REFRESH = "strava_refresh"
        private const val EXPIRES = "strava_expires"
        private const val LAST_SYNC = "strava_last_sync"

        fun extractCode(input: String): String? {
            val t = input.trim()
            if (t.isEmpty()) return null
            Regex("[?&]code=([^&#\\s]+)").find(t)?.let { return it.groupValues[1] }
            if (Regex("[?&]error=").containsMatchIn(t)) return null
            // nur der Code selbst eingefügt
            return t.takeIf { Regex("^[A-Za-z0-9]{20,}$").matches(it) }
        }

        fun typeOf(sportType: String): WorkoutType = when (sportType) {
            "Run", "TrailRun", "VirtualRun" -> WorkoutType.RUN
            "Ride", "VirtualRide", "GravelRide", "MountainBikeRide", "EBikeRide", "EMountainBikeRide", "Velomobile" -> WorkoutType.BIKE
            "Swim" -> WorkoutType.SWIM
            "WeightTraining", "Crossfit", "Workout", "HighIntensityIntervalTraining" -> WorkoutType.STRENGTH
            else -> WorkoutType.OTHER
        }

        fun parseActivities(arr: JSONArray): List<StravaActivity> = (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val sport = o.optString("sport_type").ifBlank { o.optString("type") }
            val meters = o.optDouble("distance", 0.0).toInt()
            val type = typeOf(sport)
            // kurze Spaziergänge nicht als Training zählen
            if (type == WorkoutType.OTHER && (sport == "Walk" || sport == "Hike") && meters < 3000) return@mapNotNull null
            StravaActivity(
                id = o.getLong("id"),
                date = OffsetDateTime.parse(o.getString("start_date_local").replace("Z", "+00:00")).toLocalDate(),
                type = type,
                minutes = (o.optInt("moving_time", 0) / 60).coerceAtLeast(1),
                meters = meters.takeIf { it > 0 },
                avgHeartRate = if (o.has("average_heartrate")) o.optDouble("average_heartrate").toInt() else null,
                name = o.optString("name"),
            )
        }

        /** Gleiches Training aus einer anderen Quelle? (gleicher Tag, gleiche Art, Dauer ±5 Min) */
        fun isSameWorkout(w: WorkoutEntity, a: StravaActivity) =
            w.dateEpochDay == a.date.toEpochDay() && w.type == a.type && kotlin.math.abs(w.durationMinutes - a.minutes) <= 5
    }
}
