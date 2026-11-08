package app.lifedashboard.data.repository

import app.lifedashboard.data.db.HolidayDao
import app.lifedashboard.data.db.SchoolHolidayEntity
import app.lifedashboard.planner.DateRange
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun SchoolHolidayEntity.range() = DateRange(LocalDate.ofEpochDay(startEpochDay), LocalDate.ofEpochDay(endEpochDay))

/** Ferien, Feiertage und eigene schulfreie Tage. */
@Singleton
class SchoolCalendarRepository @Inject constructor(private val dao: HolidayDao) {
    val holidays: Flow<List<SchoolHolidayEntity>> = dao.observeAll()
    val ranges: Flow<List<DateRange>> = holidays.map { list -> list.map { it.range() } }

    suspend fun currentRanges(): List<DateRange> = dao.all().map { it.range() }
    suspend fun save(item: SchoolHolidayEntity) = dao.upsert(item)
    suspend fun delete(id: Long) = dao.delete(id)
}
