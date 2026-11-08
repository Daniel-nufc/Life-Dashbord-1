package app.lifedashboard.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Holt alle 6 Stunden die Kalender-Abos im Hintergrund (nur mit Internet). */
@HiltWorker
class IcalSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: IcalSyncRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = if (repository.syncAll() == 0) Result.success() else Result.retry()

    companion object {
        private const val NAME = "ical-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<IcalSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
