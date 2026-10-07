package com.windowhyun.health.wear

import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.model.RunStatus
import com.windowhyun.health.service.RunControl
import com.windowhyun.health.service.RunServiceController
import com.windowhyun.health.shared.WatchCommand
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 시계에서 온 명령을 폰에서 실행한다.
 *
 * 명령은 그 순간의 상태에 맞을 때만 실행한다. 시계 화면이 조금 늦게 갱신된 사이에 눌린 버튼
 * (이미 끝난 러닝의 "일시정지" 등)이 엉뚱한 동작을 일으키지 않게 하려는 것이다.
 */
@Singleton
class WatchCommandHandler internal constructor(
    private val runControl: RunControl,
    private val runStatus: () -> RunStatus,
    private val link: WatchLink,
) {
    @Inject
    constructor(
        runServiceController: RunServiceController,
        runTracker: RunTracker,
        link: WatchLink,
    ) : this(runServiceController, { runTracker.state.value.status }, link)

    /** 명령을 실행했으면 true, 지금 상태에 맞지 않아 무시했으면 false. */
    fun handle(command: WatchCommand): Boolean = when (command) {
        WatchCommand.RUN_PAUSE -> runIf(runStatus() == RunStatus.TRACKING) { runControl.pause() }
        WatchCommand.RUN_RESUME -> runIf(runStatus() == RunStatus.PAUSED) { runControl.resume() }
        WatchCommand.RUN_STOP -> runIf(runStatus().let { it == RunStatus.TRACKING || it == RunStatus.PAUSED }) {
            runControl.stop()
        }
        WatchCommand.REST_SKIP,
        WatchCommand.REST_ADD,
        WatchCommand.REST_SUB,
        WatchCommand.REST_TOGGLE_PAUSE,
        -> link.emitRestCommand(command)
    }

    private inline fun runIf(allowed: Boolean, action: () -> Unit): Boolean {
        if (!allowed) return false
        action()
        return true
    }
}
