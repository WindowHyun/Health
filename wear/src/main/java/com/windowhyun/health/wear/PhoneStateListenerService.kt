package com.windowhyun.health.wear

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.windowhyun.health.shared.WearProtocol
import kotlinx.coroutines.runBlocking

/**
 * 폰이 올린 상태가 바뀌면 앱이 꺼져 있어도 깨어나, 워치페이스 칩과 타일을 새로 맞춘다.
 * 시계 앱 화면은 열려 있을 때만 상태를 따라가므로, 닫은 뒤에도 현재 상황을 보여 주는 것은 이 서비스의 일이다.
 */
class PhoneStateListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        val relevant = events.any { event -> event.dataItem.uri.path in STATE_PATHS }
        if (!relevant) return
        // 이 콜백은 화면 스레드가 아닌 곳에서 불리고, 끝나면 서비스가 곧 내려간다. 끝까지 하고 돌아간다.
        runBlocking { refreshWatchSurfaces(applicationContext) }
    }

    companion object {
        private val STATE_PATHS = setOf(
            WearProtocol.PATH_RUN_STATE,
            WearProtocol.PATH_REST_STATE,
            WearProtocol.PATH_WORKOUT_STATE,
        )
    }
}

/**
 * 지금 폰의 상태를 읽어 워치페이스 칩과 타일을 맞춘다. 읽지 못했으면(Play 서비스 오류 등) 아무것도 건드리지 않는다.
 * [read] 와 [nowMillis] 는 테스트에서 바꾼다.
 */
internal suspend fun refreshWatchSurfaces(
    context: android.content.Context,
    read: suspend () -> WatchSnapshots? = { PhoneDataSource.readCurrent(context) },
    nowMillis: Long = System.currentTimeMillis(),
) {
    val snapshots = read() ?: return
    val content = OngoingPresenter.describe(snapshots.run, snapshots.rest, snapshots.workout, nowMillis)
    OngoingNotifier(context).show(content, nowMillis)
    HealthTileService.requestUpdate(context)
}
