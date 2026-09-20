package com.windowhyun.health.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeoUtilsTest {

    /** 위도 1도는 약 111km 다. */
    @Test
    fun `measures one degree of latitude`() {
        val meters = haversineMeters(37.0, 127.0, 38.0, 127.0)
        assertThat(meters).isWithin(500.0).of(111_195.0)
    }

    /** 같은 좌표면 거리는 0 이다. */
    @Test
    fun `returns zero for the same point`() {
        assertThat(haversineMeters(37.5665, 126.9780, 37.5665, 126.9780)).isEqualTo(0.0)
    }

    /** 짧은 거리도 정확해야 한다(러닝 기록은 짧은 구간의 합이다). */
    @Test
    fun `measures a short distance accurately`() {
        // 위도 0.0001도 ≈ 11.1m
        val meters = haversineMeters(37.5665, 126.9780, 37.5666, 126.9780)
        assertThat(meters).isWithin(0.5).of(11.1)
    }

    /** 페이스는 초/킬로미터다. */
    @Test
    fun `computes pace per kilometer`() {
        // 1km 를 5분 52초(352초)에 달리면 페이스는 352
        assertThat(paceSecPerKm(1_000.0, 352)).isWithin(0.001).of(352.0)
        // 2km 를 700초면 350초/km
        assertThat(paceSecPerKm(2_000.0, 700)).isWithin(0.001).of(350.0)
    }

    /** 거리나 시간이 0 이면 페이스를 정의할 수 없다. */
    @Test
    fun `returns zero pace for empty input`() {
        assertThat(paceSecPerKm(0.0, 100)).isEqualTo(0.0)
        assertThat(paceSecPerKm(1_000.0, 0)).isEqualTo(0.0)
    }

    /** 칼로리는 체중과 거리에 비례한다. */
    @Test
    fun `estimates calories from weight and distance`() {
        // 1.036 * 70 * 5 = 362.6
        assertThat(estimateRunCalories(70.0, 5_000.0)).isEqualTo(362)
        assertThat(estimateRunCalories(70.0, 0.0)).isEqualTo(0)
    }
}
