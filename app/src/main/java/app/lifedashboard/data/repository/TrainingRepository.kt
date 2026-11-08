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
import app.lifedashboard.planner.Effort
import app.lifedashboard.planner.SessionResult
import app.lifedashboard.planner.SessionType
import app.lifedashboard.planner.StrengthExercise
import app.lifedashboard.planner.TrainingGoal
import app.lifedashboard.planner.TrainingPlanner
import app.lifedashboard.planner.TrainingProfile
import app.lifedashboard.planner.TrainingSession
import app.lifedashboard.planner.mondayOfWeek
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Eine geplante Einheit mit ihrem Stand. */
data class PlannedTraining(val session: TrainingSession, val result: TrainingResultEntity?) {
    val done get() = result?.done == true
    val skipped get() = result?.skipped == true
    val needsRating get() = done && result?.effort == null
}

data class TrainingOverview(
    val profile: TrainingProfile,
    val goal: TrainingGoal,
    val week: List<PlannedTraining>,
    val currentLongestKm: Double,
    val nextLongRunKm: Double,
    val estimatedGoalDate: LocalDate,
    val strength: List<StrengthExercise>,
    val sleepWarning: Boolean,
)

/**
 * Verbindet den Trainingsplan-Generator mit der App: Profil aus Einstellungen/Zielen, Ergebnisse aus
 * Bewertungen und Health Connect, Rücksicht auf Floorball, Klausuren und Job.
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
    /** Aktuelles Zwischenziel = erster offener Schritt des Ziels „Ironman“. */
    suspend fun currentGoal(): TrainingGoal {
        val ironman = goalDao.observeGoals().first().firstOrNull { it.title.contains("Ironman", true) } ?: return TrainingGoal.RUN_10K
        val steps = goalDao.observeMilestones().first().filter { it.goalId == ironman.id }.sortedBy { it.sortOrder }
        val next = steps.indexOfFirst { !it.done }
        return when {
            next <= 1 -> TrainingGoal.RUN_10K
            next == 2 -> TrainingGoal.SPRINT
            next == 3 -> TrainingGoal.OLYMPIC
            next == 4 -> TrainingGoal.HALF
            else -> TrainingGoal.FULL
        }
    }

    suspend fun profile(): TrainingProfile {
        val s = settings.settings.first()
        var t = s.training
        if (t.planStart == null) {
            t = t.copy(planStart = LocalDate.now().mondayOfWeek())
            settings.setTraining(t)
        }
        val hardDays = routines.routines.first().filter { it.active && it.area == LifeArea.SPORT }.flatMap { it.days() }.toSet()
        return TrainingProfile(
            goal = currentGoal(),
            startLongestKm = t.startLongestKm,
            easyPaceMinPerKm = t.easyPace,
            planStart = t.planStart!!,
            runsPerWeek = t.runsPerWeek,
            strengthPerWeek = t.strengthPerWeek,
            hasBike = t.hasBike,
            canSwim = t.canSwim,
            hardDays = hardDays,
            restDay = DayOfWeek.SUNDAY,
        )
    }

    private suspend fun results(): List<TrainingResultEntity> = sportDao.trainingResults()

    private fun toSessionResults(list: List<TrainingResultEntity>) = list.map {
        SessionResult(LocalDate.ofEpochDay(it.dateEpochDay), it.type, it.done, it.effort, it.distanceMeters?.div(1000.0))
    }

    /** Plan einer Woche inkl. Klausurphase, Klausur-Vortag, langer Job-Schichten. */
    suspend fun week(monday: LocalDate): List<PlannedTraining> {
        val profile = profile()
        val res = results()
        val sunday = monday.plusDays(6)
        val exams = studyDao.observeExams().first().map { LocalDate.ofEpochDay(it.dateEpochDay) }
        val examDays = exams.filter { !it.isBefore(monday) && !it.isAfter(sunday.plusDays(1)) }.toSet()
        val examPhase = exams.any { !it.isBefore(monday) && !it.isAfter(sunday.plusDays(7)) }
        val blocked = jobs.between(monday, sunday).first().filter { it.endMinute - it.startMinute >= 240 }
            .map { LocalDate.ofEpochDay(it.dateEpochDay) }.toSet()
        val sessions = TrainingPlanner.planWeek(profile, monday, toSessionResults(res), examDays, examPhase, blocked)
        return sessions.map { s ->
            val r = res.firstOrNull { it.dateEpochDay == s.date.toEpochDay() && it.type == s.type }
            PlannedTraining(if (r?.reduced == true) TrainingPlanner.reduce(s) else s, r)
        }
    }

    suspend fun sessionsFor(date: LocalDate): List<PlannedTraining> = week(date.mondayOfWeek()).filter { it.session.date == date }

    suspend fun overview(today: LocalDate = LocalDate.now()): TrainingOverview {
        val profile = profile()
        val res = results()
        val week = week(today.mondayOfWeek())
        val w = TrainingPlanner.weekIndex(profile, today).coerceAtLeast(0)
        val longestLogged = sportDao.observeWorkouts().first().filter { it.type == WorkoutType.RUN }.maxOfOrNull { (it.distanceMeters ?: 0) / 1000.0 } ?: 0.0
        val easyRatings = res.count { it.type == SessionType.STRENGTH && it.effort == Effort.EASY }
        val sleep = runCatching { health.lastNightSleep() }.getOrNull()
        val hardToday = week.any { it.session.date == today && it.session.type.hard && !it.done && it.result?.reduced != true }
        return TrainingOverview(
            profile = profile,
            goal = profile.goal,
            week = week,
            currentLongestKm = maxOf(longestLogged, profile.startLongestKm),
            nextLongRunKm = TrainingPlanner.longRunProgression(profile, toSessionResults(res), w).last(),
            estimatedGoalDate = TrainingPlanner.estimatedGoalDate(profile, toSessionResults(res), today),
            strength = TrainingPlanner.strengthProgram(easyRatings),
            sleepWarning = hardToday && sleep != null && sleep < Duration.ofMinutes(390),
        )
    }

    private suspend fun upsert(date: LocalDate, type: SessionType, change: (TrainingResultEntity) -> TrainingResultEntity) {
        val old = sportDao.trainingResult(date.toEpochDay(), type)
            ?: TrainingResultEntity(dateEpochDay = date.toEpochDay(), type = type, done = false, skipped = false, effort = null, distanceMeters = null, reduced = false, workoutId = null)
        sportDao.putTrainingResult(change(old))
    }

    /** Erledigt (z. B. aus dem Tagesplan) – trägt die Einheit auch ins Trainings-Log ein. */
    suspend fun markDone(session: TrainingSession) {
        val existing = sportDao.trainingResult(session.date.toEpochDay(), session.type)
        var workoutId = existing?.workoutId
        if (workoutId == null) {
            workoutId = sportDao.upsertWorkout(
                WorkoutEntity(
                    dateEpochDay = session.date.toEpochDay(),
                    type = session.type.toWorkoutType(),
                    durationMinutes = session.minutes,
                    distanceMeters = session.distanceKm?.let { (it * 1000).toInt() },
                    note = session.title,
                ),
            )
        }
        upsert(session.date, session.type) { it.copy(done = true, skipped = false, workoutId = workoutId, distanceMeters = it.distanceMeters ?: session.distanceKm?.let { km -> (km * 1000).toInt() }) }
        checkGoalMilestone()
    }

    suspend fun skip(session: TrainingSession) = upsert(session.date, session.type) { it.copy(skipped = true, done = false) }
    suspend fun rate(session: TrainingSession, effort: Effort) = upsert(session.date, session.type) { it.copy(effort = effort) }
    suspend fun reduce(session: TrainingSession) = upsert(session.date, session.type) { it.copy(reduced = true) }

    /**
     * Holt Trainings aus Health Connect ins Log und hakt passende Einheiten automatisch ab.
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
            // Passende geplante Einheit am selben Tag automatisch abhaken
            val planned = sessionsFor(hw.date).filter { !it.done }
            val match = planned.firstOrNull {
                when (hw.kind) {
                    HealthWorkout.Kind.RUN -> it.session.type in setOf(SessionType.EASY_RUN, SessionType.LONG_RUN, SessionType.INTERVALS, SessionType.TEMPO)
                    HealthWorkout.Kind.BIKE -> it.session.type == SessionType.BIKE
                    HealthWorkout.Kind.SWIM -> it.session.type == SessionType.SWIM
                    HealthWorkout.Kind.STRENGTH -> it.session.type == SessionType.STRENGTH
                    else -> false
                }
            }
            if (match != null) {
                upsert(hw.date, match.session.type) { it.copy(done = true, skipped = false, workoutId = id, distanceMeters = hw.distanceMeters) }
            }
        }
        if (added > 0) checkGoalMilestone()
        return added
    }

    /** Zieldistanz geschafft → Zwischenziel abhaken, der Plan wechselt aufs nächste. */
    private suspend fun checkGoalMilestone() {
        val goal = currentGoal()
        if (goal != TrainingGoal.RUN_10K) return
        val longest = sportDao.observeWorkouts().first().filter { it.type == WorkoutType.RUN }.maxOfOrNull { it.distanceMeters ?: 0 } ?: 0
        if (longest >= 10_000) {
            val ironman = goalDao.observeGoals().first().firstOrNull { it.title.contains("Ironman", true) } ?: return
            goalDao.observeMilestones().first().filter { it.goalId == ironman.id }.sortedBy { it.sortOrder }
                .firstOrNull { !it.done && it.title.contains("10 km") }?.let { goalDao.upsertMilestone(it.copy(done = true)) }
        }
    }
}

private fun SessionType.toWorkoutType() = when (this) {
    SessionType.EASY_RUN, SessionType.LONG_RUN, SessionType.INTERVALS, SessionType.TEMPO -> WorkoutType.RUN
    SessionType.STRENGTH -> WorkoutType.STRENGTH
    SessionType.BIKE -> WorkoutType.BIKE
    SessionType.SWIM -> WorkoutType.SWIM
}
