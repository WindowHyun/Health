package com.windowhyun.health.domain.model

/**
 * 러닝 중 폰을 보지 않아도 알아야 하는 순간. 화면을 보는 대신 진동으로 알린다.
 */
sealed interface RunCue {
    /** 구간(Lap) 하나를 마쳤다. */
    data class LapCompleted(val lapNumber: Int) : RunCue

    /** 목표 거리 · 시간을 채웠다. */
    data object GoalReached : RunCue

    /** 멈춰서 자동으로 일시정지했다. */
    data object AutoPaused : RunCue

    /** 다시 움직여서 자동으로 이어 간다. */
    data object AutoResumed : RunCue
}
