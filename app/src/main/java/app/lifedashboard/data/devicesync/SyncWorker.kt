package app.lifedashboard.data.devicesync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/** Gleicht im Hintergrund über die Cloud-Datei ab: alle 30 Min, beim Öffnen und beim Verlassen der App. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: DeviceSyncRepository,
    private val engine: app.lifedashboard.data.planning.PlanningEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val s = repo.settings.first()
        if (s.fileUri == null || !s.hasPassword) return Result.success()
        val msg = repo.syncFile()
        // Neue Klausuren/Aufgaben vom anderen Gerät → Vorschläge neu berechnen
        if (msg.contains("neu") || msg.contains("geändert") || msg.contains("gelöscht")) {
            runCatching { engine.planDays(LocalDate.now(), 14) }
            app.lifedashboard.widget.updateWidgets(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val NAME = "device-sync"
        private const val NOW = "device-sync-now"
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).setConstraints(network).build(),
            )
        }

        /** Sofort abgleichen (z. B. App geöffnet oder verlassen). */
        fun runNow(context: Context, delaySeconds: Long = 0) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).setInitialDelay(delaySeconds, TimeUnit.SECONDS).build(),
            )
        }
    }
}
