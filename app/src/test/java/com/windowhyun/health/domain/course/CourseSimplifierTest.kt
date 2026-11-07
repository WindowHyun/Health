package com.windowhyun.health.domain.course

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.domain.model.CoursePoint
import com.windowhyun.health.domain.model.RunPoint
import org.junit.Test

class CourseSimplifierTest {
    private fun run(vararg lonOffsetsAndLat: Pair<Double, Double>) =
        lonOffsetsAndLat.mapIndexed { i, (lat, lon) -> RunPoint(lat, lon, timestamp = i * 1000L) }

    @Test fun `a straight line collapses to its two ends`() {
        val route = (0..200).map { RunPoint(37.0, 127.0 + it * 0.00001, timestamp = it * 1000L) }
        val out = CourseSimplifier.fromRoute(route)
        assertThat(out).hasSize(2)
        assertThat(out.first().longitude).isEqualTo(127.0)
        assertThat(out.last().longitude).isWithin(1e-9).of(127.002)
    }

    @Test fun `a corner is kept`() {
        val straightEast = (0..50).map { RunPoint(37.0, 127.0 + it * 0.00002, timestamp = it * 1000L) }
        val straightNorth = (1..50).map { RunPoint(37.0 + it * 0.00002, 127.001, timestamp = (50 + it) * 1000L) }
        val out = CourseSimplifier.fromRoute(straightEast + straightNorth)
        assertThat(out).hasSize(3)
        assertThat(out[1].latitude).isWithin(1e-9).of(37.0)
        assertThat(out[1].longitude).isWithin(1e-9).of(127.001)
    }

    @Test fun `small gps jitter is dropped but big wiggles stay`() {
        val jitter = (0..100).map { RunPoint(37.0 + (if (it % 2 == 0) 0.00001 else -0.00001), 127.0 + it * 0.00002, timestamp = it * 1000L) }
        assertThat(CourseSimplifier.fromRoute(jitter).size).isAtMost(3)
    }

    @Test fun `never exceeds the point limit`() {
        val zigzag = (0..3000).map { RunPoint(37.0 + (if (it % 2 == 0) 0.001 else 0.0), 127.0 + it * 0.0001, timestamp = it * 1000L) }
        assertThat(CourseSimplifier.fromRoute(zigzag).size).isAtMost(CourseSimplifier.MAX_POINTS)
    }

    @Test fun `short routes pass through`() {
        assertThat(CourseSimplifier.fromRoute(emptyList())).isEmpty()
        assertThat(CourseSimplifier.fromRoute(listOf(RunPoint(1.0, 1.0, timestamp = 0)))).hasSize(1)
        val two = listOf(RunPoint(1.0, 1.0, timestamp = 0), RunPoint(1.1, 1.1, timestamp = 1))
        assertThat(CourseSimplifier.fromRoute(two)).containsExactly(CoursePoint(1.0, 1.0), CoursePoint(1.1, 1.1)).inOrder()
    }
}
