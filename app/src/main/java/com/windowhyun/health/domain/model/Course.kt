package com.windowhyun.health.domain.model

/** 코스 위의 한 지점. */
data class CoursePoint(val latitude: Double, val longitude: Double)

/** 미리 찍어 둔 러닝 코스. 지점들을 차례로 이은 선이다. */
data class Course(
    val id: Long = 0,
    val name: String,
    val points: List<CoursePoint>,
    val createdAt: Long = 0,
) {
    /** 코스 전체 길이(m). */
    val distanceMeters: Double get() = com.windowhyun.health.domain.course.CourseGeometry.length(points)
}

/**
 * 코스를 따라가는 중의 안내 값.
 *
 * @param progressMeters 코스 시작부터 지금 위치(코스 위로 옮긴 점)까지의 거리.
 * @param offCourseMeters 코스 선에서 떨어진 거리.
 * @param offCourse 코스를 벗어난 상태(잠깐 벗어난 것은 포함하지 않는다).
 * @param finished 코스 끝에 도착했다.
 * @param approaching 코스에 처음 들어서기 전(출발점으로 가는 중).
 */
data class CourseGuidance(
    val progressMeters: Double = 0.0,
    val remainingMeters: Double = 0.0,
    val totalMeters: Double = 0.0,
    val offCourseMeters: Double = 0.0,
    val offCourse: Boolean = false,
    val finished: Boolean = false,
    /** 아직 코스에 들어서지 않았다(출발점으로 가는 중). 이 동안은 벗어남 경고를 하지 않는다. */
    val approaching: Boolean = false,
    /** [approaching] 일 때 출발점까지의 거리. */
    val distanceToStartMeters: Double = 0.0,
) {
    val fraction: Float get() = if (totalMeters <= 0) 0f else (progressMeters / totalMeters).toFloat().coerceIn(0f, 1f)
}

/** 같은 코스를 달린 기록 모음. [count] 는 이 러닝까지 센 횟수, [bestDurationSeconds] 는 그중 가장 빠른 시간. */
data class CourseRunStats(val count: Int, val bestDurationSeconds: Long)
