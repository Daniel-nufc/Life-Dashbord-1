package app.lifedashboard.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import app.lifedashboard.core.di.StorageModule
import app.lifedashboard.data.db.AppDatabase
import app.lifedashboard.data.db.ExamEntity
import app.lifedashboard.data.db.ExamKind
import app.lifedashboard.data.db.GradeEntity
import app.lifedashboard.data.db.GradeKind
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.TaskEntity
import app.lifedashboard.data.db.TaskKind
import app.lifedashboard.data.db.TransactionEntity
import app.lifedashboard.data.db.WorkoutEntity
import app.lifedashboard.data.db.WorkoutType
import app.lifedashboard.data.repository.StudyRepository
import app.lifedashboard.data.seed.SeedDataInitializer
import app.lifedashboard.testing.HiltTestActivity
import app.lifedashboard.ui.navigation.LifeDashboardRoot
import app.lifedashboard.ui.theme.LifeDashboardTheme
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog

/** Im Test: unverschlüsselte In-Memory-Datenbank statt SQLCipher (gleiches Schema). */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [StorageModule::class])
object TestStorageModule {
    @Provides
    @Singleton
    fun db(@ApplicationContext context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).addCallback(AppDatabase.SYNC_CALLBACK).allowMainThreadQueries().build()

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(context.filesDir, "test-${System.nanoTime()}.preferences_pb") }
}

/**
 * Rendert die ECHTE App (alle Bildschirme, echte ViewModels & Datenbank mit Startdaten) auf
 * Handy- und Tablet-Größe und speichert Screenshots unter app/build/screenshots.
 * Schlägt fehl, wenn ein Bildschirm abstürzt oder ein erwarteter Inhalt fehlt.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = HiltTestApplication::class)
class ScreenshotTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()

    @Inject lateinit var seed: SeedDataInitializer
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var study: StudyRepository
    @Inject lateinit var engine: app.lifedashboard.data.planning.PlanningEngine

    private val outDir = File(System.getProperty("screenshots.dir") ?: "build/screenshots")

    @Before
    fun setUp() {
        ShadowLog.stream = null
        val app = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        hilt.inject()
        // Benachrichtigungen erlaubt, Akku-Ausnahme schon gesetzt → keine Dialoge über den Bildschirmen
        shadowOf(app as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(app.getSystemService(android.os.PowerManager::class.java)).setIgnoringBatteryOptimizations(app.packageName, true)
        runBlocking {
            seed.seedIfNeeded()
            sampleData()
            // wie nach dem ersten Öffnen: Vorschläge für zwei Wochen liegen bereit
            engine.planDays(LocalDate.now(), 14)
        }
    }

    /** Typische Daten, damit die Bildschirme nicht leer sind. */
    private suspend fun sampleData() {
        val today = LocalDate.now()
        val subjects = db.timetableDao().observeSubjects().first()
        val math = subjects.first { it.code.startsWith("MA", true) || it.name.contains("Mathe", true) }
        val bio = subjects.firstOrNull { it.name.contains("Bio", true) } ?: subjects[1]
        study.saveExam(ExamEntity(subjectId = math.id, dateEpochDay = today.plusDays(9).toEpochDay(), title = "Analysis", kind = ExamKind.KLAUSUR, note = null),
            listOf("Ableitungen", "Kurvendiskussion", "Extremwerte"))
        // Test ohne Themen: muss trotzdem gehen (Themen frei, keine Pflicht)
        study.saveExam(ExamEntity(subjectId = bio.id, dateEpochDay = today.plusDays(3).toEpochDay(), title = "", kind = ExamKind.TEST, note = null), emptyList())
        db.studyDao().upsertGrade(GradeEntity(subjectId = math.id, points = 9, kind = GradeKind.KLAUSUR, weight = 1.0, dateEpochDay = today.minusDays(20).toEpochDay(), note = null))
        db.studyDao().upsertGrade(GradeEntity(subjectId = bio.id, points = 12, kind = GradeKind.MUENDLICH, weight = 1.0, dateEpochDay = today.minusDays(10).toEpochDay(), note = null))
        db.taskDao().upsert(TaskEntity(title = "S. 45 Nr. 3–5", area = LifeArea.SCHOOL, kind = TaskKind.HOMEWORK, subjectId = math.id, dueEpochDay = today.plusDays(1).toEpochDay(),
            estimatedMinutes = 30, done = false, doneAtMillis = null, note = null, createdAtMillis = System.currentTimeMillis()))
        db.taskDao().upsert(TaskEntity(title = "Sporttasche packen", area = LifeArea.SPORT, kind = TaskKind.TODO, subjectId = null, dueEpochDay = today.toEpochDay(),
            estimatedMinutes = 10, done = false, doneAtMillis = null, note = null, createdAtMillis = System.currentTimeMillis()))
        db.sportDao().upsertWorkout(WorkoutEntity(dateEpochDay = today.minusDays(2).toEpochDay(), type = WorkoutType.RUN, durationMinutes = 34, distanceMeters = 5200, note = null))
        db.sportDao().upsertWorkout(WorkoutEntity(dateEpochDay = today.minusDays(5).toEpochDay(), type = WorkoutType.FLOORBALL, durationMinutes = 90, distanceMeters = null, note = null))
        val dkb = db.financeDao().accounts().first()
        // FamilyWall verbunden: nur Daniels Termine, einer davon heute
        db.icalSourceDao().upsert(app.lifedashboard.data.db.IcalSourceEntity(
            name = "FamilyWall", url = "https://api.familywall.com/ical/beispiel", importAsTasks = false,
            lastSyncMillis = System.currentTimeMillis(), lastError = null, area = LifeArea.PRIVATE, filterName = "Daniel",
            lastNote = "14 von 52 Terminen sind für dich. Personen im Kalender: Daniel, Mama, Papa",
        ))
        db.eventDao().upsert(app.lifedashboard.data.db.EventEntity(
            title = "Zahnarzt", dateEpochDay = today.toEpochDay(), startMinute = 16 * 60 + 30, endMinute = 17 * 60, area = LifeArea.PRIVATE,
            location = "Praxis Dr. Weber", note = "Aus: FamilyWall", externalUid = "familywall:zahnarzt",
        ))
        db.financeDao().upsertTransaction(TransactionEntity(accountId = dkb.id, dateEpochDay = today.minusDays(3).toEpochDay(), amountCents = -899, counterparty = "Rewe",
            description = "Snacks", category = "Essen", importHash = null, recurringId = null))
    }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun handy() = tour("handy", dark = false)

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun handyDunkel() = tour("handy-dunkel", dark = true)

    @Test
    @Config(qualifiers = "w1138dp-h712dp-land-xhdpi")
    fun tablet() = tour("tablet", dark = false)

    @Test
    @Config(qualifiers = "w1138dp-h712dp-land-xhdpi")
    fun tabletDunkel() = tour("tablet-dunkel", dark = true)

    private fun tour(device: String, dark: Boolean) {
        compose.setContent { LifeDashboardTheme(darkTheme = dark) { LifeDashboardRoot() } }
        settle()
        assertText("Fortschritt")
        shot(device, "01_heute")
        // Fokus-Modus aus „Jetzt starten“
        if (compose.onAllNodes(hasText("Jetzt starten") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodes(hasText("Jetzt starten") and hasClickAction()).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            settle()
            check(compose.onAllNodes(hasText("FOKUS")).fetchSemanticsNodes().isNotEmpty()) { "Fokus-Modus öffnet nicht" }
            shotDialog(device, "01b_fokus")
            compose.onAllNodes(hasText("Fertig") and hasClickAction()).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            settle()
            check(compose.onAllNodes(hasText("GESCHAFFT")).fetchSemanticsNodes().isNotEmpty()) { "Abschluss fehlt" }
            shotDialog(device, "01c_geschafft")
            compose.onAllNodes(hasText("Weiter") and hasClickAction()).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            settle()
        }
        scrollDown(); shot(device, "02_heute_unten")

        tab("Kalender"); shot(device, "03_kalender")
        tab("Aufgaben"); assertText("Bis morgen fällig"); shot(device, "04_aufgaben"); scrollDown(); shot(device, "04b_aufgaben_unten")
        tab("Lernen"); shot(device, "05_lernen_klausuren"); assertText("Analysis"); assertText("Plan passt"); shot(device, "05b_lernen_mathe")

        tab("Fitness"); shot(device, "07_fitness"); assertText("Ironman-Weg"); shot(device, "07b_fitness_ironman")
        assertText("Strava"); shot(device, "07c_fitness_strava")
        assertText("Sport-Zeitfenster diese Woche"); shot(device, "08_fitness_zeitfenster")
        scrollDown(); shot(device, "08b_fitness_unten")
        tab("Finanzen"); shot(device, "09_finanzen")

        tab("Mehr"); shot(device, "06_mehr")
        open("Fortschritt & Abzeichen"); assertText("Abzeichen"); shot(device, "13_fortschritt"); scrollDown(); shot(device, "13b_fortschritt_abzeichen"); back()
        open("Etsy-Shop"); assertText("Motiv-Pipeline"); shot(device, "10_etsy"); back()
        open("Ziele & Gewohnheiten"); shot(device, "11_ziele"); back()
        open("Handy ↔ Tablet"); assertText("Abgleich-Passwort"); shot(device, "12_abgleich"); back()
        open("Kalender & Abos"); assertText("Nur Termine für Daniel"); shot(device, "14_kalender_abos"); back()
    }

    // ------------------------------------------------------------------ Helfer

    private fun settle() {
        repeat(12) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeBy(250)
            Thread.sleep(60)
        }
        compose.waitForIdle()
    }

    /** Die Liste des Bildschirms (größte scrollbare Fläche) – nicht Dock oder Seitenleiste. */
    private fun mainList(): androidx.compose.ui.test.SemanticsNodeInteraction {
        val nodes = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
        val index = nodes.indices.maxByOrNull { nodes[it].size.width.toLong() * nodes[it].size.height } ?: 0
        return compose.onAllNodes(hasScrollAction())[index]
    }

    private fun tab(label: String) {
        compose.onAllNodes((hasText(label) or androidx.compose.ui.test.hasContentDescription(label)) and hasClickAction(), useUnmergedTree = false)
            .onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        settle()
    }

    private fun open(label: String) {
        // Eintrag in der Liste suchen (scrollt bei Bedarf) und öffnen
        val target = hasText(label) and hasClickAction()
        if (compose.onAllNodes(target).fetchSemanticsNodes().isEmpty()) {
            runCatching { mainList().performScrollToIndex(0) }
            settle()
        }
        if (compose.onAllNodes(target).fetchSemanticsNodes().isEmpty()) {
            mainList().performScrollToNode(hasText(label))
            settle()
        }
        compose.onAllNodes(target).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        settle()
    }

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun scrollDown() {
        if (compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()) {
            mainList().performTouchInput { swipeUp() }
            settle()
        }
    }

    private fun assertText(text: String) {
        fun present() = compose.onAllNodes(hasText(text, substring = true, ignoreCase = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        // gezielt zur Karte rollen (Wischen kann sie bei langen Seiten überspringen)
        if (!present()) {
            runCatching { mainList().performScrollToNode(hasText(text, substring = true, ignoreCase = true)) }
            settle()
        }
        var tries = 0
        while (!present() && tries++ < 6) scrollDown()
        check(present()) {
            val visible = compose.onAllNodes(androidx.compose.ui.test.isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
                .flatMap { root -> allTexts(root) }.joinToString(" | ").take(1500)
            "Erwarteter Text fehlt: $text – sichtbar: $visible"
        }
    }

    private fun allTexts(node: androidx.compose.ui.semantics.SemanticsNode): List<String> =
        node.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.map { it.text } + node.children.flatMap { allTexts(it) }

    /** Dialoge liegen in einem eigenen Fenster – das ganze Bild des Dialog-Fensters speichern. */
    private fun shotDialog(device: String, name: String) {
        compose.waitForIdle()
        val roots: List<android.view.View> = runCatching {
            val wmgClass = Class.forName("android.view.WindowManagerGlobal")
            val wmg = wmgClass.getMethod("getInstance").invoke(null)
            val field = wmgClass.getDeclaredField("mRoots").apply { isAccessible = true }
            (field.get(wmg) as List<*>).mapNotNull { root -> root?.javaClass?.getMethod("getView")?.invoke(root) as? android.view.View }
        }.getOrDefault(emptyList())
        val view = roots.lastOrNull() ?: return shot(device, name)
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        val dir = File(outDir, device).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shot(device: String, name: String) {
        compose.waitForIdle()
        var bitmap: Bitmap? = null
        compose.activityRule.scenario.onActivity { activity ->
            val view = activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        }
        val dir = File(outDir, device).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
