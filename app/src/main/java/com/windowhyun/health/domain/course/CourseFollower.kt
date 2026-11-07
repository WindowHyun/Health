package com.windowhyun.health.domain.course

import com.windowhyun.health.domain.model.CourseGuidance
import com.windowhyun.health.domain.model.CoursePoint

/**
 * 달리는 위치를 코스 위로 옮겨 "얼마나 왔고 얼마 남았는지, 코스를 벗어났는지" 를 계산한다.
 *
 * 코스가 같은 길을 오가거나 원을 그리면 위치가 코스의 여러 곳과 가까울 수 있다. 가장 가까운 곳만 보면
 * 진행 위치가 앞뒤로 튀므로, **지금까지 온 곳 앞쪽의 일부 구간만 먼저 찾고**, 거기서 못 찾았을 때만 전체에서 찾는다.
 *
 * 코스를 벗어났다는 판정은 두 번 연속 [OFF_COURSE_METERS] 보다 멀 때, 돌아왔다는 판정은 [BACK_ON_COURSE_METERS]
 * 안으로 들어왔을 때다(GPS 흔들림으로 경고가 깜빡이지 않게 문턱을 둘로 나눈다).
 */
class CourseFollower(private val points: List<CoursePoint>) {

    private val cumulative = DoubleArray(points.size).also { cum ->
        for (i in 1 until points.size) cum[i] = cum[i - 1] + CourseGeometry.distance(points[i - 1], points[i])
    }
    val totalMeters: Double = cumulative.lastOrNull() ?: 0.0

    private var segment = 0
    private var progress = 0.0
    private var offCourse = false
    private var farStreak = 0
    private var finished = false

    fun update(latitude: Double, longitude: Double): CourseGuidance {
        if (points.size < 2) return CourseGuidance(totalMeters = totalMeters)

        val best = search(latitude, longitude, from = segment, to = minOf(points.size - 2, segment + FORWARD_WINDOW))
            ?.takeIf { it.off <= SEARCH_ACCEPT_METERS }
            ?: search(latitude, longitude, 0, points.size - 2)!!

        val candidateProgress = progressOf(best)
        // 코스를 벗어나 있는 동안에는 진행 위치를 바꾸지 않는다(엉뚱한 곳에 붙어 거리가 튀지 않게).
        if (best.off <= OFF_COURSE_METERS) {
            segment = best.segment
            progress = candidateProgress
        }

        farStreak = if (best.off > OFF_COURSE_METERS) farStreak + 1 else 0
        if (!offCourse && farStreak >= 2) offCourse = true
        if (offCourse && best.off <= BACK_ON_COURSE_METERS) offCourse = false

        if (!finished && progress >= totalMeters - ARRIVE_METERS && best.off <= OFF_COURSE_METERS) finished = true

        return CourseGuidance(
            progressMeters = progress,
            remainingMeters = (totalMeters - progress).coerceAtLeast(0.0),
            totalMeters = totalMeters,
            offCourseMeters = best.off,
            offCourse = offCourse,
            finished = finished,
        )
    }

    private class Match(val segment: Int, val t: Double, val off: Double)

    private fun progressOf(m: Match) =
        cumulative[m.segment] + m.t * (cumulative[m.segment + 1] - cumulative[m.segment])

    /**
     * 가장 가까운 곳을 찾되, 비슷하게 가까운 곳이 여럿이면(같은 길을 오가는 코스) **지금 진행 위치에서 앞으로 가장 가까운 곳**을 고른다.
     * 그래야 가는 길에서 돌아오는 길로 미리 건너뛰지 않고, 돌아오는 길에서는 되돌아가지 않는다.
     */
    private fun search(lat: Double, lon: Double, from: Int, to: Int): Match? {
        val matches = (from..to).map { i ->
            val p = CourseGeometry.project(lat, lon, points[i], points[i + 1])
            Match(i, p.t, p.offMeters)
        }
        val nearest = matches.minByOrNull { it.off } ?: return null
        val similar = matches.filter { it.off <= nearest.off + TIE_METERS }
        return similar.filter { progressOf(it) >= progress - BACKWARD_SLACK_METERS }
            .minByOrNull { progressOf(it) } ?: nearest
    }

    companion object {
        const val OFF_COURSE_METERS = 40.0
        const val BACK_ON_COURSE_METERS = 25.0
        const val ARRIVE_METERS = 25.0

        /** 지금 구간에서 앞으로 몇 구간까지 먼저 찾을지. */
        private const val FORWARD_WINDOW = 25
        private const val SEARCH_ACCEPT_METERS = 60.0
        private const val TIE_METERS = 10.0
        private const val BACKWARD_SLACK_METERS = 15.0
    }
}
