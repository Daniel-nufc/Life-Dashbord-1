package app.lifedashboard.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.lifedashboard.planner.SchoolWeekCalculator
import app.lifedashboard.planner.WeekType
import app.lifedashboard.planner.minuteToTime
import app.lifedashboard.planner.mondayOfWeek
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class AppSettings(
    val appLockEnabled: Boolean,
    val lockTimeoutMinutes: Int,
    val abReferenceMonday: LocalDate,
    val abReferenceType: WeekType,
    val dayStart: LocalTime,
    val dayEnd: LocalTime,
    val schoolCommuteMinutes: Int,
    val userName: String,
    val weekendDayStart: LocalTime,
    val seedVersion: Int,
) {
    fun weekCalculator() = SchoolWeekCalculator(abReferenceMonday, abReferenceType)

    /** Am Wochenende beginnt der Tag später. */
    fun dayStartFor(date: LocalDate): LocalTime =
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) weekendDayStart else dayStart
}

/** Einfache Einstellungen (kein sensibler Inhalt) – gespeichert mit Jetpack DataStore. */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            AppSettings(
                appLockEnabled = p[APP_LOCK] ?: true,
                lockTimeoutMinutes = p[LOCK_TIMEOUT] ?: 1,
                abReferenceMonday = p[AB_REFERENCE]?.let(LocalDate::parse) ?: DEFAULT_AB_REFERENCE,
                abReferenceType = p[AB_TYPE]?.let(WeekType::valueOf) ?: WeekType.A,
                dayStart = minuteToTime(p[DAY_START] ?: 6 * 60),
                dayEnd = minuteToTime(p[DAY_END] ?: 22 * 60),
                schoolCommuteMinutes = p[COMMUTE] ?: 25,
                userName = p[USER_NAME] ?: "Daniel",
                weekendDayStart = minuteToTime(p[WEEKEND_START] ?: (9 * 60 + 30)),
                seedVersion = p[SEED_VERSION] ?: 0,
            )
        }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { it[APP_LOCK] = enabled }
    }

    /** „Diese Woche ist eine A-/B-Woche“ – setzt die Referenz neu. */
    suspend fun setWeekType(date: LocalDate, type: WeekType) {
        dataStore.edit {
            it[AB_REFERENCE] = date.mondayOfWeek().toString()
            it[AB_TYPE] = type.name
        }
    }

    suspend fun setSchoolCommuteMinutes(minutes: Int) {
        dataStore.edit { it[COMMUTE] = minutes.coerceIn(0, 180) }
    }

    suspend fun setUserName(name: String) {
        dataStore.edit { it[USER_NAME] = name.trim().ifBlank { "Daniel" } }
    }

    suspend fun setWeekendDayStart(minuteOfDay: Int) {
        dataStore.edit { it[WEEKEND_START] = minuteOfDay.coerceIn(0, 23 * 60) }
    }

    suspend fun setSeedVersion(version: Int) {
        dataStore.edit { it[SEED_VERSION] = version }
    }

    private companion object {
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val LOCK_TIMEOUT = intPreferencesKey("lock_timeout_minutes")
        val AB_REFERENCE = stringPreferencesKey("ab_reference_monday")
        val AB_TYPE = stringPreferencesKey("ab_reference_type")
        val DAY_START = intPreferencesKey("day_start_minute")
        val DAY_END = intPreferencesKey("day_end_minute")
        val COMMUTE = intPreferencesKey("school_commute_minutes")
        val USER_NAME = stringPreferencesKey("user_name")
        val WEEKEND_START = intPreferencesKey("weekend_day_start_minute")
        val SEED_VERSION = intPreferencesKey("seed_version")

        /** KW 46/2026 (ab Mo 9.11.) ist Woche A. */
        val DEFAULT_AB_REFERENCE: LocalDate = LocalDate.of(2026, 11, 9)
    }
}
