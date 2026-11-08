package app.lifedashboard.data.repository

import app.lifedashboard.data.db.ExamEntity
import app.lifedashboard.data.db.GradeEntity
import app.lifedashboard.data.db.StudyDao
import app.lifedashboard.data.db.StudySessionEntity
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.TopicEntity
import app.lifedashboard.planner.Grades
import app.lifedashboard.planner.ReviewRating
import app.lifedashboard.planner.SpacedRepetition
import app.lifedashboard.planner.StudyAllocation
import app.lifedashboard.planner.StudyExam
import app.lifedashboard.planner.StudyPlanner
import app.lifedashboard.planner.StudyTopic
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class ExamWithTopics(val exam: ExamEntity, val subject: SubjectEntity?, val topics: List<TopicEntity>) {
    val date: LocalDate get() = LocalDate.ofEpochDay(exam.dateEpochDay)
    val progress: Int get() = if (topics.isEmpty()) 0 else topics.sumOf { it.progress } / topics.size
    fun daysLeft(today: LocalDate = LocalDate.now()) = (exam.dateEpochDay - today.toEpochDay()).toInt()
}

data class SubjectGrades(val subject: SubjectEntity, val grades: List<GradeEntity>) {
    val average: Double? get() = Grades.average(grades.map { it.points to it.weight })
}

data class StudyOverview(
    val exams: List<ExamWithTopics>,
    val subjects: List<SubjectEntity>,
    val grades: List<SubjectGrades>,
    val plan: List<StudyAllocation>,
    val dueReviews: List<Pair<ExamWithTopics, TopicEntity>>,
    val sessionsThisWeek: List<StudySessionEntity>,
) {
    /** Schnitt über alle Fächer mit Noten (jedes Fach gleich gewichtet). */
    val overallAverage: Double? get() = grades.mapNotNull { it.average }.takeIf { it.isNotEmpty() }?.average()
    val upcoming: List<ExamWithTopics> get() = exams.filter { it.daysLeft() >= 0 }
}

@Singleton
class StudyRepository @Inject constructor(
    private val dao: StudyDao,
    private val timetable: TimetableRepository,
) {
    fun overview(today: LocalDate = LocalDate.now()): Flow<StudyOverview> {
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val fromMillis = weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return combine(
            dao.observeExams(),
            dao.observeTopics(),
            dao.observeGrades(),
            timetable.subjects,
            dao.observeSessionsSince(fromMillis),
        ) { exams, topics, grades, subjects, sessions ->
            val subjectById = subjects.associateBy { it.id }
            val topicsByExam = topics.groupBy { it.examId }
            val withTopics = exams.map { ExamWithTopics(it, subjectById[it.subjectId], topicsByExam[it.id].orEmpty()) }
            val plan = StudyPlanner.plan(
                exams = withTopics.map { e ->
                    StudyExam(
                        id = e.exam.id,
                        subjectName = e.subject?.name ?: e.exam.title,
                        date = e.date,
                        difficulty = e.subject?.difficulty ?: 3,
                        topics = e.topics.map { StudyTopic(it.id, it.title, it.effortMinutes, it.progress) },
                    )
                },
                today = today,
            )
            val todayEpoch = today.toEpochDay()
            val due = withTopics.filter { it.daysLeft(today) >= 0 }.flatMap { e ->
                e.topics.filter { t -> t.nextReviewEpochDay != null && t.nextReviewEpochDay <= todayEpoch }.map { e to it }
            }
            StudyOverview(
                exams = withTopics,
                subjects = subjects,
                grades = subjects.map { s -> SubjectGrades(s, grades.filter { it.subjectId == s.id }) },
                plan = plan,
                dueReviews = due,
                sessionsThisWeek = sessions,
            )
        }
    }

    suspend fun saveExam(exam: ExamEntity, topicTitles: List<String>?, defaultEffort: Int = 60): Long {
        val id = dao.upsertExam(exam).let { if (exam.id != 0L) exam.id else it }
        topicTitles?.filter { it.isNotBlank() }?.forEach { title ->
            dao.upsertTopic(TopicEntity(examId = id, title = title.trim(), effortMinutes = defaultEffort, progress = 0, intervalDays = 0, nextReviewEpochDay = null, lastReviewedEpochDay = null))
        }
        return id
    }

    suspend fun deleteExam(id: Long) {
        dao.deleteTopicsOfExam(id)
        dao.deleteExam(id)
    }

    suspend fun saveTopic(topic: TopicEntity) = dao.upsertTopic(topic)
    suspend fun deleteTopic(id: Long) = dao.deleteTopic(id)

    /** Fortschritt setzen. Ab 50 % beginnt automatisch die Wiederholung nach 1 Tag. */
    suspend fun setProgress(topic: TopicEntity, progress: Int) {
        val today = LocalDate.now().toEpochDay()
        val startReview = topic.nextReviewEpochDay == null && progress >= 50
        dao.upsertTopic(
            topic.copy(
                progress = progress.coerceIn(0, 100),
                intervalDays = if (startReview) 1 else topic.intervalDays,
                nextReviewEpochDay = if (startReview) today + 1 else topic.nextReviewEpochDay,
            ),
        )
    }

    /** Wiederholung erledigt – nächster Termin nach Spaced Repetition. */
    suspend fun review(topic: TopicEntity, rating: ReviewRating) {
        val today = LocalDate.now().toEpochDay()
        val next = SpacedRepetition.nextInterval(topic.intervalDays, rating)
        dao.upsertTopic(topic.copy(intervalDays = next, nextReviewEpochDay = today + next, lastReviewedEpochDay = today))
    }

    suspend fun saveGrade(grade: GradeEntity) = dao.upsertGrade(grade)
    suspend fun deleteGrade(id: Long) = dao.deleteGrade(id)

    suspend fun recordSession(subjectId: Long?, minutes: Int, startedAt: Long) =
        dao.insertSession(StudySessionEntity(subjectId = subjectId, examId = null, topicId = null, startedAtMillis = startedAt, minutes = minutes))
}
