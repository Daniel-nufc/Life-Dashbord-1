package app.lifedashboard.data.seed

import androidx.room.withTransaction
import app.lifedashboard.data.db.AppDatabase
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.RoutineBlockEntity
import app.lifedashboard.data.db.SchoolPeriodEntity
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.TimetableLessonEntity
import app.lifedashboard.data.db.WeekPattern
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
) {
    suspend fun seedIfNeeded() {
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
