package app.lifedashboard

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.lifedashboard.core.di.ApplicationScope
import app.lifedashboard.data.seed.SeedDataInitializer
import app.lifedashboard.data.sync.IcalSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class LifeDashboardApplication : Application(), Configuration.Provider {

    @Inject lateinit var seedData: SeedDataInitializer

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var notifier: app.lifedashboard.data.notify.Notifier

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seedData.seedIfNeeded() }
        IcalSyncWorker.schedule(this)
        notifier.createChannels()
        app.lifedashboard.data.notify.ReminderWorker.schedule(this)
    }
}
