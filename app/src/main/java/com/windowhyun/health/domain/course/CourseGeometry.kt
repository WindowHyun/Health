package com.windowhyun.health.domain.course

import com.windowhyun.health.domain.model.CoursePoint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 코스 계산에 쓰는 거리 · 투영 함수. 코스는 몇 km 규모라 평면 근사로 충분하다. */
object CourseGeometry {
    private const val EARTH_RADIUS = 6_371_000.0

    fun distance(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val dLat = Math.toRadians(bLat - aLat)
        val dLon = Math.toRadians(bLon - aLon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS * atan2(sqrt(h), sqrt(1 - h))
    }

    fun distance(a: CoursePoint, b: CoursePoint) = distance(a.latitude, a.longitude, b.latitude, b.longitude)

    fun length(points: List<CoursePoint>): Double =
        points.zipWithNext().sumOf { (a, b) -> distance(a, b) }

    /** 점 하나를 선분 위로 내린 결과. [t] 는 선분 안의 위치(0..1), [offMeters] 는 선분까지의 거리. */
    data class Projection(val t: Double, val offMeters: Double)

    /** (lat, lon) 을 선분 a-b 위로 내린다. a 를 원점으로 한 미터 좌표계(평면 근사)에서 계산한다. */
    fun project(lat: Double, lon: Double, a: CoursePoint, b: CoursePoint): Projection {
        val mPerDegLat = PI * EARTH_RADIUS / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(a.latitude))
        val bx = (b.longitude - a.longitude) * mPerDegLon
        val by = (b.latitude - a.latitude) * mPerDegLat
        val px = (lon - a.longitude) * mPerDegLon
        val py = (lat - a.latitude) * mPerDegLat
        val len2 = bx * bx + by * by
        val t = if (len2 <= 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val dx = px - t * bx
        val dy = py - t * by
        return Projection(t, sqrt(dx * dx + dy * dy))
    }
}
