package app.lifedashboard.data.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.PlanStatus
import app.lifedashboard.data.db.TaskKind
import app.lifedashboard.data.planning.PlanningEngine
import app.lifedashboard.data.repository.AgendaRepository
import app.lifedashboard.data.repository.FinanceRepository
import app.lifedashboard.data.repository.StudyRepository
import app.lifedashboard.planner.minuteOfDay
import app.lifedashboard.ui.components.minutesToTimeText
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Läuft etwa alle 30 Minuten im Hintergrund und entscheidet, ob eine Erinnerung sinnvoll ist.
 * Ton: Coach, der pusht – aber nie bestraft.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: Notifier,
    private val agenda: AgendaRepository,
    private val engine: PlanningEngine,
    private val study: StudyRepository,
    private val finance: FinanceRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { check() }
        app.lifedashboard.widget.updateWidgets(applicationContext)
        return Result.success()
    }

    private suspend fun check() {
        val today = LocalDate.now()
        val now = LocalTime.now()
        val nowMin = now.minuteOfDay()
        val range = agenda.observe(today, 2).first()
        val day = range.days.first()
        val weekend = today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY
        val name = range.settings.userName

        // 1) Morgen-Zusammenfassung (6:15–8:00, am Wochenende ab 9:45)
        val morningStart = if (weekend) 9 * 60 + 45 else 6 * 60 + 15
        if (nowMin in morningStart..(morningStart + 105)) {
            runCatching { engine.replan(today) }
            val plan = engine.observeDay(today).first().filter { it.status == PlanStatus.PROPOSED || it.status == PlanStatus.ACCEPTED }
            val dueTomorrow = range.allTasks.count { !it.done && it.dueEpochDay == today.toEpochDay() + 1 }
            val training = day.routines.firstOrNull { it.area == LifeArea.SPORT }
            val parts = buildList {
                if (day.lessons.isNotEmpty()) add("Schule bis ${day.lessons.mapNotNull { it.timeRange }.maxOf { it.end }}")
                if (plan.isNotEmpty()) add("${plan.size} Blöcke geplant")
                if (dueTomorrow > 0) add("$dueTomorrow fällig bis morgen")
                training?.let { add("${it.title} ${minutesToTimeText(it.startMinute)}") }
            }
            notifier.send(
                "morning", Channel.PLAN,
                "Guten Morgen, $name ☀️",
                (if (parts.isEmpty()) "Heute ist viel frei." else parts.joinToString(" · ")) + " – schau kurz in deinen Tagesplan.",
                ignoreLimit = true,
            )
        }

        // 2) Gleich geht's los: geplanter Block in 5–35 Minuten
        engine.observeDay(today).first()
            .filter { (it.status == PlanStatus.ACCEPTED || it.status == PlanStatus.PROPOSED) && it.startMinute - nowMin in 5..35 }
            .firstOrNull()?.let { block ->
                notifier.send(
                    "block:${block.id}", Channel.PLAN,
                    "Um ${minutesToTimeText(block.startMinute)}: ${block.title}",
                    "Nur anfangen – 5 Minuten reichen für den Start. Der Rest kommt von allein. 💪",
                )
            }

        // 3) Hausaufgaben für morgen (ab 16 Uhr, einmal pro Tag)
        if (now.hour >= 16) {
            val hw = range.allTasks.filter { !it.done && it.kind == TaskKind.HOMEWORK && it.dueEpochDay == today.toEpochDay() + 1 }
            if (hw.isNotEmpty()) {
                notifier.send(
                    "homework", Channel.SCHOOL,
                    "${hw.size} ${if (hw.size == 1) "Hausaufgabe" else "Hausaufgaben"} bis morgen",
                    hw.take(3).joinToString(", ") { it.title } + ". Jetzt erledigen, dann ist der Abend frei.",
                )
            }
        }

        // 4) Klausur in 7 / 3 / 1 Tagen (morgens)
        if (now.hour in 7..19) {
            study.overview(today).first().upcoming.forEach { exam ->
                val days = exam.daysLeft(today)
                if (days in setOf(7, 3, 1)) {
                    notifier.send(
                        "exam:${exam.exam.id}:$days", Channel.SCHOOL,
                        "${exam.subject?.name ?: "Klausur"} in $days ${if (days == 1) "Tag" else "Tagen"}",
                        when (days) {
                            1 -> "Heute nur wiederholen, nichts Neues mehr. Früh schlafen – du bist vorbereitet."
                            3 -> "Endspurt: Lernstand ${exam.progress} %. Die schwächsten Themen zuerst."
                            else -> "Noch eine Woche. Der Lernplan ist verteilt – jeden Tag ein Block reicht."
                        },
                    )
                }
            }
        }

        // 5) Training heute (ca. 1–2 Std vorher)
        day.routines.filter { it.area == LifeArea.SPORT && it.startMinute - nowMin in 60..120 }.forEach { r ->
            notifier.send("training:${r.id}", Channel.SPORT, "Heute ${r.title} um ${minutesToTimeText(r.startMinute)}", "Tasche packen, was trinken – los geht's! 🏑")
        }

        // 6) Budget zu 80 % erreicht (einmal pro Kategorie und Monat)
        if (now.hour in 10..20) {
            runCatching { finance.budgetsNow() }.getOrDefault(emptyList()).filter { it.ratio >= 0.8f }.forEach { b ->
                val month = today.toString().take(7)
                notifier.send(
                    "once:budget:${b.category}:$month", Channel.MONEY,
                    "Budget ${b.category}: ${(b.ratio * 100).toInt()} %",
                    if (b.ratio >= 1f) "Budget aufgebraucht – bis Monatsende bitte nichts mehr in dieser Kategorie." else "Noch etwas Luft, aber pass auf. Dein Puffer bleibt geschützt.",
                )
            }
        }

        // 7) Wochenrückblick sonntags ab 18 Uhr
        if (today.dayOfWeek == DayOfWeek.SUNDAY && now.hour in 18..21) {
            notifier.send("review", Channel.REVIEW, "Dein Wochenrückblick ist da 📊", "Was lief gut, was nicht – und ein Plan für die neue Woche. Dauert 2 Minuten.", ignoreLimit = true)
        }
    }

    companion object {
        private const val NAME = "reminders"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
