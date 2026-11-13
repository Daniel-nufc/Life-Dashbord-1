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

/** Holt alle 2 Stunden die Kalender-Abos im Hintergrund (nur mit Internet), damit Familientermine schnell da sind. */
@HiltWorker
class IcalSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: IcalSyncRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = if (repository.syncAll() == 0) Result.success() else Result.retry()

    companion object {
        const val NAME = "ical-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<IcalSyncWorker>(2, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
