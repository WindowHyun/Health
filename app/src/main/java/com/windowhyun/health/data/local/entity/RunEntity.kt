package com.windowhyun.health.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 러닝 1회 기록.
 *
 * @param distanceMeters 항상 meter 로 저장.
 * @param averagePaceSecPerKm 초/킬로미터.
 * @param steps 기기 걸음 센서로 센 이번 러닝의 걸음 수. 센서가 없으면 0.
 */
@Entity(
    tableName = "run",
    indices = [Index("date"), Index("endTime")],
)
data class RunEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long,
    val startTime: Long,
    val endTime: Long? = null,
    val durationSeconds: Long = 0,
    val distanceMeters: Double = 0.0,
    val averagePaceSecPerKm: Double = 0.0,
    val bestPaceSecPerKm: Double = 0.0,
    val calories: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val steps: Int = 0,
    val goalType: String = "FREE",
    val goalValue: Double = 0.0,
    val memo: String? = null,
)
