package com.windowhyun.health.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 달성한 개인 기록 1건.
 *
 * 운동을 종료하는 시점에 "이번에 새로 세운 기록"만 저장한다.
 * 화면이 다시 만들어지거나 나중에 기록을 다시 열어 봐도 PR 표시가 유지된다.
 *
 * @param type [com.windowhyun.health.core.model.PersonalRecordType] 의 name.
 */
@Entity(
    tableName = "personal_record",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class PersonalRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val type: String,
    val value: Double,
    val previousValue: Double?,
    val reps: Int?,
    val weightKg: Double?,
    val achievedAt: Long,
)
