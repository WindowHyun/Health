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
        accuracy: Float = 5f,
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
