package com.windowhyun.health.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.windowhyun.health.data.local.entity.ExerciseEntity
import com.windowhyun.health.data.local.entity.WorkoutEntity
import com.windowhyun.health.data.local.entity.WorkoutExerciseEntity
import com.windowhyun.health.data.local.entity.WorkoutSetEntity

/** 세션 내 운동 한 줄 + 종목 정보 + 세트 목록. */
data class WorkoutExerciseWithSets(
    @Embedded val workoutExercise: WorkoutExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<WorkoutSetEntity>,
)

/** 세션 전체. */
data class WorkoutWithDetail(
    @Embedded val workout: WorkoutEntity,
    @Relation(entity = WorkoutExerciseEntity::class, parentColumn = "id", entityColumn = "workoutId")
    val exercises: List<WorkoutExerciseWithSets>,
)

/** PR 계산 / 통계에 쓰는 가벼운 세트 조회 결과. */
data class ExerciseSetHistory(
    val workoutId: Long,
    val workoutExerciseId: Long,
    val weightKg: Double,
    val reps: Int,
    val startTime: Long,
)

/** personal_record + 종목 이름. */
data class PersonalRecordWithExercise(
    val id: Long,
    val workoutId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val type: String,
    val value: Double,
    val previousValue: Double?,
    val reps: Int?,
    val weightKg: Double?,
    val achievedAt: Long,
)
