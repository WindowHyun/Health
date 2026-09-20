package com.windowhyun.health.data.repository

import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.data.local.dao.ExerciseDao
import com.windowhyun.health.data.mapper.toDomain
import com.windowhyun.health.data.mapper.toEntity
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val exerciseDao: ExerciseDao,
) : ExerciseRepository {

    override fun observeExercises(): Flow<List<Exercise>> =
        exerciseDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeExercisesByBodyPart(bodyPart: BodyPart): Flow<List<Exercise>> =
        exerciseDao.observeByBodyPart(bodyPart.name).map { list -> list.map { it.toDomain() } }

    override suspend fun getExercise(id: Long): Exercise? = exerciseDao.getById(id)?.toDomain()

    override suspend fun addExercise(exercise: Exercise): Long {
        val existing = exerciseDao.getByName(exercise.name.trim())
        if (existing != null) return existing.id
        return exerciseDao.insert(exercise.copy(name = exercise.name.trim()).toEntity())
    }

    override suspend fun updateExercise(exercise: Exercise) =
        exerciseDao.update(exercise.toEntity())

    override suspend fun deleteExercise(exercise: Exercise) =
        exerciseDao.delete(exercise.toEntity())
}
