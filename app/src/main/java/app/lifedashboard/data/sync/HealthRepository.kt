package app.lifedashboard.data.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class DailyHealth(val date: LocalDate, val steps: Long, val sleep: Duration?)

/**
 * Liest Schritte und Schlaf aus Health Connect (Samsung Health synchronisiert dorthin).
 * Nur Leserechte – die App schreibt nichts.
 */
@Singleton
class HealthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    fun isAvailable(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun client() = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermissions(): Boolean =
        isAvailable() && client().permissionController.getGrantedPermissions().containsAll(permissions)

    /** Schritte und Schlaf der letzten [days] Tage (ältester zuerst). */
    suspend fun lastDays(days: Int = 7): List<DailyHealth> {
        if (!hasPermissions()) return emptyList()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val start = today.minusDays((days - 1).toLong())
        val stepsByDay = client().aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            ),
        ).associate { it.startTime.toLocalDate() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }

        // Schlaf: Sitzung, die am Morgen eines Tages endet, zählt für diesen Tag
        val sleepRecords = client().readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    start.minusDays(1).atStartOfDay(zone).toInstant(),
                    today.plusDays(1).atStartOfDay(zone).toInstant(),
                ),
            ),
        ).records
        val sleepByDay = sleepRecords.groupBy { it.endTime.atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.fold(Duration.ZERO) { acc, r -> acc + Duration.between(r.startTime, r.endTime) } }

        return (0 until days).map { offset ->
            val date = start.plusDays(offset.toLong())
            DailyHealth(date, stepsByDay[date] ?: 0L, sleepByDay[date])
        }
    }
}
