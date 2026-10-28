package com.windowhyun.health.ui.running

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.domain.model.RunPoint
import org.junit.Test

/** 지도 선 색을 정하는 구간별 빠르기(0 느림 ~ 1 빠름). */
class RoutePaceTest {

    /** 북쪽으로 일직선. 점 사이 간격(m)과 걸린 시간(초)을 구간마다 정한다. */
    private fun route(vararg legs: Pair<Double, Double>, segmentStartAt: Set<Int> = emptySet()): List<RunPoint> {
        val metersPerDegree = 111_195.0
        var lat = 37.5
        var time = 0L
        val points = mutableListOf(RunPoint(lat, 127.0, timestamp = 0))
        legs.forEachIndexed { i, (meters, seconds) ->
            lat += meters / metersPerDegree
            time += (seconds * 1_000).toLong()
            points += RunPoint(lat, 127.0, timestamp = time, isSegmentStart = (i + 1) in segmentStartAt)
        }
        return points
    }

    @Test
    fun `fewer than two points give nothing`() {
        assertThat(RoutePace.fractions(emptyList())).isEmpty()
        assertThat(RoutePace.fractions(route())).isEmpty()
    }

    @Test
    fun `one value per gap between points`() {
        assertThat(RoutePace.fractions(route(10.0 to 3.0, 10.0 to 3.0, 10.0 to 3.0))).hasSize(3)
    }

    /** 일정한 속도로 달렸다면 구간마다 색을 달리하지 않는다(중간 색). */
    @Test
    fun `an even pace is one color`() {
        val even = route(*Array(20) { 10.0 to 3.0 })

        assertThat(RoutePace.fractions(even).toSet()).containsExactly(0.5f)
    }

    @Test
    fun `a run that gets faster ends faster than it starts`() {
        val legs = Array(40) { i -> 10.0 to (if (i < 20) 4.0 else 2.5) } // 앞 절반은 느리게, 뒤 절반은 빠르게
        val fractions = RoutePace.fractions(route(*legs))

        assertThat(fractions.first()).isLessThan(0.2f)
        assertThat(fractions.last()).isGreaterThan(0.8f)
        assertThat(fractions.all { it in 0f..1f }).isTrue()
    }

    @Test
    fun `after a pause the gap is not connected`() {
        val points = route(10.0 to 3.0, 10.0 to 3.0, 500.0 to 600.0, 10.0 to 3.0, segmentStartAt = setOf(3))
        val fractions = RoutePace.fractions(points)

        assertThat(fractions[2].isNaN()).isTrue()
        assertThat(fractions.filterIndexed { i, _ -> i != 2 }.none { it.isNaN() }).isTrue()
    }

    /** GPS 가 튀어 순간 수백 km/h 가 나와도 나머지 구간의 색 범위를 망치지 않는다. */
    @Test
    fun `a gps jump does not wash out the other colors`() {
        val legs = Array(41) { i ->
            when {
                i == 20 -> 500.0 to 1.0 // 튄 점
                i < 20 -> 10.0 to 4.0
                else -> 10.0 to 2.5
            }
        }
        val fractions = RoutePace.fractions(route(*legs))

        assertThat(fractions.first()).isLessThan(0.3f)
        assertThat(fractions.last()).isGreaterThan(0.7f)
    }

    @Test
    fun `no time passing gives no value instead of a division by zero`() {
        val points = listOf(RunPoint(37.5, 127.0, timestamp = 1_000), RunPoint(37.5001, 127.0, timestamp = 1_000))

        assertThat(RoutePace.fractions(points).single().isNaN()).isTrue()
    }

    // ----- 색 -----

    private val ramp = intArrayOf(0xFF000000.toInt(), 0xFFFF0000.toInt(), 0xFFFFFFFF.toInt())

    @Test
    fun `the ends of the ramp are its first and last colors`() {
        assertThat(RoutePace.colorAt(0f, ramp)).isEqualTo(ramp.first())
        assertThat(RoutePace.colorAt(1f, ramp)).isEqualTo(ramp.last())
        assertThat(RoutePace.colorAt(0.5f, ramp)).isEqualTo(ramp[1])
    }

    @Test
    fun `colors between stops are mixed and out of range values are clamped`() {
        val quarter = RoutePace.colorAt(0.25f, ramp)

        assertThat((quarter shr 16) and 0xFF).isEqualTo(127) // 검정과 빨강의 가운데
        assertThat(quarter and 0xFF).isEqualTo(0)
        assertThat(RoutePace.colorAt(-3f, ramp)).isEqualTo(ramp.first())
        assertThat(RoutePace.colorAt(9f, ramp)).isEqualTo(ramp.last())
    }

    @Test
    fun `both ramps go from cool to the brightest accent`() {
        listOf(true, false).forEach { dark ->
            val ramp = RouteColors.ramp(dark)
            assertThat(ramp.size).isAtLeast(3)
            assertThat(ramp.all { (it ushr 24) == 0xFF }).isTrue()
        }
    }
}
