package app.lifedashboard.planner

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Ein Thema einer Klausur mit geschätztem Aufwand und Fortschritt. */
data class StudyTopic(
    val id: Long,
    val title: String,
    val effortMinutes: Int,
    /** 0–100 */
    val progress: Int,
)

data class StudyExam(
    val id: Long,
    val subjectName: String,
    val date: LocalDate,
    /** 1 = leicht … 5 = schwer */
    val difficulty: Int,
    val topics: List<StudyTopic>,
    /** Lernzeit, wenn (noch) keine Themen eingetragen sind – ein Test braucht weniger als eine Klausur. */
    val defaultMinutes: Int = StudyPlanner.DEFAULT_MINUTES_WITHOUT_TOPICS,
    /** Zuschlag, wenn das Fach bisher unter dem Ziel liegt (1,0 = kein Zuschlag). */
    val effortFactor: Double = 1.0,
)

/** Wie realistisch ist der Lernplan einer Klausur? */
enum class PlanHealth { DONE, ON_TRACK, TIGHT, SHORT }

data class StudyForecast(
    val examId: Long,
    /** Lernzeit, die noch nötig ist */
    val neededMinutes: Int,
    /** Davon bis zur Klausur eingeplant */
    val plannedMinutes: Int,
    val firstDay: LocalDate?,
    /** Minuten pro Tag (inkl. Wiederholung) – für die kleine Vorschau */
    val perDay: List<Pair<LocalDate, Int>>,
    val health: PlanHealth,
) {
    val missingMinutes: Int get() = (neededMinutes - plannedMinutes).coerceAtLeast(0)
}

/** Eine geplante Lerneinheit an einem Tag. */
data class StudyAllocation(
    val date: LocalDate,
    val examId: Long,
    val topicId: Long?,
    val title: String,
    val minutes: Int,
    val isReview: Boolean,
)

/**
 * Verteilt Lernzeit rückwärts von Klausurterminen.
 *
 * Regeln:
 * - Restaufwand = Σ Aufwand × (1 − Fortschritt) × Schwierigkeitsfaktor (leicht 0,8 … schwer 1,4)
 * - Gelernt wird in den [leadDays] Tagen vor der Klausur, mit Steigerung zum Ende hin
 * - Der Tag direkt vor der Klausur ist nur Wiederholung (kurz, alle Themen)
 * - Pro Tag gibt es eine Obergrenze [dailyCapMinutes] über alle Klausuren
 * - Blöcke werden auf [blockMinutes] (Pomodoro) gerundet
 */
object StudyPlanner {

    fun difficultyFactor(difficulty: Int): Double = when (difficulty.coerceIn(1, 5)) {
        1 -> 0.8
        2 -> 0.9
        3 -> 1.0
        4 -> 1.2
        else -> 1.4
    }

    /** Ohne eingetragene Themen: pauschal 4 Std Lernzeit, damit der Plan trotzdem startet. */
    const val DEFAULT_MINUTES_WITHOUT_TOPICS = 240

    fun remainingMinutes(exam: StudyExam): Int = if (exam.topics.isEmpty()) {
        (exam.defaultMinutes * difficultyFactor(exam.difficulty) * exam.effortFactor).roundToInt()
    } else exam.topics.sumOf { topic ->
        topic.effortMinutes * (100 - topic.progress.coerceIn(0, 100)) / 100.0
    }.let { (it * difficultyFactor(exam.difficulty) * exam.effortFactor).roundToInt() }

    /** Liegt der Schnitt unter dem Ziel, gibt es bis zu 30 % mehr Lernzeit (pro fehlendem Punkt 6 %). */
    fun effortFactor(averagePoints: Double?, targetPoints: Double): Double =
        if (averagePoints == null) 1.0 else (1.0 + (targetPoints - averagePoints).coerceIn(0.0, 5.0) * 0.06)

    fun plan(
        exams: List<StudyExam>,
        today: LocalDate,
        leadDays: Int = 10,
        dailyCapMinutes: Int = 120,
        blockMinutes: Int = 25,
        reviewMinutes: Int = 25,
        /** Längster Vorlauf, wenn die Zeit in [leadDays] nicht reicht */
        maxLeadDays: Int = 21,
        /** Wie viele Minuten an diesem Tag realistisch zum Lernen frei sind (Training, Job, langer Schultag …) */
        capacity: (LocalDate) -> Int = { dailyCapMinutes },
    ): List<StudyAllocation> {
        fun cap(date: LocalDate) = minOf(dailyCapMinutes, capacity(date)).coerceAtLeast(0)
        val upcoming = exams.filter { it.date.isAfter(today) }.sortedBy { it.date }
        val used = mutableMapOf<LocalDate, Int>()
        val result = mutableListOf<StudyAllocation>()

        // Früheste Klausur zuerst, damit sie die Kapazität zuerst bekommt
        for (exam in upcoming) {
            val dayBefore = exam.date.minusDays(1)
            // 1) Wiederholung am Vortag (falls nicht heute schon der Vortag vorbei ist)
            if (!dayBefore.isBefore(today)) {
                val minutes = minOf(reviewMinutes, maxOf(cap(dayBefore), reviewMinutes) - (used[dayBefore] ?: 0))
                if (minutes > 0) {
                    result += StudyAllocation(dayBefore, exam.id, null, "Wiederholung ${exam.subjectName}", minutes, isReview = true)
                    used[dayBefore] = (used[dayBefore] ?: 0) + minutes
                }
            }

            // 2) Lerntage: von max(heute, Klausur − leadDays) bis Vortag − 1
            var firstDay = maxOf(today, exam.date.minusDays(leadDays.toLong()))
            val lastDay = dayBefore.minusDays(1)
            // Reicht die freie Zeit im normalen Vorlauf nicht, fängt der Plan früher an (statt Lernzeit zu streichen)
            if (!lastDay.isBefore(firstDay)) {
                val needed = remainingMinutes(exam)
                fun room(from: LocalDate): Int {
                    var sum = 0
                    var d = from
                    while (!d.isAfter(lastDay)) {
                        sum += roundDownToBlocks(cap(d) - (used[d] ?: 0), blockMinutes).coerceAtLeast(0)
                        d = d.plusDays(1)
                    }
                    return sum
                }
                val earliest = maxOf(today, exam.date.minusDays(maxLeadDays.toLong()))
                while (firstDay.isAfter(earliest) && room(firstDay) < needed) firstDay = firstDay.minusDays(1)
            }
            if (lastDay.isBefore(firstDay)) {
                // Zu knapp (Klausur morgen): heute = Vortag → so viel lernen wie das Tageslimit erlaubt
                if (!dayBefore.isBefore(today)) {
                    val left = roundDownToBlocks(maxOf(cap(dayBefore), blockMinutes * 2) - (used[dayBefore] ?: 0), blockMinutes)
                    val minutes = minOf(left, roundToBlocks(remainingMinutes(exam), blockMinutes))
                    if (minutes > 0) {
                        val topic = exam.topics.firstOrNull { it.progress < 100 }
                        result += StudyAllocation(dayBefore, exam.id, topic?.id, "${exam.subjectName}: ${topic?.title ?: "lernen"}", minutes, isReview = false)
                        used[dayBefore] = (used[dayBefore] ?: 0) + minutes
                    }
                }
                continue
            }
            val days = ChronoUnit.DAYS.between(firstDay, lastDay).toInt() + 1
            var remaining = remainingMinutes(exam)
            if (remaining <= 0) continue

            // Gewichte steigen linear: Tag 1 = 1, letzter Tag = 2
            val weights = (0 until days).map { 1.0 + it.toDouble() / maxOf(1, days - 1) }
            val openTopics = exam.topics.filter { it.progress < 100 }.toMutableList()
            var topicIndex = 0

            for (i in 0 until days) {
                if (remaining <= 0) break
                val date = firstDay.plusDays(i.toLong())
                val target = ceil(remaining * weights[i] / weights.drop(i).sum().coerceAtLeast(weights[i]))
                    .let { roundToBlocks(it.toInt().coerceAtLeast(blockMinutes), blockMinutes) }
                val free = cap(date) - (used[date] ?: 0)
                val minutes = minOf(target, roundDownToBlocks(free, blockMinutes), roundToBlocks(remaining, blockMinutes))
                if (minutes <= 0) continue
                val topic = openTopics.getOrNull(topicIndex % maxOf(1, openTopics.size))
                topicIndex++
                result += StudyAllocation(
                    date = date,
                    examId = exam.id,
                    topicId = topic?.id,
                    title = "${exam.subjectName}: ${topic?.title ?: "lernen"}",
                    minutes = minutes,
                    isReview = false,
                )
                used[date] = (used[date] ?: 0) + minutes
                remaining -= minutes
            }
        }
        return result.sortedWith(compareBy({ it.date }, { it.isReview }))
    }

    /** Einschätzung je Klausur: passt der Plan, wird es eng, oder fehlt Zeit? */
    fun forecast(exams: List<StudyExam>, plan: List<StudyAllocation>, today: LocalDate, blockMinutes: Int = 25): Map<Long, StudyForecast> =
        exams.filter { it.date.isAfter(today) }.associate { exam ->
            val mine = plan.filter { it.examId == exam.id }
            val learn = mine.filter { !it.isReview }
            val needed = remainingMinutes(exam)
            val planned = learn.sumOf { it.minutes }
            val perDay = mine.groupBy { it.date }.map { (d, l) -> d to l.sumOf { it.minutes } }.sortedBy { it.first }
            val health = when {
                needed <= 0 -> PlanHealth.DONE
                // weniger als ein Block Unterschied ist nur Rundung
                needed - planned >= blockMinutes -> PlanHealth.SHORT
                learn.isNotEmpty() && planned / learn.map { it.date }.toSet().size >= 75 -> PlanHealth.TIGHT
                else -> PlanHealth.ON_TRACK
            }
            exam.id to StudyForecast(exam.id, needed, planned, mine.minOfOrNull { it.date }, perDay, health)
        }

    private fun roundToBlocks(minutes: Int, block: Int): Int = ((minutes + block - 1) / block) * block
    private fun roundDownToBlocks(minutes: Int, block: Int): Int = (minutes / block) * block
}

/** Wie gut lief die Wiederholung? */
enum class ReviewRating { HARD, OK, EASY }

/**
 * Einfache Spaced Repetition (angelehnt an SM-2): Intervalle 1 → 3 → 7 → 14 → 30 Tage.
 * „Schwer“ setzt zurück, „leicht“ springt eine Stufe weiter.
 */
object SpacedRepetition {
    private val steps = listOf(1, 3, 7, 14, 30)

    fun nextInterval(currentIntervalDays: Int, rating: ReviewRating): Int {
        val index = steps.indexOfLast { it <= currentIntervalDays }.coerceAtLeast(-1)
        val next = when (rating) {
            ReviewRating.HARD -> 0
            ReviewRating.OK -> index + 1
            ReviewRating.EASY -> index + 2
        }
        return steps[next.coerceIn(0, steps.lastIndex)]
    }
}

/** Notenpunkte (0–15) ↔ Note, Durchschnitt mit Gewichten. */
object Grades {
    /** Gewichteter Durchschnitt in Punkten, null wenn keine Noten. */
    fun average(points: List<Pair<Int, Double>>): Double? {
        val total = points.sumOf { it.second }
        if (points.isEmpty() || total <= 0.0) return null
        return points.sumOf { it.first * it.second } / total
    }

    /**
     * Umrechnung Durchschnittspunkte → Note nach der üblichen Formel (17 − P) / 3,
     * begrenzt auf 1,0–6,0 (grobe Orientierung, nicht die offizielle Abi-Berechnung).
     */
    fun pointsToGrade(points: Double): Double = ((17.0 - points) / 3.0).coerceIn(1.0, 6.0)

    /** Punkte, die man im Schnitt für eine Zielnote braucht (z. B. 1,7 → 11,9). */
    fun pointsForGrade(grade: Double): Double = (17.0 - 3.0 * grade).coerceIn(0.0, 15.0)
}
