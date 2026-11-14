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
    /** Schon gelernt je Klausur (Minuten aus geschafften Lern-Blöcken) */
    val studiedMinutes: Map<Long, Int> = emptyMap(),
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
    private val agenda: AgendaRepository,
    private val planDao: app.lifedashboard.data.db.PlanDao,
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
        val busy = combine(
            routines.routines, jobs.between(today, today.plusDays(35)), schoolCalendar.ranges, agenda.reservedDays(today, today.plusDays(35)),
        ) { r, j, h, reserved -> Busy(r, j, h, reserved) }
        return combine(
            dao.observeExams(),
            dao.observeTopics(),
            dao.observeGrades(),
            timetable.subjects,
            combine(
                dao.observeSessionsSince(fromMillis), busy,
                planDao.observeBetween(today.toEpochDay() - 90, today.toEpochDay()),
            ) { s, b, blocks -> Triple(s, b, blocks) },
        ) { exams, topics, grades, subjects, sessionsAndBusy ->
            val (sessions, busyDays, recentBlocks) = sessionsAndBusy
            // Schon gelernt je Klausur: geschaffte Lern-Blöcke (study:<Tag>:<Klausur>:…) der letzten 90 Tage
            val studied = recentBlocks.filter { it.status == app.lifedashboard.data.db.PlanStatus.DONE && it.demandKey.startsWith("study:") }
                .groupBy { it.demandKey.split(":").getOrNull(2)?.toLongOrNull() ?: -1L }
                .mapValues { (_, l) -> l.sumOf { it.endMinute - it.startMinute } }
            val (routineBlocks, shifts, freeRanges, reservedDays) = busyDays
            val targetPoints = app.lifedashboard.planner.Grades.pointsForGrade(TARGET_GRADE)
            val averageBySubject = grades.groupBy { it.subjectId }
                .mapValues { (_, g) -> app.lifedashboard.planner.Grades.average(g.map { it.points to it.weight }) }
            /** Realistische Lernzeit an einem Tag: an Trainings- und Arbeitstagen weniger, an freien Tagen mehr. */
            // gleiche Regeln wie das Tages-Pensum: werktags 2 Std (mit Training/Job 1 Std), Samstag 2 Std, Sonntag frei
            fun capacity(date: LocalDate): Int {
                val busy = routineBlocks.any { it.active && it.area == app.lifedashboard.data.db.LifeArea.SPORT && date.dayOfWeek in it.days() } ||
                    shifts.any { it.dateEpochDay == date.toEpochDay() }
                // Lernwoche: Klausur in den nächsten 7 Tagen → 1 Std mehr erlaubt
                val examWeek = if (exams.any { it.dateEpochDay - date.toEpochDay() in 1..7 }) 60 else 0
                return when {
                    // eigener Ganztagstermin: nur lernen, wenn die Klausur in 1–2 Tagen ist
                    date in reservedDays -> if (exams.any { it.dateEpochDay - date.toEpochDay() in 1..2 }) 60 else 0
                    date.dayOfWeek == java.time.DayOfWeek.SUNDAY -> if (exams.any { it.dateEpochDay - date.toEpochDay() in 1..2 }) 60 else 0
                    date.dayOfWeek == java.time.DayOfWeek.SATURDAY -> 120 + examWeek
                    freeRanges.any { date in it } -> 150 + examWeek
                    busy -> 60 + examWeek
                    else -> 120 + examWeek
                }
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
                        app.lifedashboard.data.db.ExamKind.KLAUSUR -> 480
                        app.lifedashboard.data.db.ExamKind.TEST -> 180
                        else -> 180
                    },
                    effortFactor = StudyPlanner.effortFactor(averageBySubject[e.exam.subjectId], targetPoints),
                    // Oberstufe: eine Klausur braucht mehr als die Summe kleiner Themen – mind. 8 Std, Test 3 Std
                    minimumMinutes = when (e.exam.kind) {
                        app.lifedashboard.data.db.ExamKind.KLAUSUR -> 480
                        app.lifedashboard.data.db.ExamKind.TEST -> 180
                        else -> 240
                    },
                )
            }
            // Ab 21 Uhr passt heute nichts mehr hinein → Lernplan startet morgen (sonst ginge die Zeit verloren)
            val planFrom = if (today == LocalDate.now() && java.time.LocalTime.now().isAfter(LATE_EVENING)) today.plusDays(1) else today
            val plan = StudyPlanner.plan(exams = studyExams, today = planFrom, dailyCapMinutes = 210, capacity = ::capacity)
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
                studiedMinutes = studied,
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
    suspend fun topic(id: Long): TopicEntity? = dao.observeTopics().first().firstOrNull { it.id == id }

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

private data class Busy(
    val routines: List<app.lifedashboard.data.db.RoutineBlockEntity>,
    val shifts: List<app.lifedashboard.data.db.JobShiftEntity>,
    val freeRanges: List<app.lifedashboard.planner.DateRange>,
    val reserved: Set<LocalDate>,
)
