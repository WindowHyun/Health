package com.windowhyun.health.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** GPS 경로 포인트. */
@Entity(
    tableName = "run_location",
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId")],
)
data class RunLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val runId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val timestamp: Long,
    /** 일시정지 후 재개된 첫 포인트면 true. 경로를 그릴 때 선을 끊는다. */
    val isSegmentStart: Boolean = false,
)
