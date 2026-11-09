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
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.aggregate.AggregateMetric
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

/** Ein Tag im Fitness-Bereich. null = keine Daten/keine Freigabe. */
data class FitnessDay(
    val date: LocalDate,
    val steps: Long?,
    val sleep: Duration?,
    val activeKcal: Int?,
    val burnedKcal: Int?,
    val eatenKcal: Int?,
    val exerciseMinutes: Int?,
)

/** Alles aus Health Connect auf einen Blick. */
data class FitnessSnapshot(
    /** älteste zuerst, letzter Eintrag = heute */
    val days: List<FitnessDay>,
    val avgHeartRateToday: Int?,
    val restingHeartRate: Int?,
    val weightKg: Double?,
    val weightDate: LocalDate?,
    val proteinG: Int?,
    val carbsG: Int?,
    val fatG: Int?,
    val waterMl: Int?,
    /** Paketnamen der Apps, von denen Daten kamen (z. B. Samsung Health, MyFitnessPal) */
    val sources: Set<String>,
    /** Datenarten ohne Freigabe */
    val missing: Set<String>,
) {
    val today: FitnessDay? get() = days.lastOrNull()
}

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
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        // Damit Schritte/Trainings auch im Hintergrund (Erinnerungen, Gewohnheiten) gelesen werden dürfen
        BACKGROUND_READ,
    )

    /** Freigaben, die noch fehlen (leer = alles erlaubt) */
    suspend fun missingPermissions(): Set<String> =
        if (!isAvailable()) permissions else permissions - client().permissionController.getGrantedPermissions()

    /**
     * Fitness-Übersicht der letzten [days] Tage. Liest nur, was freigegeben ist –
     * fehlende Freigaben werden in [FitnessSnapshot.missing] gemeldet statt abzustürzen.
     */
    suspend fun snapshot(days: Int = 7): FitnessSnapshot? {
        if (!isAvailable()) return null
        val c = client()
        val granted = c.permissionController.getGrantedPermissions()
        fun ok(type: kotlin.reflect.KClass<out androidx.health.connect.client.records.Record>) = HealthPermission.getReadPermission(type) in granted
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val start = today.minusDays((days - 1).toLong())
        val sources = mutableSetOf<String>()

        val metrics = buildSet<AggregateMetric<*>> {
            if (ok(StepsRecord::class)) add(StepsRecord.COUNT_TOTAL)
            if (ok(ActiveCaloriesBurnedRecord::class)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (ok(TotalCaloriesBurnedRecord::class)) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            if (ok(NutritionRecord::class)) add(NutritionRecord.ENERGY_TOTAL)
            if (ok(ExerciseSessionRecord::class)) add(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)
        }
        val perDay = if (metrics.isEmpty()) emptyMap() else runCatching {
            c.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = metrics,
                    timeRangeFilter = TimeRangeFilter.between(start.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            ).associate { g ->
                g.result.dataOrigins.forEach { sources += it.packageName }
                g.startTime.toLocalDate() to g.result
            }
        }.getOrDefault(emptyMap())

        val sleepByDay = if (!ok(SleepSessionRecord::class)) emptyMap() else runCatching {
            c.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    TimeRangeFilter.between(start.minusDays(1).atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant()),
                ),
            ).records.onEach { sources += it.metadata.dataOrigin.packageName }
                .groupBy { it.endTime.atZone(zone).toLocalDate() }
                .mapValues { (_, l) -> l.fold(Duration.ZERO) { acc, r -> acc + Duration.between(r.startTime, r.endTime) } }
        }.getOrDefault(emptyMap())

        val list = (0 until days).map { i ->
            val d = start.plusDays(i.toLong())
            val r = perDay[d]
            FitnessDay(
                date = d,
                steps = r?.get(StepsRecord.COUNT_TOTAL),
                sleep = sleepByDay[d],
                activeKcal = r?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories?.toInt(),
                burnedKcal = r?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories?.toInt(),
                eatenKcal = r?.get(NutritionRecord.ENERGY_TOTAL)?.inKilocalories?.toInt(),
                exerciseMinutes = r?.get(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)?.toMinutes()?.toInt(),
            )
        }

        // Heute: Puls, Ernährung, Wasser
        val todayFilter = TimeRangeFilter.between(today.atStartOfDay(zone).toInstant(), java.time.Instant.now())
        val todayMetrics = buildSet<AggregateMetric<*>> {
            if (ok(HeartRateRecord::class)) add(HeartRateRecord.BPM_AVG)
            if (ok(NutritionRecord::class)) { add(NutritionRecord.PROTEIN_TOTAL); add(NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL); add(NutritionRecord.TOTAL_FAT_TOTAL) }
            if (ok(HydrationRecord::class)) add(HydrationRecord.VOLUME_TOTAL)
        }
        val t = if (todayMetrics.isEmpty()) null else runCatching { c.aggregate(AggregateRequest(todayMetrics, todayFilter)) }.getOrNull()
        t?.dataOrigins?.forEach { sources += it.packageName }

        // Ruhepuls: Durchschnitt der letzten 7 Tage
        val resting = if (!ok(RestingHeartRateRecord::class)) null else runCatching {
            c.aggregate(
                AggregateRequest(setOf(RestingHeartRateRecord.BPM_AVG), TimeRangeFilter.between(start.atStartOfDay(zone).toInstant(), java.time.Instant.now())),
            ).also { a -> a.dataOrigins.forEach { sources += it.packageName } }[RestingHeartRateRecord.BPM_AVG]
        }.getOrNull()

        // Gewicht: letzter Eintrag der letzten 60 Tage
        val weight = if (!ok(WeightRecord::class)) null else runCatching {
            c.readRecords(
                ReadRecordsRequest(
                    WeightRecord::class,
                    TimeRangeFilter.between(today.minusDays(60).atStartOfDay(zone).toInstant(), java.time.Instant.now()),
                    ascendingOrder = false,
                    pageSize = 1,
                ),
            ).records.firstOrNull()?.also { sources += it.metadata.dataOrigin.packageName }
        }.getOrNull()

        return FitnessSnapshot(
            days = list,
            avgHeartRateToday = t?.get(HeartRateRecord.BPM_AVG)?.toInt(),
            restingHeartRate = resting?.toInt(),
            weightKg = weight?.weight?.inKilograms,
            weightDate = weight?.time?.atZone(zone)?.toLocalDate(),
            proteinG = t?.get(NutritionRecord.PROTEIN_TOTAL)?.inGrams?.toInt(),
            carbsG = t?.get(NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL)?.inGrams?.toInt(),
            fatG = t?.get(NutritionRecord.TOTAL_FAT_TOTAL)?.inGrams?.toInt(),
            waterMl = t?.get(HydrationRecord.VOLUME_TOTAL)?.inMilliliters?.toInt(),
            sources = sources - context.packageName,
            missing = permissions - granted,
        )
    }

    companion object {
        const val BACKGROUND_READ = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

        /** Bekannte Apps, die in Health Connect schreiben. */
        val KNOWN_SOURCES = mapOf(
            "com.sec.android.app.shealth" to "Samsung Health",
            "com.myfitnesspal.android" to "MyFitnessPal",
            "com.strava" to "Strava",
            "com.garmin.android.apps.connectmobile" to "Garmin Connect",
            "com.google.android.apps.fitness" to "Google Fit",
            "com.fitbit.FitbitMobile" to "Fitbit",
            "com.yazio.android" to "YAZIO",
            "com.fatsecret.android" to "FatSecret",
            "com.sillens.shapeupclub" to "Lifesum",
            "com.runtastic.android" to "adidas Running",
            "com.withings.wiscale2" to "Withings",
            "com.huawei.health" to "Huawei Health",
            "com.huami.watch.hmwatchmanager" to "Zepp",
            "com.google.android.apps.healthdata" to "Health Connect",
        )

        fun sourceName(pkg: String) = KNOWN_SOURCES[pkg] ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

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
