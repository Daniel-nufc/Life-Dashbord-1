package app.lifedashboard.data.planning

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import app.lifedashboard.data.db.AppDatabase
import app.lifedashboard.data.db.ExamEntity
import app.lifedashboard.data.db.ExamKind
import app.lifedashboard.data.db.PlanStatus
import app.lifedashboard.data.repository.StudyRepository
import app.lifedashboard.data.repository.TrainingRepository
import app.lifedashboard.data.seed.SeedDataInitializer
import app.lifedashboard.planner.DemandType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Planung mit ECHTER Datenbank, echten Repositories und Startdaten (Stundenplan, Floorball, Ferien …).
 * Deckt die Grenzfälle aus der Gesamtprüfung ab.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class PlanningIntegrationTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var seed: SeedDataInitializer
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var study: StudyRepository
    @Inject lateinit var engine: PlanningEngine
    @Inject lateinit var training: TrainingRepository
    @Inject lateinit var agenda: app.lifedashboard.data.repository.AgendaRepository

    private val today = LocalDate.now()
    private val tomorrow = today.plusDays(1)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext<Context>())
        hilt.inject()
    }

    private fun blocks(from: LocalDate, days: Int) = runBlocking {
        db.planDao().between(from.toEpochDay(), from.plusDays(days.toLong() - 1).toEpochDay())
    }

    /** Lern-Schlüssel: study:<tag>:<examId>:<topicId>:<l|r> */
    private fun isStudyFor(key: String, examId: Long) = key.startsWith("study:") && key.split(":")[2] == examId.toString()

    private suspend fun subject(name: String) = db.timetableDao().observeSubjects().first().first { it.name.contains(name, true) || it.code.contains(name, true) }

    @Test
    fun `leere Datenbank - Planung stuerzt nicht ab`() = runBlocking {
        engine.planDays(today, 14)
        // ohne Stundenplan/Klausuren: nur Sport-Zeitfenster (Standard 3/Woche) – sonst nichts Erfundenes
        assertTrue(blocks(today, 14).all { it.type == DemandType.SPORT || it.type == DemandType.ETSY || it.type == DemandType.TASK })
    }

    @Test
    fun `Klausur ohne Themen bekommt trotzdem Lernzeit vor dem Termin`() = runBlocking {
        seed.seedIfNeeded()
        val bio = subject("Bio")
        val examDay = today.plusDays(4)
        val id = study.saveExam(ExamEntity(subjectId = bio.id, dateEpochDay = examDay.toEpochDay(), title = "", kind = ExamKind.TEST, note = null), emptyList())
        engine.planDays(today, 7)
        val studyBlocks = blocks(today, 7).filter { it.type == DemandType.STUDY && isStudyFor(it.demandKey, id) }
        assertTrue("Lernblöcke für den Test erwartet, gefunden: ${blocks(today, 7).map { it.demandKey }}", studyBlocks.isNotEmpty())
        assertTrue("nie am oder nach dem Klausurtag", studyBlocks.all { it.dateEpochDay < examDay.toEpochDay() })
        // Keine Themen automatisch erfunden
        assertTrue(study.overview(today).first().exams.first { it.exam.id == id }.topics.isEmpty())
    }

    @Test
    fun `eigene Themen werden genau so uebernommen`() = runBlocking {
        seed.seedIfNeeded()
        val math = subject("Mathe")
        val id = study.saveExam(
            ExamEntity(subjectId = math.id, dateEpochDay = today.plusDays(10).toEpochDay(), title = "Analysis", kind = ExamKind.KLAUSUR, note = null),
            listOf("Ableitungen", "  Kurvendiskussion ", ""),
        )
        val topics = study.overview(today).first().exams.first { it.exam.id == id }.topics.map { it.title }
        assertEquals(listOf("Ableitungen", "Kurvendiskussion"), topics)
    }

    @Test
    fun `Klausur morgen - heute oder morgen frueh wird noch gelernt, geloeschte Klausur verschwindet aus dem Plan`() = runBlocking {
        seed.seedIfNeeded()
        val math = subject("Mathe")
        val examDay = today.plusDays(2)
        val id = study.saveExam(ExamEntity(subjectId = math.id, dateEpochDay = examDay.toEpochDay(), title = "", kind = ExamKind.KLAUSUR, note = null), listOf("Integrale"))
        engine.planDays(tomorrow, 1)
        // Vortag = morgen: Lernen bzw. Wiederholung muss dort stehen
        assertTrue(blocks(tomorrow, 1).any { (it.type == DemandType.STUDY || it.type == DemandType.REVIEW) && isStudyFor(it.demandKey, id) })

        study.deleteExam(id)
        engine.planDays(tomorrow, 1)
        assertTrue(blocks(tomorrow, 1).none { isStudyFor(it.demandKey, id) && it.status == PlanStatus.PROPOSED })
    }

    @Test
    fun `Sport nur als Zeitfenster und nie an Floorball-Tagen`() = runBlocking {
        seed.seedIfNeeded()
        engine.planDays(tomorrow, 13)
        val sport = blocks(tomorrow, 13).filter { it.type == DemandType.SPORT }
        assertTrue("Sport-Zeitfenster erwartet", sport.isNotEmpty())
        sport.forEach { b ->
            assertTrue(b.demandKey, b.demandKey.endsWith(":WINDOW"))
            val d = LocalDate.ofEpochDay(b.dateEpochDay).dayOfWeek
            assertTrue("kein Fenster an Floorball-Tagen ($d)", d != DayOfWeek.MONDAY && d != DayOfWeek.WEDNESDAY)
            assertTrue(b.title.contains("du entscheidest"))
        }
        // Erledigt per Tagesplan → Zeitfenster gilt als genutzt
        val first = sport.first()
        engine.done(first)
        assertTrue(training.sessionsFor(LocalDate.ofEpochDay(first.dateEpochDay)).single().done)
    }

    @Test
    fun `Ferien und Wochenende - Tag startet spaeter, Planung laeuft ueber Jahreswechsel`() = runBlocking {
        seed.seedIfNeeded()
        val christmas = LocalDate.of(2026, 12, 28) // Weihnachtsferien, Montag
        if (christmas.isBefore(today)) return@runBlocking
        engine.planDays(christmas, 7) // 28.12.2026 – 3.1.2027
        val earliest = blocks(christmas, 7).minOfOrNull { it.startMinute }
        if (earliest != null) assertTrue("in den Ferien nicht vor 9:30 planen ($earliest)", earliest >= 9 * 60 + 30)
    }

    @Test
    fun `voller Tag - Tageslimit wird eingehalten`() = runBlocking {
        seed.seedIfNeeded()
        val math = subject("Mathe")
        val bio = subject("Bio")
        listOf(math, bio).forEach { s ->
            study.saveExam(
                ExamEntity(subjectId = s.id, dateEpochDay = today.plusDays(3).toEpochDay(), title = "", kind = ExamKind.KLAUSUR, note = null),
                (1..10).map { "Thema $it" },
            )
        }
        engine.planDays(tomorrow, 1)
        val school = blocks(tomorrow, 1).filter { it.type == DemandType.STUDY || it.type == DemandType.HOMEWORK || it.type == DemandType.REVIEW }
            .sumOf { it.endMinute - it.startMinute }
        // Schul-Pensum: höchstens 2 Std (Sonntag mit Klausur in 1–2 Tagen: 1 Std)
        assertTrue("$school Min Schule geplant – Limit 120", school <= 120)
    }

    @Test
    fun `Sonntag bleibt frei, wenn keine Klausur ansteht`() = runBlocking {
        seed.seedIfNeeded()
        val sunday = today.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.SUNDAY))
        db.taskDao().upsert(
            app.lifedashboard.data.db.TaskEntity(title = "Aufsatz", area = app.lifedashboard.data.db.LifeArea.SCHOOL, kind = app.lifedashboard.data.db.TaskKind.TODO,
                subjectId = null, dueEpochDay = sunday.plusDays(3).toEpochDay(), estimatedMinutes = 120, done = false, doneAtMillis = null, note = null,
                createdAtMillis = System.currentTimeMillis()),
        )
        engine.planDays(sunday, 1)
        assertTrue("am Sonntag nichts geplant: ${blocks(sunday, 1).map { it.title }}", blocks(sunday, 1).isEmpty())
    }

    @Test
    fun `nach der Schule eine Stunde Pause`() = runBlocking {
        seed.seedIfNeeded()
        val day = (1L..10L).map { today.plusDays(it) }.first { it.dayOfWeek == DayOfWeek.TUESDAY }
        db.taskDao().upsert(
            app.lifedashboard.data.db.TaskEntity(title = "Lesen", area = app.lifedashboard.data.db.LifeArea.SCHOOL, kind = app.lifedashboard.data.db.TaskKind.TODO,
                subjectId = null, dueEpochDay = day.toEpochDay(), estimatedMinutes = 50, done = false, doneAtMillis = null, note = null,
                createdAtMillis = System.currentTimeMillis()),
        )
        engine.planDays(day, 1)
        val agendaDay = agenda.observe(day, 1).first().days.first()
        val schoolEnd = agendaDay.lessons.mapNotNull { it.timeRange?.end }.maxOrNull() ?: return@runBlocking
        val home = schoolEnd.hour * 60 + schoolEnd.minute + agendaDay.settings.schoolCommuteMinutes
        val afterSchool = blocks(day, 1).filter { it.startMinute >= schoolEnd.hour * 60 + schoolEnd.minute }
        assertTrue("erst 60 Min nach dem Heimweg planen: ${afterSchool.map { it.startMinute }}", afterSchool.all { it.startMinute >= home + 60 })
    }
}
