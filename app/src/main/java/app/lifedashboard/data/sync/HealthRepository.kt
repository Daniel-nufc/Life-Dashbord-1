package app.lifedashboard.data.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.AggregateRequest
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

/** Training aus Health Connect (z. B. von Samsung Health aufgezeichnet). */
data class HealthWorkout(
    val id: String,
    val date: LocalDate,
    val kind: Kind,
    val minutes: Int,
    val distanceMeters: Int?,
    val avgHeartRate: Int?,
) {
    enum class Kind { RUN, WALK, BIKE, SWIM, STRENGTH, OTHER }
}

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
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    /** Mindestens Schritte + Schlaf erlaubt (ältere Freigabe) */
    suspend fun hasBasicPermissions(): Boolean =
        isAvailable() && client().permissionController.getGrantedPermissions().containsAll(permissions.take(2))

    /** Schlaf der letzten Nacht (endet heute), null wenn unbekannt */
    suspend fun lastNightSleep(): Duration? = runCatching { lastDays(1).lastOrNull()?.sleep }.getOrNull()

    /** Trainings der letzten [days] Tage. */
    suspend fun recentWorkouts(days: Int = 14): List<HealthWorkout> {
        if (!isAvailable()) return emptyList()
        val granted = client().permissionController.getGrantedPermissions()
        if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) !in granted) return emptyList()
        val zone = ZoneId.systemDefault()
        val from = LocalDate.now().minusDays(days.toLong()).atStartOfDay(zone).toInstant()
        val to = java.time.Instant.now()
        val sessions = client().readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(from, to))).records
        val canDistance = HealthPermission.getReadPermission(DistanceRecord::class) in granted
        val canHr = HealthPermission.getReadPermission(HeartRateRecord::class) in granted
        return sessions.map { s ->
            val window = TimeRangeFilter.between(s.startTime, s.endTime)
            val metrics = buildSet {
                if (canDistance) add(DistanceRecord.DISTANCE_TOTAL)
                if (canHr) add(HeartRateRecord.BPM_AVG)
            }
            val agg = if (metrics.isNotEmpty()) runCatching { client().aggregate(AggregateRequest(metrics, window)) }.getOrNull() else null
            HealthWorkout(
                id = s.metadata.id,
                date = s.startTime.atZone(zone).toLocalDate(),
                kind = when (s.exerciseType) {
                    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> HealthWorkout.Kind.RUN
                    ExerciseSessionRecord.EXERCISE_TYPE_WALKING, ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> HealthWorkout.Kind.WALK
                    ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> HealthWorkout.Kind.BIKE
                    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> HealthWorkout.Kind.SWIM
                    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> HealthWorkout.Kind.STRENGTH
                    else -> HealthWorkout.Kind.OTHER
                },
                minutes = Duration.between(s.startTime, s.endTime).toMinutes().toInt(),
                distanceMeters = agg?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters?.toInt(),
                avgHeartRate = agg?.get(HeartRateRecord.BPM_AVG)?.toInt(),
            )
        }
    }

    fun isAvailable(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun client() = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermissions(): Boolean =
        isAvailable() && client().permissionController.getGrantedPermissions().containsAll(permissions)

    /** Schritte und Schlaf der letzten [days] Tage (ältester zuerst). */
    suspend fun lastDays(days: Int = 7): List<DailyHealth> {
        if (!hasBasicPermissions()) return emptyList()
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
