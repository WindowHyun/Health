package com.windowhyun.health.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 러닝 1회 기록. (Phase 2 에서 기록을 채우지만 스키마는 v1 에서 확정해 마이그레이션을 줄인다.)
 *
 * @param distanceMeters 항상 meter 로 저장.
 * @param averagePaceSecPerKm 초/킬로미터.
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
    val goalType: String = "FREE",
    val goalValue: Double = 0.0,
    val memo: String? = null,
)
