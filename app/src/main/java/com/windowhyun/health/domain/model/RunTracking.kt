package com.windowhyun.health.domain.model

/** 러닝 추적 상태. */
enum class RunStatus {
    /** 아직 시작하지 않음. */
    IDLE,

    /** GPS 로 기록 중. */
    TRACKING,

    /** 일시정지. 시간도 거리도 늘지 않는다. */
    PAUSED,

    /** 종료됨. 결과 화면에서 저장/삭제를 기다린다. */
    FINISHED,
}

/** 러닝 목표. */
data class RunGoal(
    val type: RunGoalType = RunGoalType.FREE,
    /** 목표 거리(m) 또는 목표 시간(초). 자유 러닝이면 0. */
    val value: Double = 0.0,
) {
    /** 목표 대비 진행률 0..1. 자유 러닝이면 null. */
    fun progress(distanceMeters: Double, durationSeconds: Long): Float? = when (type) {
        RunGoalType.FREE -> null
        RunGoalType.DISTANCE -> if (value <= 0) null else (distanceMeters / value).toFloat().coerceIn(0f, 1f)
        RunGoalType.DURATION -> if (value <= 0) null else (durationSeconds / value).toFloat().coerceIn(0f, 1f)
    }

    fun isReached(distanceMeters: Double, durationSeconds: Long): Boolean = when (type) {
        RunGoalType.FREE -> false
        RunGoalType.DISTANCE -> value > 0 && distanceMeters >= value
        RunGoalType.DURATION -> value > 0 && durationSeconds >= value
    }
}

/**
 * GPS 한 지점. android.location.Location 을 그대로 쓰지 않고 이 타입으로 바꿔서
 * 거리/페이스/Lap 계산 로직을 JVM 단위 테스트로 검증할 수 있게 한다.
 */
data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    /** 수평 정확도(m). 값이 클수록 신뢰도가 낮다. */
    val accuracyMeters: Float = 0f,
    val timestamp: Long,
)

/** 러닝 진행 화면이 구독하는 상태. */
data class RunTrackingState(
    val status: RunStatus = RunStatus.IDLE,
    val runId: Long = 0,
    val goal: RunGoal = RunGoal(),
    val distanceMeters: Double = 0.0,
    val durationSeconds: Long = 0,
    /** 최근 구간 기준 현재 페이스(초/km). 0 이면 아직 계산할 수 없음. */
    val currentPaceSecPerKm: Double = 0.0,
    val averagePaceSecPerKm: Double = 0.0,
    val bestPaceSecPerKm: Double = 0.0,
    val calories: Int = 0,
    /** 기기 걸음 센서로 센 이번 러닝의 걸음 수. */
    val steps: Long = 0,
    /** 평균 케이던스(분당 걸음 수). */
    val cadenceStepsPerMinute: Int = 0,
    /** 걸음 센서를 쓸 수 있는지. 없으면 화면에서 걸음 수를 숨긴다. */
    val stepCountAvailable: Boolean = false,
    val laps: List<RunLap> = emptyList(),
    val route: List<RunPoint> = emptyList(),
    /** 마지막으로 받은 GPS 정확도. null 이면 아직 첫 수신 전. */
    val lastAccuracyMeters: Float? = null,
    val startTime: Long = 0,
) {
    val isActive: Boolean get() = status == RunStatus.TRACKING || status == RunStatus.PAUSED
    val hasFix: Boolean get() = lastAccuracyMeters != null
}
