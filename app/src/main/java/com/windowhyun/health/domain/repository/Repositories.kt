package com.windowhyun.health.domain.repository

import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.PersonalRecord
import com.windowhyun.health.core.model.SetType
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.RunLap
import com.windowhyun.health.domain.model.RunPoint
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.model.WorkoutSet
import com.windowhyun.health.domain.model.WorkoutSummary
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream
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
    suspend fun setCompleted(
        setId: Long,
        weightKg: Double,
        reps: Int,
        completed: Boolean,
        durationSeconds: Int = 0,
    )

    /** 세트 종류 변경(본세트 · 워밍업 · 드롭 · 실패). */
    suspend fun setSetType(setId: Long, setType: SetType)

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

/** 러닝 개인 기록 비교 결과. */
data class RunPersonalBests(
    val isLongestDistance: Boolean = false,
    val isFastestAveragePace: Boolean = false,
    val previousLongestMeters: Double = 0.0,
    val previousBestPaceSecPerKm: Double? = null,
)

interface RunRepository {
    fun observeRecentRuns(limit: Int): Flow<List<Run>>
    fun observeRunsBetween(from: LocalDate, to: LocalDate): Flow<List<Run>>
    fun observeDistanceBetween(from: LocalDate, to: LocalDate): Flow<Double>
    fun observeDurationBetween(from: LocalDate, to: LocalDate): Flow<Long>
    suspend fun getRun(id: Long): Run?
    suspend fun saveRun(run: Run): Long
    suspend fun updateMemo(runId: Long, memo: String?)
    suspend fun deleteRun(id: Long)

    // ----- 기록 중 사용하는 증분 저장 -----

    /**
     * 러닝을 시작하며 endTime = null 인 행을 먼저 만든다.
     * 기록 중 위치/Lap 을 계속 붙이기 때문에 앱이 죽어도 데이터가 남는다.
     */
    suspend fun startRun(goalType: RunGoalType, goalValue: Double): Long

    /** 아직 끝나지 않은 러닝(복구용). */
    suspend fun getActiveRun(): Run?

    suspend fun appendRoutePoints(runId: Long, points: List<RunPoint>)

    suspend fun appendLap(runId: Long, lap: RunLap)

    suspend fun updateProgress(
        runId: Long,
        distanceMeters: Double,
        durationSeconds: Long,
        averagePaceSecPerKm: Double,
        bestPaceSecPerKm: Double,
        calories: Int,
        steps: Int,
    )

    /** 러닝을 종료 상태로 만든다. */
    suspend fun finishRun(
        runId: Long,
        endTime: Long,
        distanceMeters: Double,
        durationSeconds: Long,
        averagePaceSecPerKm: Double,
        bestPaceSecPerKm: Double,
        calories: Int,
        steps: Int,
    )

    /** 이번 러닝이 개인 기록을 갱신했는지 확인한다. */
    suspend fun comparePersonalBests(run: Run): RunPersonalBests
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/** 백업 파일 한 건의 내용 요약. 내보내기·복원 후 무엇이 오갔는지 알려 준다. */
data class BackupSummary(
    val createdAt: Long = 0,
    val appVersion: String = "",
    val routineCount: Int = 0,
    val customExerciseCount: Int = 0,
    val workoutCount: Int = 0,
    val setCount: Int = 0,
    val runCount: Int = 0,
    /** 연결이 끊어져 버린 행 수. 정상 파일이면 0 이다. */
    val droppedRows: Int = 0,
)

/** 백업 파일을 읽을 수 없을 때. 메시지는 그대로 사용자에게 보여 준다. */
class BackupFormatException(message: String) : Exception(message)

/**
 * 기록 내보내기 / 가져오기.
 *
 * 서버가 없는 앱이라 기기를 바꾸거나 앱을 지우면 기록이 사라진다.
 * 백업 파일이 유일한 복구 수단이다.
 */
interface BackupRepository {

    /** 전체 기록을 JSON 으로 쓴다. */
    suspend fun exportBackup(output: OutputStream): BackupSummary

    /** 백업 파일을 읽어 기존 기록을 **전부 대체**한다. */
    suspend fun restoreBackup(input: InputStream): BackupSummary

    /** 헬스 기록을 세트 단위 CSV 로 쓴다. 반환값은 줄 수(머리글 제외). */
    suspend fun exportWorkoutCsv(output: OutputStream): Int

    /** 러닝 기록을 CSV 로 쓴다. 반환값은 줄 수(머리글 제외). */
    suspend fun exportRunCsv(output: OutputStream): Int
}
