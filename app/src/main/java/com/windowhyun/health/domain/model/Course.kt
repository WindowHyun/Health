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
 */
data class CourseGuidance(
    val progressMeters: Double = 0.0,
    val remainingMeters: Double = 0.0,
    val totalMeters: Double = 0.0,
    val offCourseMeters: Double = 0.0,
    val offCourse: Boolean = false,
    val finished: Boolean = false,
) {
    val fraction: Float get() = if (totalMeters <= 0) 0f else (progressMeters / totalMeters).toFloat().coerceIn(0f, 1f)
}
