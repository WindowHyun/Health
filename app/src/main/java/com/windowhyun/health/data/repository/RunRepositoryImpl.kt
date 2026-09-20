package com.windowhyun.health.data.repository

import com.windowhyun.health.data.local.dao.RunDao
import com.windowhyun.health.data.local.entity.RunEntity
import com.windowhyun.health.data.local.entity.RunLapEntity
import com.windowhyun.health.data.local.entity.RunLocationEntity
import com.windowhyun.health.data.mapper.toDomain
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.repository.RunRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Phase 2 에서 러닝 서비스가 사용한다. Phase 1 에서는 홈의 주간 합계에만 쓰인다. */
@Singleton
class RunRepositoryImpl @Inject constructor(
    private val runDao: RunDao,
) : RunRepository {

    override fun observeRecentRuns(limit: Int): Flow<List<Run>> =
        runDao.observeRecentRuns(limit).map { list -> list.map { it.toDomain() } }

    override fun observeRunsBetween(from: LocalDate, to: LocalDate): Flow<List<Run>> =
        runDao.observeRunsBetween(from.toEpochDay(), to.toEpochDay())
            .map { list -> list.map { it.toDomain() } }

    override fun observeDistanceBetween(from: LocalDate, to: LocalDate): Flow<Double> =
        runDao.observeDistanceBetween(from.toEpochDay(), to.toEpochDay())

    override fun observeDurationBetween(from: LocalDate, to: LocalDate): Flow<Long> =
        runDao.observeDurationBetween(from.toEpochDay(), to.toEpochDay())

    override suspend fun getRun(id: Long): Run? {
        val run = runDao.getRun(id) ?: return null
        return run.toDomain(laps = runDao.getLaps(id), locations = runDao.getLocations(id))
    }

    override suspend fun saveRun(run: Run): Long {
        val runId = runDao.insertRun(
            RunEntity(
                date = run.date.toEpochDay(),
                startTime = run.startTime,
                endTime = run.endTime,
                durationSeconds = run.durationSeconds,
                distanceMeters = run.distanceMeters,
                averagePaceSecPerKm = run.averagePaceSecPerKm,
                bestPaceSecPerKm = run.bestPaceSecPerKm,
                calories = run.calories,
                goalType = run.goalType.name,
                goalValue = run.goalValue,
                memo = run.memo,
            ),
        )
        runDao.insertLaps(
            run.laps.map {
                RunLapEntity(
                    runId = runId,
                    lapNumber = it.lapNumber,
                    distanceMeters = it.distanceMeters,
                    durationSeconds = it.durationSeconds,
                    paceSecPerKm = it.paceSecPerKm,
                )
            },
        )
        runDao.insertLocations(
            run.route.map {
                RunLocationEntity(
                    runId = runId,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitude = it.altitude,
                    timestamp = it.timestamp,
                    isSegmentStart = it.isSegmentStart,
                )
            },
        )
        return runId
    }

    override suspend fun updateMemo(runId: Long, memo: String?) {
        val run = runDao.getRun(runId) ?: return
        runDao.updateRun(run.copy(memo = memo?.takeIf { it.isNotBlank() }))
    }

    override suspend fun deleteRun(id: Long) = runDao.deleteRun(id)
}
