package com.windowhyun.health.wear

import com.windowhyun.health.shared.RestSnapshot
import com.windowhyun.health.shared.WatchCommand
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 운동 세션(화면에 사는 ViewModel)과 시계 연동 사이의 다리.
 *
 * 휴식 타이머는 세션 화면의 ViewModel 이 들고 있어서, 시계에 보여 주려면 그쪽에서 상태를 내려 주고
 * 시계가 보낸 명령(건너뛰기 · ±15초 · 일시정지)을 거기로 돌려줘야 한다.
 */
@Singleton
class WatchLink internal constructor(
    private val wallClock: () -> Long,
) {
    @Inject
    constructor() : this(System::currentTimeMillis)

    private val _rest = MutableStateFlow(RestSnapshot.None)

    /** 시계에 보낼 휴식 타이머 상태. 타이머가 없으면 [RestSnapshot.None]. */
    val rest: StateFlow<RestSnapshot> = _rest.asStateFlow()

    private val _restCommands = MutableSharedFlow<WatchCommand>(extraBufferCapacity = 8)

    /** 시계에서 온 휴식 타이머 명령. 세션 화면이 없으면 받는 쪽이 없어 버려진다. */
    val restCommands: SharedFlow<WatchCommand> = _restCommands.asSharedFlow()

    /**
     * 휴식 타이머가 돌고 있다고 알린다.
     *
     * 타이머는 기기가 켜진 뒤 흐른 시간(elapsedRealtime)으로 세는데, 시계는 그 시계를 모른다.
     * 그래서 "앞으로 몇 ms 남았다"만 받아 지금의 실제 시각에 더해 끝나는 시각으로 바꿔 보낸다.
     */
    fun publishRest(totalSeconds: Int, remainingMillis: Long, paused: Boolean) {
        val now = wallClock()
        val remaining = remainingMillis.coerceAtLeast(0)
        _rest.value = RestSnapshot(
            active = true,
            totalSeconds = totalSeconds,
            // 멈춘 동안에는 끝나는 시각이 없다. 시계마다 달라지는 값을 보내면 같은 상태가 다른 상태로 보인다.
            endsAtMillis = if (paused) 0 else now + remaining,
            paused = paused,
            pausedRemainingMillis = if (paused) remaining else 0,
            sentAtMillis = now,
        )
    }

    fun clearRest() {
        _rest.value = RestSnapshot.None
    }

    /** 휴식 타이머 명령을 세션에 넘긴다. 휴식이 없는데 온 명령은 무시한다. */
    fun emitRestCommand(command: WatchCommand): Boolean {
        if (!_rest.value.active) return false
        return _restCommands.tryEmit(command)
    }
}
