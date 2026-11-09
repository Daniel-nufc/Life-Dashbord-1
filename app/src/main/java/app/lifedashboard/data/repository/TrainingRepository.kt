package app.lifedashboard.data.repository

import app.lifedashboard.data.db.GoalDao
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.SportDao
import app.lifedashboard.data.db.StudyDao
import app.lifedashboard.data.db.TrainingResultEntity
import app.lifedashboard.data.db.WorkoutEntity
import app.lifedashboard.data.db.WorkoutType
import app.lifedashboard.data.settings.SettingsRepository
import app.lifedashboard.data.sync.HealthRepository
import app.lifedashboard.data.sync.HealthWorkout
import app.lifedashboard.planner.SessionType
import app.lifedashboard.planner.SportProfile
import app.lifedashboard.planner.SportWindow
import app.lifedashboard.planner.SportWindowPlanner
import app.lifedashboard.planner.mondayOfWeek
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Ein geplantes Sport-Zeitfenster mit seinem Stand. */
data class PlannedTraining(val session: SportWindow, val result: TrainingResultEntity?) {
    val done get() = result?.done == true
    val skipped get() = result?.skipped == true
}

data class TrainingOverview(
    val profile: SportProfile,
    val week: List<PlannedTraining>,
    /** Sport-Minuten diese Woche (Log inkl. Floorball & Health Connect) */
    val minutesThisWeek: Int,
    val longestRunKm: Double,
    /** Nächster offener Schritt des Ziels „Ironman“ (falls vorhanden) */
    val nextGoalStep: String?,
    val sleepWarning: Boolean,
)

/**
 * Plant freie Sport-Zeitfenster – WAS du darin machst, entscheidest du.
 * Rücksicht auf Floorball (feste Trainings), Klausuren und lange Job-Schichten.
 * Der Plan wird für jede Woche neu berechnet (Regel), gespeichert werden nur Ergebnisse.
 */
@Singleton
class TrainingRepository @Inject constructor(
    private val sportDao: SportDao,
    private val goalDao: GoalDao,
    private val studyDao: StudyDao,
    private val settings: SettingsRepository,
    private val routines: RoutineRepository,
    private val jobs: JobRepository,
    private val health: HealthRepository,
) {
    suspend fun profile(): SportProfile {
        val t = settings.settings.first().training
        val fixed = routines.routines.first().filter { it.active && it.area == LifeArea.SPORT }.flatMap { it.days() }.toSet()
        return SportProfile(
            windowsPerWeek = t.windowsPerWeek,
            minutes = t.windowMinutes,
            fixedSportDays = fixed,
            restDay = if (t.sundayRest) DayOfWeek.SUNDAY else null,
        )
    }

    /** Zeitfenster einer Woche inkl. Klausurphase, Klausur-Vortag, langer Job-Schichten. */
    suspend fun week(monday: LocalDate): List<PlannedTraining> {
        val profile = profile()
        val sunday = monday.plusDays(6)
        val exams = studyDao.observeExams().first().map { LocalDate.ofEpochDay(it.dateEpochDay) }
        val examDays = exams.filter { !it.isBefore(monday) && !it.isAfter(sunday.plusDays(1)) }.toSet()
        val examPhase = exams.any { !it.isBefore(monday) && !it.isAfter(sunday.plusDays(7)) }
        val blocked = jobs.between(monday, sunday).first().filter { it.endMinute - it.startMinute >= 240 }
            .map { LocalDate.ofEpochDay(it.dateEpochDay) }.toSet()
        val res = sportDao.trainingResults().filter { it.dateEpochDay in monday.toEpochDay()..sunday.toEpochDay() && it.type == SessionType.WINDOW }
        return SportWindowPlanner.planWeek(profile, monday, examDays, examPhase, blocked).map { w ->
            val r = res.firstOrNull { it.dateEpochDay == w.date.toEpochDay() }
            PlannedTraining(if (r?.reduced == true) SportWindowPlanner.shorten(w) else w, r)
        }
    }

    suspend fun sessionsFor(date: LocalDate): List<PlannedTraining> = week(date.mondayOfWeek()).filter { it.session.date == date }

    suspend fun overview(today: LocalDate = LocalDate.now()): TrainingOverview {
        val monday = today.mondayOfWeek()
        val workouts = sportDao.observeWorkouts().first()
        val minutes = workouts.filter { it.dateEpochDay in monday.toEpochDay()..monday.plusDays(6).toEpochDay() }.sumOf { it.durationMinutes }
        val longest = workouts.filter { it.type == WorkoutType.RUN }.maxOfOrNull { (it.distanceMeters ?: 0) / 1000.0 } ?: 0.0
        val week = week(monday)
        val sleep = runCatching { health.lastNightSleep() }.getOrNull()
        val windowToday = week.any { it.session.date == today && !it.done && !it.skipped && it.result?.reduced != true }
        return TrainingOverview(
            profile = profile(),
            week = week,
            minutesThisWeek = minutes,
            longestRunKm = longest,
            nextGoalStep = nextIronmanStep(),
            sleepWarning = windowToday && sleep != null && sleep < Duration.ofMinutes(390),
        )
    }

    private suspend fun nextIronmanStep(): String? {
        val ironman = goalDao.observeGoals().first().firstOrNull { it.title.contains("Ironman", true) } ?: return null
        return goalDao.observeMilestones().first().filter { it.goalId == ironman.id }.sortedBy { it.sortOrder }.firstOrNull { !it.done }?.title
    }

    private suspend fun upsert(date: LocalDate, change: (TrainingResultEntity) -> TrainingResultEntity) {
        val old = sportDao.trainingResult(date.toEpochDay(), SessionType.WINDOW)
            ?: TrainingResultEntity(dateEpochDay = date.toEpochDay(), type = SessionType.WINDOW, done = false, skipped = false, effort = null, distanceMeters = null, reduced = false, workoutId = null)
        sportDao.putTrainingResult(change(old))
    }

    /**
     * Zeitfenster genutzt. Mit [what] wird zusätzlich ein Eintrag im Trainings-Log angelegt
     * (z. B. „Laufen 35 Min“). Ohne – etwa beim ✓ im Tagesplan – nur abgehakt; ein Training aus
     * Health Connect wird später automatisch zugeordnet.
     */
    suspend fun markDone(window: SportWindow, what: WorkoutEntity? = null) {
        val workoutId = what?.let { sportDao.upsertWorkout(it.copy(dateEpochDay = window.date.toEpochDay())) }
        upsert(window.date) { it.copy(done = true, skipped = false, workoutId = workoutId ?: it.workoutId, distanceMeters = what?.distanceMeters ?: it.distanceMeters) }
        if (what != null) checkGoalMilestone()
    }

    suspend fun skip(window: SportWindow) = upsert(window.date) { it.copy(skipped = true, done = false) }
    suspend fun undo(window: SportWindow) = upsert(window.date) { it.copy(skipped = false, done = false, reduced = false) }
    suspend fun shorten(window: SportWindow) = upsert(window.date) { it.copy(reduced = true) }

    /**
     * Holt Trainings aus Health Connect (Samsung Health, Strava, Garmin …) ins Log und hakt
     * das Zeitfenster desselben Tages automatisch ab.
     * @return Anzahl neu übernommener Trainings
     */
    suspend fun syncFromHealth(): Int {
        val list = runCatching { health.recentWorkouts(14) }.getOrDefault(emptyList())
        var added = 0
        for (hw in list) {
            if (sportDao.countExternal(hw.id) > 0) continue
            if (hw.kind == HealthWorkout.Kind.WALK && (hw.distanceMeters ?: 0) < 3000) continue
            val id = sportDao.upsertWorkout(
                WorkoutEntity(
                    dateEpochDay = hw.date.toEpochDay(),
                    type = when (hw.kind) {
                        HealthWorkout.Kind.RUN -> WorkoutType.RUN
                        HealthWorkout.Kind.BIKE -> WorkoutType.BIKE
                        HealthWorkout.Kind.SWIM -> WorkoutType.SWIM
                        HealthWorkout.Kind.STRENGTH -> WorkoutType.STRENGTH
                        else -> WorkoutType.OTHER
                    },
                    durationMinutes = hw.minutes,
                    distanceMeters = hw.distanceMeters,
                    note = "Automatisch aus Health Connect",
                    externalId = hw.id,
                    avgHeartRate = hw.avgHeartRate,
                ),
            )
            added++
            sessionsFor(hw.date).firstOrNull { !it.done }?.let { planned ->
                upsert(planned.session.date) { it.copy(done = true, skipped = false, workoutId = id, distanceMeters = hw.distanceMeters) }
            }
        }
        if (added > 0) checkGoalMilestone()
        return added
    }

    /** 10 km gelaufen → Schritt „10 km“ beim Ziel Ironman automatisch abhaken. */
    private suspend fun checkGoalMilestone() {
        val longest = sportDao.observeWorkouts().first().filter { it.type == WorkoutType.RUN }.maxOfOrNull { it.distanceMeters ?: 0 } ?: 0
        if (longest < 10_000) return
        val ironman = goalDao.observeGoals().first().firstOrNull { it.title.contains("Ironman", true) } ?: return
        goalDao.observeMilestones().first().filter { it.goalId == ironman.id }.sortedBy { it.sortOrder }
            .firstOrNull { !it.done && it.title.contains("10 km") }?.let { goalDao.upsertMilestone(it.copy(done = true)) }
    }
}
