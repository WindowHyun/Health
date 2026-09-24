package com.windowhyun.health.data.tracking

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.domain.model.LocationSample
import org.junit.Test

/**
 * 거리/페이스/Lap 계산 검증.
 *
 * 위도만 움직이는 직선 경로를 쓴다. 위도 0.0001도 ≈ 11.1m 이므로
 * 원하는 거리만큼 정확히 떨어진 좌표를 만들 수 있다.
 */
class RunMetricsAccumulatorTest {

    private val baseLat = 37.5665
    private val baseLon = 126.9780
    private val metersPerDegreeLat = 111_195.0

    /** 출발점에서 북쪽으로 [meters] 만큼 떨어진 샘플. */
    private fun sampleAt(
        meters: Double,
        timestamp: Long,
        accuracy: Float? = 5f,
    ) = LocationSample(
        latitude = baseLat + meters / metersPerDegreeLat,
        longitude = baseLon,
        accuracyMeters = accuracy,
        timestamp = timestamp,
    )

    /**
     * [totalMeters] 를 [stepMeters] 씩, 1초마다 [stepMeters] 를 달리는 속도로 넣는다.
     */
    private fun RunMetricsAccumulator.run(
        totalMeters: Double,
        stepMeters: Double,
        secondsPerStep: Long,
        startMeters: Double = 0.0,
        startSecond: Long = 0,
    ): Long {
        var travelled = startMeters
        var second = startSecond
        onLocation(sampleAt(travelled, second * 1000))
        while (travelled < startMeters + totalMeters) {
            travelled += stepMeters
            repeat(secondsPerStep.toInt()) { advanceTime() }
            second += secondsPerStep
            onLocation(sampleAt(travelled, second * 1000))
        }
        return second
    }

    /** 이동한 만큼 거리가 쌓여야 한다. */
    @Test
    fun `accumulates distance from gps samples`() {
        val accumulator = RunMetricsAccumulator()
        accumulator.run(totalMeters = 500.0, stepMeters = 10.0, secondsPerStep = 3)

        assertThat(accumulator.distanceMeters).isWithin(5.0).of(500.0)
    }

    /** 정확도가 나쁜 샘플은 거리에 반영하지 않는다. */
    @Test
    fun `ignores samples with poor accuracy`() {
        val accumulator = RunMetricsAccumulator()
        accumulator.onLocation(sampleAt(0.0, 0))
        accumulator.onLocation(sampleAt(100.0, 10_000, accuracy = 80f))

        assertThat(accumulator.distanceMeters).isEqualTo(0.0)
    }

    /** GPS 흔들림 수준(3m 미만)의 이동은 무시한다. */
    @Test
    fun `ignores gps jitter below the minimum displacement`() {
        val accumulator = RunMetricsAccumulator()
        accumulator.onLocation(sampleAt(0.0, 0))
        repeat(10) { index ->
            accumulator.onLocation(sampleAt(1.0, (index + 1) * 1_000L))
        }

        assertThat(accumulator.distanceMeters).isEqualTo(0.0)
    }

    /** 사람이 낼 수 없는 속도로 튄 좌표는 버린다. */
    @Test
    fun `rejects an impossible speed jump`() {
        val accumulator = RunMetricsAccumulator()
        accumulator.onLocation(sampleAt(0.0, 0))
        // 1초 만에 500m -> 500m/s
        accumulator.onLocation(sampleAt(500.0, 1_000))

        assertThat(accumulator.distanceMeters).isEqualTo(0.0)
    }

    /**
     * 기기가 정확도를 알려 주지 않으면(null) 버린다.
     * 예전에는 이런 좌표가 0m(=최상)로 들어와 노이즈 필터를 그냥 통과했다.
     */
    @Test
    fun `rejects samples with unknown accuracy`() {
        val accumulator = RunMetricsAccumulator()
        accumulator.onLocation(sampleAt(0.0, 0))
        accumulator.onLocation(sampleAt(100.0, 30_000, accuracy = null))

        assertThat(accumulator.distanceMeters).isEqualTo(0.0)
    }

    /**
     * 신호가 오래 끊겼다가 큰 점프가 들어오면 Lap 경계를 여러 번 넘는다.
     * 한 번에 하나만 만들면 그 Lap 하나가 몇 km 짜리가 된다.
     */
    @Test
    fun `creates every lap crossed by one large jump`() {
        val accumulator = RunMetricsAccumulator(autoLapMeters = 1_000)
        accumulator.onLocation(sampleAt(0.0, 0))
        // 신호 끊김: 600초 동안 3.2km 이동(평균 5.3m/s 라 속도 필터는 통과)
        repeat(600) { accumulator.advanceTime() }
        val laps = accumulator.onLocation(sampleAt(3_200.0, 600_000))

        assertThat(laps).hasSize(3)
        assertThat(accumulator.laps.map { it.lapNumber }).containsExactly(1, 2, 3).inOrder()
        accumulator.laps.forEach {
            assertThat(it.distanceMeters).isWithin(1.0).of(1_000.0)
        }
        // 나눠 준 시간의 합이 실제 경과 시간을 넘지 않아야 한다.
        assertThat(accumulator.laps.sumOf { it.durationSeconds }).isAtMost(600)
    }

    /** 1km 마다 Lap 이 만들어지고 Lap 페이스가 계산된다. */
    @Test
    fun `creates an automatic lap every kilometer`() {
        val accumulator = RunMetricsAccumulator(autoLapMeters = 1_000)
        // 10m 를 3초에 -> 1km 에 300초 (5'00")
        accumulator.run(totalMeters = 2_000.0, stepMeters = 10.0, secondsPerStep = 3)

        assertThat(accumulator.laps).hasSize(2)
        assertThat(accumulator.laps[0].lapNumber).isEqualTo(1)
        assertThat(accumulator.laps[0].distanceMeters).isWithin(15.0).of(1_000.0)
        assertThat(accumulator.laps[0].paceSecPerKm).isWithin(10.0).of(300.0)
        assertThat(accumulator.laps[1].lapNumber).isEqualTo(2)
    }

    /** Lap 거리를 설정으로 바꿀 수 있다. */
    @Test
    fun `honours a custom lap distance`() {
        val accumulator = RunMetricsAccumulator(autoLapMeters = 500)
        accumulator.run(totalMeters = 1_000.0, stepMeters = 10.0, secondsPerStep = 2)

        assertThat(accumulator.laps).hasSize(2)
        assertThat(accumulator.laps[0].distanceMeters).isWithin(15.0).of(500.0)
    }

    /** 종료할 때 1km 를 못 채운 자투리도 Lap 으로 남긴다. */
    @Test
    fun `creates a partial lap on finish`() {
        val accumulator = RunMetricsAccumulator(autoLapMeters = 1_000)
        accumulator.run(totalMeters = 1_300.0, stepMeters = 10.0, secondsPerStep = 3)

        val partial = accumulator.finalizePartialLap()

        assertThat(partial).isNotNull()
        assertThat(partial!!.lapNumber).isEqualTo(2)
        assertThat(partial.distanceMeters).isWithin(20.0).of(300.0)
    }

    /** 자투리가 아주 짧으면(50m 미만) Lap 을 만들지 않는다. */
    @Test
    fun `skips a negligible partial lap`() {
        val accumulator = RunMetricsAccumulator(autoLapMeters = 1_000)
        accumulator.run(totalMeters = 1_010.0, stepMeters = 10.0, secondsPerStep = 3)

        assertThat(accumulator.finalizePartialLap()).isNull()
    }

    /** 평균 페이스는 전체 거리와 전체 시간으로 계산한다. */
    @Test
    fun `computes average pace over the whole run`() {
        val accumulator = RunMetricsAccumulator()
        // 10m 를 3초 -> 300초/km
        accumulator.run(totalMeters = 1_000.0, stepMeters = 10.0, secondsPerStep = 3)

        assertThat(accumulator.averagePaceSecPerKm).isWithin(10.0).of(300.0)
    }

    /** 일시정지 구간의 이동은 거리에 들어가지 않고, 경로는 끊어진 것으로 표시된다. */
    @Test
    fun `does not count movement across a pause`() {
        val accumulator = RunMetricsAccumulator()
        accumulator.onLocation(sampleAt(0.0, 0))
        accumulator.onLocation(sampleAt(100.0, 30_000))
        assertThat(accumulator.distanceMeters).isWithin(2.0).of(100.0)

        accumulator.breakSegment()
        // 정지한 사이 차로 1km 이동한 셈
        accumulator.onLocation(sampleAt(1_100.0, 600_000))

        assertThat(accumulator.distanceMeters).isWithin(2.0).of(100.0)
        val points = accumulator.drainPendingPoints()
        assertThat(points.last().isSegmentStart).isTrue()
    }

    /** 걸음 센서는 부팅 이후 누적값을 주므로 차이만 더한다. */
    @Test
    fun `accumulates steps from the cumulative sensor value`() {
        val accumulator = RunMetricsAccumulator()
        // 러닝 시작 시점에 기기는 이미 12,000 걸음을 세어 두었다.
        accumulator.onStepCount(12_000)
        accumulator.onStepCount(12_150)
        accumulator.onStepCount(12_400)

        assertThat(accumulator.steps).isEqualTo(400)
    }

    /** 첫 값은 기준점일 뿐 걸음 수로 잡지 않는다. */
    @Test
    fun `treats the first sensor value as a baseline`() {
        val accumulator = RunMetricsAccumulator()
        accumulator.onStepCount(50_000)

        assertThat(accumulator.steps).isEqualTo(0)
    }

    /** 일시정지 동안 걸은 걸음은 더하지 않는다. */
    @Test
    fun `does not count steps taken while paused`() {
        val accumulator = RunMetricsAccumulator()
        accumulator.onStepCount(1_000)
        accumulator.onStepCount(1_300)
        assertThat(accumulator.steps).isEqualTo(300)

        accumulator.breakSegment()
        // 정지한 사이에 500 걸음을 더 걸었다.
        accumulator.onStepCount(1_800)
        assertThat(accumulator.steps).isEqualTo(300)

        // 재개 후부터 다시 센다.
        accumulator.onStepCount(1_900)
        assertThat(accumulator.steps).isEqualTo(400)
    }

    /** 기기를 재부팅하면 누적값이 0 으로 돌아간다. 그때는 기준점만 새로 잡는다. */
    @Test
    fun `handles the sensor counter resetting`() {
        val accumulator = RunMetricsAccumulator()
        accumulator.onStepCount(90_000)
        accumulator.onStepCount(90_200)
        assertThat(accumulator.steps).isEqualTo(200)

        accumulator.onStepCount(10)
        assertThat(accumulator.steps).isEqualTo(200)

        accumulator.onStepCount(60)
        assertThat(accumulator.steps).isEqualTo(250)
    }

    /** 케이던스는 분당 걸음 수다. */
    @Test
    fun `computes cadence per minute`() {
        val accumulator = RunMetricsAccumulator()
        accumulator.onStepCount(0)
        accumulator.onStepCount(360)
        repeat(120) { accumulator.advanceTime() } // 2분

        assertThat(accumulator.cadenceStepsPerMinute).isEqualTo(180)
    }

    /** 걸음 수가 없으면 케이던스와 보폭은 0 이다. */
    @Test
    fun `returns zero cadence without steps`() {
        val accumulator = RunMetricsAccumulator()
        repeat(60) { accumulator.advanceTime() }

        assertThat(accumulator.cadenceStepsPerMinute).isEqualTo(0)
        assertThat(accumulator.strideMeters).isEqualTo(0.0)
    }

    /** 지도에 그릴 전체 경로는 저장 여부와 무관하게 계속 쌓인다. */
    @Test
    fun `keeps the full route for the map`() {
        val accumulator = RunMetricsAccumulator()
        accumulator.run(totalMeters = 100.0, stepMeters = 10.0, secondsPerStep = 3)

        val pointCount = accumulator.routePoints.size
        assertThat(pointCount).isAtLeast(10)

        // DB 로 내보내도 지도용 경로는 남아 있어야 한다.
        accumulator.drainPendingPoints()
        assertThat(accumulator.routePoints).hasSize(pointCount)
    }

    /** 경로 포인트는 한 번 꺼내면 비워진다(중복 저장 방지). */
    @Test
    fun `drains pending points only once`() {
        val accumulator = RunMetricsAccumulator()
        accumulator.onLocation(sampleAt(0.0, 0))
        accumulator.onLocation(sampleAt(20.0, 5_000))

        assertThat(accumulator.drainPendingPoints()).hasSize(2)
        assertThat(accumulator.drainPendingPoints()).isEmpty()
    }

    /** 최고 페이스는 가장 빨랐던 구간의 페이스다. */
    @Test
    fun `tracks the best pace across the run`() {
        val accumulator = RunMetricsAccumulator()
        // 느린 구간: 10m / 5초 -> 500초/km
        val afterSlow = accumulator.run(totalMeters = 600.0, stepMeters = 10.0, secondsPerStep = 5)
        // 빠른 구간: 10m / 2초 -> 200초/km
        accumulator.run(
            totalMeters = 600.0,
            stepMeters = 10.0,
            secondsPerStep = 2,
            startMeters = accumulator.distanceMeters,
            startSecond = afterSlow,
        )

        assertThat(accumulator.bestPaceSecPerKm).isWithin(30.0).of(200.0)
        assertThat(accumulator.bestPaceSecPerKm).isLessThan(accumulator.averagePaceSecPerKm)
    }
}
