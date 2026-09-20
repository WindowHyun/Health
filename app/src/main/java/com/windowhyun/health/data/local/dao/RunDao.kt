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

/** Phase 2 에서 본격적으로 사용한다. 홈 화면 주간 합계는 Phase 1 부터 사용. */
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
