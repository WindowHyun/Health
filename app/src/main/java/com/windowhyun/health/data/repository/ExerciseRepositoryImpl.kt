package com.windowhyun.health.data.repository

import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.data.local.dao.ExerciseDao
import com.windowhyun.health.data.mapper.toDomain
import com.windowhyun.health.data.mapper.toEntity
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.ExerciseDeleteResult
import com.windowhyun.health.domain.model.ExerciseEditResult
import com.windowhyun.health.domain.model.ExerciseUsage
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

    override suspend fun getUsage(id: Long): ExerciseUsage =
        ExerciseUsage(
            workoutCount = exerciseDao.countWorkoutUses(id),
            routineCount = exerciseDao.countRoutineUses(id),
        )

    override suspend fun editExercise(
        id: Long,
        name: String,
        category: ExerciseCategory,
        bodyPart: BodyPart,
        trackingType: ExerciseTrackingType,
    ): ExerciseEditResult {
        val current = exerciseDao.getById(id)
        if (current == null || current.isBuiltIn) return ExerciseEditResult.NotEditable
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return ExerciseEditResult.NameBlank
        val sameName = exerciseDao.getByName(trimmed)
        if (sameName != null && sameName.id != id) return ExerciseEditResult.NameTaken
        if (trackingType != current.trackingType && exerciseDao.countWorkoutUses(id) > 0) {
            return ExerciseEditResult.TrackingTypeLocked
        }
        exerciseDao.update(
            current.copy(name = trimmed, category = category, bodyPart = bodyPart, trackingType = trackingType),
        )
        return ExerciseEditResult.Saved
    }

    override suspend fun deleteCustomExercise(id: Long): ExerciseDeleteResult {
        val current = exerciseDao.getById(id)
        if (current == null || current.isBuiltIn) return ExerciseDeleteResult.NotDeletable
        val workouts = exerciseDao.countWorkoutUses(id)
        if (workouts > 0) return ExerciseDeleteResult.InUse(workouts)
        exerciseDao.delete(current)
        return ExerciseDeleteResult.Deleted
    }
}
