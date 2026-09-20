package com.windowhyun.health.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 자동 Lap 한 구간. */
@Entity(
    tableName = "run_lap",
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
data class RunLapEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val runId: Long,
    val lapNumber: Int,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val paceSecPerKm: Double,
)
