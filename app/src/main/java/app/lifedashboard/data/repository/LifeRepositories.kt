package app.lifedashboard.data.repository

import app.lifedashboard.data.db.GoalDao
import app.lifedashboard.data.db.GoalEntity
import app.lifedashboard.data.db.HabitEntity
import app.lifedashboard.data.db.HabitLogEntity
import app.lifedashboard.data.db.MilestoneEntity
import app.lifedashboard.data.db.SportDao
import app.lifedashboard.data.db.WorkoutEntity
import app.lifedashboard.data.db.WorkoutSetEntity
import app.lifedashboard.planner.Streaks
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class WorkoutWithSets(val workout: WorkoutEntity, val sets: List<WorkoutSetEntity>) {
    val date: LocalDate get() = LocalDate.ofEpochDay(workout.dateEpochDay)
}

@Singleton
class SportRepository @Inject constructor(private val dao: SportDao) {
    val workouts: Flow<List<WorkoutWithSets>> = combine(dao.observeWorkouts(), dao.observeSets()) { w, s ->
        val byWorkout = s.groupBy { it.workoutId }
        w.map { WorkoutWithSets(it, byWorkout[it.id].orEmpty()) }
    }

    suspend fun save(workout: WorkoutEntity, sets: List<WorkoutSetEntity>) {
        val id = dao.upsertWorkout(workout).let { if (workout.id != 0L) workout.id else it }
        dao.deleteSetsOf(id)
        if (sets.isNotEmpty()) dao.insertSets(sets.map { it.copy(id = 0, workoutId = id) })
    }

    suspend fun delete(id: Long) {
        dao.deleteSetsOf(id)
        dao.deleteWorkout(id)
    }
}

data class HabitStatus(
    val habit: HabitEntity,
    val doneToday: Boolean,
    val streak: Streaks.Result,
    /** Letzte 7 Tage (ältester zuerst): erledigt? */
    val lastWeek: List<Boolean>,
)

data class GoalWithMilestones(val goal: GoalEntity, val milestones: List<MilestoneEntity>) {
    val progress: Float get() = if (milestones.isEmpty()) (if (goal.done) 1f else 0f) else milestones.count { it.done }.toFloat() / milestones.size
    val nextStep: MilestoneEntity? get() = milestones.firstOrNull { !it.done }
}

@Singleton
class GoalRepository @Inject constructor(private val dao: GoalDao) {

    fun habits(today: LocalDate = LocalDate.now()): Flow<List<HabitStatus>> =
        combine(dao.observeHabits(), dao.observeHabitLogs(today.minusDays(400).toEpochDay())) { habits, logs ->
            val byHabit = logs.groupBy { it.habitId }
            habits.filter { it.active }.map { habit ->
                val days = byHabit[habit.id].orEmpty().map { LocalDate.ofEpochDay(it.dateEpochDay) }.toSet()
                HabitStatus(
                    habit = habit,
                    doneToday = today in days,
                    streak = Streaks.compute(days, today),
                    lastWeek = (6 downTo 0).map { today.minusDays(it.toLong()) in days },
                )
            }
        }

    val allHabits: Flow<List<HabitEntity>> = dao.observeHabits()

    val goals: Flow<List<GoalWithMilestones>> = combine(dao.observeGoals(), dao.observeMilestones()) { goals, ms ->
        val byGoal = ms.groupBy { it.goalId }
        goals.map { GoalWithMilestones(it, byGoal[it.id].orEmpty()) }
    }

    suspend fun toggleHabit(habitId: Long, date: LocalDate, done: Boolean) {
        if (done) dao.insertHabitLog(HabitLogEntity(habitId = habitId, dateEpochDay = date.toEpochDay()))
        else dao.deleteHabitLog(habitId, date.toEpochDay())
    }

    suspend fun saveHabit(habit: HabitEntity) = dao.upsertHabit(habit)
    suspend fun deleteHabit(id: Long) = dao.deleteHabit(id)

    suspend fun saveGoal(goal: GoalEntity): Long = dao.upsertGoal(goal).let { if (goal.id != 0L) goal.id else it }
    suspend fun deleteGoal(id: Long) {
        dao.deleteMilestonesOf(id)
        dao.deleteGoal(id)
    }

    suspend fun saveMilestone(m: MilestoneEntity) = dao.upsertMilestone(m)
    suspend fun deleteMilestone(id: Long) = dao.deleteMilestone(id)
}
