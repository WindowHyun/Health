package com.windowhyun.health.data.repository

import com.windowhyun.health.data.local.dao.RunDao
import com.windowhyun.health.data.local.entity.RunEntity
import com.windowhyun.health.data.local.entity.RunLapEntity
import com.windowhyun.health.data.local.entity.RunLocationEntity
import com.windowhyun.health.data.mapper.toDomain
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.RunLap
import com.windowhyun.health.domain.model.RunPoint
import com.windowhyun.health.domain.repository.RunPersonalBests
import com.windowhyun.health.domain.repository.RunRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 러닝 저장소.
 *
 * 기록 중에는 [startRun] 으로 행을 먼저 만들고 위치/Lap 을 증분 저장한다.
 * 덕분에 기록 도중 앱이 죽어도 이미 달린 구간은 남는다.
 */
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
                steps = run.steps,
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

    // ----- 기록 중 증분 저장 -----

    override suspend fun startRun(goalType: RunGoalType, goalValue: Double): Long {
        val now = System.currentTimeMillis()
        return runDao.insertRun(
            RunEntity(
                date = LocalDate.now().toEpochDay(),
                startTime = now,
                endTime = null,
                goalType = goalType.name,
                goalValue = goalValue,
            ),
        )
    }

    override suspend fun getActiveRun(): Run? {
        val run = runDao.getActiveRun() ?: return null
        return run.toDomain(laps = runDao.getLaps(run.id), locations = runDao.getLocations(run.id))
    }

    override suspend fun appendRoutePoints(runId: Long, points: List<RunPoint>) {
        if (points.isEmpty()) return
        runDao.insertLocations(
            points.map {
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
    }

    override suspend fun appendLap(runId: Long, lap: RunLap) {
        runDao.insertLap(
            RunLapEntity(
                runId = runId,
                lapNumber = lap.lapNumber,
                distanceMeters = lap.distanceMeters,
                durationSeconds = lap.durationSeconds,
                paceSecPerKm = lap.paceSecPerKm,
            ),
        )
    }

    override suspend fun updateProgress(
        runId: Long,
        distanceMeters: Double,
        durationSeconds: Long,
        averagePaceSecPerKm: Double,
        bestPaceSecPerKm: Double,
        calories: Int,
        steps: Int,
    ) = runDao.updateProgress(
        id = runId,
        distanceMeters = distanceMeters,
        durationSeconds = durationSeconds,
        averagePace = averagePaceSecPerKm,
        bestPace = bestPaceSecPerKm,
        calories = calories,
        steps = steps,
    )

    override suspend fun finishRun(
        runId: Long,
        endTime: Long,
        distanceMeters: Double,
        durationSeconds: Long,
        averagePaceSecPerKm: Double,
        bestPaceSecPerKm: Double,
        calories: Int,
        steps: Int,
    ) {
        runDao.updateProgress(
            id = runId,
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            averagePace = averagePaceSecPerKm,
            bestPace = bestPaceSecPerKm,
            calories = calories,
            steps = steps,
        )
        runDao.markFinished(runId, endTime)
    }

    override suspend fun comparePersonalBests(run: Run): RunPersonalBests {
        val previousLongest = runDao.maxDistanceExcluding(run.id)
        // 아주 짧은 러닝끼리 페이스를 비교하면 의미가 없으므로 1km 이상만 본다.
        val comparable = run.distanceMeters >= MIN_PACE_RECORD_METERS
        val previousBestPace = if (comparable) {
            runDao.bestAveragePaceExcluding(run.id, MIN_PACE_RECORD_METERS)
        } else {
            null
        }
        return RunPersonalBests(
            isLongestDistance = run.distanceMeters > previousLongest && run.distanceMeters > 0,
            isFastestAveragePace = comparable && run.averagePaceSecPerKm > 0 &&
                (previousBestPace == null || run.averagePaceSecPerKm < previousBestPace),
            previousLongestMeters = previousLongest,
            previousBestPaceSecPerKm = previousBestPace,
        )
    }

    private companion object {
        const val MIN_PACE_RECORD_METERS = 1_000.0
    }
}
