package com.windowhyun.health.domain.course

import com.windowhyun.health.domain.model.CoursePoint
import com.windowhyun.health.domain.model.RunPoint
import kotlin.math.cos

/**
 * 달린 경로(수천 개의 GPS 점)를 코스로 쓸 수 있게 점 수를 줄인다(Douglas-Peucker).
 * 모양이 [epsilonMeters] 이상 달라지는 점만 남기므로 꺾이는 곳은 그대로 남는다.
 */
object CourseSimplifier {

    const val MAX_POINTS = 300

    fun fromRoute(route: List<RunPoint>, epsilonMeters: Double = 6.0, maxPoints: Int = MAX_POINTS): List<CoursePoint> {
        val points = route.map { CoursePoint(it.latitude, it.longitude) }
        if (points.size <= 2) return points
        var epsilon = epsilonMeters
        var result = simplify(points, epsilon)
        // 아주 구불구불한 경로는 한도를 넘을 수 있어 허용 오차를 키워 가며 줄인다.
        while (result.size > maxPoints && epsilon < 500.0) {
            epsilon *= 1.5
            result = simplify(points, epsilon)
        }
        return result
    }

    internal fun simplify(points: List<CoursePoint>, epsilonMeters: Double): List<CoursePoint> {
        if (points.size <= 2) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        // 재귀 대신 스택을 쓴다(점이 수천 개라 깊은 재귀를 피한다).
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, points.lastIndex))
        while (stack.isNotEmpty()) {
            val (from, to) = stack.removeLast()
            var farthest = -1
            var farthestDistance = epsilonMeters
            for (i in from + 1 until to) {
                val d = CourseGeometry.project(points[i].latitude, points[i].longitude, points[from], points[to]).offMeters
                if (d > farthestDistance) {
                    farthestDistance = d
                    farthest = i
                }
            }
            if (farthest != -1) {
                keep[farthest] = true
                stack.addLast(intArrayOf(from, farthest))
                stack.addLast(intArrayOf(farthest, to))
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }
}
