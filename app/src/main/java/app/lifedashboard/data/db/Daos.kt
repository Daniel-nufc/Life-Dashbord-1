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

    @Query("DELETE FROM ical_sources WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface StudyDao {
    @Query("SELECT * FROM exams ORDER BY dateEpochDay")
    fun observeExams(): Flow<List<ExamEntity>>

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

    @Query("SELECT * FROM plan_blocks WHERE id = :id")
    suspend fun byId(id: Long): PlanBlockEntity?

    @Insert
    suspend fun insertAll(blocks: List<PlanBlockEntity>)

    @Query("DELETE FROM plan_blocks WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("UPDATE plan_blocks SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: PlanStatus)

    @Insert
    suspend fun insertFeedback(feedback: PlanFeedbackEntity)

    @Query("SELECT * FROM plan_feedback WHERE atMillis >= :sinceMillis")
    suspend fun feedbackSince(sinceMillis: Long): List<PlanFeedbackEntity>
}
