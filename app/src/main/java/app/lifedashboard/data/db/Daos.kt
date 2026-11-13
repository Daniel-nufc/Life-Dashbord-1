package app.lifedashboard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {
    @Query("SELECT * FROM subjects ORDER BY code COLLATE NOCASE")
    fun observeSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM school_periods ORDER BY number")
    fun observePeriods(): Flow<List<SchoolPeriodEntity>>

    @Query("SELECT * FROM timetable_lessons")
    fun observeLessons(): Flow<List<TimetableLessonEntity>>

    @Query("SELECT * FROM timetable_lessons WHERE dayOfWeek = :dayOfWeek AND period = :period")
    suspend fun lessonsAt(dayOfWeek: Int, period: Int): List<TimetableLessonEntity>

    @Query("SELECT COUNT(*) FROM subjects")
    suspend fun countSubjects(): Int

    @Insert
    suspend fun insertSubjects(subjects: List<SubjectEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeriods(periods: List<SchoolPeriodEntity>)

    @Insert
    suspend fun insertLessons(lessons: List<TimetableLessonEntity>)

    @Upsert
    suspend fun upsertLesson(lesson: TimetableLessonEntity)

    @Query("DELETE FROM timetable_lessons WHERE id = :id")
    suspend fun deleteLesson(id: Long)

    @Upsert
    suspend fun upsertSubject(subject: SubjectEntity)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routine_blocks ORDER BY startMinute")
    fun observeAll(): Flow<List<RoutineBlockEntity>>

    @Insert
    suspend fun insertAll(blocks: List<RoutineBlockEntity>)

    @Upsert
    suspend fun upsert(block: RoutineBlockEntity)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE dateEpochDay BETWEEN :fromDay AND :toDay ORDER BY dateEpochDay, startMinute")
    fun observeBetween(fromDay: Long, toDay: Long): Flow<List<EventEntity>>

    @Upsert
    suspend fun upsert(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM events WHERE externalUid LIKE :prefix || '%'")
    suspend fun byExternalPrefix(prefix: String): List<EventEntity>
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY done, dueEpochDay IS NULL, dueEpochDay, createdAtMillis")
    fun observeAll(): Flow<List<TaskEntity>>

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Query("UPDATE tasks SET done = :done, doneAtMillis = :doneAt WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, doneAt: Long?)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM tasks WHERE externalUid LIKE :prefix || '%'")
    suspend fun byExternalPrefix(prefix: String): List<TaskEntity>
}

@Dao
interface JobShiftDao {
    @Query("SELECT * FROM job_shifts WHERE dateEpochDay BETWEEN :fromDay AND :toDay ORDER BY dateEpochDay, startMinute")
    fun observeBetween(fromDay: Long, toDay: Long): Flow<List<JobShiftEntity>>

    @Upsert
    suspend fun upsert(shift: JobShiftEntity)

    @Query("DELETE FROM job_shifts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface IcalSourceDao {
    @Query("SELECT * FROM ical_sources ORDER BY name")
    fun observeAll(): Flow<List<IcalSourceEntity>>

    @Query("SELECT * FROM ical_sources")
    suspend fun all(): List<IcalSourceEntity>

    @Upsert
    suspend fun upsert(source: IcalSourceEntity): Long

    @Query("UPDATE ical_sources SET lastSyncMillis = :at, lastError = :error WHERE id = :id")
    suspend fun setResult(id: Long, at: Long, error: String?)

    @Query("UPDATE ical_sources SET lastSyncMillis = :at, lastError = NULL, lastNote = :note WHERE id = :id")
    suspend fun setSuccess(id: Long, at: Long, note: String?)

    @Query("DELETE FROM ical_sources WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface StudyDao {
    @Query("SELECT * FROM exams ORDER BY dateEpochDay")
    fun observeExams(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams WHERE dateEpochDay BETWEEN :fromDay AND :toDay ORDER BY dateEpochDay")
    fun observeExamsBetween(fromDay: Long, toDay: Long): Flow<List<ExamEntity>>

    @Query("SELECT * FROM topics ORDER BY id")
    fun observeTopics(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM grades ORDER BY dateEpochDay DESC")
    fun observeGrades(): Flow<List<GradeEntity>>

    @Query("SELECT * FROM study_sessions WHERE startedAtMillis >= :fromMillis ORDER BY startedAtMillis")
    fun observeSessionsSince(fromMillis: Long): Flow<List<StudySessionEntity>>

    @Upsert
    suspend fun upsertExam(exam: ExamEntity): Long

    @Query("DELETE FROM exams WHERE id = :id")
    suspend fun deleteExam(id: Long)

    @Upsert
    suspend fun upsertTopic(topic: TopicEntity)

    @Query("DELETE FROM topics WHERE id = :id")
    suspend fun deleteTopic(id: Long)

    @Query("DELETE FROM topics WHERE examId = :examId")
    suspend fun deleteTopicsOfExam(examId: Long)

    @Upsert
    suspend fun upsertGrade(grade: GradeEntity)

    @Query("DELETE FROM grades WHERE id = :id")
    suspend fun deleteGrade(id: Long)

    @Insert
    suspend fun insertSession(session: StudySessionEntity)
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plan_blocks WHERE dateEpochDay = :day ORDER BY startMinute")
    fun observeDay(day: Long): Flow<List<PlanBlockEntity>>

    @Query("SELECT * FROM plan_blocks WHERE dateEpochDay = :day ORDER BY startMinute")
    suspend fun forDay(day: Long): List<PlanBlockEntity>

    @Query("SELECT * FROM plan_blocks WHERE dateEpochDay BETWEEN :fromDay AND :toDay")
    suspend fun between(fromDay: Long, toDay: Long): List<PlanBlockEntity>

    @Query("SELECT * FROM plan_blocks WHERE dateEpochDay BETWEEN :fromDay AND :toDay ORDER BY dateEpochDay, startMinute")
    fun observeBetween(fromDay: Long, toDay: Long): Flow<List<PlanBlockEntity>>

    @Query("SELECT * FROM plan_blocks WHERE id = :id")
    suspend fun byId(id: Long): PlanBlockEntity?

    @Insert
    suspend fun insertAll(blocks: List<PlanBlockEntity>)

    @Query("DELETE FROM plan_blocks WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("UPDATE plan_blocks SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: PlanStatus)

    @Query("UPDATE plan_blocks SET endMinute = :endMinute WHERE id = :id")
    suspend fun setEnd(id: Long, endMinute: Int)

    /** Alte Trainings-Einheiten (vor den Sport-Zeitfenstern) ab [fromDay] löschen – erledigte bleiben. */
    @Query("DELETE FROM plan_blocks WHERE dateEpochDay >= :fromDay AND demandKey LIKE 'train:%' AND demandKey NOT LIKE '%:WINDOW' AND status IN ('PROPOSED', 'ACCEPTED')")
    suspend fun deleteOldTrainingBlocks(fromDay: Long)

    @Insert
    suspend fun insertFeedback(feedback: PlanFeedbackEntity)

    @Query("SELECT * FROM plan_feedback WHERE atMillis >= :sinceMillis")
    suspend fun feedbackSince(sinceMillis: Long): List<PlanFeedbackEntity>
}

@Dao
interface SportDao {
    @Query("SELECT * FROM workouts ORDER BY dateEpochDay DESC, id DESC")
    fun observeWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workout_sets")
    fun observeSets(): Flow<List<WorkoutSetEntity>>

    @Upsert
    suspend fun upsertWorkout(workout: WorkoutEntity): Long

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Query("DELETE FROM workout_sets WHERE workoutId = :workoutId")
    suspend fun deleteSetsOf(workoutId: Long)

    @Query("SELECT COUNT(*) FROM workouts WHERE externalId = :externalId")
    suspend fun countExternal(externalId: String): Int

    @Query("SELECT * FROM training_results ORDER BY dateEpochDay")
    fun observeTrainingResults(): Flow<List<TrainingResultEntity>>

    @Query("SELECT * FROM training_results")
    suspend fun trainingResults(): List<TrainingResultEntity>

    @Query("SELECT * FROM training_results WHERE dateEpochDay = :day AND type = :type")
    suspend fun trainingResult(day: Long, type: app.lifedashboard.planner.SessionType): TrainingResultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTrainingResult(result: TrainingResultEntity)

    @Insert
    suspend fun insertSets(sets: List<WorkoutSetEntity>)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM habits ORDER BY sortOrder, id")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_logs WHERE dateEpochDay >= :fromDay")
    fun observeHabitLogs(fromDay: Long): Flow<List<HabitLogEntity>>

    @Upsert
    suspend fun upsertHabit(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabit(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHabitLog(log: HabitLogEntity)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND dateEpochDay = :day")
    suspend fun deleteHabitLog(habitId: Long, day: Long)

    @Query("SELECT * FROM goals ORDER BY done, sortOrder, id")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM milestones ORDER BY sortOrder, id")
    fun observeMilestones(): Flow<List<MilestoneEntity>>

    @Upsert
    suspend fun upsertGoal(goal: GoalEntity): Long

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Upsert
    suspend fun upsertMilestone(milestone: MilestoneEntity)

    @Query("DELETE FROM milestones WHERE id = :id")
    suspend fun deleteMilestone(id: Long)

    @Query("DELETE FROM milestones WHERE goalId = :goalId")
    suspend fun deleteMilestonesOf(goalId: Long)

    @Insert
    suspend fun insertHabits(habits: List<HabitEntity>)

    @Insert
    suspend fun insertGoal(goal: GoalEntity): Long

    @Insert
    suspend fun insertMilestones(milestones: List<MilestoneEntity>)
}

@Dao
interface FinanceDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    suspend fun accounts(): List<AccountEntity>

    @Upsert
    suspend fun upsertAccount(account: AccountEntity): Long

    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, id DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Upsert
    suspend fun upsertTransaction(tx: TransactionEntity)

    @Insert
    suspend fun insertTransactions(tx: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)

    @Query("SELECT importHash FROM transactions WHERE importHash IS NOT NULL")
    suspend fun importHashes(): List<String>

    @Query("SELECT COUNT(*) FROM transactions WHERE recurringId = :recurringId AND dateEpochDay = :day")
    suspend fun countRecurringOn(recurringId: Long, day: Long): Int

    @Query("SELECT * FROM category_rules")
    fun observeRules(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules")
    suspend fun rules(): List<CategoryRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRule(rule: CategoryRuleEntity)

    @Query("SELECT * FROM budgets ORDER BY category")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    @Upsert
    suspend fun upsertBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun deleteBudget(category: String)

    @Query("SELECT * FROM recurring ORDER BY dayOfMonth")
    fun observeRecurring(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring WHERE active = 1")
    suspend fun activeRecurring(): List<RecurringEntity>

    @Upsert
    suspend fun upsertRecurring(r: RecurringEntity)

    @Query("DELETE FROM recurring WHERE id = :id")
    suspend fun deleteRecurring(id: Long)

    @Query("SELECT * FROM savings_goals ORDER BY id")
    fun observeSavings(): Flow<List<SavingsGoalEntity>>

    @Upsert
    suspend fun upsertSavings(goal: SavingsGoalEntity)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteSavings(id: Long)
}

@Dao
interface EtsyDao {
    @Query("SELECT * FROM etsy_designs ORDER BY setName, id")
    fun observeDesigns(): Flow<List<EtsyDesignEntity>>

    @Upsert
    suspend fun upsertDesign(design: EtsyDesignEntity)

    @Insert
    suspend fun insertDesigns(designs: List<EtsyDesignEntity>)

    @Query("DELETE FROM etsy_designs WHERE id = :id")
    suspend fun deleteDesign(id: Long)

    @Query("SELECT * FROM etsy_sales ORDER BY dateEpochDay DESC, id DESC")
    fun observeSales(): Flow<List<EtsySaleEntity>>

    @Upsert
    suspend fun upsertSale(sale: EtsySaleEntity)

    @Query("DELETE FROM etsy_sales WHERE id = :id")
    suspend fun deleteSale(id: Long)

    @Query("SELECT * FROM etsy_stats ORDER BY monthKey DESC")
    fun observeStats(): Flow<List<EtsyStatsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putStats(stats: EtsyStatsEntity)
}

@Dao
interface HolidayDao {
    @Query("SELECT * FROM school_holidays ORDER BY startEpochDay")
    fun observeAll(): Flow<List<SchoolHolidayEntity>>

    @Query("SELECT * FROM school_holidays ORDER BY startEpochDay")
    suspend fun all(): List<SchoolHolidayEntity>

    @Insert
    suspend fun insertAll(items: List<SchoolHolidayEntity>)

    @Upsert
    suspend fun upsert(item: SchoolHolidayEntity)

    @Query("DELETE FROM school_holidays WHERE id = :id")
    suspend fun delete(id: Long)
}
