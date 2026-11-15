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

    /** 코스에 한 번이라도 들어섰는가. 그 전에는 출발점으로 가는 중이라 "벗어남" 이 아니다. */
    private var entered = false

    /** 출발점과 도착점이 같은(가까운) 코스인가. 이런 코스는 출발선 근처가 마지막 구간과도 가깝다. */
    private val isLoop = points.size >= 3 &&
        CourseGeometry.distance(points.first(), points.last()) <= LOOP_CLOSE_METERS

    /** 코스를 따라 **이어서** 달린 거리(m). 다른 곳에 붙으며 건너뛴 거리는 넣지 않는다. */
    private var covered = 0.0

    fun update(latitude: Double, longitude: Double): CourseGuidance {
        if (points.size < 2) return CourseGuidance(totalMeters = totalMeters)

        val best = search(latitude, longitude, from = segment, to = minOf(points.size - 2, segment + FORWARD_WINDOW))
            ?.takeIf { it.off <= SEARCH_ACCEPT_METERS }
            ?: search(latitude, longitude, 0, points.size - 2)!!

        val candidateProgress = progressOf(best)
        if (!entered) {
            if (best.off > OFF_COURSE_METERS) {
                return CourseGuidance(
                    remainingMeters = totalMeters,
                    totalMeters = totalMeters,
                    offCourseMeters = best.off,
                    approaching = true,
                    distanceToStartMeters = CourseGeometry.distance(latitude, longitude, points[0].latitude, points[0].longitude),
                )
            }
            entered = true
        }

        // 코스를 벗어나 있는 동안에는 진행 위치를 바꾸지 않는다(엉뚱한 곳에 붙어 거리가 튀지 않게).
        if (best.off <= OFF_COURSE_METERS) {
            val step = candidateProgress - progress
            if (kotlin.math.abs(step) <= CONTINUOUS_STEP_METERS) covered = (covered + step).coerceAtLeast(0.0)
            segment = best.segment
            progress = candidateProgress
        }

        farStreak = if (best.off > OFF_COURSE_METERS) farStreak + 1 else 0
        if (!offCourse && farStreak >= 2) offCourse = true
        if (offCourse && best.off <= BACK_ON_COURSE_METERS) offCourse = false

        if (!finished && progress >= totalMeters - ARRIVE_METERS && best.off <= OFF_COURSE_METERS) {
            if (!isLoop || covered >= totalMeters * LOOP_FINISH_MIN_COVERAGE) {
                finished = true
            } else {
                // 순환 코스의 끝점은 출발점이기도 하다. 코스를 거의 달리지 않고 여기 있다면 도착한 것이 아니라 출발선에 선 것이다.
                // (출발선 조금 앞 = 마지막 구간 위에서 시작하면 GPS 가 마지막 구간에 붙는다. 그대로 두면 시작하자마자 "도착" 이 되고,
                //  한 번 도착하면 풀리지 않아 그 러닝 내내 코스 안내가 멈춘다.)
                segment = 0
                progress = 0.0
                covered = 0.0
            }
        }

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

        /** 위치 한 번에 진행 거리가 이만큼 넘게 바뀌면 "이어 달린 것" 이 아니라 다른 곳에 붙은 것으로 본다(위치는 1~2초마다 온다). */
        private const val CONTINUOUS_STEP_METERS = 150.0

        /** 출발점과 도착점이 이만큼 가까우면 순환 코스로 본다. */
        private const val LOOP_CLOSE_METERS = 40.0

        /** 순환 코스에서 "도착" 으로 인정하려면 코스의 이만큼은 이어서 달렸어야 한다. */
        private const val LOOP_FINISH_MIN_COVERAGE = 0.5
    }
}
