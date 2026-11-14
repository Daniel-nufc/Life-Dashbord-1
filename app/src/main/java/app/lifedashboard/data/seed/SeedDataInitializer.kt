package app.lifedashboard.data.seed

import androidx.room.withTransaction
import app.lifedashboard.data.db.AppDatabase
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.RoutineBlockEntity
import app.lifedashboard.data.db.SchoolPeriodEntity
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.TimetableLessonEntity
import app.lifedashboard.data.db.AccountEntity
import app.lifedashboard.data.db.AccountKind
import app.lifedashboard.data.db.GoalEntity
import app.lifedashboard.data.db.RecurringEntity
import app.lifedashboard.data.db.HabitEntity
import app.lifedashboard.data.db.MilestoneEntity
import app.lifedashboard.data.db.TaskEntity
import app.lifedashboard.data.db.TaskKind
import app.lifedashboard.data.db.WeekPattern
import app.lifedashboard.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trägt beim allerersten Start deine echten Startdaten ein:
 * Stundenplan (A/B-Woche, Stand KW 46/47 2026) und Floorball-Training.
 * Alles kann danach in der App geändert werden.
 */
@Singleton
class SeedDataInitializer @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    suspend fun seedIfNeeded() {
        val version = settings.settings.first().seedVersion
        if (version < 1) {
            seedTimetable()
            settings.setSeedVersion(1)
        }
        if (version < 2) {
            seedEtsyTasks()
            settings.setSeedVersion(2)
        }
        if (version < 3) {
            seedGoalsAndHabits()
            settings.setSeedVersion(3)
        }
        if (version < 4) {
            seedFinance()
            settings.setSeedVersion(4)
        }
        if (version < 5) {
            seedEtsyDesigns()
            settings.setSeedVersion(5)
        }
        if (version < 6) {
            seedHolidays()
            settings.setSeedVersion(6)
        }
        if (version < 7) {
            seedAutomation()
            settings.setSeedVersion(7)
        }
        if (version < 8) {
            // Update 20: Sport nur noch als freie Zeitfenster – alte Einheiten-Vorschläge (Intervalle, langer Lauf …) entfernen
            db.planDao().deleteOldTrainingBlocks(java.time.LocalDate.now().toEpochDay())
            settings.setSeedVersion(8)
        }
        if (version < 9) {
            addMissingHolidays()
            settings.setSeedVersion(9)
        }
    }

    /** Update 36: Ferien/Feiertage 2027/28 (vorläufig) ergänzen – vorhandene und selbst geänderte bleiben unangetastet. */
    private suspend fun addMissingHolidays() {
        val dao = db.holidayDao()
        val existing = dao.all()
        if (existing.isEmpty()) return // frische Installation: seedHolidays hat schon alles angelegt
        val known = existing.map { it.startEpochDay to it.kind }.toSet()
        val missing = app.lifedashboard.planner.BerlinSchoolCalendar.entries.map {
            app.lifedashboard.data.db.SchoolHolidayEntity(
                name = it.name,
                startEpochDay = it.range.start.toEpochDay(),
                endEpochDay = it.range.endInclusive.toEpochDay(),
                kind = if (it.publicHoliday) app.lifedashboard.data.db.HolidayKind.PUBLIC_HOLIDAY else app.lifedashboard.data.db.HolidayKind.SCHOOL_HOLIDAY,
            )
        }.filter { (it.startEpochDay to it.kind) !in known }
        if (missing.isNotEmpty()) dao.insertAll(missing)
    }

    /** Automatik: Gewohnheiten-Regeln, Führerschein-Schritte, Sparziele. */
    private suspend fun seedAutomation() {
        val goals = db.goalDao()
        goals.observeHabits().first().forEach { h ->
            val rule = when {
                h.emoji == "🏃" || h.title.contains("Bewegung", true) -> "STEPS"
                h.emoji == "😴" || h.title.contains("schlafen", true) -> "SLEEP_PHONE"
                h.emoji == "📵" || h.title.contains("Handyzeit", true) -> "SCREEN_SOCIAL"
                else -> null
            }
            if (rule != null && h.autoRule == null) goals.upsertHabit(h.copy(autoRule = rule))
        }
        // Führerschein: Sehtest und Erste-Hilfe-Kurs ergänzen
        val license = goals.observeGoals().first().firstOrNull { it.title.contains("Führerschein", true) }
        if (license != null) {
            val steps = goals.observeMilestones().first().filter { it.goalId == license.id }
            if (steps.none { it.title.contains("Sehtest", true) }) {
                val shifted = steps.map { it.copy(sortOrder = it.sortOrder + 2) }
                shifted.forEach { goals.upsertMilestone(it) }
                goals.insertMilestones(
                    listOf(
                        MilestoneEntity(goalId = license.id, title = "Sehtest (Optiker, ca. 7 €)", sortOrder = 0, done = false),
                        MilestoneEntity(goalId = license.id, title = "Erste-Hilfe-Kurs (1 Tag)", sortOrder = 1, done = false),
                    ),
                )
            }
        }
        // Sparziele (Vorschlag aus dem Fragebogen) – nur wenn noch keine da sind
        val finance = db.financeDao()
        if (finance.observeSavings().first().isEmpty()) {
            finance.upsertSavings(app.lifedashboard.data.db.SavingsGoalEntity(title = "Führerschein", targetCents = 200_000, savedCents = 0, deadlineEpochDay = java.time.LocalDate.of(2027, 12, 31).toEpochDay()))
            finance.upsertSavings(app.lifedashboard.data.db.SavingsGoalEntity(title = "Feriencamp", targetCents = 40_000, savedCents = 0, deadlineEpochDay = java.time.LocalDate.of(2027, 6, 1).toEpochDay()))
        }
    }

    /** Berliner Ferien und Feiertage 2026/27 (in der App änderbar). */
    private suspend fun seedHolidays() {
        val dao = db.holidayDao()
        if (dao.all().isNotEmpty()) return
        dao.insertAll(
            app.lifedashboard.planner.BerlinSchoolCalendar.entries.map {
                app.lifedashboard.data.db.SchoolHolidayEntity(
                    name = it.name,
                    startEpochDay = it.range.start.toEpochDay(),
                    endEpochDay = it.range.endInclusive.toEpochDay(),
                    kind = if (it.publicHoliday) app.lifedashboard.data.db.HolidayKind.PUBLIC_HOLIDAY else app.lifedashboard.data.db.HolidayKind.SCHOOL_HOLIDAY,
                )
            },
        )
    }

    /** Winter-Set (fertig, Probedruck bei Oma steht noch aus). */
    private suspend fun seedEtsyDesigns() {
        val now = System.currentTimeMillis()
        fun design(title: String, page: Int) = app.lifedashboard.data.db.EtsyDesignEntity(
            setName = "Winter-Set",
            title = title,
            status = app.lifedashboard.data.db.DesignStatus.CHECKED,
            canvaRef = "Canva DAHZ_AX8P3M, Seite $page",
            note = "Tassen-Wrap 11 oz, 2850 × 1200 px, PNG (~224 dpi)",
            createdAtMillis = now,
        )
        db.etsyDao().insertDesigns(
            listOf(
                design("Rotkehlchen mit Laterne", 1),
                design("Hütte im Schnee mit Brücke", 2),
                design("Fuchs mit Strickschal", 3),
            ),
        )
    }

    /** Konten DKB + Bargeld und dein Taschengeld (Eltern 20 € am 21., Opa 20 € bar). */
    private suspend fun seedFinance() {
        val dao = db.financeDao()
        if (dao.accounts().isNotEmpty()) return
        val dkb = dao.upsertAccount(AccountEntity(name = "DKB Girokonto", kind = AccountKind.BANK, initialBalanceCents = 0, sortOrder = 0))
        val cash = dao.upsertAccount(AccountEntity(name = "Bargeld", kind = AccountKind.CASH, initialBalanceCents = 0, sortOrder = 1))
        val today = java.time.LocalDate.now().toEpochDay()
        dao.upsertRecurring(RecurringEntity(accountId = dkb, title = "Taschengeld Eltern", amountCents = 2000, dayOfMonth = 21, category = "Taschengeld", active = true, lastGeneratedEpochDay = today))
        dao.upsertRecurring(RecurringEntity(accountId = cash, title = "Taschengeld Opa", amountCents = 2000, dayOfMonth = 1, category = "Taschengeld", active = true, lastGeneratedEpochDay = today))
    }

    /** Deine Gewohnheiten und Ziele aus dem Fragebogen (08.11.2026). */
    private suspend fun seedGoalsAndHabits() {
        val dao = db.goalDao()
        dao.insertHabits(
            listOf(
                HabitEntity(title = "Pünktlich schlafen (22 Uhr, Handy weg)", emoji = "😴", area = LifeArea.PRIVATE, active = true, sortOrder = 0),
                HabitEntity(title = "Handyzeit unter 2 Std", emoji = "📵", area = LifeArea.PRIVATE, active = true, sortOrder = 1),
                HabitEntity(title = "10 Min Bewegung", emoji = "🏃", area = LifeArea.SPORT, active = true, sortOrder = 2),
                HabitEntity(title = "Lesen / Vokabeln 10 Min", emoji = "📖", area = LifeArea.SCHOOL, active = true, sortOrder = 3),
            ),
        )
        suspend fun goal(area: LifeArea, title: String, description: String?, order: Int, steps: List<Pair<String, Boolean>>) {
            val id = dao.insertGoal(GoalEntity(area = area, title = title, description = description, deadlineEpochDay = null, done = false, sortOrder = order))
            dao.insertMilestones(steps.mapIndexed { i, (t, d) -> MilestoneEntity(goalId = id, title = t, sortOrder = i, done = d) })
        }
        goal(
            LifeArea.SPORT, "Ironman", "Immer nur der nächste Schritt – nicht das ganze Ziel auf einmal.", 0,
            listOf(
                "5 km laufen" to true,
                "10 km laufen" to false,
                "Sprint-Triathlon (0,75 km / 20 km / 5 km)" to false,
                "Olympische Distanz (1,5 / 40 / 10)" to false,
                "Mitteldistanz (1,9 / 90 / 21,1)" to false,
                "Ironman (3,8 / 180 / 42,2)" to false,
            ),
        )
        goal(
            LifeArea.SCHOOL, "Abi mit 1,7", "≈ 12 Punkte im Schnitt. Abi voraussichtlich 2029.", 1,
            listOf(
                "11. Klasse (Einführungsphase) gut abschließen" to false,
                "Leistungskurse / Prüfungsfächer wählen" to false,
                "Q1 & Q2 (12. Klasse)" to false,
                "Q3 & Q4 (13. Klasse)" to false,
                "Abiturprüfungen" to false,
            ),
        )
        goal(
            LifeArea.ETSY, "Etsy-Shop starten", null, 2,
            listOf(
                "Etsy-Account erstellen" to false,
                "Probedruck bei Oma + Feedback" to false,
                "Erstes Listing online (Winter-Set)" to false,
                "Erste Bestellung" to false,
            ),
        )
        goal(
            LifeArea.PRIVATE, "Führerschein", "Über die Fahrschule vom Onkel.", 3,
            listOf("Anmelden" to false, "Theorieprüfung" to false, "Fahrstunden" to false, "Praktische Prüfung" to false),
        )
        goal(LifeArea.PRIVATE, "Feriencamp", null, 4, listOf("Camp aussuchen" to false, "Anmelden & bezahlen" to false))
    }

    /** Offene Punkte aus der Etsy-Zusammenfassung vom 08.11.2026. */
    private suspend fun seedEtsyTasks() {
        val now = System.currentTimeMillis()
        fun etsy(title: String, minutes: Int, note: String? = null) = TaskEntity(
            title = title,
            area = LifeArea.ETSY,
            kind = TaskKind.TODO,
            subjectId = null,
            dueEpochDay = null,
            estimatedMinutes = minutes,
            done = false,
            doneAtMillis = null,
            note = note,
            createdAtMillis = now,
        )
        db.taskDao().insertAll(
            listOf(
                etsy("Probedruck bei Oma: Größe, Farben, Schärfe prüfen", 60, "Winter-Set: Rotkehlchen, Hütte, Fuchs"),
                etsy("Google-Lens-Bildersuche + Vergleich mit Konkurrenz-Set", 30),
                etsy("Mockups (Motiv auf Tasse) + Titelbild fürs Paket", 90),
                etsy("15-oz-Datei erstellen", 45, "„15oz“ erst im Titel nennen, wenn die Datei da ist"),
                etsy("Entscheiden: Tagesaufgabe bis zu Omas Feedback pausieren?", 10),
                etsy("Etsy-Account erstellen", 30),
            ),
        )
    }

    private suspend fun seedTimetable() {
        val dao = db.timetableDao()
        if (dao.countSubjects() > 0) return

        db.withTransaction {
            dao.insertPeriods(
                listOf(
                    SchoolPeriodEntity(1, 8 * 60, 9 * 60 + 30),
                    SchoolPeriodEntity(2, 9 * 60 + 50, 11 * 60 + 20),
                    SchoolPeriodEntity(3, 11 * 60 + 40, 13 * 60 + 10),
                    SchoolPeriodEntity(4, 13 * 60 + 30, 15 * 60),
                ),
            )

            val subjects = listOf(
                SubjectEntity(code = "MA-B", name = "Mathematik", colorArgb = 0xFF2E8BF0),
                SubjectEntity(code = "DE-B", name = "Deutsch", colorArgb = 0xFFE53935),
                SubjectEntity(code = "E-B", name = "Englisch", colorArgb = 0xFFEC6FA8),
                SubjectEntity(code = "GeWi-B", name = "Gesellschaftswissenschaften", colorArgb = 0xFFB8893A),
                SubjectEntity(code = "BIO", name = "Biologie", colorArgb = 0xFFC9B23E),
                SubjectEntity(code = "BIO-Pro", name = "Biologie (Pro)", colorArgb = 0xFF9E8A1F),
                SubjectEntity(code = "CH", name = "Chemie", colorArgb = 0xFFF29D38),
                SubjectEntity(code = "SP", name = "Sport", colorArgb = 0xFF4DB6AC),
                SubjectEntity(code = "DS", name = "Darstellendes Spiel", colorArgb = 0xFF90A4AE),
                SubjectEntity(code = "TuMM-B", name = "Technik & Management – Management", colorArgb = 0xFF64B5F6),
                SubjectEntity(code = "TuMT-B", name = "Technik & Management – Technik", colorArgb = 0xFF5C6BC0),
                SubjectEntity(code = "PM", name = "Projektmanagement", colorArgb = 0xFF7E57C2),
            )
            val ids = dao.insertSubjects(subjects)
            val idByCode = subjects.map { it.code }.zip(ids).toMap()

            fun lesson(day: Int, period: Int, code: String, room: String, pattern: WeekPattern = WeekPattern.EVERY) =
                TimetableLessonEntity(
                    subjectId = idByCode.getValue(code),
                    dayOfWeek = day,
                    period = period,
                    weekPattern = pattern,
                    room = room,
                )

            dao.insertLessons(
                listOf(
                    // Montag
                    lesson(1, 1, "DE-B", "F309", WeekPattern.A),
                    lesson(1, 1, "MA-B", "F309", WeekPattern.B),
                    lesson(1, 2, "BIO", "F305"),
                    lesson(1, 3, "GeWi-B", "E207"),
                    lesson(1, 4, "DS", "G025"),
                    // Dienstag
                    lesson(2, 1, "TuMM-B", "G112"),
                    lesson(2, 2, "TuMM-B", "G112"),
                    lesson(2, 3, "TuMT-B", "G113"),
                    lesson(2, 4, "TuMT-B", "G113"),
                    // Mittwoch
                    lesson(3, 1, "BIO-Pro", "F305"),
                    lesson(3, 2, "SP", "GYM"),
                    lesson(3, 3, "DE-B", "F309"),
                    lesson(3, 4, "CH", "F305"),
                    // Donnerstag
                    lesson(4, 1, "E-B", "F309"),
                    // Freitag
                    lesson(5, 1, "MA-B", "F309"),
                    lesson(5, 2, "PM", "F210"),
                    lesson(5, 3, "E-B", "F309", WeekPattern.A),
                    lesson(5, 3, "TuMT-B", "G113", WeekPattern.B),
                    lesson(5, 4, "GeWi-B", "E207", WeekPattern.A),
                ),
            )

            db.routineDao().insertAll(
                listOf(
                    RoutineBlockEntity(
                        title = "Floorball",
                        area = LifeArea.SPORT,
                        daysOfWeek = "1,3",
                        startMinute = 18 * 60,
                        endMinute = 20 * 60 + 15,
                        location = "Berliner TSC",
                        note = "Training 18:30–20:00 · Abfahrt 18:00",
                    ),
                ),
            )
        }
    }
}
