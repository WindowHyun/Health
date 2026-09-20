package com.windowhyun.health.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.windowhyun.health.data.local.entity.ExerciseEntity
import com.windowhyun.health.data.local.entity.RoutineEntity
import com.windowhyun.health.data.local.entity.RoutineExerciseEntity

/** routine_exercise 한 줄 + 해당 운동 종목 정보. */
data class RoutineExerciseWithExercise(
    @Embedded val routineExercise: RoutineExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)

/** 루틴 + 포함된 운동 목록. */
data class RoutineWithItems(
    @Embedded val routine: RoutineEntity,
    @Relation(entity = RoutineExerciseEntity::class, parentColumn = "id", entityColumn = "routineId")
    val items: List<RoutineExerciseWithExercise>,
)
