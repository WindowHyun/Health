package com.windowhyun.health.data.tracking

import com.windowhyun.health.domain.model.Course
import com.windowhyun.health.domain.repository.CourseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** 테스트용 메모리 코스 저장소. */
class InMemoryCourses(initial: List<Course> = emptyList()) : CourseRepository {
    private val state = MutableStateFlow(initial)
    override fun observeAll() = state.map { it }
    override suspend fun get(id: Long) = state.value.firstOrNull { it.id == id }
    override suspend fun save(course: Course): Long {
        require(course.points.size >= 2)
        val id = if (course.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1 else course.id
        state.value = state.value.filter { it.id != id } + course.copy(id = id)
        return id
    }
    override suspend fun delete(id: Long) { state.value = state.value.filter { it.id != id } }
}
