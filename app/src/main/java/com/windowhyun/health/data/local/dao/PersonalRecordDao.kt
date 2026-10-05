package com.windowhyun.health.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.windowhyun.health.data.local.entity.PersonalRecordEntity
import com.windowhyun.health.data.local.relation.PersonalRecordWithExercise
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalRecordDao {

    @Insert
    suspend fun insertAll(records: List<PersonalRecordEntity>)

    @Query(
        """
        SELECT pr.id AS id, pr.workoutId AS workoutId, pr.exerciseId AS exerciseId,
               e.name AS exerciseName, pr.type AS type, pr.value AS value,
               pr.previousValue AS previousValue, pr.reps AS reps,
               pr.weightKg AS weightKg, pr.achievedAt AS achievedAt
        FROM personal_record pr
        JOIN exercise e ON e.id = pr.exerciseId
        WHERE pr.workoutId = :workoutId
        ORDER BY pr.id
        """,
    )
    fun observeByWorkout(workoutId: Long): Flow<List<PersonalRecordWithExercise>>

    @Query(
        """
        SELECT pr.id AS id, pr.workoutId AS workoutId, pr.exerciseId AS exerciseId,
               e.name AS exerciseName, pr.type AS type, pr.value AS value,
               pr.previousValue AS previousValue, pr.reps AS reps,
               pr.weightKg AS weightKg, pr.achievedAt AS achievedAt
        FROM personal_record pr
        JOIN exercise e ON e.id = pr.exerciseId
        WHERE pr.workoutId = :workoutId
        ORDER BY pr.id
        """,
    )
    suspend fun getByWorkout(workoutId: Long): List<PersonalRecordWithExercise>

    @Query("DELETE FROM personal_record WHERE workoutId = :workoutId")
    suspend fun deleteByWorkout(workoutId: Long)

    @Query("DELETE FROM personal_record WHERE exerciseId = :exerciseId")
    suspend fun deleteByExercise(exerciseId: Long)

    /** 한 종목의 PR 을 통째로 다시 쓴다. 중간에 실패하면 원래 기록이 남는다. */
    @Transaction
    suspend fun replaceForExercise(exerciseId: Long, records: List<PersonalRecordEntity>) {
        deleteByExercise(exerciseId)
        if (records.isNotEmpty()) insertAll(records)
    }
}
