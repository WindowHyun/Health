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

    override fun observeRunCountBefore(date: LocalDate): Flow<Int> =
        runDao.observeRunCountBefore(date.toEpochDay())

    override fun observeDistanceBetween(from: LocalDate, to: LocalDate): Flow<Double> =
        runDao.observeDistanceBetween(from.toEpochDay(), to.toEpochDay())

    override fun observeDurationBetween(from: LocalDate, to: LocalDate): Flow<Long> =
        runDao.observeDurationBetween(from.toEpochDay(), to.toEpochDay())

    override suspend fun getRun(id: Long): Run? {
        val run = runDao.getRun(id) ?: return null
        return run.toDomain(laps = runDao.getLaps(id), locations = runDao.getLocations(id))
    }

    /**
     * 러닝 1건을 저장한다.
     *
     * - 새 기록(id 0 이거나 DB 에 없는 id): 기록과 Lap·경로를 한 트랜잭션으로 넣는다.
     * - 이미 있는 기록: 기록 행만 고친다. Lap·경로는 건드리지 않는다. 목록 조회로 받은
     *   Run 에는 Lap·경로가 비어 있어서, 그대로 갈아 끼우면 경로가 통째로 사라진다.
     */
    override suspend fun saveRun(run: Run): Long {
        val entity = RunEntity(
            id = run.id,
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
        )
        if (run.id != 0L && runDao.getRun(run.id) != null) {
            runDao.updateRun(entity)
            return run.id
        }
        return runDao.insertRunWithDetails(
            run = entity,
            laps = run.laps.map {
                RunLapEntity(
                    runId = 0,
                    lapNumber = it.lapNumber,
                    distanceMeters = it.distanceMeters,
                    durationSeconds = it.durationSeconds,
                    paceSecPerKm = it.paceSecPerKm,
                )
            },
            locations = run.route.map {
                RunLocationEntity(
                    runId = 0,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitude = it.altitude,
                    timestamp = it.timestamp,
                    isSegmentStart = it.isSegmentStart,
                )
            },
        )
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

    /**
     * 앱이 강제로 종료되면 러닝이 "끝나지 않음"으로 남는다. 모든 목록 · 통계 · 기록이 끝난
     * 러닝만 보므로 그대로 두면 어디에도 나오지 않는다. 달린 거리와 경로는 기록 중에
     * 계속 저장해 두었으니, 마지막으로 저장된 운동 시간만큼 지난 시각으로 마감해 살린다.
     *
     * 아무것도 기록되지 않은 러닝(시작하자마자 끊김)은 남길 이유가 없어 지운다.
     */
    override suspend fun closeUnfinishedRuns(excludeRunId: Long): Int {
        var closed = 0
        runDao.getUnfinishedRuns()
            .filter { it.id != excludeRunId }
            .forEach { run ->
                val lastPoint = runDao.getLastLocationTime(run.id)
                if (run.distanceMeters <= 0.0 && run.durationSeconds <= 0L && lastPoint == null) {
                    runDao.deleteRun(run.id)
                } else {
                    // 시작 시각과 같은 기기 시계로 맞춘다. 위치 시각은 GPS 시계라서 기기 시계가
                    // 틀려 있으면 몇 분씩 어긋난다. 운동 시간이 아직 저장되지 않았을 때만 쓴다.
                    val end = if (run.durationSeconds > 0) {
                        run.startTime + run.durationSeconds * 1_000
                    } else {
                        lastPoint ?: run.startTime
                    }
                    runDao.updateRun(
                        run.copy(
                            endTime = maxOf(end, run.startTime),
                            memo = run.memo ?: RECOVERED_MEMO,
                        ),
                    )
                    closed++
                }
            }
        return closed
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

/** 자동으로 마감한 러닝에 붙이는 메모. 사용자가 왜 끝났는지 알 수 있게 한다. */
internal const val RECOVERED_MEMO = "앱이 종료되어 마지막 기록 지점에서 자동으로 마감한 러닝입니다."
