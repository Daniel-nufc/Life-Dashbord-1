package app.lifedashboard.data.devicesync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.lifedashboard.core.security.PasswordCrypto
import app.lifedashboard.data.db.AppDatabase
import app.lifedashboard.data.db.ExamEntity
import app.lifedashboard.data.db.ExamKind
import app.lifedashboard.data.db.HabitEntity
import app.lifedashboard.data.db.HabitLogEntity
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.TaskEntity
import app.lifedashboard.data.db.TaskKind
import app.lifedashboard.data.db.TopicEntity
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Ein „Gerät“ im Test: eigene Datenbank + Abgleich-Logik. */
private class Device(val name: String, context: Context) : SyncPeer {
    val db: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .addCallback(AppDatabase.SYNC_CALLBACK).allowMainThreadQueries().build()
    val engine = SyncEngine(db) { name }
    var password = "geheim123"

    override suspend fun encodedSnapshot(): ByteArray = PasswordCrypto.encrypt(engine.snapshot(name).toString().toByteArray(), password)
    override suspend fun mergeEncoded(bytes: ByteArray): Pair<MergeResult, String> {
        val json = JSONObject(String(PasswordCrypto.decrypt(bytes, password)))
        return engine.merge(json) to json.optString("deviceName")
    }

    /** Startdaten wie beim ersten Öffnen der App – auf jedem Gerät separat angelegt. */
    suspend fun seed() {
        db.timetableDao().insertSubjects(
            listOf(SubjectEntity(code = "MA-B", name = "Mathematik", colorArgb = 1), SubjectEntity(code = "BIO", name = "Biologie", colorArgb = 2)),
        )
        db.goalDao().upsertHabit(HabitEntity(title = "Lesen", emoji = "📖", area = LifeArea.PRIVATE, active = true, sortOrder = 1))
    }

    suspend fun subjects() = db.timetableDao().observeSubjects().first()
    suspend fun tasks() = db.taskDao().observeAll().first()
    suspend fun exams() = db.studyDao().observeExams().first()
    suspend fun topics() = db.studyDao().observeTopics().first()
}

/** Abgleich Handy ↔ Tablet: zusammenführen, nie doppelt, Löschen, Konflikte, Verschlüsselung, WLAN. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class DeviceSyncTest {
    private lateinit var phone: Device
    private lateinit var tablet: Device

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        phone = Device("handy", ctx)
        tablet = Device("tablet", ctx)
    }

    @After
    fun tearDown() {
        phone.db.close()
        tablet.db.close()
    }

    /** Ein vollständiger Abgleich in beide Richtungen (wie über die Cloud-Datei). */
    private suspend fun sync(a: Device, b: Device) {
        b.mergeEncoded(a.encodedSnapshot())
        a.mergeEncoded(b.encodedSnapshot())
    }

    private fun task(title: String, subjectId: Long? = null) = TaskEntity(
        title = title, area = LifeArea.SCHOOL, kind = TaskKind.TODO, subjectId = subjectId, dueEpochDay = 20_400,
        estimatedMinutes = 30, done = false, doneAtMillis = null, note = null, createdAtMillis = 1,
    )

    @Test
    fun `Startdaten auf beiden Geraeten werden nicht doppelt`() = runBlocking {
        phone.seed()
        tablet.seed()
        sync(phone, tablet)
        assertEquals(2, phone.subjects().size)
        assertEquals(2, tablet.subjects().size)
        assertEquals(1, tablet.db.goalDao().observeHabits().first().size)
    }

    @Test
    fun `Eintrag vom Tablet erscheint auf dem Handy - und umgekehrt`() = runBlocking {
        phone.seed(); tablet.seed()
        tablet.db.taskDao().upsert(task("Referat vorbereiten"))
        phone.db.taskDao().upsert(task("Sporttasche packen"))
        sync(tablet, phone)
        assertEquals(setOf("Referat vorbereiten", "Sporttasche packen"), phone.tasks().map { it.title }.toSet())
        assertEquals(setOf("Referat vorbereiten", "Sporttasche packen"), tablet.tasks().map { it.title }.toSet())
        // Zweiter Abgleich ändert nichts mehr
        val again = phone.mergeEncoded(tablet.encodedSnapshot()).first
        assertEquals(0, again.changes)
    }

    @Test
    fun `Klausur mit Themen - Verweise werden richtig uebersetzt`() = runBlocking {
        phone.seed(); tablet.seed()
        // Tablet bekommt vorher andere interne Nummern (zusätzliches Fach)
        tablet.db.timetableDao().insertSubjects(listOf(SubjectEntity(code = "DS", name = "Darstellendes Spiel", colorArgb = 3)))
        val bio = tablet.subjects().first { it.code == "BIO" }
        val examId = tablet.db.studyDao().upsertExam(ExamEntity(subjectId = bio.id, dateEpochDay = 20_410, title = "Genetik", kind = ExamKind.TEST, note = null))
        tablet.db.studyDao().upsertTopic(TopicEntity(examId = examId, title = "Mendel", effortMinutes = 60, progress = 0, intervalDays = 0, nextReviewEpochDay = null, lastReviewedEpochDay = null))
        sync(tablet, phone)
        val exam = phone.exams().single()
        assertEquals("BIO", phone.subjects().first { it.id == exam.subjectId }.code)
        assertEquals("Mendel", phone.topics().single { it.examId == exam.id }.title)
    }

    @Test
    fun `Loeschen wird uebertragen`() = runBlocking {
        phone.seed(); tablet.seed()
        phone.db.taskDao().upsert(task("Alt"))
        sync(phone, tablet)
        val id = tablet.tasks().single().id
        Thread.sleep(5)
        tablet.db.taskDao().delete(id)
        sync(tablet, phone)
        assertTrue(phone.tasks().isEmpty())
        // und kommt nicht zurück
        sync(phone, tablet)
        assertTrue(tablet.tasks().isEmpty())
    }

    @Test
    fun `Konflikt - die neuere Aenderung gewinnt`() = runBlocking {
        phone.seed(); tablet.seed()
        phone.db.taskDao().upsert(task("Mathe HA"))
        sync(phone, tablet)
        val onPhone = phone.tasks().single()
        val onTablet = tablet.tasks().single()
        phone.db.taskDao().upsert(onPhone.copy(estimatedMinutes = 45))
        Thread.sleep(20)
        tablet.db.taskDao().upsert(onTablet.copy(estimatedMinutes = 90, done = true))
        sync(phone, tablet)
        assertEquals(90, phone.tasks().single().estimatedMinutes)
        assertTrue(phone.tasks().single().done)
        assertEquals(90, tablet.tasks().single().estimatedMinutes)
    }

    @Test
    fun `Gewohnheit am selben Tag auf beiden Geraeten abgehakt - nur einmal`() = runBlocking {
        phone.seed(); tablet.seed()
        val hp = phone.db.goalDao().observeHabits().first().single().id
        val ht = tablet.db.goalDao().observeHabits().first().single().id
        phone.db.goalDao().insertHabitLog(HabitLogEntity(habitId = hp, dateEpochDay = 20_400))
        tablet.db.goalDao().insertHabitLog(HabitLogEntity(habitId = ht, dateEpochDay = 20_400))
        sync(phone, tablet)
        val count = phone.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM habit_logs").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(1, count)
    }

    @Test(expected = PasswordCrypto.WrongPasswordException::class)
    fun `falsches Passwort - nichts wird eingelesen`(): Unit = runBlocking {
        phone.seed()
        val data = phone.encodedSnapshot()
        tablet.password = "anderes-passwort"
        tablet.mergeEncoded(data)
    }

    @Test
    fun `Datei-Rahmen - leere Datei und Inhalt`() {
        assertEquals(null, SyncFrame.read(ByteArray(0).inputStream()))
        val payload = "hallo".toByteArray()
        assertEquals("hallo", String(SyncFrame.read(SyncFrame.toBytes(payload).inputStream())!!))
    }

    @Test
    fun `WLAN-Austausch ueber echte Verbindung`() = runBlocking {
        phone.seed(); tablet.seed()
        phone.db.taskDao().upsert(task("Vom Handy"))
        tablet.db.taskDao().upsert(task("Vom Tablet"))
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val serving = async(kotlinx.coroutines.Dispatchers.IO) { LanProtocol.serve(server.accept(), phone) }
            val (result, from) = LanProtocol.request(InetAddress.getLoopbackAddress(), server.localPort, tablet)
            serving.await()
            assertEquals("handy", from)
            assertTrue(result.inserted >= 1)
        }
        assertEquals(setOf("Vom Handy", "Vom Tablet"), phone.tasks().map { it.title }.toSet())
        assertEquals(setOf("Vom Handy", "Vom Tablet"), tablet.tasks().map { it.title }.toSet())
    }

    @Test
    fun `sehr altes Format wird erkannt - andere neuere Versionen gleichen trotzdem ab`() = runBlocking {
        val json = phone.engine.snapshot().put("dbVersion", 11)
        try {
            tablet.engine.merge(json)
            error("Versionsfehler erwartet")
        } catch (e: SyncVersionException) {
            assertEquals(11, e.remote)
        }
        // Tablet noch auf der vorigen Version: Aufgaben kommen trotzdem an
        phone.db.taskDao().upsert(task("Vokabeln"))
        tablet.engine.merge(phone.engine.snapshot().put("dbVersion", 14))
        assertEquals(listOf("Vokabeln"), tablet.tasks().map { it.title })
    }

    private fun block(day: Long, key: String, start: Int, status: app.lifedashboard.data.db.PlanStatus) = app.lifedashboard.data.db.PlanBlockEntity(
        dateEpochDay = day, startMinute = start, endMinute = start + 50, type = app.lifedashboard.planner.DemandType.STUDY,
        demandKey = key, title = key, refId = null, status = status, createdAtMillis = 0,
    )

    @Test
    fun `Tagesplan - erledigte und bestaetigte Bloecke werden abgeglichen, Vorschlaege nicht`() = runBlocking {
        val day = 20_500L
        phone.db.planDao().insertAll(
            listOf(
                block(day, "study:a", 960, app.lifedashboard.data.db.PlanStatus.DONE),
                block(day, "study:b", 1020, app.lifedashboard.data.db.PlanStatus.ACCEPTED),
                block(day, "study:c", 1080, app.lifedashboard.data.db.PlanStatus.PROPOSED),
            ),
        )
        // Tablet hat denselben Block selbst bestätigt – zu einer anderen Zeit
        tablet.db.planDao().insertAll(listOf(block(day, "study:b", 1100, app.lifedashboard.data.db.PlanStatus.ACCEPTED)))
        sync(phone, tablet)
        val onTablet = tablet.db.planDao().forDay(day)
        assertEquals(listOf("study:a", "study:b"), onTablet.map { it.demandKey }.sorted()) // kein Doppel, kein Vorschlag
        assertEquals(app.lifedashboard.data.db.PlanStatus.DONE, onTablet.first { it.demandKey == "study:a" }.status)
        assertEquals(3, phone.db.planDao().forDay(day).size) // eigener Vorschlag bleibt auf dem Handy
    }

    /** Zustand direkt nach dem Update auf Version 21: alte Einträge ohne Kennung und ohne Zeitstempel. */
    private fun asIfMigrated(d: Device) {
        d.db.openHelper.writableDatabase.execSQL("UPDATE sync_meta SET gid = NULL, updatedAt = 0")
    }

    @Test
    fun `erster Abgleich - schon eingetragene Tablet-Daten bleiben erhalten und kommen aufs Handy`() = runBlocking {
        phone.seed(); tablet.seed()
        val bio = tablet.subjects().first { it.code == "BIO" }
        tablet.db.taskDao().upsert(task("Tablet: Referat", bio.id))
        tablet.db.taskDao().upsert(task("Tablet: Etsy-Motiv"))
        val examId = tablet.db.studyDao().upsertExam(ExamEntity(subjectId = bio.id, dateEpochDay = 20_420, title = "Ökologie", kind = ExamKind.KLAUSUR, note = null))
        tablet.db.studyDao().upsertTopic(TopicEntity(examId = examId, title = "Nahrungsnetze", effortMinutes = 60, progress = 40, intervalDays = 0, nextReviewEpochDay = null, lastReviewedEpochDay = null))
        phone.db.taskDao().upsert(task("Handy: Zahnarzt"))
        // Dasselbe Fach auf beiden Geräten unterschiedlich eingestellt (vor dem Update)
        phone.db.timetableDao().upsertSubject(phone.subjects().first { it.code == "BIO" }.copy(difficulty = 5))
        asIfMigrated(phone); asIfMigrated(tablet)

        val tabletBefore = tablet.tasks().map { it.title }.toSet()
        sync(tablet, phone)

        // Nichts vom Tablet verloren
        assertTrue(tablet.tasks().map { it.title }.toSet().containsAll(tabletBefore))
        assertEquals(setOf("Tablet: Referat", "Tablet: Etsy-Motiv", "Handy: Zahnarzt"), phone.tasks().map { it.title }.toSet())
        assertEquals(setOf("Tablet: Referat", "Tablet: Etsy-Motiv", "Handy: Zahnarzt"), tablet.tasks().map { it.title }.toSet())
        assertEquals(40, phone.topics().single().progress)
        assertEquals("BIO", phone.subjects().first { s -> s.id == phone.tasks().first { it.title == "Tablet: Referat" }.subjectId }.code)
        // Unklar, welche Einstellung neuer ist → jedes Gerät behält seine (nichts wird überschrieben)
        assertEquals(5, phone.subjects().first { it.code == "BIO" }.difficulty)
        assertEquals(3, tablet.subjects().first { it.code == "BIO" }.difficulty)
        assertEquals(2, phone.subjects().size)
    }
}
