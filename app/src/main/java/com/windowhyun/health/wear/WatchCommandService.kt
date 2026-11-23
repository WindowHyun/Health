package com.windowhyun.health.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.windowhyun.health.shared.WatchCommand
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 시계가 보낸 메시지를 받는다. 앱이 꺼져 있어도 Google Play 서비스가 이 서비스를 깨워 준다.
 * 모르는 경로(다른 버전의 시계 앱이 보낸 것)는 조용히 무시한다.
 * 명령을 처리한 뒤에는 받아들였는지 거절했는지를 시계에 돌려준다(시계는 "보냈다"까지만 알기 때문이다).
 */
@AndroidEntryPoint
class WatchCommandService : WearableListenerService() {

    @Inject
    lateinit var handler: WatchCommandHandler

    override fun onMessageReceived(event: MessageEvent) {
        val command = WatchCommand.fromPath(event.path) ?: return
        val ack = handler.handleForAck(command, event.data)
        // 시계가 "받아들였다 / 못 했다"를 알 수 있게 결과를 돌려준다. 못 보내도 폰 쪽 동작에는 영향이 없다.
        runCatching {
            Wearable.getMessageClient(this).sendMessage(event.sourceNodeId, ack.path, ack.payload)
        }
    }
}
