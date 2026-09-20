package com.windowhyun.health.domain.repository

import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.PersonalRecord
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.model.WorkoutSet
import com.windowhyun.health.domain.model.WorkoutSummary
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate

interface ExerciseRepository {
    fun observeExercises(): Flow<List<Exercise>>
    fun observeExercisesByBodyPart(bodyPart: BodyPart): Flow<List<Exercise>>
    suspend fun getExercise(id: Long): Exercise?
    suspend fun addExercise(exercise: Exercise): Long
    suspend fun updateExercise(exercise: Exercise)
    suspend fun deleteExercise(exercise: Exercise)
}

interface RoutineRepository {
    fun observeRoutines(): Flow<List<Routine>>
    fun observeRoutine(id: Long): Flow<Routine?>
    fun observeRoutinesForDay(day: DayOfWeek): Flow<List<Routine>>
    suspend fun getRoutine(id: Long): Routine?
    suspend fun saveRoutine(routine: Routine): Long
    suspend fun deleteRoutine(id: Long)
}

interface WorkoutRepository {
    /** 진행 중(종료되지 않은) 세션. */
    fun observeActiveWorkout(): Flow<Workout?>
    fun observeWorkoutDetail(workoutId: Long): Flow<Workout?>
    fun observeRecentWorkouts(limit: Int): Flow<List<Workout>>
    fun observeWorkoutsBetween(from: LocalDate, to: LocalDate): Flow<List<Workout>>
    fun observeWorkoutCountBetween(from: LocalDate, to: LocalDate): Flow<Int>
    fun observeWorkoutDurationBetween(from: LocalDate, to: LocalDate): Flow<Long>

    suspend fun getWorkout(workoutId: Long): Workout?

    /**
     * 루틴으로 세션을 시작한다. 루틴의 운동/세트를 미리 만들어 두고,
     * 지난 기록이 있으면 중량/횟수 기본값으로 채운다.
     * [routineId] 가 null 이면 빈 자유 운동 세션을 만든다.
     */
    suspend fun startWorkout(routineId: Long?): Long

    suspend fun addExerciseToWorkout(workoutId: Long, exerciseId: Long): Long
    suspend fun removeWorkoutExercise(workoutExerciseId: Long)

    suspend fun addSet(workoutExerciseId: Long): Long
    suspend fun removeSet(setId: Long)
    suspend fun updateSet(set: WorkoutSet, workoutExerciseId: Long)
    suspend fun setCompleted(setId: Long, weightKg: Double, reps: Int, completed: Boolean)

    /** 같은 종목을 마지막으로 수행했을 때의 세트들. */
    suspend fun getLastPerformance(exerciseId: Long, excludeWorkoutId: Long): List<WorkoutSet>

    /** 세션을 종료하고 요약을 만든다. 완료되지 않은 세트는 정리한다. */
    suspend fun finishWorkout(workoutId: Long): WorkoutSummary

    /** 세션을 버린다(기록 삭제). */
    suspend fun discardWorkout(workoutId: Long)

    suspend fun updateMemo(workoutId: Long, memo: String?)
    suspend fun deleteWorkout(workoutId: Long)

    /** 해당 종목의 현재 PR 목록. */
    suspend fun getPersonalRecords(exerciseId: Long): List<PersonalRecord>

    /** 특정 세션에서 새로 달성한 PR. */
    fun observeWorkoutRecords(workoutId: Long): Flow<List<PersonalRecord>>
}

interface RunRepository {
    fun observeRecentRuns(limit: Int): Flow<List<Run>>
    fun observeRunsBetween(from: LocalDate, to: LocalDate): Flow<List<Run>>
    fun observeDistanceBetween(from: LocalDate, to: LocalDate): Flow<Double>
    fun observeDurationBetween(from: LocalDate, to: LocalDate): Flow<Long>
    suspend fun getRun(id: Long): Run?
    suspend fun saveRun(run: Run): Long
    suspend fun updateMemo(runId: Long, memo: String?)
    suspend fun deleteRun(id: Long)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
