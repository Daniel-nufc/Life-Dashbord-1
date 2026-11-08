package app.lifedashboard.planner

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Zwischenziele auf dem Weg zum Ironman – der Plan richtet sich immer nach dem nächsten. */
enum class TrainingGoal(val label: String, val runKm: Double) {
    RUN_10K("10 km laufen", 10.0),
    SPRINT("Sprint-Triathlon (0,75 / 20 / 5)", 5.0),
    OLYMPIC("Olympische Distanz (1,5 / 40 / 10)", 10.0),
    HALF("Mitteldistanz (1,9 / 90 / 21,1)", 21.1),
    FULL("Ironman (3,8 / 180 / 42,2)", 42.2),
}

enum class SessionType(val label: String, val hard: Boolean) {
    EASY_RUN("Lockerer Lauf", false),
    INTERVALS("Intervalle", true),
    TEMPO("Tempolauf", true),
    LONG_RUN("Langer Lauf", true),
    STRENGTH("Kraft & Stabi", false),
    BIKE("Radfahren locker", false),
    SWIM("Schwimmen (Technik)", false),
}

/** Bewertung nach der Einheit. */
enum class Effort { EASY, OK, HARD }

data class TrainingProfile(
    val goal: TrainingGoal = TrainingGoal.RUN_10K,
    /** Längster Lauf zu Beginn in km */
    val startLongestKm: Double = 5.0,
    /** Tempo locker in Minuten pro km */
    val easyPaceMinPerKm: Double = 6.5,
    val planStart: LocalDate,
    /** Laufeinheiten pro Woche (2–4) */
    val runsPerWeek: Int = 3,
    val strengthPerWeek: Int = 1,
    val hasBike: Boolean = true,
    val canSwim: Boolean = false,
    /** Feste harte Tage, z. B. Floorball Mo/Mi */
    val hardDays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
    /** Fester Ruhetag */
    val restDay: DayOfWeek = DayOfWeek.SUNDAY,
)

/** Was tatsächlich passiert ist (aus Log/Health Connect + Bewertung). */
data class SessionResult(val date: LocalDate, val type: SessionType, val done: Boolean, val effort: Effort?, val distanceKm: Double? = null)

data class TrainingSession(
    val date: LocalDate,
    val type: SessionType,
    val minutes: Int,
    val distanceKm: Double?,
    val title: String,
    val details: String,
    val weekIndex: Int,
    val recoveryWeek: Boolean,
    /** Vorschlag zum Abschwächen (z. B. nach schlechtem Schlaf) übernommen */
    val reduced: Boolean = false,
) {
    val key: String get() = "train:${date.toEpochDay()}:${type.name}"
}

/** Kraft & Stabi ohne Geräte – mit automatischer Steigerung. */
data class StrengthExercise(val name: String, val sets: Int, val reps: String)

/**
 * Trainingsplan-Generator (reines Kotlin, getestet).
 *
 * Regeln (bewusst konservativ für einen Schüler):
 * - Langer Lauf steigt höchstens ~10 % pro Woche, jede 4. Woche ist Erholung (−25 %)
 * - Nie zwei harte Tage hintereinander; Floorball zählt als hart
 * - Mindestens ein fester Ruhetag
 * - Klausurphase: Umfang −30 %, keine Intervalle (aber nicht null)
 * - Ausgefallene Einheiten werden nicht nachgeholt; ein verpasster langer Lauf → Umfang bleibt gleich
 * - „Zu hart“ → keine Steigerung, „Leicht“ → normale Steigerung
 */
object TrainingPlanner {

    /** Woche (Montag) → Index ab Planstart */
    fun weekIndex(profile: TrainingProfile, date: LocalDate): Int =
        ChronoUnit.WEEKS.between(profile.planStart.mondayOfWeek(), date.mondayOfWeek()).toInt()

    fun isRecoveryWeek(index: Int) = index > 0 && (index + 1) % 4 == 0

    /**
     * Länge des langen Laufs für jede Woche bis [untilWeek], unter Berücksichtigung der Ergebnisse.
     */
    fun longRunProgression(profile: TrainingProfile, results: List<SessionResult>, untilWeek: Int): List<Double> {
        val target = profile.goal.runKm * if (profile.goal == TrainingGoal.RUN_10K) 1.05 else 0.9
        val list = mutableListOf<Double>()
        var base = profile.startLongestKm
        for (w in 0..max(0, untilWeek)) {
            val monday = profile.planStart.mondayOfWeek().plusWeeks(w.toLong())
            val prevMonday = monday.minusWeeks(1)
            val prev = results.filter { !it.date.isBefore(prevMonday) && it.date.isBefore(monday) }
            if (w > 0) {
                val longDone = prev.firstOrNull { it.type == SessionType.LONG_RUN }
                val tooHard = prev.any { it.effort == Effort.HARD }
                val previousWasRecovery = isRecoveryWeek(w - 1)
                when {
                    previousWasRecovery -> Unit // nach Erholung weiter vom alten Stand
                    longDone == null || !longDone.done -> Unit // verpasst → nicht steigern, nicht nachholen
                    tooHard -> Unit // zu hart → Umfang halten
                    else -> base = min(target, base * 1.10)
                }
                // Tatsächlich weiter gelaufen? Dann als neuen Stand übernehmen (nur nach oben, max +10 %)
                longDone?.distanceKm?.let { if (it > base) base = min(it, base * 1.10) }
            }
            list += if (isRecoveryWeek(w)) roundHalf(base * 0.75) else roundHalf(base)
        }
        return list
    }

    /** Geschätzte Woche, in der das Ziel (langer Lauf = Zieldistanz) erreicht ist. */
    fun estimatedGoalDate(profile: TrainingProfile, results: List<SessionResult>, today: LocalDate): LocalDate {
        val target = profile.goal.runKm
        val currentWeek = max(0, weekIndex(profile, today))
        var base = longRunProgression(profile, results, currentWeek).filterIndexed { i, _ -> !isRecoveryWeek(i) }.lastOrNull() ?: profile.startLongestKm
        var week = currentWeek
        while (base < target && week < currentWeek + 104) {
            week++
            if (!isRecoveryWeek(week)) base *= 1.10
        }
        return profile.planStart.mondayOfWeek().plusWeeks(week.toLong()).plusDays(5)
    }

    /**
     * Plant eine Woche.
     * @param examDays Tage mit Klausur (am Vortag kein hartes Training)
     * @param examPhase Klausurphase in dieser Woche → weniger Umfang
     * @param blockedDays Tage ohne Zeit (z. B. lange Job-Schicht)
     */
    fun planWeek(
        profile: TrainingProfile,
        monday: LocalDate,
        results: List<SessionResult>,
        examDays: Set<LocalDate> = emptySet(),
        examPhase: Boolean = false,
        blockedDays: Set<LocalDate> = emptySet(),
    ): List<TrainingSession> {
        val w = weekIndex(profile, monday)
        if (w < 0) return emptyList()
        val recovery = isRecoveryWeek(w)
        val longKm = longRunProgression(profile, results, w).last()
        val factor = (if (examPhase) 0.7 else 1.0)
        val pace = profile.easyPaceMinPerKm
        val days = (0L..6L).map { monday.plusDays(it) }

        val hard = profile.hardDays.toMutableSet()
        val used = mutableSetOf<DayOfWeek>()
        val sessions = mutableListOf<TrainingSession>()
        fun free(day: LocalDate) = day.dayOfWeek != profile.restDay && day.dayOfWeek !in profile.hardDays &&
            day.dayOfWeek !in used && day !in blockedDays
        fun adjacentHard(d: DayOfWeek) = d.plus(1) in hard || d.minus(1) in hard
        fun beforeExam(day: LocalDate) = day.plusDays(1) in examDays || day in examDays

        // 1) Langer Lauf (hart): ein Tag ohne harte Nachbarn, bevorzugt Samstag
        val longDay = days.sortedBy { prefOrder(it.dayOfWeek, listOf(DayOfWeek.SATURDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY, DayOfWeek.THURSDAY, DayOfWeek.TUESDAY)) }
            .firstOrNull { free(it) && !adjacentHard(it.dayOfWeek) && !beforeExam(it) }
        if (longDay != null) {
            val km = roundHalf(longKm * factor)
            sessions += session(longDay, SessionType.LONG_RUN, km, pace + 0.3, w, recovery,
                "Langer Lauf ${fmt(km)} km", "Ganz ruhig – du musst dich dabei noch unterhalten können. Lieber langsamer als zu schnell. Am Ende 5 Min gehen und dehnen.")
            hard += longDay.dayOfWeek
            used += longDay.dayOfWeek
        }

        // 2) Qualitätseinheit (Intervalle/Tempo) – nur außerhalb von Erholungswoche & Klausurphase, sonst locker
        val runsLeft = max(0, profile.runsPerWeek - sessions.size)
        if (runsLeft > 0 && !recovery && !examPhase && w >= 1) {
            val qualityDay = days.firstOrNull { free(it) && !adjacentHard(it.dayOfWeek) && !beforeExam(it) }
            if (qualityDay != null) {
                val intervals = w % 2 == 1
                val reps = min(6, 3 + w / 3)
                val (type, title, details, minutes) = if (intervals) {
                    Quad(SessionType.INTERVALS, "Intervalle $reps × 2 Min",
                        "10 Min locker einlaufen · $reps × 2 Min zügig (schneller als locker, aber nicht Vollgas) mit je 2 Min Gehpause · 10 Min auslaufen.",
                        20 + reps * 4)
                } else {
                    val t = min(20, 8 + w)
                    Quad(SessionType.TEMPO, "Tempolauf $t Min",
                        "10 Min locker · $t Min zügig im „angenehm anstrengenden“ Tempo (kurze Sätze sprechen geht noch) · 10 Min auslaufen.",
                        20 + t)
                }
                sessions += TrainingSession(qualityDay, type, minutes, null, title, details, w, recovery)
                hard += qualityDay.dayOfWeek
                used += qualityDay.dayOfWeek
            }
        }

        // 3) Lockere Läufe auffüllen (nie an harten Tagen)
        val easyKm = roundHalf(max(3.0, longKm * 0.55) * factor * (if (recovery) 0.85 else 1.0))
        while (sessions.count { it.type.name.endsWith("RUN") || it.type == SessionType.INTERVALS || it.type == SessionType.TEMPO } < profile.runsPerWeek) {
            val day = days.sortedBy { prefOrder(it.dayOfWeek, listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)) }
                .firstOrNull { free(it) } ?: break
            sessions += session(day, SessionType.EASY_RUN, easyKm, pace, w, recovery,
                "Lockerer Lauf ${fmt(easyKm)} km", "Ganz locker, Puls niedrig (Nasenatmung fast möglich). Am Ende 4 × 15 Sek locker steigern.")
            used += day.dayOfWeek
        }

        // 4) Kraft & Stabi an einem lockeren Tag (darf neben einem Lauf liegen, auch an Floorball-freien Tagen)
        repeat(profile.strengthPerWeek) {
            val day = days.sortedBy { prefOrder(it.dayOfWeek, listOf(DayOfWeek.THURSDAY, DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)) }
                .firstOrNull { free(it) } ?: days.firstOrNull { it.dayOfWeek !in hard && it.dayOfWeek != profile.restDay && it !in blockedDays }
            if (day != null) {
                sessions += TrainingSession(day, SessionType.STRENGTH, if (examPhase) 15 else 20, null, "Kraft & Stabi 20 Min",
                    "Ohne Geräte, siehe Übungsliste. Saubere Ausführung vor Wiederholungen.", w, recovery)
                used += day.dayOfWeek
            }
        }

        // 5) Ab Triathlon-Zielen: Rad/Schwimmen ergänzen
        if (profile.goal != TrainingGoal.RUN_10K) {
            days.firstOrNull { free(it) }?.let { day ->
                if (profile.hasBike) {
                    sessions += TrainingSession(day, SessionType.BIKE, 45, null, "Rad locker 45 Min", "Gleichmäßig, leichter Gang, hohe Trittfrequenz.", w, recovery)
                    used += day.dayOfWeek
                }
            }
            days.firstOrNull { free(it) }?.let { day ->
                sessions += TrainingSession(day, SessionType.SWIM, 40, null, "Schwimmen Technik 40 Min",
                    if (profile.canSwim) "Kraul-Technik: 8 × 50 m locker mit Pausen, Fokus auf Atmung." else "Kraulen lernen: Beine mit Brett, Atmung zur Seite üben, kurze Bahnen.", w, recovery)
                used += day.dayOfWeek
            }
        }
        return sessions.sortedBy { it.date }
    }

    /** Kraft-Programm: Steigerung um eine Stufe pro „Leicht“-Bewertung (max. Stufe 6). */
    fun strengthProgram(easyRatings: Int): List<StrengthExercise> {
        val level = min(6, easyRatings)
        val reps = 10 + level * 2
        val sets = if (level >= 3) 3 else 2
        val hold = 30 + level * 5
        return listOf(
            StrengthExercise("Kniebeugen", sets, "$reps"),
            StrengthExercise("Ausfallschritte (je Bein)", sets, "${reps / 2 + 2}"),
            StrengthExercise("Liegestütze (auf Knien ok)", sets, "${max(6, reps - 4)}"),
            StrengthExercise("Glute Bridge", sets, "$reps"),
            StrengthExercise("Unterarmstütz (Plank)", sets, "$hold Sek"),
            StrengthExercise("Seitstütz (je Seite)", sets, "${hold - 10} Sek"),
            StrengthExercise("Wadenheben", sets, "${reps + 5}"),
        )
    }

    /** Nach schlechtem Schlaf: harte Einheit leichter machen. */
    fun reduce(session: TrainingSession): TrainingSession = when (session.type) {
        SessionType.INTERVALS, SessionType.TEMPO -> session.copy(
            type = SessionType.EASY_RUN, title = "Lockerer Lauf (statt ${session.type.label})", reduced = true,
            minutes = max(20, (session.minutes * 0.7).roundToInt()),
            details = "Heute nur locker – Erholung ist auch Training.",
        )
        SessionType.LONG_RUN -> session.copy(
            title = session.title + " (verkürzt)", reduced = true,
            minutes = max(20, (session.minutes * 0.7).roundToInt()), distanceKm = session.distanceKm?.let { roundHalf(it * 0.7) },
        )
        else -> session.copy(minutes = max(10, (session.minutes * 0.7).roundToInt()), reduced = true)
    }

    // ------------------------------------------------------------------
    private data class Quad(val type: SessionType, val title: String, val details: String, val minutes: Int)

    private fun session(day: LocalDate, type: SessionType, km: Double, pace: Double, w: Int, recovery: Boolean, title: String, details: String) =
        TrainingSession(day, type, ceil(km * pace + 5).toInt(), km, title, details + " (ca. ${ceil(km * pace).toInt()} Min)", w, recovery)

    private fun prefOrder(day: DayOfWeek, order: List<DayOfWeek>) = order.indexOf(day).let { if (it < 0) 99 else it }
    private fun roundHalf(v: Double) = (v * 2).roundToInt() / 2.0
    private fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString().replace('.', ',')
}
