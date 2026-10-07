package com.windowhyun.health.data.healthconnect

import com.windowhyun.health.domain.model.HealthSession
import com.windowhyun.health.domain.model.HealthSessionKind
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.Workout

/** 기록 하나를 가리키는 이름표. 같은 기록은 늘 같은 이름이라, 다시 보내도 Health Connect 에 하나만 남는다. */
internal fun runClientId(runId: Long) = "health-run-$runId"

internal fun workoutClientId(workoutId: Long) = "health-workout-$workoutId"

/**
 * 끝난 러닝을 내보낼 모양으로 바꾼다. 끝나지 않았거나 시간이 없으면 null.
 * 기록하다 만 러닝이 다른 앱에 새어 나가면 안 된다.
 */
internal fun Run.toHealthSession(): HealthSession? {
    val end = endTime ?: return null
    if (end <= startTime) return null
    return HealthSession(
        clientId = runClientId(id),
        kind = HealthSessionKind.RUN,
        title = "러닝",
        startMillis = startTime,
        endMillis = end,
        distanceMeters = distanceMeters,
        calories = calories,
        steps = steps,
        fingerprint = "$end:${distanceMeters.toLong()}:$durationSeconds:$calories:$steps",
    )
}

/** 끝난 헬스 운동. 끝나지 않았거나 한 세트도 안 했으면 null(빈 운동은 보내지 않는다). */
internal fun Workout.toHealthSession(): HealthSession? {
    val end = endTime ?: return null
    if (end <= startTime || totalCompletedSets == 0) return null
    return HealthSession(
        clientId = workoutClientId(id),
        kind = HealthSessionKind.STRENGTH,
        title = displayName,
        startMillis = startTime,
        endMillis = end,
        fingerprint = "$end:$totalCompletedSets:${totalVolume.toLong()}:$displayName",
    )
}
