package com.windowhyun.health.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.windowhyun.health.data.local.entity.WorkoutEntity
import com.windowhyun.health.data.local.entity.WorkoutExerciseEntity
import com.windowhyun.health.data.local.entity.WorkoutSetEntity
import com.windowhyun.health.data.local.relation.ExerciseSetHistory
import com.windowhyun.health.data.local.relation.WorkoutWithDetail
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    // ---------- 세션 ----------

    @Insert
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun getWorkout(id: Long): WorkoutEntity?

    @Query("DELETE FROM workout WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Query("UPDATE workout SET memo = :memo WHERE id = :id")
    suspend fun updateMemo(id: Long, memo: String?)

    /** 아직 끝나지 않은 세션. 앱을 다시 켰을 때 이어하기 위해 사용한다. */
    @Query("SELECT * FROM workout WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun observeActiveWorkout(): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workout WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveWorkout(): WorkoutEntity?

    @Transaction
    @Query("SELECT * FROM workout WHERE id = :id")
    fun observeWorkoutDetail(id: Long): Flow<WorkoutWithDetail?>

    @Transaction
    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun getWorkoutDetail(id: Long): WorkoutWithDetail?

    @Transaction
    @Query("SELECT * FROM workout WHERE endTime IS NOT NULL ORDER BY startTime DESC LIMIT :limit")
    fun observeRecentWorkouts(limit: Int): Flow<List<WorkoutWithDetail>>

    @Transaction
    @Query(
        "SELECT * FROM workout WHERE endTime IS NOT NULL AND date BETWEEN :fromEpochDay AND :toEpochDay " +
            "ORDER BY startTime DESC",
    )
    fun observeWorkoutsBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<WorkoutWithDetail>>

    @Query(
        "SELECT COUNT(*) FROM workout WHERE endTime IS NOT NULL AND date BETWEEN :fromEpochDay AND :toEpochDay",
    )
    fun observeWorkoutCountBetween(fromEpochDay: Long, toEpochDay: Long): Flow<Int>

    @Query(
        "SELECT COALESCE(SUM(durationSeconds), 0) FROM workout " +
            "WHERE endTime IS NOT NULL AND date BETWEEN :fromEpochDay AND :toEpochDay",
    )
    fun observeWorkoutDurationBetween(fromEpochDay: Long, toEpochDay: Long): Flow<Long>

    // ---------- 세션 내 운동 ----------

    @Insert
    suspend fun insertWorkoutExercise(workoutExercise: WorkoutExerciseEntity): Long

    @Update
    suspend fun updateWorkoutExercise(workoutExercise: WorkoutExerciseEntity)

    @Query("DELETE FROM workout_exercise WHERE id = :id")
    suspend fun deleteWorkoutExercise(id: Long)

    @Query("SELECT * FROM workout_exercise WHERE workoutId = :workoutId ORDER BY orderIndex")
    suspend fun getWorkoutExercises(workoutId: Long): List<WorkoutExerciseEntity>

    @Query("SELECT COALESCE(MAX(orderIndex), -1) + 1 FROM workout_exercise WHERE workoutId = :workoutId")
    suspend fun nextExerciseOrder(workoutId: Long): Int

    // ---------- 세트 ----------

    @Insert
    suspend fun insertSet(set: WorkoutSetEntity): Long

    @Insert
    suspend fun insertSets(sets: List<WorkoutSetEntity>)

    @Update
    suspend fun updateSet(set: WorkoutSetEntity)

    @Delete
    suspend fun deleteSet(set: WorkoutSetEntity)

    @Query("SELECT * FROM workout_set WHERE id = :id")
    suspend fun getSet(id: Long): WorkoutSetEntity?

    @Query("SELECT * FROM workout_set WHERE workoutExerciseId = :workoutExerciseId ORDER BY setNumber")
    suspend fun getSets(workoutExerciseId: Long): List<WorkoutSetEntity>

    @Query("DELETE FROM workout_set WHERE workoutExerciseId = :workoutExerciseId AND completed = 0")
    suspend fun deleteIncompleteSets(workoutExerciseId: Long)

    /** 한 종목의 세트 번호를 1..n 으로 다시 매긴다. */
    @Transaction
    suspend fun renumberSets(workoutExerciseId: Long) {
        getSets(workoutExerciseId).forEachIndexed { index, set ->
            val expected = index + 1
            if (set.setNumber != expected) updateSet(set.copy(setNumber = expected))
        }
    }

    // ---------- 지난 기록 / PR ----------

    /**
     * 같은 종목을 마지막으로 수행한 세션의 완료된 세트들.
     * [excludeWorkoutId] 로 진행 중인 세션을 제외한다.
     */
    @Query(
        """
        SELECT s.* FROM workout_set s
        WHERE s.completed = 1 AND s.setType != 'WARMUP' AND s.workoutExerciseId = (
            SELECT we.id FROM workout_exercise we
            JOIN workout w ON w.id = we.workoutId
            WHERE we.exerciseId = :exerciseId
              AND w.endTime IS NOT NULL
              AND w.id != :excludeWorkoutId
              AND EXISTS (
                  SELECT 1 FROM workout_set x
                  WHERE x.workoutExerciseId = we.id AND x.completed = 1 AND x.setType != 'WARMUP'
              )
            ORDER BY w.startTime DESC LIMIT 1
        )
        ORDER BY s.setNumber
        """,
    )
    suspend fun getLastPerformedSets(exerciseId: Long, excludeWorkoutId: Long): List<WorkoutSetEntity>

    /**
     * 종목의 완료 세트 전체 이력. PR(최고 중량 / 최고 볼륨 / 예상 1RM) 계산에 사용한다.
     * 계산식을 SQL 과 Kotlin 두 곳에 두지 않기 위해 값만 가져와 Kotlin 에서 계산한다.
     */
    @Query(
        """
        SELECT w.id AS workoutId, we.id AS workoutExerciseId, s.weightKg AS weightKg,
               s.reps AS reps, s.durationSeconds AS durationSeconds, w.startTime AS startTime
        FROM workout_set s
        JOIN workout_exercise we ON we.id = s.workoutExerciseId
        JOIN workout w ON w.id = we.workoutId
        WHERE we.exerciseId = :exerciseId AND s.completed = 1 AND s.setType != 'WARMUP'
          AND w.endTime IS NOT NULL
        ORDER BY w.startTime
        """,
    )
    suspend fun getCompletedSetHistory(exerciseId: Long): List<ExerciseSetHistory>

    @Query("SELECT DISTINCT we.exerciseId FROM workout_exercise we WHERE we.workoutId = :workoutId")
    suspend fun getExerciseIdsInWorkout(workoutId: Long): List<Long>
}
