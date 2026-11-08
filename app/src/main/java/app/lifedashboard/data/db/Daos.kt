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
