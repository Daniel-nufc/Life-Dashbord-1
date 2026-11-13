package app.lifedashboard

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import app.lifedashboard.core.security.AppLockManager
import app.lifedashboard.ui.lock.LockScreen
import app.lifedashboard.ui.navigation.LifeDashboardRoot
import app.lifedashboard.ui.theme.LifeDashboardTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var appLock: AppLockManager

    @Inject lateinit var settings: app.lifedashboard.data.settings.SettingsRepository

    @Inject lateinit var lanSync: app.lifedashboard.data.devicesync.LanSyncManager

    @Inject lateinit var strava: app.lifedashboard.data.sync.StravaRepository

    private var promptShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleLink(intent)
        setContent {
            val themeMode by settings.settings.map { it.themeMode }.collectAsStateWithLifecycle("SYSTEM")
            val dark = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            LifeDashboardTheme(darkTheme = dark) {
                val locked by appLock.locked.collectAsStateWithLifecycle()
                if (locked) {
                    LockScreen(onUnlockRequest = ::authenticate)
                } else {
                    LifeDashboardRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    /** Rückkehr von Strava: lifedashboard://localhost/strava?code=… */
    private fun handleLink(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "lifedashboard" || data.path?.startsWith("/strava") != true) return
        lifecycleScope.launch {
            val result = strava.handleRedirect(data.toString())
            android.widget.Toast.makeText(
                this@MainActivity,
                result.fold({ "Strava verbunden – $it Trainings übernommen" }, { "Strava: ${it.message}" }),
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onStart() {
        super.onStart()
        appLock.onAppForeground()
        // Abgleich Handy ↔ Tablet: beim Öffnen holen, im WLAN direkt verbinden
        app.lifedashboard.data.devicesync.SyncWorker.runNow(this)
        lanSync.start()
    }

    override fun onStop() {
        super.onStop()
        appLock.onAppBackground()
        lanSync.stop()
        // Beim Verlassen die eigenen Änderungen hochladen
        app.lifedashboard.data.devicesync.SyncWorker.runNow(this, delaySeconds = 3)
    }

    /** Fingerabdruck/Gesicht, ersatzweise Geräte-PIN. Ohne eingerichtete Sperre: direkt öffnen. */
    private fun authenticate() {
        if (promptShowing) return
        val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            appLock.unlock()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    appLock.unlock()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptShowing = false
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Life Dashboard entsperren")
            .setSubtitle("Mit Fingerabdruck oder Geräte-PIN")
            .setAllowedAuthenticators(authenticators)
            .build()
        promptShowing = true
        prompt.authenticate(info)
    }
}
