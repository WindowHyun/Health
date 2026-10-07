package com.windowhyun.health.wear

import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.Wearable
import com.windowhyun.health.shared.RestSnapshot
import com.windowhyun.health.shared.RunSnapshot
import com.windowhyun.health.shared.WatchCommand
import com.windowhyun.health.shared.WearCodec
import com.windowhyun.health.shared.WearProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Wearable Data Layer 로 폰과 이야기한다. */
class PhoneDataSource(private val context: Context) : WatchDataSource {

    private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }

    override val run: Flow<RunSnapshot> = observe(WearProtocol.PATH_RUN_STATE) { WearCodec.decodeRun(it) }

    override val rest: Flow<RestSnapshot> = observe(WearProtocol.PATH_REST_STATE) { WearCodec.decodeRest(it) }

    /**
     * [path] 의 데이터 항목을 지켜본다. 먼저 이미 있는 값을 한 번 읽고(앱을 늦게 켜도 마지막 상태가 보이게),
     * 이후 바뀔 때마다 흘려 보낸다.
     */
    private fun <T : Any> observe(path: String, decode: (ByteArray?) -> T?): Flow<T> = callbackFlow {
        val listener = DataClient.OnDataChangedListener { events: DataEventBuffer ->
            events.use {
                it.filter { event -> event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == path }
                    .forEach { event -> decode(event.dataItem.data)?.let { value -> trySend(value) } }
            }
        }
        dataClient.addListener(listener)

        // 현재 값 읽기. 폰이 아직 아무것도 올리지 않았으면 비어 있다.
        try {
            val items = dataClient.dataItems.await()
            items.use { buffer ->
                buffer.filter { item: DataItem -> item.uri.path == path }
                    .forEach { item -> decode(item.data)?.let { value -> trySend(value) } }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Play 서비스를 쓸 수 없는 등. 이후 변화는 리스너로 받는다.
        }

        awaitClose { dataClient.removeListener(listener) }
    }

    override suspend fun send(command: WatchCommand): Boolean = try {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        var delivered = false
        val messages = Wearable.getMessageClient(context)
        for (node in nodes) {
            try {
                messages.sendMessage(node.id, command.path, ByteArray(0)).await()
                delivered = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 다른 노드로 계속 시도한다.
            }
        }
        delivered
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}
