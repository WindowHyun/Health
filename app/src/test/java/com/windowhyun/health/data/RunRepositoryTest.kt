package com.windowhyun.health.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.RunRepositoryImpl
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.RunLap
import com.windowhyun.health.domain.model.RunPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 러닝의 증분 저장 흐름(시작 -> 경로/Lap 추가 -> 종료)을 실제 Room 으로 검증한다.
 */
@RunWith(RobolectricTestRunner::class)
class RunRepositoryTest {

    private lateinit var db: HealthDatabase
    private lateinit var repository: RunRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RunRepositoryImpl(db.runDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun finishedRun(
        distanceMeters: Double,
        durationSeconds: Long,
        averagePace: Double = 0.0,
        steps: Int = 0,
    ): Long {
        val runId = repository.startRun(RunGoalType.FREE, 0.0)
        repository.finishRun(
            runId = runId,
            endTime = System.currentTimeMillis(),
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            averagePaceSecPerKm = averagePace,
            bestPaceSecPerKm = averagePace,
            calories = 100,
            steps = steps,
        )
        return runId
    }

    /** 시작하면 아직 끝나지 않은 러닝으로 조회된다. */
    @Test
    fun `creates an unfinished run on start`() = runTest {
        val runId = repository.startRun(RunGoalType.DISTANCE, 5_000.0)

        val active = repository.getActiveRun()
        assertThat(active).isNotNull()
        assertThat(active!!.id).isEqualTo(runId)
        assertThat(active.endTime).isNull()
        assertThat(active.goalType).isEqualTo(RunGoalType.DISTANCE)
        assertThat(active.goalValue).isWithin(0.001).of(5_000.0)
    }

    /** 기록 중 붙인 경로와 Lap 이 그대로 남는다. */
    @Test
    fun `keeps route points and laps appended during the run`() = runTest {
        val runId = repository.startRun(RunGoalType.FREE, 0.0)

        repository.appendRoutePoints(
            runId,
            listOf(
                RunPoint(latitude = 37.5, longitude = 127.0, timestamp = 1_000, isSegmentStart = true),
                RunPoint(latitude = 37.6, longitude = 127.0, timestamp = 2_000),
            ),
        )
        repository.appendRoutePoints(
            runId,
            listOf(RunPoint(latitude = 37.7, longitude = 127.0, timestamp = 3_000)),
        )
        repository.appendLap(
            runId,
            RunLap(lapNumber = 1, distanceMeters = 1_000.0, durationSeconds = 300, paceSecPerKm = 300.0),
        )

        val run = repository.getRun(runId)!!
        assertThat(run.route).hasSize(3)
        assertThat(run.route.first().isSegmentStart).isTrue()
        assertThat(run.route.map { it.timestamp }).containsExactly(1_000L, 2_000L, 3_000L).inOrder()
        assertThat(run.laps).hasSize(1)
        assertThat(run.laps.first().paceSecPerKm).isWithin(0.001).of(300.0)
    }

    /** 진행 중 갱신은 누적값만 바꾸고 시작 시각은 건드리지 않는다. */
    @Test
    fun `updates progress without touching the start time`() = runTest {
        val runId = repository.startRun(RunGoalType.FREE, 0.0)
        val startTime = repository.getRun(runId)!!.startTime

        repository.updateProgress(
            runId = runId,
            distanceMeters = 1_234.0,
            durationSeconds = 420,
            averagePaceSecPerKm = 340.0,
            bestPaceSecPerKm = 300.0,
            calories = 90,
            steps = 1_500,
        )

        val run = repository.getRun(runId)!!
        assertThat(run.distanceMeters).isWithin(0.001).of(1_234.0)
        assertThat(run.durationSeconds).isEqualTo(420)
        assertThat(run.steps).isEqualTo(1_500)
        assertThat(run.startTime).isEqualTo(startTime)
        assertThat(run.endTime).isNull()
    }

    /** 종료하면 더 이상 미완료 러닝으로 잡히지 않는다. */
    @Test
    fun `marks the run finished`() = runTest {
        val runId = finishedRun(distanceMeters = 5_000.0, durationSeconds = 1_500)

        assertThat(repository.getActiveRun()).isNull()
        val run = repository.getRun(runId)!!
        assertThat(run.endTime).isNotNull()
        assertThat(run.distanceMeters).isWithin(0.001).of(5_000.0)
    }

    /** 러닝을 지우면 경로와 Lap 도 함께 지워진다(외래키 CASCADE). */
    @Test
    fun `deletes route and laps with the run`() = runTest {
        val runId = repository.startRun(RunGoalType.FREE, 0.0)
        repository.appendRoutePoints(
            runId,
            listOf(RunPoint(latitude = 37.5, longitude = 127.0, timestamp = 1_000)),
        )
        repository.appendLap(
            runId,
            RunLap(lapNumber = 1, distanceMeters = 1_000.0, durationSeconds = 300, paceSecPerKm = 300.0),
        )

        repository.deleteRun(runId)

        assertThat(repository.getRun(runId)).isNull()
        assertThat(db.runDao().getLocations(runId)).isEmpty()
        assertThat(db.runDao().getLaps(runId)).isEmpty()
    }

    /** 첫 러닝은 최장 거리 기록이다. */
    @Test
    fun `reports the first run as the longest distance`() = runTest {
        val runId = finishedRun(distanceMeters = 3_000.0, durationSeconds = 900, averagePace = 300.0)

        val bests = repository.comparePersonalBests(repository.getRun(runId)!!)

        assertThat(bests.isLongestDistance).isTrue()
        assertThat(bests.previousLongestMeters).isEqualTo(0.0)
    }

    /** 더 멀리 달리면 최장 거리 기록을 갱신한다. */
    @Test
    fun `detects a new longest distance`() = runTest {
        finishedRun(distanceMeters = 3_000.0, durationSeconds = 900, averagePace = 300.0)
        val longerId = finishedRun(distanceMeters = 8_000.0, durationSeconds = 2_600, averagePace = 325.0)

        val bests = repository.comparePersonalBests(repository.getRun(longerId)!!)

        assertThat(bests.isLongestDistance).isTrue()
        assertThat(bests.previousLongestMeters).isWithin(0.001).of(3_000.0)
    }

    /** 더 짧게 달리면 최장 거리 기록이 아니다. */
    @Test
    fun `does not report a shorter run as the longest`() = runTest {
        finishedRun(distanceMeters = 8_000.0, durationSeconds = 2_600, averagePace = 325.0)
        val shorterId = finishedRun(distanceMeters = 3_000.0, durationSeconds = 900, averagePace = 300.0)

        val bests = repository.comparePersonalBests(repository.getRun(shorterId)!!)

        assertThat(bests.isLongestDistance).isFalse()
    }

    /** 더 빠른 평균 페이스는 기록으로 잡는다. */
    @Test
    fun `detects a faster average pace`() = runTest {
        finishedRun(distanceMeters = 5_000.0, durationSeconds = 1_750, averagePace = 350.0)
        val fasterId = finishedRun(distanceMeters = 5_000.0, durationSeconds = 1_500, averagePace = 300.0)

        val bests = repository.comparePersonalBests(repository.getRun(fasterId)!!)

        assertThat(bests.isFastestAveragePace).isTrue()
        assertThat(bests.previousBestPaceSecPerKm).isWithin(0.001).of(350.0)
    }

    /** 1km 미만은 페이스 기록 비교에서 제외한다(짧은 구간은 의미가 없다). */
    @Test
    fun `ignores very short runs for the pace record`() = runTest {
        finishedRun(distanceMeters = 5_000.0, durationSeconds = 1_500, averagePace = 300.0)
        val sprintId = finishedRun(distanceMeters = 400.0, durationSeconds = 80, averagePace = 200.0)

        val bests = repository.comparePersonalBests(repository.getRun(sprintId)!!)

        assertThat(bests.isFastestAveragePace).isFalse()
    }

    /** 걸음 수가 저장되고 케이던스·보폭이 계산된다. */
    @Test
    fun `stores steps and derives cadence`() = runTest {
        // 5km 를 30분에, 5,400 걸음
        val runId = finishedRun(
            distanceMeters = 5_000.0,
            durationSeconds = 1_800,
            averagePace = 360.0,
            steps = 5_400,
        )

        val run = repository.getRun(runId)!!
        assertThat(run.steps).isEqualTo(5_400)
        assertThat(run.cadenceStepsPerMinute).isEqualTo(180)
        assertThat(run.strideMeters).isWithin(0.001).of(5_000.0 / 5_400)
    }

    /** 홈 화면의 주간 합계에 러닝 거리가 반영된다. */
    @Test
    fun `aggregates weekly distance`() = runTest {
        finishedRun(distanceMeters = 5_000.0, durationSeconds = 1_500)
        finishedRun(distanceMeters = 3_000.0, durationSeconds = 900)

        val today = java.time.LocalDate.now()
        val total = repository.observeDistanceBetween(today, today).first()

        assertThat(total).isWithin(0.001).of(8_000.0)
    }
}
