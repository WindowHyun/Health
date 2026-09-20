package com.windowhyun.health.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.windowhyun.health.data.local.entity.RunEntity
import com.windowhyun.health.data.local.entity.RunLapEntity
import com.windowhyun.health.data.local.entity.RunLocationEntity
import kotlinx.coroutines.flow.Flow

/** 러닝 기록 조회/저장. */
@Dao
interface RunDao {

    @Insert
    suspend fun insertRun(run: RunEntity): Long

    @Update
    suspend fun updateRun(run: RunEntity)

    @Query("DELETE FROM run WHERE id = :id")
    suspend fun deleteRun(id: Long)

    @Query("SELECT * FROM run WHERE id = :id")
    suspend fun getRun(id: Long): RunEntity?

    /** 아직 끝나지 않은 러닝. 앱이 다시 켜졌을 때 복구에 사용한다. */
    @Query("SELECT * FROM run WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveRun(): RunEntity?

    @Query("SELECT * FROM run WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun observeActiveRun(): Flow<RunEntity?>

    /** 기록 중 주기적으로 누적값만 갱신한다(행 전체를 다시 쓰지 않는다). */
    @Query(
        """
        UPDATE run SET distanceMeters = :distanceMeters, durationSeconds = :durationSeconds,
            averagePaceSecPerKm = :averagePace, bestPaceSecPerKm = :bestPace, calories = :calories
        WHERE id = :id
        """,
    )
    suspend fun updateProgress(
        id: Long,
        distanceMeters: Double,
        durationSeconds: Long,
        averagePace: Double,
        bestPace: Double,
        calories: Int,
    )

    @Query("UPDATE run SET endTime = :endTime WHERE id = :id")
    suspend fun markFinished(id: Long, endTime: Long)

    @Query("UPDATE run SET memo = :memo WHERE id = :id")
    suspend fun updateMemoOf(id: Long, memo: String?)

    /** 개인 기록 비교용. 특정 러닝은 제외하고 조회한다. */
    @Query("SELECT COALESCE(MAX(distanceMeters), 0) FROM run WHERE endTime IS NOT NULL AND id != :excludeRunId")
    suspend fun maxDistanceExcluding(excludeRunId: Long): Double

    @Query(
        """
        SELECT MIN(averagePaceSecPerKm) FROM run
        WHERE endTime IS NOT NULL AND id != :excludeRunId
          AND averagePaceSecPerKm > 0 AND distanceMeters >= :minDistanceMeters
        """,
    )
    suspend fun bestAveragePaceExcluding(excludeRunId: Long, minDistanceMeters: Double): Double?

    @Query("SELECT * FROM run WHERE endTime IS NOT NULL ORDER BY startTime DESC LIMIT :limit")
    fun observeRecentRuns(limit: Int): Flow<List<RunEntity>>

    @Query(
        "SELECT COALESCE(SUM(distanceMeters), 0) FROM run " +
            "WHERE endTime IS NOT NULL AND date BETWEEN :fromEpochDay AND :toEpochDay",
    )
    fun observeDistanceBetween(fromEpochDay: Long, toEpochDay: Long): Flow<Double>

    @Query(
        "SELECT COALESCE(SUM(durationSeconds), 0) FROM run " +
            "WHERE endTime IS NOT NULL AND date BETWEEN :fromEpochDay AND :toEpochDay",
    )
    fun observeDurationBetween(fromEpochDay: Long, toEpochDay: Long): Flow<Long>

    @Insert
    suspend fun insertLaps(laps: List<RunLapEntity>)

    @Insert
    suspend fun insertLap(lap: RunLapEntity): Long

    @Query("SELECT * FROM run_lap WHERE runId = :runId ORDER BY lapNumber")
    suspend fun getLaps(runId: Long): List<RunLapEntity>

    @Insert
    suspend fun insertLocations(locations: List<RunLocationEntity>)

    @Query("SELECT * FROM run_location WHERE runId = :runId ORDER BY timestamp")
    suspend fun getLocations(runId: Long): List<RunLocationEntity>

    @Transaction
    @Query("SELECT * FROM run WHERE endTime IS NOT NULL AND date BETWEEN :fromEpochDay AND :toEpochDay ORDER BY startTime DESC")
    fun observeRunsBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<RunEntity>>
}
