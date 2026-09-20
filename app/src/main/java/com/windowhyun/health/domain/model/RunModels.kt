package com.windowhyun.health.domain.model

import java.time.LocalDate

/** 러닝 목표 모드. */
enum class RunGoalType(val label: String) {
    FREE("자유 러닝"),
    DISTANCE("목표 거리"),
    DURATION("목표 시간"),
}

data class RunLap(
    val id: Long = 0,
    val lapNumber: Int,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val paceSecPerKm: Double,
)

data class RunPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val timestamp: Long,
    val isSegmentStart: Boolean = false,
)

/** 러닝 1회 기록. Phase 2 에서 채워진다. */
data class Run(
    val id: Long = 0,
    val date: LocalDate,
    val startTime: Long,
    val endTime: Long? = null,
    val durationSeconds: Long = 0,
    val distanceMeters: Double = 0.0,
    val averagePaceSecPerKm: Double = 0.0,
    val bestPaceSecPerKm: Double = 0.0,
    val calories: Int = 0,
    val steps: Int = 0,
    val goalType: RunGoalType = RunGoalType.FREE,
    val goalValue: Double = 0.0,
    val memo: String? = null,
    val laps: List<RunLap> = emptyList(),
    val route: List<RunPoint> = emptyList(),
) {
    /** 평균 케이던스(분당 걸음 수). */
    val cadenceStepsPerMinute: Int
        get() = if (durationSeconds <= 0 || steps <= 0) 0 else (steps * 60.0 / durationSeconds).toInt()

    /** 평균 보폭(m). */
    val strideMeters: Double
        get() = if (steps <= 0) 0.0 else distanceMeters / steps
}
