package app.lifedashboard.data.sync

import app.lifedashboard.data.db.EventDao
import app.lifedashboard.data.db.EventEntity
import app.lifedashboard.data.db.IcalSourceDao
import app.lifedashboard.data.db.IcalSourceEntity
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.TaskDao
import app.lifedashboard.data.db.TaskEntity
import app.lifedashboard.data.db.TaskKind
import app.lifedashboard.planner.IcsParser
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Holt Kalender-Abos (iCal-Links) und übernimmt die Einträge als Aufgaben oder Termine.
 * Läuft direkt vom Handy aus – kein eigener Server.
 */
@Singleton
class IcalSyncRepository @Inject constructor(
    private val sources: IcalSourceDao,
    private val events: EventDao,
    private val tasks: TaskDao,
    private val study: app.lifedashboard.data.repository.StudyRepository,
    private val studyDao: app.lifedashboard.data.db.StudyDao,
    private val timetable: app.lifedashboard.data.repository.TimetableRepository,
) {
    private val examWords = Regex("\\b(klausur|klassenarbeit|lek|lernerfolgskontrolle|test|kontrolle)\\b", RegexOption.IGNORE_CASE)

    /** Klausuren in Abos erkennen und automatisch anlegen (Lernplan startet dann von selbst). */
    private suspend fun detectExams(source: IcalSourceEntity, items: List<app.lifedashboard.planner.IcsEvent>) {
        val subjects = timetable.subjects.first()
        val existing = studyDao.observeExams().first()
        items.filter { examWords.containsMatchIn(it.summary) && !it.startDate.isBefore(java.time.LocalDate.now()) }.forEach { item ->
            val marker = "ical:${source.id}:${item.uid}"
            if (existing.any { it.note == marker }) return@forEach
            val intent = app.lifedashboard.planner.QuickParser.parse(
                item.summary, java.time.LocalDate.now(), subjects.map { app.lifedashboard.planner.QuickSubject(it.code, it.name) },
            )
            val code = (intent as? app.lifedashboard.planner.QuickIntent.Exam)?.subjectCode
                ?: (intent as? app.lifedashboard.planner.QuickIntent.Homework)?.subjectCode
            val subject = subjects.firstOrNull { s -> code != null && (s.code.equals(code, true) || s.code.substringBefore('-').equals(code, true)) }
                ?: subjects.firstOrNull { item.summary.contains(it.name, true) || item.summary.contains(it.code.substringBefore('-'), true) }
                ?: return@forEach
            study.saveExam(
                app.lifedashboard.data.db.ExamEntity(
                    subjectId = subject.id, dateEpochDay = item.startDate.toEpochDay(), title = item.summary.take(60),
                    kind = app.lifedashboard.data.db.ExamKind.KLAUSUR, note = marker,
                ),
                null,
            )
        }
    }
    val all: Flow<List<IcalSourceEntity>> = sources.observeAll()

    suspend fun save(source: IcalSourceEntity): Long = sources.upsert(source)

    suspend fun delete(source: IcalSourceEntity) {
        // Importierte, noch offene Einträge dieses Abos mit entfernen
        val prefix = prefixOf(source.id)
        tasks.byExternalPrefix(prefix).filter { !it.done }.forEach { tasks.delete(it.id) }
        events.byExternalPrefix(prefix).forEach { events.delete(it.id) }
        sources.delete(source.id)
    }

    /** Synchronisiert alle Abos. Gibt die Anzahl fehlerhafter Abos zurück. */
    suspend fun syncAll(): Int {
        var failures = 0
        sources.all().forEach { if (!sync(it)) failures++ }
        return failures
    }

    suspend fun sync(source: IcalSourceEntity): Boolean {
        val now = System.currentTimeMillis()
        return try {
            val text = download(source.url)
            if (!text.contains("BEGIN:VCALENDAR", ignoreCase = true)) {
                error("Der Link liefert keinen Kalender. Ist es der richtige Export-Link?")
            }
            val parsed = IcsParser.parse(text)
            val from = LocalDate.now().minusDays(14)
            val to = LocalDate.now().plusDays(180)
            val relevant = parsed.filter { !it.startDate.isBefore(from) && !it.startDate.isAfter(to) }
            if (source.importAsTasks) importTasks(source, relevant) else importEvents(source, relevant)
            detectExams(source, relevant)
            sources.setResult(source.id, now, null)
            true
        } catch (e: Exception) {
            sources.setResult(source.id, now, e.message ?: e.javaClass.simpleName)
            false
        }
    }

    private suspend fun importTasks(source: IcalSourceEntity, items: List<app.lifedashboard.planner.IcsEvent>) {
        val prefix = prefixOf(source.id)
        val existing = tasks.byExternalPrefix(prefix).associateBy { it.externalUid }
        val seen = mutableSetOf<String>()
        items.forEach { item ->
            val uid = prefix + item.uid
            seen += uid
            val old = existing[uid]
            tasks.upsert(
                TaskEntity(
                    id = old?.id ?: 0,
                    title = item.summary.removeSuffix(" ist fällig").removeSuffix(" ist fällig."),
                    area = LifeArea.SCHOOL,
                    kind = TaskKind.TODO,
                    subjectId = old?.subjectId,
                    dueEpochDay = item.startDate.toEpochDay(),
                    estimatedMinutes = old?.estimatedMinutes ?: 45,
                    done = old?.done ?: false,
                    doneAtMillis = old?.doneAtMillis,
                    note = listOfNotNull("Aus: ${source.name}", item.description?.take(500)).joinToString("\n"),
                    createdAtMillis = old?.createdAtMillis ?: System.currentTimeMillis(),
                    externalUid = uid,
                ),
            )
        }
        // Im Abo gelöschte, noch offene Einträge entfernen
        existing.values.filter { it.externalUid !in seen && !it.done }.forEach { tasks.delete(it.id) }
    }

    private suspend fun importEvents(source: IcalSourceEntity, items: List<app.lifedashboard.planner.IcsEvent>) {
        val prefix = prefixOf(source.id)
        val existing = events.byExternalPrefix(prefix).associateBy { it.externalUid }
        val seen = mutableSetOf<String>()
        items.forEach { item ->
            val uid = prefix + item.uid
            seen += uid
            val start = item.startTime?.let { it.hour * 60 + it.minute }
            val end = item.endTime?.let { it.hour * 60 + it.minute }
            events.upsert(
                EventEntity(
                    id = existing[uid]?.id ?: 0,
                    title = item.summary,
                    dateEpochDay = item.startDate.toEpochDay(),
                    startMinute = start,
                    endMinute = end?.coerceAtLeast(start ?: 0),
                    area = LifeArea.SCHOOL,
                    location = null,
                    note = listOfNotNull("Aus: ${source.name}", item.description?.take(500)).joinToString("\n"),
                    externalUid = uid,
                ),
            )
        }
        existing.values.filter { it.externalUid !in seen }.forEach { events.delete(it.id) }
    }

    private suspend fun download(url: String): String = withContext(Dispatchers.IO) {
        val fixed = url.trim().replaceFirst("webcal://", "https://")
        val connection = URL(fixed).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "LifeDashboard-Android")
            val code = connection.responseCode
            if (code !in 200..299) error("Server antwortet mit Fehler $code")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun prefixOf(sourceId: Long) = "ical$sourceId:"
}
