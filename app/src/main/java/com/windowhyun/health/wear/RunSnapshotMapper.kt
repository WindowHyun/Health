package com.windowhyun.health.wear

import com.windowhyun.health.domain.model.RunStatus
import com.windowhyun.health.domain.model.RunTrackingState
import com.windowhyun.health.shared.RunSnapshot
import com.windowhyun.health.shared.WatchRunStatus

/** 러닝 추적 상태를 시계로 보낼 모양으로 줄인다. */
internal fun RunTrackingState.toSnapshot(useMiles: Boolean, nowMillis: Long): RunSnapshot = RunSnapshot(
    status = when (status) {
        RunStatus.IDLE -> WatchRunStatus.IDLE
        RunStatus.TRACKING -> WatchRunStatus.TRACKING
        RunStatus.PAUSED -> WatchRunStatus.PAUSED
        RunStatus.FINISHED -> WatchRunStatus.FINISHED
    },
    distanceMeters = distanceMeters,
    elapsedSeconds = durationSeconds,
    currentPaceSecPerKm = currentPaceSecPerKm,
    averagePaceSecPerKm = averagePaceSecPerKm,
    autoPaused = autoPaused,
    signalLost = signalLost,
    useMiles = useMiles,
    goalProgress = goal.progress(distanceMeters, durationSeconds),
    sentAtMillis = nowMillis,
)
