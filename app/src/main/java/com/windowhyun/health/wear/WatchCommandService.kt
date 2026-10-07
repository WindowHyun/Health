package com.windowhyun.health.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.windowhyun.health.shared.WatchCommand
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 시계가 보낸 메시지를 받는다. 앱이 꺼져 있어도 Google Play 서비스가 이 서비스를 깨워 준다.
 * 모르는 경로(다른 버전의 시계 앱이 보낸 것)는 조용히 무시한다.
 */
@AndroidEntryPoint
class WatchCommandService : WearableListenerService() {

    @Inject
    lateinit var handler: WatchCommandHandler

    override fun onMessageReceived(event: MessageEvent) {
        WatchCommand.fromPath(event.path)?.let { handler.handle(it, event.data) }
    }
}
