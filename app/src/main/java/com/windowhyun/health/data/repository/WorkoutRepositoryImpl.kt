package com.windowhyun.health.data.repository

import com.windowhyun.health.core.model.PersonalRecord
import com.windowhyun.health.core.model.PersonalRecordType
import com.windowhyun.health.data.local.dao.ExerciseDao
import com.windowhyun.health.data.local.dao.PersonalRecordDao
import com.windowhyun.health.data.local.dao.RoutineDao
import com.windowhyun.health.data.local.dao.WorkoutDao
import com.windowhyun.health.data.local.entity.PersonalRecordEntity
import com.windowhyun.health.data.local.entity.WorkoutEntity
import com.windowhyun.health.data.local.entity.WorkoutExerciseEntity
import com.windowhyun.health.data.local.entity.WorkoutSetEntity
import com.windowhyun.health.data.mapper.toDomain
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.model.WorkoutSet
import com.windowhyun.health.domain.model.WorkoutSummary
import com.windowhyun.health.domain.repository.WorkoutRepository
import com.windowhyun.health.domain.usecase.PersonalRecordCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * 헬스 세션의 모든 쓰기/읽기를 담당한다.
 *
 * 설계상의 핵심: 운동을 "시작"하는 순간 세션과 세트 행을 DB 에 먼저 만든다.
 * 덕분에 앱이 죽어도 진행 상황이 남고, UI 는 DB Flow 만 구독하면 된다.
 */
@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val routineDao: RoutineDao,
    private val exerciseDao: ExerciseDao,
    private val personalRecordDao: PersonalRecordDao,
) : WorkoutRepository {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    override fun observeActiveWorkout(): Flow<Workout?> =
        workoutDao.observeActiveWorkout().map { it?.toDomain() }

    override fun observeWorkoutDetail(workoutId: Long): Flow<Workout?> =
        workoutDao.observeWorkoutDetail(workoutId).map { it?.toDomain() }

    override fun observeRecentWorkouts(limit: Int): Flow<List<Workout>> =
        workoutDao.observeRecentWorkouts(limit).map { list -> list.map { it.toDomain() } }

    override fun observeWorkoutsBetween(from: LocalDate, to: LocalDate): Flow<List<Workout>> =
        workoutDao.observeWorkoutsBetween(from.toEpochDay(), to.toEpochDay())
            .map { list -> list.map { it.toDomain() } }

    override fun observeWorkoutCountBetween(from: LocalDate, to: LocalDate): Flow<Int> =
        workoutDao.observeWorkoutCountBetween(from.toEpochDay(), to.toEpochDay())

    override fun observeWorkoutDurationBetween(from: LocalDate, to: LocalDate): Flow<Long> =
        workoutDao.observeWorkoutDurationBetween(from.toEpochDay(), to.toEpochDay())

    override suspend fun getWorkout(workoutId: Long): Workout? =
        workoutDao.getWorkoutDetail(workoutId)?.toDomain()

    override suspend fun startWorkout(routineId: Long?): Long {
        val now = System.currentTimeMillis()
        val routine = routineId?.let { routineDao.getRoutine(it) }
        val workoutId = workoutDao.insertWorkout(
            WorkoutEntity(
                routineId = routine?.routine?.id,
                routineName = routine?.routine?.name,
                date = LocalDate.now(zone).toEpochDay(),
                startTime = now,
                endTime = null,
            ),
        )

        routine?.items?.sortedBy { it.routineExercise.orderIndex }?.forEachIndexed { index, item ->
            val workoutExerciseId = workoutDao.insertWorkoutExercise(
                WorkoutExerciseEntity(
                    workoutId = workoutId,
                    exerciseId = item.exercise.id,
                    orderIndex = index,
                    restSeconds = item.routineExercise.restSeconds ?: item.exercise.defaultRestSeconds,
                ),
            )
            val lastSets = workoutDao.getLastPerformedSets(item.exercise.id, workoutId)
            val plannedSets = max(1, item.routineExercise.defaultSets)
            workoutDao.insertSets(buildPlannedSets(workoutExerciseId, plannedSets, lastSets))
        }
        return workoutId
    }

    /** 지난 기록을 기본값으로 채운 계획 세트를 만든다. */
    private fun buildPlannedSets(
        workoutExerciseId: Long,
        plannedSetCount: Int,
        lastSets: List<WorkoutSetEntity>,
    ): List<WorkoutSetEntity> = (1..plannedSetCount).map { setNumber ->
        // 지난번 같은 번호의 세트 -> 없으면 지난번 마지막 세트 -> 그것도 없으면 0
        val reference = lastSets.getOrNull(setNumber - 1) ?: lastSets.lastOrNull()
        WorkoutSetEntity(
            workoutExerciseId = workoutExerciseId,
            setNumber = setNumber,
            weightKg = reference?.weightKg ?: 0.0,
            reps = reference?.reps ?: 0,
            completed = false,
        )
    }

    override suspend fun addExerciseToWorkout(workoutId: Long, exerciseId: Long): Long {
        val exercise = exerciseDao.getById(exerciseId) ?: return -1
        val order = workoutDao.nextExerciseOrder(workoutId)
        val workoutExerciseId = workoutDao.insertWorkoutExercise(
            WorkoutExerciseEntity(
                workoutId = workoutId,
                exerciseId = exerciseId,
                orderIndex = order,
                restSeconds = exercise.defaultRestSeconds,
            ),
        )
        val lastSets = workoutDao.getLastPerformedSets(exerciseId, workoutId)
        val plannedCount = max(1, lastSets.size)
        workoutDao.insertSets(buildPlannedSets(workoutExerciseId, plannedCount, lastSets))
        return workoutExerciseId
    }

    override suspend fun removeWorkoutExercise(workoutExerciseId: Long) =
        workoutDao.deleteWorkoutExercise(workoutExerciseId)

    override suspend fun addSet(workoutExerciseId: Long): Long {
        val sets = workoutDao.getSets(workoutExerciseId)
        val last = sets.lastOrNull()
        return workoutDao.insertSet(
            WorkoutSetEntity(
                workoutExerciseId = workoutExerciseId,
                setNumber = (last?.setNumber ?: 0) + 1,
                weightKg = last?.weightKg ?: 0.0,
                reps = last?.reps ?: 0,
                completed = false,
            ),
        )
    }

    override suspend fun removeSet(setId: Long) {
        val set = workoutDao.getSet(setId) ?: return
        workoutDao.deleteSet(set)
        workoutDao.renumberSets(set.workoutExerciseId)
    }

    override suspend fun updateSet(set: WorkoutSet, workoutExerciseId: Long) {
        val stored = workoutDao.getSet(set.id) ?: return
        workoutDao.updateSet(
            stored.copy(
                weightKg = set.weightKg,
                reps = set.reps,
                completed = set.completed,
                completedAt = if (set.completed) stored.completedAt ?: System.currentTimeMillis() else null,
            ),
        )
    }

    override suspend fun setCompleted(setId: Long, weightKg: Double, reps: Int, completed: Boolean) {
        val stored = workoutDao.getSet(setId) ?: return
        workoutDao.updateSet(
            stored.copy(
                weightKg = weightKg,
                reps = reps,
                completed = completed,
                completedAt = if (completed) System.currentTimeMillis() else null,
            ),
        )
    }

    override suspend fun getLastPerformance(exerciseId: Long, excludeWorkoutId: Long): List<WorkoutSet> =
        workoutDao.getLastPerformedSets(exerciseId, excludeWorkoutId).map { it.toDomain() }

    override suspend fun finishWorkout(workoutId: Long): WorkoutSummary {
        val detail = workoutDao.getWorkoutDetail(workoutId)
            ?: return WorkoutSummary(workoutId, "", 0, 0, 0, 0, 0.0)
        val workout = detail.toDomain()

        // PR 은 endTime 을 채우기 전에 계산한다.
        // 이력 조회가 endTime IS NOT NULL 조건을 쓰므로 진행 중인 세션이 자동으로 제외된다.
        val personalRecords = buildList {
            workout.exercises.filter { it.completedSets.isNotEmpty() }.forEach { record ->
                val history = workoutDao.getCompletedSetHistory(record.exercise.id)
                addAll(
                    PersonalRecordCalculator.newRecords(
                        exerciseId = record.exercise.id,
                        exerciseName = record.exercise.name,
                        previous = PersonalRecordCalculator.fromHistory(history),
                        session = PersonalRecordCalculator.fromSession(record.sets),
                    ),
                )
            }
        }

        // 완료하지 않은 세트와, 한 세트도 하지 않은 운동은 기록에서 정리한다.
        workout.exercises.forEach { record ->
            if (record.completedSets.isEmpty()) {
                workoutDao.deleteWorkoutExercise(record.id)
            } else {
                workoutDao.deleteIncompleteSets(record.id)
                workoutDao.renumberSets(record.id)
            }
        }

        // 새로 세운 기록을 저장해 둔다. 화면이 다시 만들어져도 PR 표시가 유지된다.
        if (personalRecords.isNotEmpty()) {
            val achievedAt = System.currentTimeMillis()
            personalRecordDao.insertAll(
                personalRecords.map { record ->
                    PersonalRecordEntity(
                        workoutId = workoutId,
                        exerciseId = record.exerciseId,
                        type = record.type.name,
                        value = record.value,
                        previousValue = record.previousValue,
                        reps = record.reps,
                        weightKg = record.weightKg,
                        achievedAt = achievedAt,
                    )
                },
            )
        }

        val end = System.currentTimeMillis()
        val duration = ((end - workout.startTime) / 1000).coerceAtLeast(0)
        workoutDao.updateWorkout(
            detail.workout.copy(endTime = end, durationSeconds = duration),
        )

        return WorkoutSummary(
            workoutId = workoutId,
            routineName = workout.displayName,
            durationSeconds = duration,
            exerciseCount = workout.performedExerciseCount,
            totalSets = workout.totalCompletedSets,
            totalReps = workout.totalReps,
            totalVolumeKg = workout.totalVolume,
            personalRecords = personalRecords,
            memo = workout.memo,
        )
    }

    override suspend fun discardWorkout(workoutId: Long) = workoutDao.deleteWorkout(workoutId)

    override suspend fun updateMemo(workoutId: Long, memo: String?) =
        workoutDao.updateMemo(workoutId, memo?.takeIf { it.isNotBlank() })

    override suspend fun deleteWorkout(workoutId: Long) = workoutDao.deleteWorkout(workoutId)

    override fun observeWorkoutRecords(workoutId: Long): Flow<List<PersonalRecord>> =
        personalRecordDao.observeByWorkout(workoutId).map { rows ->
            rows.map { row ->
                PersonalRecord(
                    exerciseId = row.exerciseId,
                    exerciseName = row.exerciseName,
                    type = runCatching { PersonalRecordType.valueOf(row.type) }
                        .getOrDefault(PersonalRecordType.MAX_WEIGHT),
                    value = row.value,
                    previousValue = row.previousValue,
                    reps = row.reps,
                    weightKg = row.weightKg,
                )
            }
        }

    override suspend fun getPersonalRecords(exerciseId: Long): List<PersonalRecord> {
        val exercise = exerciseDao.getById(exerciseId) ?: return emptyList()
        val bests = PersonalRecordCalculator.fromHistory(workoutDao.getCompletedSetHistory(exerciseId))
        return PersonalRecordCalculator.currentRecords(exerciseId, exercise.name, bests)
    }
}
