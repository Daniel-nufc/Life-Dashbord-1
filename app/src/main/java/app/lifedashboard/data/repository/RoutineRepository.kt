package app.lifedashboard.data.repository

import app.lifedashboard.data.db.RoutineBlockEntity
import app.lifedashboard.data.db.RoutineDao
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class RoutineRepository @Inject constructor(
    private val dao: RoutineDao,
) {
    val routines: Flow<List<RoutineBlockEntity>> = dao.observeAll()

    suspend fun save(block: RoutineBlockEntity) = dao.upsert(block)
}

fun List<RoutineBlockEntity>.forDay(day: DayOfWeek): List<RoutineBlockEntity> =
    filter { it.active && day in it.days() }
