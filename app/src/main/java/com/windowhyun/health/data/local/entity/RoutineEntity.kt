package com.windowhyun.health.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 사용자가 만든 운동 루틴. 예) 상체 / 하체 / 전신 A
 *
 * @param scheduledDayMask 요일 비트마스크. 월=1 shl 0 ... 일=1 shl 6. 0 이면 요일 지정 없음.
 */
@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val scheduledDayMask: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
)
