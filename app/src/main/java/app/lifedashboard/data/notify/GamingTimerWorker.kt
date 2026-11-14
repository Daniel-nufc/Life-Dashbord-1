package app.lifedashboard.data.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Zockzeit-Timer: Beim Einlösen läuft ein Timer – 5 Min vor Schluss ein Hinweis, am Ende „vorbei“.
 * Belohnung ja, Endlos-Zocken nein. Nichts wird gesperrt, nur erinnert.
 */
@HiltWorker
class GamingTimerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: Notifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val end = inputData.getBoolean(KEY_END, false)
        val stamp = inputData.getLong(KEY_STAMP, 0)
        if (end) {
            notifier.send("gaming:$stamp:end", Channel.PLAN, "Zockzeit vorbei", "Gut gespielt. Jetzt Handy weg – der nächste Block bringt neue Zockzeit.", ignoreLimit = true)
        } else {
            notifier.send("gaming:$stamp:warn", Channel.PLAN, "Noch 5 Minuten Zockzeit", "Runde zu Ende spielen und dann Schluss.", ignoreLimit = true)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "gaming-timer"
        private const val KEY_END = "end"
        private const val KEY_STAMP = "stamp"

        /** Startet den Timer für [minutes] Zockzeit (ersetzt einen laufenden). */
        fun start(context: Context, minutes: Int) {
            val stamp = System.currentTimeMillis()
            val wm = WorkManager.getInstance(context)
            val end = OneTimeWorkRequestBuilder<GamingTimerWorker>()
                .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_END to true, KEY_STAMP to stamp))
                .build()
            var chain = wm.beginUniqueWork(NAME, ExistingWorkPolicy.REPLACE, listOfNotNull(
                if (minutes > 5) OneTimeWorkRequestBuilder<GamingTimerWorker>()
                    .setInitialDelay(minutes - 5L, TimeUnit.MINUTES)
                    .setInputData(workDataOf(KEY_END to false, KEY_STAMP to stamp))
                    .build() else null,
            ).ifEmpty { listOf(end) })
            if (minutes > 5) chain = chain.then(
                OneTimeWorkRequestBuilder<GamingTimerWorker>().setInitialDelay(5, TimeUnit.MINUTES)
                    .setInputData(workDataOf(KEY_END to true, KEY_STAMP to stamp)).build(),
            )
            chain.enqueue()
        }
    }
}
