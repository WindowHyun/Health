package com.windowhyun.health.domain.course

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.domain.model.CoursePoint
import org.junit.Test

class CourseFollowerTest {

    // 위도 0.001도 = 약 111m. 동쪽으로 곧게 뻗은 1km 코스(경도 0.009도).
    private val east = listOf(CoursePoint(37.0, 127.0), CoursePoint(37.0, 127.0 + 0.0113))
    private val lonPerMeter = 0.0113 / CourseGeometry.length(east)
    private fun lon(m: Double) = 127.0 + m * lonPerMeter
    private fun latOff(m: Double) = 37.0 + m / 111_195.0

    @Test fun `progress and remaining on the line`() {
        val f = CourseFollower(east)
        val g = f.update(37.0, lon(400.0))
        assertThat(g.progressMeters).isWithin(5.0).of(400.0)
        assertThat(g.remainingMeters).isWithin(5.0).of(f.totalMeters - 400.0)
        assertThat(g.offCourse).isFalse()
    }

    @Test fun `off course needs two far samples and clears with hysteresis`() {
        val f = CourseFollower(east)
        f.update(37.0, lon(100.0))
        assertThat(f.update(latOff(60.0), lon(200.0)).offCourse).isFalse() // 한 번은 흔들림으로 본다
        assertThat(f.update(latOff(60.0), lon(210.0)).offCourse).isTrue()
        assertThat(f.update(latOff(30.0), lon(220.0)).offCourse).isTrue() // 25m 안으로 들어와야 풀린다
        assertThat(f.update(latOff(10.0), lon(230.0)).offCourse).isFalse()
    }

    @Test fun `progress does not move while off course`() {
        val f = CourseFollower(east)
        val before = f.update(37.0, lon(300.0)).progressMeters
        val g = f.update(latOff(200.0), lon(800.0))
        assertThat(g.progressMeters).isEqualTo(before)
    }

    @Test fun `out and back course does not jump to the return leg early`() {
        // 동쪽 500m 갔다가 같은 길로 돌아오는 코스.
        val course = listOf(CoursePoint(37.0, 127.0), CoursePoint(37.0, lon(500.0)), CoursePoint(37.0, 127.0))
        val f = CourseFollower(course)
        val out = f.update(37.0, lon(250.0))
        assertThat(out.progressMeters).isWithin(5.0).of(250.0) // 오는 길(750m 지점)이 아니라 가는 길
        f.update(37.0, lon(500.0))
        val back = f.update(37.0, lon(400.0))
        assertThat(back.progressMeters).isWithin(8.0).of(600.0)
    }

    @Test fun `arriving at the end finishes and stays finished`() {
        val f = CourseFollower(east)
        f.update(37.0, lon(500.0))
        assertThat(f.update(37.0, lon(f.totalMeters - 10.0)).finished).isTrue()
        assertThat(f.update(37.0, lon(f.totalMeters - 200.0)).finished).isTrue()
    }

    @Test fun `far away start does not finish and finds the course globally`() {
        val f = CourseFollower(east)
        val g = f.update(latOff(500.0), lon(100.0))
        assertThat(g.finished).isFalse()
        assertThat(g.offCourseMeters).isWithin(15.0).of(500.0)
    }

    @Test fun `too short course gives empty guidance`() {
        val g = CourseFollower(listOf(CoursePoint(37.0, 127.0))).update(37.0, 127.0)
        assertThat(g.totalMeters).isEqualTo(0.0)
        assertThat(g.finished).isFalse()
    }

    @Test fun `length of a known leg`() {
        assertThat(CourseGeometry.distance(37.0, 127.0, 37.001, 127.0)).isWithin(1.0).of(111.2)
    }

    @Test fun `far from the start is approaching not off course and reports distance to start`() {
        val f = CourseFollower(east)
        val g = f.update(latOff(1_000.0), lon(0.0))
        assertThat(g.approaching).isTrue()
        assertThat(g.offCourse).isFalse()
        assertThat(g.distanceToStartMeters).isWithin(15.0).of(1_000.0)
        assertThat(f.update(latOff(800.0), lon(0.0)).offCourse).isFalse()
        assertThat(f.update(latOff(300.0), lon(0.0)).offCourse).isFalse()
    }

    @Test fun `once on the course the off course rules apply again`() {
        val f = CourseFollower(east)
        f.update(latOff(500.0), lon(0.0))
        assertThat(f.update(latOff(10.0), lon(20.0)).approaching).isFalse()
        f.update(latOff(80.0), lon(100.0))
        assertThat(f.update(latOff(80.0), lon(110.0)).offCourse).isTrue()
    }

    @Test fun `joining the course in the middle also counts as entered`() {
        val f = CourseFollower(east)
        assertThat(f.update(latOff(300.0), lon(500.0)).approaching).isTrue()
        val g = f.update(latOff(5.0), lon(500.0))
        assertThat(g.approaching).isFalse()
        assertThat(g.progressMeters).isWithin(8.0).of(500.0)
    }

    // ----- 순환 코스(출발점 = 도착점) -----

    /** 한 변 300m 정사각형을 돌아 출발점으로 돌아오는 코스(1.2km). */
    private val square = listOf(
        CoursePoint(37.0, 127.0),
        CoursePoint(37.0, lon(300.0)),
        CoursePoint(latOff(300.0), lon(300.0)),
        CoursePoint(latOff(300.0), 127.0),
        CoursePoint(37.0, 127.0),
    )

    /** 코스 위에서 출발점부터 [meters] 만큼 간 곳. */
    private fun pointAt(course: List<CoursePoint>, meters: Double): CoursePoint {
        var left = meters
        for (i in 0 until course.size - 1) {
            val length = CourseGeometry.distance(course[i], course[i + 1])
            if (left <= length || i == course.size - 2) {
                val t = (left / length).coerceIn(0.0, 1.0)
                return CoursePoint(
                    course[i].latitude + (course[i + 1].latitude - course[i].latitude) * t,
                    course[i].longitude + (course[i + 1].longitude - course[i].longitude) * t,
                )
            }
            left -= length
        }
        return course.last()
    }

    /** 실제 러닝처럼 [step] m 마다 위치를 넣으며 코스를 따라 달린다. 마지막 안내를 돌려준다. */
    private fun CourseFollower.runAlong(course: List<CoursePoint>, from: Double, to: Double, step: Double = 10.0) =
        generateSequence(from) { it + step }.takeWhile { it <= to }
            .map { pointAt(course, it).let { p -> update(p.latitude, p.longitude) } }
            .last()

    /** 출발선 조금 앞(= 마지막 구간 위)에서 시작하면 GPS 가 마지막 구간에 붙는다. 그래도 도착이 아니라 출발이다. */
    @Test fun `a first fix just behind the start line of a loop is the start not the finish`() {
        val f = CourseFollower(square)
        val g = f.update(latOff(12.0), 127.0) // 마지막 구간 위, 출발점 12m 앞

        assertThat(g.finished).isFalse()
        assertThat(g.progressMeters).isLessThan(30.0)
        assertThat(g.remainingMeters).isWithin(30.0).of(f.totalMeters)
    }

    @Test fun `a loop finishes only after it was actually run`() {
        val f = CourseFollower(square)
        f.update(latOff(12.0), 127.0)

        val half = f.runAlong(square, 0.0, 600.0)
        assertThat(half.finished).isFalse()
        assertThat(half.progressMeters).isWithin(15.0).of(600.0)

        val end = f.runAlong(square, 610.0, f.totalMeters)
        assertThat(end.finished).isTrue()
    }

    /** 집이 마지막 구간 쪽에 있어, 마지막 구간을 따라 출발점까지 걸어온 경우. 출발선에 선 것이지 완주한 것이 아니다. */
    @Test fun `walking up to the start along the last leg does not finish the loop`() {
        val f = CourseFollower(square)
        val atStart = f.runAlong(square, f.totalMeters - 200.0, f.totalMeters)
        assertThat(atStart.finished).isFalse()

        val onFirstLeg = f.runAlong(square, 0.0, 100.0)
        assertThat(onFirstLeg.finished).isFalse()
        assertThat(onFirstLeg.offCourse).isFalse()
        assertThat(onFirstLeg.progressMeters).isWithin(15.0).of(100.0)
    }

    /** 첫 신호가 늦게 잡혀 코스 중간(앞 절반 안)에서 합류해도, 절반 넘게 달렸으면 도착으로 본다. */
    @Test fun `a loop joined in its first half still finishes`() {
        val f = CourseFollower(square)
        val end = f.runAlong(square, 300.0, f.totalMeters)
        assertThat(end.finished).isTrue()
    }

    /** 순환 코스가 아니면 예전과 같다: 끝 근처에 닿으면 얼마를 달렸든 도착이다. */
    @Test fun `a one way course still finishes when joined near its end`() {
        val f = CourseFollower(east)
        assertThat(f.update(37.0, lon(f.totalMeters - 10.0)).finished).isTrue()
    }
}
