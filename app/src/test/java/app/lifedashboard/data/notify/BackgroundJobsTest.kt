package app.lifedashboard.data.notify

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import app.lifedashboard.data.backup.BackupWorker
import app.lifedashboard.data.seed.SeedDataInitializer
import app.lifedashboard.data.sync.IcalSyncWorker
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Hintergrund-Jobs: laufen ohne Absturz (auch ohne Health Connect / Daten) und sind genau einmal geplant. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class BackgroundJobsTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var seed: SeedDataInitializer

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        hilt.inject()
    }

    private fun <W : ListenableWorker> run(worker: Class<W>): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder.from(context, worker).setWorkerFactory(workerFactory).build().startWork().get()
    }

    @Test
    fun `Erinnerungen laufen mit leerer Datenbank`() {
        assertEquals(ListenableWorker.Result.success(), run(ReminderWorker::class.java))
    }

    @Test
    fun `Erinnerungen laufen mit Startdaten`() {
        runBlocking { seed.seedIfNeeded() }
        assertEquals(ListenableWorker.Result.success(), run(ReminderWorker::class.java))
    }

    @Test
    fun `Kalender-Abo-Sync ohne Abos laeuft`() {
        val result = run(IcalSyncWorker::class.java)
        assertEquals(true, result is ListenableWorker.Result.Success || result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `Jobs sind genau einmal geplant - auch nach mehrfachem Start`() {
        repeat(3) {
            ReminderWorker.schedule(context)
            IcalSyncWorker.schedule(context)
            BackupWorker.schedule(context)
        }
        val wm = WorkManager.getInstance(context)
        listOf(ReminderWorker.NAME to TimeUnit.MINUTES.toMillis(30), BackupWorker.NAME to TimeUnit.DAYS.toMillis(7), IcalSyncWorker.NAME to TimeUnit.HOURS.toMillis(2)).forEach { (name, interval) ->
            val infos = wm.getWorkInfosForUniqueWork(name).get().filter { it.state != WorkInfo.State.CANCELLED }
            assertEquals(name, 1, infos.size)
            assertEquals(name, interval, infos.single().periodicityInfo?.repeatIntervalMillis)
        }
    }
}
