package app.lifedashboard

import android.app.Application
import app.lifedashboard.core.di.ApplicationScope
import app.lifedashboard.data.seed.SeedDataInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class LifeDashboardApplication : Application() {

    @Inject lateinit var seedData: SeedDataInitializer

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seedData.seedIfNeeded() }
    }
}
