package app.lifedashboard.planner

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** Ausdauer-Disziplin für den Ironman-Weg. Alles andere (Floorball, Kraft …) zählt als Zeit, nicht als Strecke. */
enum class Discipline { SWIM, BIKE, RUN, OTHER }

data class ActivityLog(val date: LocalDate, val discipline: Discipline, val minutes: Int, val meters: Int?)

data class StudyLog(val date: LocalDate, val minutes: Int)

/** Was aus einem geplanten Schul-Block (Lernen, Hausaufgabe, Wiederholung) geworden ist. */
enum class BlockOutcome { DONE, MISSED }

/** Ein vergangener Schul-Block aus dem Tagesplan. */
data class BlockLog(val date: LocalDate, val minutes: Int, val outcome: BlockOutcome)

/** Zockzeit: verdient mit Lernen und Sport, gesperrt bei zu viel Rückstand. */
data class RewardState(
    val earnedMinutes: Int,
    val redeemedMinutes: Int,
    /** Rückstand (verpasste Schul-Minuten) in dieser Woche */
    val backlogMinutes: Int,
) {
    val locked: Boolean get() = backlogMinutes >= Progress.LOCK_BACKLOG_MINUTES
    val availableMinutes: Int get() = if (locked) 0 else (earnedMinutes - redeemedMinutes).coerceAtLeast(0)
}

data class ProgressInput(
    val today: LocalDate,
    val activities: List<ActivityLog>,
    val study: List<StudyLog>,
    /** Ein Eintrag pro erledigter Aufgabe (Tag des Abhakens) */
    val tasksDone: List<LocalDate>,
    /** Ein Eintrag pro abgehakter Gewohnheit */
    val habitDays: List<LocalDate>,
    val weeklySportGoalMinutes: Int,
    val weeklyStudyGoalMinutes: Int,
    /** Vergangene Schul-Blöcke aus dem Tagesplan (erledigt / verpasst) */
    val blocks: List<BlockLog> = emptyList(),
    /** Strenger Coach: kein Joker, verpasste Blöcke kosten Punkte und brechen die Serie */
    val strict: Boolean = true,
    /** Diese Woche schon eingelöste Zockzeit */
    val redeemedMinutes: Int = 0,
    /** Schritte heute (null = keine Daten) und eigenes Tagesziel */
    val stepsToday: Long? = null,
    val stepGoal: Int = 8000,
    /** Ab diesem Tag zählt der strenge Coach (Neustart) – davor gibt es keinen Rückstand und keine Strafpunkte */
    val coachStart: LocalDate? = null,
)

data class LevelInfo(
    val level: Int,
    val title: String,
    val xpTotal: Int,
    /** Punkte seit Beginn dieses Levels */
    val xpIntoLevel: Int,
    /** Punkte, die dieses Level insgesamt braucht */
    val xpForLevel: Int,
) {
    val fraction: Float get() = if (xpForLevel <= 0) 0f else (xpIntoLevel.toFloat() / xpForLevel).coerceIn(0f, 1f)
    val xpToNext: Int get() = (xpForLevel - xpIntoLevel).coerceAtLeast(0)
}

data class WeekRing(val minutes: Int, val goalMinutes: Int) {
    val fraction: Float get() = if (goalMinutes <= 0) 0f else (minutes.toFloat() / goalMinutes).coerceIn(0f, 1f)
    val reached: Boolean get() = goalMinutes > 0 && minutes >= goalMinutes
    val missingMinutes: Int get() = (goalMinutes - minutes).coerceAtLeast(0)
}

/** Eine Etappe auf dem Weg zum Ironman: so weit musst du jede Disziplin einmal am Stück geschafft haben. */
data class IronmanStage(val name: String, val swimMeters: Int, val bikeMeters: Int, val runMeters: Int)

data class StageProgress(
    val stage: IronmanStage,
    val swim: Float?,
    val bike: Float?,
    val run: Float?,
) {
    val fraction: Float get() = listOfNotNull(swim, bike, run).let { if (it.isEmpty()) 1f else it.average().toFloat() }
    val done: Boolean get() = listOfNotNull(swim, bike, run).all { it >= 1f }
}

data class IronmanPath(
    val stages: List<StageProgress>,
    val bestSwimMeters: Int,
    val bestBikeMeters: Int,
    val bestRunMeters: Int,
    val totalSwimMeters: Int,
    val totalBikeMeters: Int,
    val totalRunMeters: Int,
) {
    /** Index der aktuellen Etappe (erste, die noch nicht geschafft ist); = stages.size, wenn alles geschafft ist. */
    val currentIndex: Int get() = stages.indexOfFirst { !it.done }.let { if (it < 0) stages.size else it }
    val current: StageProgress? get() = stages.getOrNull(currentIndex)
    val fraction: Float
        get() = if (stages.isEmpty()) 0f else ((currentIndex + (current?.fraction ?: 0f)) / stages.size).coerceIn(0f, 1f)
}

data class Badge(
    val id: String,
    val title: String,
    val description: String,
    val progress: Float,
) {
    val earned: Boolean get() = progress >= 1f
}

data class ProgressState(
    val level: LevelInfo,
    val xpToday: Int,
    val streak: Streaks.Result,
    val activeToday: Boolean,
    val sportWeek: WeekRing,
    val studyWeek: WeekRing,
    val ironman: IronmanPath,
    val badges: List<Badge>,
    /** Verpasste Schul-Minuten in dieser Woche */
    val backlogMinutes: Int = 0,
    /** Durch verpasste Blöcke verlorene Punkte (insgesamt) */
    val penaltyXp: Int = 0,
    val reward: RewardState = RewardState(0, 0, 0),
    /** Heute geschaffte Schul-Minuten (Blöcke + Lernzeit) */
    val schoolMinutesToday: Int = 0,
    /** Schritte heute (null = keine Daten) */
    val stepsToday: Long? = null,
    val stepGoal: Int = 8000,
) {
    val stepsFraction: Float get() = if (stepsToday == null || stepGoal <= 0) 0f else (stepsToday.toFloat() / stepGoal).coerceIn(0f, 1f)
    val earnedBadges: Int get() = badges.count { it.earned }
}

/**
 * Punkte, Level, Serien, Ironman-Weg und Abzeichen – komplett aus den vorhandenen Einträgen berechnet.
 * Es wird nichts extra gespeichert: Löschst du ein Training, verschwinden auch dessen Punkte.
 */
object Progress {
    const val XP_PER_SPORT_MINUTE = 2
    const val XP_PER_STUDY_MINUTE = 2
    const val XP_PER_TASK = 15
    const val XP_PER_HABIT = 5
    /** Mehr als 4 Std pro Einheit bzw. Lerntag bringt keine Extra-Punkte (kein Anreiz zum Übertreiben). */
    const val MAX_MINUTES_COUNTED = 240
    /** Verpasster Schul-Block: so viele Punkte pro Minute gehen verloren (strenger Coach) */
    const val PENALTY_PER_MISSED_MINUTE = 1
    /** Ab so viel Rückstand in der Woche ist die Zockzeit gesperrt */
    const val LOCK_BACKLOG_MINUTES = 60

    /** Zockzeit: 25 Min Schule → 15 Min, 45 Min Sport → 15 Min (auf 5 Min gerundet) */
    fun gamingMinutes(schoolMinutes: Int, sportMinutes: Int): Int =
        ((schoolMinutes * 3 / 5 + sportMinutes / 3) / 5) * 5

    val STAGES = listOf(
        IronmanStage("Erste 10 km", 0, 0, 10_000),
        IronmanStage("Sprint-Triathlon", 750, 20_000, 5_000),
        IronmanStage("Olympische Distanz", 1_500, 40_000, 10_000),
        IronmanStage("Halbmarathon", 0, 0, 21_100),
        IronmanStage("Mitteldistanz 70.3", 1_900, 90_000, 21_100),
        IronmanStage("Marathon", 0, 0, 42_200),
        IronmanStage("Ironman", 3_800, 180_000, 42_200),
    )

    /** Punkte, die man insgesamt braucht, um [level] zu erreichen: 0, 200, 500, 900, 1400 … */
    fun xpToReach(level: Int): Int = if (level <= 1) 0 else 50 * (level - 1) * (level + 2)

    fun levelFor(xp: Int): LevelInfo {
        var level = 1
        while (xpToReach(level + 1) <= xp) level++
        val base = xpToReach(level)
        return LevelInfo(level, levelTitle(level), xp, xp - base, xpToReach(level + 1) - base)
    }

    fun levelTitle(level: Int): String = when {
        level >= 25 -> "Eisenhart"
        level >= 18 -> "Langdistanz"
        level >= 12 -> "Wettkampfform"
        level >= 8 -> "Ausdauer"
        level >= 5 -> "Im Rhythmus"
        level >= 3 -> "Aufbau"
        else -> "Start"
    }

    private fun sportXp(a: ActivityLog) = a.minutes.coerceIn(0, MAX_MINUTES_COUNTED) * XP_PER_SPORT_MINUTE

    private fun studyXpByDay(study: List<StudyLog>): Map<LocalDate, Int> =
        study.groupBy { it.date }.mapValues { (_, l) -> l.sumOf { it.minutes }.coerceIn(0, MAX_MINUTES_COUNTED) * XP_PER_STUDY_MINUTE }

    fun penaltyXp(input: ProgressInput): Int =
        if (!input.strict) 0 else input.blocks.filter { it.outcome == BlockOutcome.MISSED }.sumOf { it.minutes } * PENALTY_PER_MISSED_MINUTE

    fun totalXp(input: ProgressInput): Int =
        (input.activities.sumOf(::sportXp) + studyXpByDay(input.study).values.sum() +
            input.tasksDone.size * XP_PER_TASK + input.habitDays.size * XP_PER_HABIT - penaltyXp(input)).coerceAtLeast(0)

    fun xpOn(input: ProgressInput, day: LocalDate): Int =
        input.activities.filter { it.date == day }.sumOf(::sportXp) + (studyXpByDay(input.study)[day] ?: 0) +
            input.tasksDone.count { it == day } * XP_PER_TASK + input.habitDays.count { it == day } * XP_PER_HABIT

    private fun ratio(best: Int, required: Int): Float? = if (required <= 0) null else (best.toFloat() / required).coerceIn(0f, 1f)

    fun ironman(activities: List<ActivityLog>): IronmanPath {
        fun best(d: Discipline) = activities.filter { it.discipline == d }.maxOfOrNull { it.meters ?: 0 } ?: 0
        fun total(d: Discipline) = activities.filter { it.discipline == d }.sumOf { it.meters ?: 0 }
        val swim = best(Discipline.SWIM)
        val bike = best(Discipline.BIKE)
        val run = best(Discipline.RUN)
        return IronmanPath(
            stages = STAGES.map { StageProgress(it, ratio(swim, it.swimMeters), ratio(bike, it.bikeMeters), ratio(run, it.runMeters)) },
            bestSwimMeters = swim, bestBikeMeters = bike, bestRunMeters = run,
            totalSwimMeters = total(Discipline.SWIM), totalBikeMeters = total(Discipline.BIKE), totalRunMeters = total(Discipline.RUN),
        )
    }

    fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun compute(raw: ProgressInput): ProgressState {
        // Neustart: Blöcke vor dem Start-Tag zählen weder als verpasst noch als erledigt
        val input = raw.coachStart?.let { start -> raw.copy(blocks = raw.blocks.filter { !it.date.isBefore(start) }) } ?: raw
        val today = input.today
        val monday = weekStart(today)
        fun inWeek(d: LocalDate) = !d.isBefore(monday) && !d.isAfter(today)

        // Ein Tag zählt für die Serie, wenn du etwas gemacht hast – im strengen Modus nur, wenn kein Schul-Block verpasst wurde
        val missedDays = if (input.strict) input.blocks.filter { it.outcome == BlockOutcome.MISSED }.map { it.date }.toSet() else emptySet()
        val activeDays = (input.activities.filter { it.minutes > 0 }.map { it.date } + input.study.filter { it.minutes > 0 }.map { it.date } +
            input.blocks.filter { it.outcome == BlockOutcome.DONE }.map { it.date }).toSet() - missedDays
        val streak = Streaks.compute(activeDays, today, jokersPerWeek = if (input.strict) 0 else 1)
        val level = levelFor(totalXp(input))
        val path = ironman(input.activities)
        val sportMinutes = input.activities.filter { inWeek(it.date) }.sumOf { it.minutes }
        val studyMinutes = input.study.filter { inWeek(it.date) }.sumOf { it.minutes }
        val backlog = input.blocks.filter { inWeek(it.date) && it.outcome == BlockOutcome.MISSED }.sumOf { it.minutes }
        // Schul-Zeit dieser Woche: erledigte Blöcke + freie Lernzeit (Fokus-Timer ohne Block zählt über study)
        val schoolWeek = maxOf(studyMinutes, input.blocks.filter { inWeek(it.date) && it.outcome == BlockOutcome.DONE }.sumOf { it.minutes })

        return ProgressState(
            level = level,
            xpToday = xpOn(input, today),
            streak = streak,
            activeToday = today in activeDays,
            sportWeek = WeekRing(sportMinutes, input.weeklySportGoalMinutes),
            studyWeek = WeekRing(studyMinutes, input.weeklyStudyGoalMinutes),
            ironman = path,
            badges = badges(input, level, streak, path),
            backlogMinutes = backlog,
            penaltyXp = penaltyXp(input),
            reward = RewardState(gamingMinutes(schoolWeek, sportMinutes), input.redeemedMinutes, if (input.strict) backlog else 0),
            stepsToday = input.stepsToday,
            stepGoal = input.stepGoal,
            schoolMinutesToday = maxOf(
                input.study.filter { it.date == today }.sumOf { it.minutes },
                input.blocks.filter { it.date == today && it.outcome == BlockOutcome.DONE }.sumOf { it.minutes },
            ),
        )
    }

    private fun frac(value: Int, goal: Int): Float = if (goal <= 0) 0f else (value.toFloat() / goal).coerceIn(0f, 1f)

    private fun badges(input: ProgressInput, level: LevelInfo, streak: Streaks.Result, path: IronmanPath): List<Badge> {
        val studyTotal = input.study.sumOf { it.minutes }
        val bestStudyDay = input.study.groupBy { it.date }.values.maxOfOrNull { l -> l.sumOf { it.minutes } } ?: 0
        val totalMeters = path.totalSwimMeters + path.totalBikeMeters + path.totalRunMeters
        // Triathlon-Woche: Schwimmen, Rad und Laufen in derselben Kalenderwoche
        val triWeek = input.activities.filter { it.discipline != Discipline.OTHER }.groupBy { weekStart(it.date) }
            .values.maxOfOrNull { week -> week.map { it.discipline }.toSet().size } ?: 0

        return listOf(
            Badge("first_workout", "Erster Schritt", "Das erste Training eingetragen", frac(input.activities.size, 1)),
            Badge("run_5k", "5 km", "5 km am Stück gelaufen", frac(path.bestRunMeters, 5_000)),
            Badge("run_10k", "10 km", "10 km am Stück gelaufen", frac(path.bestRunMeters, 10_000)),
            Badge("run_hm", "21,1 km", "21,1 km am Stück gelaufen", frac(path.bestRunMeters, 21_100)),
            Badge("swim_1k", "Wasserfest", "1 km am Stück geschwommen", frac(path.bestSwimMeters, 1_000)),
            Badge("bike_40k", "Rollt", "40 km am Stück auf dem Rad", frac(path.bestBikeMeters, 40_000)),
            Badge("tri_week", "Dreikampf", "Schwimmen, Rad und Laufen in einer Woche", frac(triWeek, 3)),
            Badge("dist_100k", "100 km", "100 km Ausdauer insgesamt", frac(totalMeters, 100_000)),
            Badge("streak_7", "Sieben am Stück", "7 Tage Serie", frac(streak.best, 7)),
            Badge("streak_30", "Ein Monat", "30 Tage Serie", frac(streak.best, 30)),
            Badge("study_10h", "Zehn Stunden", "10 Stunden gelernt", frac(studyTotal, 600)),
            Badge("study_50h", "Fünfzig Stunden", "50 Stunden gelernt", frac(studyTotal, 3_000)),
            Badge("focus_day", "Tiefenfokus", "2 Stunden an einem Tag gelernt", frac(bestStudyDay, 120)),
            Badge("tasks_25", "Abgehakt", "25 Aufgaben erledigt", frac(input.tasksDone.size, 25)),
            Badge("tasks_100", "Hundert", "100 Aufgaben erledigt", frac(input.tasksDone.size, 100)),
            Badge("level_5", "Level 5", "Level 5 erreicht", frac(level.level, 5)),
            Badge("level_10", "Level 10", "Level 10 erreicht", frac(level.level, 10)),
        )
    }

    /**
     * Eine einzige, passende Erinnerung am Abend – oder keine. Reihenfolge: Serie retten,
     * knapp vor dem nächsten Level, Wochenziel in Reichweite.
     */
    fun nudge(state: ProgressState, today: LocalDate): Pair<String, String>? {
        if (state.activeToday) {
            return if (state.level.xpToNext in 1..60) {
                "Noch ${state.level.xpToNext} Punkte bis Level ${state.level.level + 1}" to "Eine kurze Einheit reicht dafür."
            } else null
        }
        if (state.streak.current >= 2) {
            return "${state.streak.current} Tage Serie" to "Heute fehlt noch etwas. 20 Minuten Lernen oder Bewegung halten sie am Leben."
        }
        val dow = today.dayOfWeek
        if (dow >= DayOfWeek.THURSDAY && !state.sportWeek.reached && state.sportWeek.goalMinutes > 0 &&
            state.sportWeek.missingMinutes <= (state.sportWeek.goalMinutes * 0.5).roundToInt()
        ) {
            return "Noch ${state.sportWeek.missingMinutes} Min bis zum Sport-Wochenziel" to "Das passt noch in diese Woche. Such dir ein freies Zeitfenster."
        }
        return null
    }
}
