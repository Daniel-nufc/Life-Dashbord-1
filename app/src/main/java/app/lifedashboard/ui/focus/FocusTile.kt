package app.lifedashboard.ui.focus

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.flow.MutableStateFlow

/** Signal: Fokus für den nächsten Block starten (von der Schnellkachel oder einem Widget). */
object FocusLauncher {
    val requested = MutableStateFlow(false)
    const val EXTRA = "start_focus"
}

/**
 * Schnelleinstellungs-Kachel „Fokus“: ein Tipp aus der Benachrichtigungsleiste öffnet die App
 * direkt im Fokus-Modus für den nächsten Block – ohne Umweg über Instagram & Co.
 */
class FocusTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Fokus"
            subtitle = "Nächster Block"
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, app.lifedashboard.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(FocusLauncher.EXTRA, true)
        val pending = PendingIntent.getActivity(this, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startActivityAndCollapse(pending)
    }
}
