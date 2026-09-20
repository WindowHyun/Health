package com.windowhyun.health.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 세트 한 개.
 *
 * @param weightKg 항상 kg 로 저장한다. 표시 단위 변환은 UI 에서 수행.
 * @param completed 세트 완료 버튼을 눌렀는지 여부.
 */
@Entity(
    tableName = "workout_set",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutExerciseId")],
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workoutExerciseId: Long,
    val setNumber: Int,
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val completed: Boolean = false,
    val completedAt: Long? = null,
)
