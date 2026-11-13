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
import kotlinx.coroutines.flow.first

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
    /** Einschätzung je Klausur (passt / eng / Zeit fehlt), Schlüssel = Klausur-ID */
    val forecasts: Map<Long, app.lifedashboard.planner.StudyForecast> = emptyMap(),
) {
    /** Schnitt über alle Fächer mit Noten (jedes Fach gleich gewichtet). */
    val overallAverage: Double? get() = grades.mapNotNull { it.average }.takeIf { it.isNotEmpty() }?.average()
    val upcoming: List<ExamWithTopics> get() = exams.filter { it.daysLeft() >= 0 }
}

@Singleton
class StudyRepository @Inject constructor(
    private val dao: StudyDao,
    private val timetable: TimetableRepository,
    private val routines: RoutineRepository,
    private val jobs: JobRepository,
    private val schoolCalendar: SchoolCalendarRepository,
) {
    private companion object {
        val LATE_EVENING: java.time.LocalTime = java.time.LocalTime.of(21, 0)
        /** Ziel-Abischnitt – liegt ein Fach darunter, bekommt es etwas mehr Lernzeit. */
        const val TARGET_GRADE = 1.7
    }

    fun overview(today: LocalDate = LocalDate.now()): Flow<StudyOverview> {
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val fromMillis = weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Was den Tag sonst füllt: feste Blöcke (Floorball …), Job-Schichten, Ferien
        val busy = combine(routines.routines, jobs.between(today, today.plusDays(35)), schoolCalendar.ranges) { r, j, h -> Triple(r, j, h) }
        return combine(
            dao.observeExams(),
            dao.observeTopics(),
            dao.observeGrades(),
            timetable.subjects,
            combine(dao.observeSessionsSince(fromMillis), busy) { s, b -> s to b },
        ) { exams, topics, grades, subjects, sessionsAndBusy ->
            val (sessions, busyDays) = sessionsAndBusy
            val (routineBlocks, shifts, freeRanges) = busyDays
            val targetPoints = app.lifedashboard.planner.Grades.pointsForGrade(TARGET_GRADE)
            val averageBySubject = grades.groupBy { it.subjectId }
                .mapValues { (_, g) -> app.lifedashboard.planner.Grades.average(g.map { it.points to it.weight }) }
            /** Realistische Lernzeit an einem Tag: an Trainings- und Arbeitstagen weniger, an freien Tagen mehr. */
            fun capacity(date: LocalDate): Int {
                val free = date.dayOfWeek.value >= 6 || freeRanges.any { date in it }
                val routineMinutes = routineBlocks.filter { it.active && date.dayOfWeek in it.days() }.sumOf { it.endMinute - it.startMinute }
                val shiftMinutes = shifts.filter { it.dateEpochDay == date.toEpochDay() }.sumOf { it.endMinute - it.startMinute }
                val base = if (free) 150 else 120
                return (base - (routineMinutes / 2).coerceAtMost(60) - shiftMinutes * 2 / 5).coerceAtLeast(25)
            }
            val subjectById = subjects.associateBy { it.id }
            val topicsByExam = topics.groupBy { it.examId }
            val withTopics = exams.map { ExamWithTopics(it, subjectById[it.subjectId], topicsByExam[it.id].orEmpty()) }
            val studyExams = withTopics.map { e ->
                StudyExam(
                    id = e.exam.id,
                    subjectName = e.subject?.name ?: e.exam.title,
                    date = e.date,
                    difficulty = e.subject?.difficulty ?: 3,
                    topics = e.topics.map { StudyTopic(it.id, it.title, it.effortMinutes, it.progress) },
                    defaultMinutes = when (e.exam.kind) {
                        app.lifedashboard.data.db.ExamKind.KLAUSUR -> 240
                        app.lifedashboard.data.db.ExamKind.TEST -> 120
                        else -> 180
                    },
                    effortFactor = StudyPlanner.effortFactor(averageBySubject[e.exam.subjectId], targetPoints),
                )
            }
            // Ab 21 Uhr passt heute nichts mehr hinein → Lernplan startet morgen (sonst ginge die Zeit verloren)
            val planFrom = if (today == LocalDate.now() && java.time.LocalTime.now().isAfter(LATE_EVENING)) today.plusDays(1) else today
            val plan = StudyPlanner.plan(exams = studyExams, today = planFrom, dailyCapMinutes = 150, capacity = ::capacity)
            val forecasts = StudyPlanner.forecast(studyExams, plan, today)
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
                forecasts = forecasts,
            )
        }
    }

    suspend fun saveExam(exam: ExamEntity, topicTitles: List<String>?, defaultEffort: Int = 60): Long {
        val id = dao.upsertExam(exam).let { if (exam.id != 0L) exam.id else it }
        // Themen trägst du selbst ein – Vorschläge gibt es nur zum Antippen im Dialog
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
    suspend fun renameTopic(topic: TopicEntity, title: String, effortMinutes: Int) = dao.upsertTopic(topic.copy(title = title, effortMinutes = effortMinutes))

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
