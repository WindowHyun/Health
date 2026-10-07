package com.windowhyun.health.domain.model

/** 인터벌 구간의 종류. */
enum class IntervalPhase(val label: String) {
    RUN("달리기"),
    WALK("걷기"),
}

/**
 * 달리기와 걷기를 번갈아 하는 인터벌 계획. 달리기 → 걷기를 한 라운드로 [rounds] 번 한다.
 *
 * 시간은 일시정지를 뺀 러닝 시간으로 센다. 신호등에서 멈춰도 구간이 흘러가 버리지 않는다.
 */
data class RunInterval(
    val runSeconds: Int = DEFAULT_RUN_SECONDS,
    val walkSeconds: Int = DEFAULT_WALK_SECONDS,
    val rounds: Int = DEFAULT_ROUNDS,
) {
    /** 한 라운드(달리기 + 걷기) 길이. */
    val cycleSeconds: Int get() = runSeconds + walkSeconds

    /** 인터벌 전체 길이. */
    val totalSeconds: Int get() = cycleSeconds * rounds

    /** [elapsedSeconds] 시점에 어느 구간인가. */
    fun progressAt(elapsedSeconds: Long): IntervalProgress {
        val elapsed = elapsedSeconds.coerceAtLeast(0)
        if (cycleSeconds <= 0 || elapsed >= totalSeconds) {
            return IntervalProgress(IntervalPhase.WALK, rounds, rounds, secondsLeft = 0, finished = true)
        }
        val round = (elapsed / cycleSeconds).toInt()
        val position = (elapsed % cycleSeconds).toInt()
        return if (position < runSeconds) {
            IntervalProgress(IntervalPhase.RUN, round + 1, rounds, runSeconds - position, finished = false)
        } else {
            IntervalProgress(IntervalPhase.WALK, round + 1, rounds, cycleSeconds - position, finished = false)
        }
    }

    companion object {
        const val DEFAULT_RUN_SECONDS = 60
        const val DEFAULT_WALK_SECONDS = 90
        const val DEFAULT_ROUNDS = 8

        const val MIN_SECONDS = 10
        const val MAX_SECONDS = 1_800
        const val MIN_ROUNDS = 1
        const val MAX_ROUNDS = 30
    }
}

/** 지금 인터벌이 어디까지 왔는가. 화면 · 음성 · 시계가 같은 값을 쓴다. */
data class IntervalProgress(
    val phase: IntervalPhase,
    /** 1 부터 센다. */
    val round: Int,
    val rounds: Int,
    /** 이번 구간이 끝나기까지 남은 시간. */
    val secondsLeft: Int,
    /** 정해 둔 라운드를 모두 마쳤다. 이후 러닝은 계속 이어진다. */
    val finished: Boolean,
)
