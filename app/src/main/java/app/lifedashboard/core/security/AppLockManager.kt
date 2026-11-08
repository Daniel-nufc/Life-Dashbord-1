package app.lifedashboard.core.security

import android.os.SystemClock
import app.lifedashboard.core.di.ApplicationScope
import app.lifedashboard.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Merkt sich, ob die App gesperrt ist. Gesperrt wird beim Start und wenn die App länger als
 * die eingestellte Zeit im Hintergrund war (Standard: 1 Minute).
 */
@Singleton
class AppLockManager @Inject constructor(
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundSince: Long? = null

    init {
        scope.launch {
            if (!settings.settings.first().appLockEnabled) _locked.value = false
        }
    }

    fun onAppBackground() {
        backgroundSince = SystemClock.elapsedRealtime()
    }

    fun onAppForeground() {
        val since = backgroundSince ?: return
        backgroundSince = null
        scope.launch {
            val current = settings.settings.first()
            val elapsed = SystemClock.elapsedRealtime() - since
            if (current.appLockEnabled && elapsed > current.lockTimeoutMinutes * 60_000L) {
                _locked.value = true
            }
        }
    }

    fun unlock() {
        _locked.value = false
    }
}
