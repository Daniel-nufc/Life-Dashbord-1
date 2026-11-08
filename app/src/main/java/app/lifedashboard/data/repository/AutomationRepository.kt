package app.lifedashboard.data.repository

import app.lifedashboard.data.db.GoalDao
import app.lifedashboard.data.db.HabitLogEntity
import app.lifedashboard.data.db.SportDao
import app.lifedashboard.data.sync.HealthRepository
import app.lifedashboard.data.sync.UsageRepository
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Hakt Gewohnheiten automatisch ab, wo es Daten gibt:
 * - STEPS: ≥ 6.000 Schritte oder ein Training an dem Tag
 * - SLEEP_PHONE: Handy nach 22:30 Uhr nicht mehr benutzt (gilt für den Vorabend, morgens geprüft)
 * - SCREEN_SOCIAL: Social Media & Videos unter 2 Std (für gestern endgültig, heute sobald sicher)
 * Abhaken passiert nur, nie automatisch „ent-haken“ – du behältst die Kontrolle.
 */
@Singleton
class AutomationRepository @Inject constructor(
    private val goals: GoalDao,
    private val sport: SportDao,
    private val health: HealthRepository,
    private val usage: UsageRepository,
) {
    suspend fun autoCheckHabits(today: LocalDate = LocalDate.now()) {
        val habits = goals.observeHabits().first().filter { it.active && it.autoRule != null }
        if (habits.isEmpty()) return
        val yesterday = today.minusDays(1)
        val healthDays = runCatching { health.lastDays(2) }.getOrDefault(emptyList())
        val workouts = sport.observeWorkouts().first()
        for (h in habits) {
            val days: List<LocalDate> = when (h.autoRule) {
                "STEPS" -> listOf(yesterday, today).filter { d ->
                    (healthDays.firstOrNull { it.date == d }?.steps ?: 0) >= 6000 || workouts.any { it.dateEpochDay == d.toEpochDay() }
                }
                "SLEEP_PHONE" -> if (LocalTime.now().hour >= 5 && usage.usedPhoneLate(yesterday) == false) listOf(yesterday) else emptyList()
                "SCREEN_SOCIAL" -> buildList {
                    usage.socialTime(yesterday)?.let { if (it < Duration.ofHours(2)) add(yesterday) }
                    // Heute erst ab 21 Uhr werten (vorher steht der Tag nicht fest)
                    if (LocalTime.now().hour >= 21) usage.socialTime(today)?.let { if (it < Duration.ofHours(2)) add(today) }
                }
                else -> emptyList()
            }
            days.forEach { goals.insertHabitLog(HabitLogEntity(habitId = h.id, dateEpochDay = it.toEpochDay())) }
        }
    }
}
