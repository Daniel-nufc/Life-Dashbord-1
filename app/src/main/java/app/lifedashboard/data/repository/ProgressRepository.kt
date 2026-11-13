package app.lifedashboard.data.repository

import app.lifedashboard.data.db.GoalDao
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.SportDao
import app.lifedashboard.data.db.StudyDao
import app.lifedashboard.data.db.TaskDao
import app.lifedashboard.data.db.WorkoutType
import app.lifedashboard.data.settings.SettingsRepository
import app.lifedashboard.planner.ActivityLog
import app.lifedashboard.planner.Discipline
import app.lifedashboard.planner.Progress
import app.lifedashboard.planner.ProgressInput
import app.lifedashboard.planner.ProgressState
import app.lifedashboard.planner.StudyLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Punkte, Level, Serie, Wochenziele, Ironman-Weg und Abzeichen.
 * Alles wird live aus den vorhandenen Tabellen berechnet – es gibt keine eigene Tabelle dafür,
 * also auch nichts, was beim Update oder Abgleich verloren gehen kann.
 */
@Singleton
class ProgressRepository @Inject constructor(
    private val sportDao: SportDao,
    private val studyDao: StudyDao,
    private val taskDao: TaskDao,
    private val goalDao: GoalDao,
    private val routines: RoutineRepository,
    private val study: StudyRepository,
    private val settings: SettingsRepository,
) {
    private fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun WorkoutType.discipline() = when (this) {
        WorkoutType.RUN -> Discipline.RUN
        WorkoutType.SWIM -> Discipline.SWIM
        WorkoutType.BIKE -> Discipline.BIKE
        else -> Discipline.OTHER
    }

    fun observe(today: LocalDate = LocalDate.now()): Flow<ProgressState> {
        val logs = combine(sportDao.observeWorkouts(), studyDao.observeSessionsSince(0), taskDao.observeAll(), goalDao.observeHabitLogs(0)) { w, s, t, h ->
            Logs(
                activities = w.map { ActivityLog(LocalDate.ofEpochDay(it.dateEpochDay), it.type.discipline(), it.durationMinutes, it.distanceMeters) },
                study = s.map { StudyLog(it.startedAtMillis.toDate(), it.minutes) },
                tasksDone = t.filter { it.done }.map { (it.doneAtMillis ?: it.createdAtMillis).toDate() },
                habitDays = h.map { LocalDate.ofEpochDay(it.dateEpochDay) },
            )
        }
        val goals = combine(settings.settings.map { it.training }.distinctUntilChanged(), routines.routines, study.overview(today)) { training, routineBlocks, overview ->
            // Sport-Wochenziel = feste Trainings (Floorball …) + freie Sport-Zeitfenster
            val fixed = routineBlocks.filter { it.active && it.area == LifeArea.SPORT }.sumOf { (it.endMinute - it.startMinute) * it.days().size }
            val sportGoal = fixed + training.windowsPerWeek * training.windowMinutes
            // Lern-Wochenziel = was der Lernplan für diese Woche noch vorsieht + was schon geschafft ist (mind. 3 Std)
            val sunday = Progress.weekStart(today).plusDays(6)
            val plannedRest = overview.plan.filter { !it.date.isBefore(today) && !it.date.isAfter(sunday) }.sumOf { it.minutes }
            val doneWeek = overview.sessionsThisWeek.sumOf { it.minutes }
            sportGoal to maxOf(MIN_STUDY_GOAL, doneWeek + plannedRest)
        }
        return combine(logs, goals) { l, g ->
            Progress.compute(ProgressInput(today, l.activities, l.study, l.tasksDone, l.habitDays, weeklySportGoalMinutes = g.first, weeklyStudyGoalMinutes = g.second))
        }.distinctUntilChanged()
    }

    private data class Logs(
        val activities: List<ActivityLog>,
        val study: List<StudyLog>,
        val tasksDone: List<LocalDate>,
        val habitDays: List<LocalDate>,
    )

    private companion object {
        const val MIN_STUDY_GOAL = 180
    }
}
