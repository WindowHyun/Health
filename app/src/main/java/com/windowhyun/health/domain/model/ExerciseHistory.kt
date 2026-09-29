package com.windowhyun.health.domain.model

import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseTrackingType
import java.time.LocalDate

/** 기록이 있는 종목 한 줄. 기록 탭의 "종목" 목록에 쓴다. */
data class ExerciseHistorySummary(
    val exerciseId: Long,
    val name: String,
    val bodyPart: BodyPart,
    val trackingType: ExerciseTrackingType,
    val sessionCount: Int,
    val lastDate: LocalDate,
)

/**
 * 한 종목을 한 운동에서 한 기록. 같은 운동에 그 종목을 두 번 넣었으면 세트를 합친다.
 *
 * 집계는 [WorkoutSet.counts] 기준이라 워밍업은 빠진다. 세트 목록에는 워밍업도 남겨
 * 화면에서 구분해 보여 준다.
 */
data class ExerciseSession(
    val workoutId: Long,
    val date: LocalDate,
    val startTime: Long,
    val routineName: String?,
    val sets: List<WorkoutSet>,
) {
    private val counted: List<WorkoutSet> get() = sets.filter { it.counts }

    val maxWeightKg: Double get() = counted.maxOfOrNull { it.weightKg } ?: 0.0
    val bestOneRepMaxKg: Double get() = counted.maxOfOrNull { it.estimatedOneRepMax } ?: 0.0
    val volumeKg: Double get() = counted.sumOf { it.volume }
    val maxReps: Int get() = counted.maxOfOrNull { it.reps } ?: 0
    val totalReps: Int get() = counted.sumOf { it.reps }
    val maxDurationSeconds: Int get() = counted.maxOfOrNull { it.durationSeconds } ?: 0
}

/** 성장 그래프에서 고를 수 있는 지표. 기록 방식마다 의미 있는 것만 보여 준다. */
enum class ProgressMetric(val label: String) {
    ESTIMATED_ONE_RM("예상 1RM"),
    MAX_WEIGHT("최고 중량"),
    VOLUME("볼륨"),
    MAX_REPS("최고 횟수"),
    TOTAL_REPS("총 횟수"),
    MAX_DURATION("최고 시간"),
    ;

    fun valueOf(session: ExerciseSession): Double = when (this) {
        ESTIMATED_ONE_RM -> session.bestOneRepMaxKg
        MAX_WEIGHT -> session.maxWeightKg
        VOLUME -> session.volumeKg
        MAX_REPS -> session.maxReps.toDouble()
        TOTAL_REPS -> session.totalReps.toDouble()
        MAX_DURATION -> session.maxDurationSeconds.toDouble()
    }

    companion object {
        fun forTrackingType(type: ExerciseTrackingType): List<ProgressMetric> = when (type) {
            ExerciseTrackingType.WEIGHT_REPS -> listOf(ESTIMATED_ONE_RM, MAX_WEIGHT, VOLUME)
            ExerciseTrackingType.REPS_ONLY -> listOf(MAX_REPS, TOTAL_REPS)
            ExerciseTrackingType.TIME -> listOf(MAX_DURATION)
        }
    }
}

/** 그래프의 점 하나. 값이 0 인 세션(워밍업만 한 날 등)은 그리지 않는다. */
data class ProgressPoint(val workoutId: Long, val date: LocalDate, val value: Double)

/** 세션 목록(최신순)을 그래프 점(오래된 순)으로 바꾼다. */
fun List<ExerciseSession>.progressPoints(metric: ProgressMetric): List<ProgressPoint> =
    sortedBy { it.startTime }
        .map { ProgressPoint(it.workoutId, it.date, metric.valueOf(it)) }
        .filter { it.value > 0.0 }
