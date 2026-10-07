package com.windowhyun.health.wear

import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.usecase.CurrentSetFinder
import com.windowhyun.health.shared.WatchSetKind
import com.windowhyun.health.shared.WorkoutSnapshot

/**
 * 진행 중인 헬스 운동을 시계로 보낼 모양으로 줄인다. 끝난 운동이면 "없음".
 *
 * 남은 세트가 없어도(전부 끝냄) 운동은 아직 진행 중이므로 [WorkoutSnapshot.allDone] 으로 알린다.
 */
internal fun Workout.toSnapshot(useLb: Boolean): WorkoutSnapshot {
    if (!isActive) return WorkoutSnapshot.None
    val current = CurrentSetFinder.find(exercises)
        ?: return WorkoutSnapshot(active = true, allDone = exercises.isNotEmpty(), useLb = useLb)
    val set = current.set
    return WorkoutSnapshot(
        active = true,
        setId = set.id,
        exerciseName = current.record.exercise.name,
        setNumber = current.record.sets.indexOf(set) + 1,
        setCount = current.record.sets.size,
        weightKg = set.weightKg,
        reps = set.reps,
        durationSeconds = set.durationSeconds,
        kind = when (current.record.exercise.trackingType) {
            ExerciseTrackingType.WEIGHT_REPS -> WatchSetKind.WEIGHT_REPS
            ExerciseTrackingType.REPS_ONLY -> WatchSetKind.REPS_ONLY
            ExerciseTrackingType.TIME -> WatchSetKind.TIME
        },
        useLb = useLb,
    )
}
