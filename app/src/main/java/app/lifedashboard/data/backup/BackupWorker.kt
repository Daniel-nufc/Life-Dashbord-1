package app.lifedashboard.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.lifedashboard.core.security.SecretStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Wöchentliches automatisches Backup in den gewählten Ordner (behält die letzten 6). */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backup: BackupManager,
    private val secrets: SecretStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val folder = secrets.get(FOLDER_KEY)?.let(Uri::parse) ?: return Result.success()
        val password = secrets.get(PASSWORD_KEY) ?: return Result.success()
        return try {
            val dir = DocumentFile.fromTreeUri(applicationContext, folder) ?: return Result.failure()
            val name = "LifeDashboard-auto-${LocalDate.now()}.ldbackup"
            dir.findFile(name)?.delete()
            val file = dir.createFile("application/octet-stream", name) ?: return Result.retry()
            backup.export(file.uri, password)
            // Alte automatische Backups aufräumen
            dir.listFiles().filter { it.name?.startsWith("LifeDashboard-auto-") == true }
                .sortedByDescending { it.name }
                .drop(6)
                .forEach { it.delete() }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val FOLDER_KEY = "backup_folder"
        const val PASSWORD_KEY = "backup_password"
        private const val NAME = "auto-backup"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
