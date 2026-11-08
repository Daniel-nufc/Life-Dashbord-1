package app.lifedashboard.data.sync

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handyzeit aus Android (UsageStatsManager). Braucht die Erlaubnis „Nutzungsdatenzugriff“,
 * die man einmal selbst in den Einstellungen gibt. Daten bleiben auf dem Gerät.
 */
@Singleton
class UsageRepository @Inject constructor(@ApplicationContext private val context: Context) {

    /** Social Media & Videos (für die Gewohnheit „Handyzeit unter 2 Std“) */
    val socialPackages = setOf(
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", // TikTok
        "com.instagram.android", "com.instagram.barcelona", // Instagram, Threads
        "com.google.android.youtube", "com.google.android.apps.youtube.music",
        "com.snapchat.android", "com.facebook.katana", "com.twitter.android", "com.reddit.frontpage",
        "tv.twitch.android.app", "com.netflix.mediaclient", "com.disney.disneyplus", "com.amazon.avod.thirdpartyclient",
        "com.pinterest", "com.bereal.ft",
    )

    fun hasPermission(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Vordergrundzeit pro App in [fromMillis, toMillis) aus Ereignissen (genauer als die Tages-Statistik). */
    private fun foregroundTimes(fromMillis: Long, toMillis: Long): Map<String, Long> {
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val events = manager.queryEvents(fromMillis, toMillis)
        val started = mutableMapOf<String, Long>()
        val total = mutableMapOf<String, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> started[e.packageName] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> started.remove(e.packageName)?.let {
                    total[e.packageName] = (total[e.packageName] ?: 0) + (e.timeStamp - it)
                }
            }
        }
        started.forEach { (pkg, start) -> total[pkg] = (total[pkg] ?: 0) + (minOf(toMillis, System.currentTimeMillis()) - start) }
        return total
    }

    /** Social-Media-/Video-Zeit an einem Tag, null ohne Erlaubnis */
    fun socialTime(date: LocalDate): Duration? {
        if (!hasPermission()) return null
        val zone = ZoneId.systemDefault()
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val ms = foregroundTimes(from, to).filterKeys { it in socialPackages }.values.sum()
        return Duration.ofMillis(ms)
    }

    /**
     * Wurde das Handy in der Nacht nach [date] zwischen [bedtime] und 5 Uhr benutzt?
     * null ohne Erlaubnis. (Eigene App und Systemoberfläche zählen nicht.)
     */
    fun usedPhoneLate(date: LocalDate, bedtime: LocalTime = LocalTime.of(22, 30)): Boolean? {
        if (!hasPermission()) return null
        val zone = ZoneId.systemDefault()
        val from = date.atTime(bedtime).atZone(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atTime(5, 0).atZone(zone).toInstant().toEpochMilli()
        val ignore = setOf(context.packageName, "com.sec.android.app.launcher", "com.android.systemui", "com.samsung.android.app.aodservice")
        return foregroundTimes(from, to).any { (pkg, ms) -> pkg !in ignore && ms > 60_000 }
    }
}
