package com.windowhyun.health.data.repository

import com.windowhyun.health.data.local.dao.CourseDao
import com.windowhyun.health.data.local.entity.CourseEntity
import com.windowhyun.health.domain.model.Course
import com.windowhyun.health.domain.model.CoursePoint
import com.windowhyun.health.domain.repository.CourseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CourseRepositoryImpl internal constructor(
    private val dao: CourseDao,
    private val clock: () -> Long,
) : CourseRepository {

    @Inject
    constructor(dao: CourseDao) : this(dao, System::currentTimeMillis)

    override fun observeAll(): Flow<List<Course>> = dao.observeAll().map { list -> list.mapNotNull { it.toDomain() } }

    override suspend fun get(id: Long): Course? = dao.get(id)?.toDomain()

    override suspend fun save(course: Course): Long {
        require(course.points.size >= 2) { "코스는 지점이 2개 이상이어야 해요." }
        val name = course.name.trim().ifEmpty { "코스" }.take(MAX_NAME)
        val points = encode(course.points)
        return if (course.id == 0L) {
            dao.insert(CourseEntity(name = name, createdAt = clock(), points = points))
        } else {
            val old = dao.get(course.id)
            dao.update(CourseEntity(course.id, name, old?.createdAt ?: clock(), points))
            course.id
        }
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    companion object {
        const val MAX_NAME = 40

        internal fun encode(points: List<CoursePoint>): String =
            points.joinToString(";") { "%.6f,%.6f".format(java.util.Locale.ROOT, it.latitude, it.longitude) }

        /** 깨진 줄은 건너뛴다. 읽을 수 있는 지점이 2개 미만이면 null(목록에서 빠진다). */
        internal fun decode(text: String): List<CoursePoint>? {
            val points = text.split(';').mapNotNull { part ->
                val (lat, lon) = part.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
                val la = lat.toDoubleOrNull() ?: return@mapNotNull null
                val lo = lon.toDoubleOrNull() ?: return@mapNotNull null
                if (la !in -90.0..90.0 || lo !in -180.0..180.0) null else CoursePoint(la, lo)
            }
            return points.takeIf { it.size >= 2 }
        }
    }

    private fun CourseEntity.toDomain(): Course? =
        decode(points)?.let { Course(id = id, name = name, points = it, createdAt = createdAt) }
}
