package com.windowhyun.health.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.windowhyun.health.data.local.entity.RoutineEntity
import com.windowhyun.health.data.local.entity.RoutineExerciseEntity
import com.windowhyun.health.data.local.relation.RoutineWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Transaction
    @Query("SELECT * FROM routine ORDER BY sortOrder, createdAt")
    fun observeRoutines(): Flow<List<RoutineWithItems>>

    @Transaction
    @Query("SELECT * FROM routine WHERE id = :id")
    fun observeRoutine(id: Long): Flow<RoutineWithItems?>

    @Transaction
    @Query("SELECT * FROM routine WHERE id = :id")
    suspend fun getRoutine(id: Long): RoutineWithItems?

    /**
     * 오늘 요일에 예정된 루틴. [dayMask] 는 월=1, 화=2, 수=4 ... 형태의 단일 비트.
     */
    @Transaction
    @Query("SELECT * FROM routine WHERE (scheduledDayMask & :dayMask) != 0 ORDER BY sortOrder, createdAt")
    fun observeRoutinesForDay(dayMask: Int): Flow<List<RoutineWithItems>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routine WHERE id = :id")
    suspend fun deleteRoutine(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routine_exercise WHERE routineId = :routineId")
    suspend fun deleteItemsOf(routineId: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM routine")
    suspend fun nextSortOrder(): Int

    @Query("SELECT * FROM routine WHERE id = :id")
    suspend fun getRoutineRow(id: Long): RoutineEntity?

    /** 루틴 저장은 "기존 항목 전부 삭제 후 재삽입"으로 단순하게 처리한다. */
    @Transaction
    suspend fun upsertRoutineWithItems(routine: RoutineEntity, items: List<RoutineExerciseEntity>): Long {
        val routineId = if (routine.id == 0L) {
            insertRoutine(routine.copy(sortOrder = nextSortOrder()))
        } else {
            // 수정할 때 생성 시각과 정렬 순서는 기존 값을 유지한다.
            val existing = getRoutineRow(routine.id)
            updateRoutine(
                routine.copy(
                    createdAt = existing?.createdAt ?: routine.createdAt,
                    sortOrder = existing?.sortOrder ?: routine.sortOrder,
                ),
            )
            routine.id
        }
        deleteItemsOf(routineId)
        insertItems(
            items.mapIndexed { index, item ->
                item.copy(id = 0, routineId = routineId, orderIndex = index)
            },
        )
        return routineId
    }
}
