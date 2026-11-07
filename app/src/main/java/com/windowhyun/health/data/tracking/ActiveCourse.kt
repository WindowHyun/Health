package com.windowhyun.health.data.tracking

import com.windowhyun.health.domain.model.Course
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 시작 화면에서 고른 "따라갈 코스". 러닝을 시작할 때 서비스가 한 번 읽어 간다.
 * 앱 프로세스가 같은 동안만 유지된다(러닝 중 앱이 강제로 죽으면 코스 안내만 사라지고 기록은 복구된다).
 */
@Singleton
class ActiveCourse @Inject constructor() {
    private val _course = MutableStateFlow<Course?>(null)
    val course: StateFlow<Course?> = _course.asStateFlow()

    fun select(course: Course?) {
        _course.value = course
    }

    /**
     * 서비스가 러닝을 시작하며 고른 코스를 **한 번만** 가져간다. 가져가면 비운다.
     * 그렇지 않으면 시계에서 시작한 러닝 같은, 코스를 고르지 않은 러닝에 지난번 코스가 따라붙는다.
     */
    fun take(): Course? = _course.getAndUpdate { null }
}
