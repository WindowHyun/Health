package com.windowhyun.health.domain.model

import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.model.SetType
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
    val trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_REPS,
)

/** 루틴에 들어 있는 운동 한 줄. */
data class RoutineItem(
    val id: Long = 0,
    val exercise: Exercise,
    val orderIndex: Int,
    val defaultSets: Int,
    val restSeconds: Int? = null,
    /** 슈퍼셋 묶음 번호. 0 이면 묶음 없음. */
    val supersetGroup: Int = 0,
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
    val durationSeconds: Int = 0,
    val setType: SetType = SetType.NORMAL,
) {
    /** 무게 · 횟수 · 시간 중 하나라도 들어 있는가. 비어 있는 미완료 세트는 종료할 때 버려도 잃는 것이 없다. */
    val hasValues: Boolean get() = weightKg > 0 || reps > 0 || durationSeconds > 0

    /**
     * 완료로 저장할 수 있는 값이 들어 있는가. 화면의 완료 버튼과 같은 규칙이다: 시간 종목은 시간이, 그 밖에는 횟수가 있어야 한다.
     * 무게만 있는 세트(앞 세트의 무게가 따라 채워진 것)는 한 적 없는 세트라 완료로 볼 수 없다.
     */
    fun canComplete(trackingType: ExerciseTrackingType): Boolean = when (trackingType) {
        ExerciseTrackingType.TIME -> durationSeconds > 0
        else -> reps > 0
    }

    /** 집계에 넣는 세트인가. 워밍업은 뺀다. */
    val counts: Boolean get() = completed && setType.countsTowardVolume

    val volume: Double get() = if (counts) setVolume(weightKg, reps) else 0.0

    val estimatedOneRepMax: Double
        get() = if (counts) estimateOneRepMax(weightKg, reps) else 0.0
}

/** 세션 안에서 수행한 운동 한 줄. */
data class WorkoutExerciseRecord(
    val id: Long = 0,
    val exercise: Exercise,
    val orderIndex: Int,
    val restSeconds: Int? = null,
    val supersetGroup: Int = 0,
    val sets: List<WorkoutSet> = emptyList(),
) {
    val completedSets: List<WorkoutSet> get() = sets.filter { it.completed }

    /**
     * 종료할 때 "체크만 깜빡했다"고 보고 완료로 저장해 줄 수 있는 세트.
     *
     * 세트에는 지난 운동의 값이 미리 채워져 있어서, 값이 있다는 것만으로는 한 세트인지 알 수 없다(안 한 마지막 세트도 값이 있다).
     * 그래서 아래 둘 중 하나일 때만 한 것으로 본다. 둘 다 완료로 저장할 수 있는 값(횟수 · 시간)이 있어야 한다.
     * - 이번 운동에서 사용자가 직접 값을 고친 세트([editedSetIds])
     * - 뒤의 세트를 이미 완료한 세트(세트는 순서대로 하므로, 2세트를 건너뛰고 3세트를 끝냈다면 2세트는 한 것이다)
     * 미리 채워진 값 그대로이고 뒤에 끝낸 세트도 없는 세트(대개 안 한 마지막 세트)는 포함하지 않는다.
     */
    fun rescuableSets(editedSetIds: Set<Long>): List<WorkoutSet> {
        val lastCompleted = sets.indexOfLast { it.completed }
        return sets.filterIndexed { index, set ->
            !set.completed && set.canComplete(exercise.trackingType) && (set.id in editedSetIds || index < lastCompleted)
        }
    }

    /** 집계 대상 세트. 워밍업은 빠진다. */
    val countedSets: List<WorkoutSet> get() = sets.filter { it.counts }

    val totalVolume: Double get() = countedSets.sumOf { it.volume }
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

    /** 완료한 세트가 하나라도 있는가(워밍업 포함). 없으면 종료해도 남길 기록이 없다. */
    val hasCompletedSets: Boolean get() = exercises.any { it.completedSets.isNotEmpty() }

    // 아래 집계는 모두 본세트 기준이다. 워밍업까지 세면 실제 운동량보다 부풀려진다.
    val totalCompletedSets: Int get() = exercises.sumOf { it.countedSets.size }
    val totalReps: Int get() = exercises.sumOf { ex -> ex.countedSets.sumOf { it.reps } }
    val performedExerciseCount: Int get() = exercises.count { it.countedSets.isNotEmpty() }
    val displayName: String get() = routineName ?: "자유 운동"
}
