package com.windowhyun.health.data.local.relation

import androidx.room.Embedded
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.model.SetType
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
    val durationSeconds: Int,
    val startTime: Long,
)

/** 종목별 전체 이력용 세트 한 줄. 끝난 운동의 완료한 세트만 담는다. */
data class ExerciseHistorySetRow(
    val workoutId: Long,
    val date: Long,
    val startTime: Long,
    val routineName: String?,
    val setId: Long,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val durationSeconds: Int,
    val setType: SetType,
)

/** 기록이 있는 종목 한 줄(종목 목록 화면). */
data class ExerciseHistorySummaryRow(
    val exerciseId: Long,
    val name: String,
    val bodyPart: BodyPart,
    val trackingType: ExerciseTrackingType,
    val sessionCount: Int,
    val lastStartTime: Long,
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
