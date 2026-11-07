package com.windowhyun.health.data.repository

import com.windowhyun.health.domain.model.Course
import com.windowhyun.health.domain.repository.CourseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** 코스를 저장하지 못하는 자리(테스트 등)를 위한 빈 저장소. 저장은 거절한다. */
object NoCourseRepository : CourseRepository {
    override fun observeAll(): Flow<List<Course>> = flowOf(emptyList())
    override suspend fun get(id: Long): Course? = null
    override suspend fun save(course: Course): Long = throw UnsupportedOperationException("코스를 저장할 수 없어요.")
    override suspend fun delete(id: Long) = Unit
}
