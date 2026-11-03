package com.windowhyun.health.data.tracking

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.domain.model.LocationSample
import org.junit.Test

/** GPS 가 튀거나 이상한 값이 와도 거리가 망가지지 않게 걸러내는 규칙. 위도 0.000009도 ≈ 1m. */
class RunLocationRobustnessTest {

    private fun sample(meters: Double, seconds: Long, accuracy: Float? = 5f, lat: Double? = null) =
        LocationSample(
            latitude = lat ?: (37.5 + meters * 0.000009),
            longitude = 127.0,
            accuracyMeters = accuracy,
            timestamp = seconds * 1_000,
        )

    /** 3m/s 로 달리며 2초마다 받은 위치. */
    private fun RunMetricsAccumulator.runFrom(startMeters: Double, startSecond: Long, count: Int) {
        repeat(count) { i ->
            advanceTime(2)
            onLocation(sample(startMeters + i * 6.0, startSecond + i * 2L))
        }
    }

    /**
     * 첫 위치가 5km 어긋난 값이면 그 뒤의 진짜 위치가 모두 "튄 값"으로 버려져, 거리가 점프만큼 부풀려지고
     * 5분 넘게 아무것도 기록되지 않았다. 진짜 위치가 연달아 나오면 그쪽이 맞다고 보고 기준점을 옮긴다.
     */
    @Test
    fun `a wrong first fix does not swallow the real run`() {
        val accumulator = RunMetricsAccumulator()
        accumulator.onLocation(sample(5_000.0, seconds = 0)) // 엉뚱한 첫 위치
        accumulator.runFrom(startMeters = 0.0, startSecond = 2, count = 40)

        // 실제로 달린 건 약 234m(39구간 x 6m). 5km 점프가 더해지면 안 되고, 거의 다 기록돼야 한다.
        assertThat(accumulator.distanceMeters).isWithin(40.0).of(39 * 6.0 - 3 * 6.0)
        assertThat(accumulator.distanceMeters).isLessThan(400.0)
    }

    /** 몇 킬로미터 떨어진 값이 한두 번 튄 것은 여전히 버린다(이미 달리고 있었다면 기준점을 옮기지 않는다). */
    @Test
    fun `a single stray fix in the middle of a run is still ignored`() {
        val accumulator = RunMetricsAccumulator()
        accumulator.runFrom(startMeters = 0.0, startSecond = 0, count = 60) // 약 354m 달림
        val before = accumulator.distanceMeters
        accumulator.advanceTime(2)
        accumulator.onLocation(sample(3_000.0, seconds = 122)) // 튄 값 하나

        assertThat(accumulator.distanceMeters).isEqualTo(before)
        accumulator.advanceTime(2)
        accumulator.onLocation(sample(60 * 6.0, seconds = 124)) // 다시 정상
        assertThat(accumulator.distanceMeters).isGreaterThan(before)
        assertThat(accumulator.distanceMeters).isLessThan(before + 30.0)
    }

    /** 시각이 같거나 거꾸로인 샘플은 속도 검사를 건너뛰어, 1km 점프가 그대로 거리에 더해졌다. */
    @Test
    fun `a big jump with the same timestamp is rejected`() {
        val accumulator = RunMetricsAccumulator()
        accumulator.onLocation(sample(0.0, seconds = 10))
        accumulator.onLocation(sample(1_000.0, seconds = 10)) // 같은 시각에 1km
        accumulator.onLocation(sample(2_000.0, seconds = 9)) // 거꾸로

        assertThat(accumulator.distanceMeters).isEqualTo(0.0)
    }

    /** NaN 좌표가 한 번 들어오면 거리가 영구히 NaN 이 되었다. */
    @Test
    fun `non finite coordinates never reach the distance`() {
        val accumulator = RunMetricsAccumulator()
        accumulator.onLocation(sample(0.0, seconds = 0))
        accumulator.advanceTime(2)
        accumulator.onLocation(sample(0.0, seconds = 2, lat = Double.NaN))
        accumulator.onLocation(sample(0.0, seconds = 3, lat = Double.POSITIVE_INFINITY))
        accumulator.advanceTime(2)
        accumulator.onLocation(sample(12.0, seconds = 4))

        assertThat(accumulator.distanceMeters.isNaN()).isFalse()
        assertThat(accumulator.distanceMeters).isWithin(1.0).of(12.0)
    }
}
