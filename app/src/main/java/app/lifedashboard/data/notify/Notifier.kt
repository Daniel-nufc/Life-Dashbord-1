package app.lifedashboard.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.lifedashboard.MainActivity
import app.lifedashboard.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

enum class Channel(val id: String, val title: String, val importance: Int) {
    PLAN("plan", "Tagesplan & Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT),
    SCHOOL("school", "Klausuren & Hausaufgaben", NotificationManager.IMPORTANCE_DEFAULT),
    SPORT("sport", "Training", NotificationManager.IMPORTANCE_LOW),
    MONEY("money", "Finanzen", NotificationManager.IMPORTANCE_LOW),
    REVIEW("review", "Wochenrückblick", NotificationManager.IMPORTANCE_LOW),
}

/**
 * Schickt Benachrichtigungen – aber nicht nervig:
 * - Ruhezeit (Standard 22–6 Uhr): dann gar nichts
 * - höchstens [MAX_PER_DAY] pro Tag
 * - jede Erinnerung nur einmal (Schlüssel wird gemerkt)
 */
@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        Channel.entries.forEach { manager.createNotificationChannel(NotificationChannel(it.id, it.title, it.importance)) }
    }

    fun canNotify(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun isQuietTime(now: LocalTime = LocalTime.now(), quietStart: Int = 22, quietEnd: Int = 6): Boolean =
        now.hour >= quietStart || now.hour < quietEnd

    /** @return true, wenn gesendet */
    suspend fun send(key: String, channel: Channel, title: String, text: String, ignoreLimit: Boolean = false): Boolean {
        if (!canNotify() || isQuietTime()) return false
        val today = LocalDate.now().toString()
        val sent = dataStore.data.first()[SENT].orEmpty()
        val fullKey = "$today|$key"
        if (sent.any { it.endsWith("|$key") && (it.startsWith(today) || key.startsWith("once:")) }) return false
        val todayCount = sent.count { it.startsWith(today) }
        if (!ignoreLimit && todayCount >= MAX_PER_DAY) return false

        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(fullKey.hashCode(), notification)
        } catch (e: SecurityException) {
            return false
        }
        // Gesendete Schlüssel merken (nur die letzten 14 Tage behalten)
        val cutoff = LocalDate.now().minusDays(14).toString()
        dataStore.edit { prefs ->
            prefs[SENT] = (prefs[SENT].orEmpty().filter { it.substringBefore('|') >= cutoff } + fullKey).toSet()
        }
        return true
    }

    companion object {
        const val MAX_PER_DAY = 5
        private val SENT = stringSetPreferencesKey("sent_notifications")
    }
}
