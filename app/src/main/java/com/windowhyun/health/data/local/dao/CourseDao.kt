package com.windowhyun.health.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.windowhyun.health.data.local.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM course ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<CourseEntity>>

    /** 이름으로 찾는다(러닝 기록은 코스를 이름으로만 기억한다). 같은 이름이 여럿이면 가장 최근 것. */
    @Query("SELECT * FROM course WHERE name = :name ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun getByName(name: String): CourseEntity?

    @Query("SELECT * FROM course WHERE id = :id")
    suspend fun get(id: Long): CourseEntity?

    @Insert
    suspend fun insert(course: CourseEntity): Long

    @Update
    suspend fun update(course: CourseEntity)

    @Query("DELETE FROM course WHERE id = :id")
    suspend fun delete(id: Long)
}
