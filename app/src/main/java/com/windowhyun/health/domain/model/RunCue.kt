package com.windowhyun.health.domain.model

/**
 * 러닝 중 폰을 보지 않아도 알아야 하는 순간. 화면을 보는 대신 진동으로 알린다.
 */
sealed interface RunCue {
    /**
     * 구간(Lap) 하나를 마쳤다. 음성 안내가 읽을 값을 함께 싣는다(진동만 쓸 때는 [lapNumber] 면 된다).
     */
    data class LapCompleted(
        val lapNumber: Int,
        val lap: RunLap? = null,
        /** 이 구간을 마친 시점의 누적 거리와 시간. */
        val totalDistanceMeters: Double = 0.0,
        val totalDurationSeconds: Long = 0,
    ) : RunCue

    /** 인터벌의 달리기/걷기가 바뀌었다. */
    data class IntervalChanged(val phase: IntervalPhase, val round: Int, val rounds: Int) : RunCue

    /** 정해 둔 인터벌을 모두 마쳤다. */
    data object IntervalsFinished : RunCue

    /** 목표 거리 · 시간을 채웠다. */
    data object GoalReached : RunCue

    /** 멈춰서 자동으로 일시정지했다. */
    data object AutoPaused : RunCue

    /** 다시 움직여서 자동으로 이어 간다. */
    data object AutoResumed : RunCue
}
