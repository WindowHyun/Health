package com.windowhyun.health.ui.session

/**
 * 휴식 타이머 상태.
 *
 * @param visible 타이머 바를 보여 줄지 여부. 건너뛰기/종료하면 false.
 */
data class RestTimerState(
    val visible: Boolean = false,
    val totalSeconds: Int = 60,
    val remainingSeconds: Int = 60,
    val paused: Boolean = false,
) {
    val progress: Float
        get() = if (totalSeconds <= 0) 0f else remainingSeconds.toFloat() / totalSeconds.toFloat()
}
