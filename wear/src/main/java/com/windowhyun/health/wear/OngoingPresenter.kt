package com.windowhyun.health.wear

import com.windowhyun.health.shared.RestSnapshot
import com.windowhyun.health.shared.RunSnapshot
import com.windowhyun.health.shared.WatchRunStatus
import com.windowhyun.health.shared.WorkoutSnapshot

/** 워치페이스 · 타일에 보여 줄 지금의 운동. 안드로이드 타입 없이 값만 담아 JVM 에서 검증한다. */
internal data class OngoingContent(
    val kind: Kind,
    /** 알림 제목: "러닝", "휴식", 종목 이름. */
    val title: String,
    /** 시간이 흐르지 않는 부분의 글. 예) "5.12km", "2/4세트". */
    val text: String,
    /** 흐르는 시계. 없으면 null. */
    val clock: Clock? = null,
    /** 타일에 줄마다 적는 글. */
    val tileLines: List<String>,
) {
    enum class Kind { RUN, REST, WORKOUT }

    /** 흐르는 시계 하나. 시간은 시계(기기)의 절대 시각이라 앱이 꺼져 있어도 알아서 흐른다. */
    sealed interface Clock {
        /** [zeroMillis] 부터 센다(러닝 경과 시간). */
        data class Stopwatch(val zeroMillis: Long) : Clock

        /** [endMillis] 까지 줄어든다(휴식 남은 시간). */
        data class Countdown(val endMillis: Long) : Clock

        /** 멈춘 시간. 흐르지 않는다. */
        data class Frozen(val text: String) : Clock
    }
}

/**
 * 폰이 보낸 상태에서 "지금 워치페이스에 무엇을 띄울까"를 고른다.
 *
 * 화면과 같은 순서다: 휴식 > 러닝 > 헬스 세트. 폰이 한동안 소식이 없으면(앱이 죽었다) 아무것도 띄우지 않는다.
 * 지나간 러닝이 워치페이스에 영원히 남으면 안 된다.
 */
internal object OngoingPresenter {

    fun describe(
        run: RunSnapshot,
        rest: RestSnapshot,
        workout: WorkoutSnapshot,
        nowMillis: Long,
    ): OngoingContent? {
        if (rest.active && !rest.isFinished(nowMillis) && !isRestStale(rest, nowMillis)) return rest(rest, nowMillis)
        if (run.isActive && !run.isStale(nowMillis)) return run(run, nowMillis)
        if (workout.active && !workout.allDone && !workout.isStale(nowMillis)) return workout(workout)
        return null
    }

    /** 타일에 적을 글. 띄울 운동이 없으면 안내 문구. */
    fun tileLines(content: OngoingContent?): List<String> =
        content?.tileLines ?: listOf("Health", "폰에서 운동을 시작하세요")

    private fun rest(rest: RestSnapshot, nowMillis: Long): OngoingContent {
        val remaining = WatchFormat.restClock(rest.remainingSeconds(nowMillis))
        return OngoingContent(
            kind = OngoingContent.Kind.REST,
            title = "휴식",
            text = if (rest.paused) "멈춤" else "",
            clock = if (rest.paused) {
                OngoingContent.Clock.Frozen(remaining)
            } else {
                OngoingContent.Clock.Countdown(rest.endsAtMillis)
            },
            tileLines = listOf(if (rest.paused) "휴식 · 멈춤" else "휴식", remaining),
        )
    }

    private fun run(run: RunSnapshot, nowMillis: Long): OngoingContent {
        val distance = WatchFormat.distanceNumber(run.distanceMeters, run.useMiles) + WatchFormat.distanceUnit(run.useMiles)
        val elapsed = run.elapsedAt(nowMillis)
        val paused = run.status == WatchRunStatus.PAUSED
        return OngoingContent(
            kind = OngoingContent.Kind.RUN,
            title = if (paused) "러닝 · 일시정지" else "러닝",
            text = distance,
            clock = if (paused) {
                OngoingContent.Clock.Frozen(WatchFormat.duration(elapsed))
            } else {
                // 보낸 시각에서 경과 시간을 뺀 곳이 0 이다. 그 뒤로는 시계가 알아서 센다.
                OngoingContent.Clock.Stopwatch(run.sentAtMillis - run.elapsedSeconds * 1_000)
            },
            tileLines = listOf(
                if (paused) "러닝 · 일시정지" else "러닝",
                distance,
                "${WatchFormat.duration(elapsed)} · ${WatchFormat.pace(run.currentPaceSecPerKm, run.useMiles)}",
            ),
        )
    }

    private fun workout(workout: WorkoutSnapshot) = OngoingContent(
        kind = OngoingContent.Kind.WORKOUT,
        title = workout.exerciseName,
        text = "${workout.setNumber}/${workout.setCount}세트",
        tileLines = listOf(workout.exerciseName, "${workout.setNumber}/${workout.setCount}세트", WatchFormat.setValue(workout)),
    )

    /** 휴식은 길어야 몇 분이다. 끝나는 시각에서 한참 지났거나 보낸 지 오래면 폰이 그만둔 것이다. */
    private fun isRestStale(rest: RestSnapshot, nowMillis: Long): Boolean =
        !rest.paused && nowMillis - rest.sentAtMillis > REST_STALE_MILLIS + rest.totalSeconds * 1_000L

    private const val REST_STALE_MILLIS = 60_000L
}
