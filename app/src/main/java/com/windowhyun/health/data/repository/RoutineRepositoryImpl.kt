package com.windowhyun.health.data.repository

import com.windowhyun.health.data.local.dao.RoutineDao
import com.windowhyun.health.data.mapper.toDayBit
import com.windowhyun.health.data.mapper.toDomain
import com.windowhyun.health.data.mapper.toEntity
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineRepositoryImpl @Inject constructor(
    private val routineDao: RoutineDao,
) : RoutineRepository {

    override fun observeRoutines(): Flow<List<Routine>> =
        routineDao.observeRoutines().map { list -> list.map { it.toDomain() } }

    override fun observeRoutine(id: Long): Flow<Routine?> =
        routineDao.observeRoutine(id).map { it?.toDomain() }

    override fun observeRoutinesForDay(day: DayOfWeek): Flow<List<Routine>> =
        routineDao.observeRoutinesForDay(day.toDayBit()).map { list -> list.map { it.toDomain() } }

    override suspend fun getRoutine(id: Long): Routine? = routineDao.getRoutine(id)?.toDomain()

    override suspend fun saveRoutine(routine: Routine): Long = routineDao.upsertRoutineWithItems(
        routine = routine.toEntity(),
        items = routine.items.mapIndexed { index, item -> item.toEntity(routine.id).copy(orderIndex = index) },
    )

    override suspend fun deleteRoutine(id: Long) = routineDao.deleteRoutine(id)
}
