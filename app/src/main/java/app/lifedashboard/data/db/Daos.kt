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
