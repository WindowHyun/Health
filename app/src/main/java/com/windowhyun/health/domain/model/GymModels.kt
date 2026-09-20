package com.windowhyun.health.domain.model

import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.util.estimateOneRepMax
import com.windowhyun.health.core.util.setVolume
import java.time.DayOfWeek
import java.time.LocalDate

/** 운동 종목. */
data class Exercise(
    val id: Long,
    val name: String,
    val category: ExerciseCategory,
    val bodyPart: BodyPart,
    val isBuiltIn: Boolean = false,
    val defaultRestSeconds: Int? = null,
)

/** 루틴에 들어 있는 운동 한 줄. */
data class RoutineItem(
    val id: Long = 0,
    val exercise: Exercise,
    val orderIndex: Int,
    val defaultSets: Int,
    val restSeconds: Int? = null,
)

/** 운동 루틴. */
data class Routine(
    val id: Long = 0,
    val name: String,
    val scheduledDays: Set<DayOfWeek> = emptySet(),
    val items: List<RoutineItem> = emptyList(),
) {
    val exerciseCount: Int get() = items.size
    val totalSets: Int get() = items.sumOf { it.defaultSets }
}

/** 세트 한 개. */
data class WorkoutSet(
    val id: Long = 0,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val completed: Boolean,
) {
    val volume: Double get() = setVolume(weightKg, reps)
    val estimatedOneRepMax: Double get() = estimateOneRepMax(weightKg, reps)
}

/** 세션 안에서 수행한 운동 한 줄. */
data class WorkoutExerciseRecord(
    val id: Long = 0,
    val exercise: Exercise,
    val orderIndex: Int,
    val restSeconds: Int? = null,
    val sets: List<WorkoutSet> = emptyList(),
) {
    val completedSets: List<WorkoutSet> get() = sets.filter { it.completed }
    val totalVolume: Double get() = completedSets.sumOf { it.volume }
    val isFinished: Boolean get() = sets.isNotEmpty() && sets.all { it.completed }
}

/** 헬스 운동 1회 기록. */
data class Workout(
    val id: Long = 0,
    val routineId: Long? = null,
    val routineName: String? = null,
    val date: LocalDate,
    val startTime: Long,
    val endTime: Long? = null,
    val durationSeconds: Long = 0,
    val memo: String? = null,
    val exercises: List<WorkoutExerciseRecord> = emptyList(),
) {
    val isActive: Boolean get() = endTime == null
    val totalVolume: Double get() = exercises.sumOf { it.totalVolume }
    val totalCompletedSets: Int get() = exercises.sumOf { it.completedSets.size }
    val totalReps: Int get() = exercises.sumOf { ex -> ex.completedSets.sumOf { it.reps } }
    val performedExerciseCount: Int get() = exercises.count { it.completedSets.isNotEmpty() }
    val displayName: String get() = routineName ?: "자유 운동"
}
