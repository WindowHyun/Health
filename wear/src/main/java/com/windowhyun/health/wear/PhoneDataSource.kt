package com.windowhyun.health.wear

import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.windowhyun.health.shared.CommandAck
import com.windowhyun.health.shared.RestSnapshot
import com.windowhyun.health.shared.RunSnapshot
import com.windowhyun.health.shared.WatchCommand
import com.windowhyun.health.shared.WearCodec
import com.windowhyun.health.shared.WearProtocol
import com.windowhyun.health.shared.WorkoutSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** 폰이 올려 둔 마지막 상태 셋. 없는 것은 기본값(없음)이다. */
internal data class WatchSnapshots(
    val run: RunSnapshot = RunSnapshot(),
    val rest: RestSnapshot = RestSnapshot.None,
    val workout: WorkoutSnapshot = WorkoutSnapshot.None,
)

/** Wearable Data Layer 로 폰과 이야기한다. */
class PhoneDataSource(private val context: Context) : WatchDataSource {

    internal companion object {
        /**
         * 지금 Data Layer 에 있는 세 상태를 한 번 읽는다. 읽을 수 없으면 null.
         * "읽지 못했다"와 "폰이 아무것도 안 올렸다"는 다르다. 읽지 못한 것을 "없음"으로 보면 일시적인 오류에
         * 워치페이스 칩이 사라진다.
         */
        suspend fun readCurrent(context: Context): WatchSnapshots? = try {
            Wearable.getDataClient(context).dataItems.await().use { buffer ->
                var run = RunSnapshot()
                var rest = RestSnapshot.None
                var workout = WorkoutSnapshot.None
                buffer.forEach { item ->
                    when (item.uri.path) {
                        WearProtocol.PATH_RUN_STATE -> WearCodec.decodeRun(item.data)?.let { run = it }
                        WearProtocol.PATH_REST_STATE -> WearCodec.decodeRest(item.data)?.let { rest = it }
                        WearProtocol.PATH_WORKOUT_STATE -> WearCodec.decodeWorkout(item.data)?.let { workout = it }
                    }
                }
                WatchSnapshots(run, rest, workout)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }

    override val run: Flow<RunSnapshot> = observe(WearProtocol.PATH_RUN_STATE) { WearCodec.decodeRun(it) }

    override val rest: Flow<RestSnapshot> = observe(WearProtocol.PATH_REST_STATE) { WearCodec.decodeRest(it) }

    override val workout: Flow<WorkoutSnapshot> =
        observe(WearProtocol.PATH_WORKOUT_STATE) { WearCodec.decodeWorkout(it) }

    /** 폰이 돌려보내는 명령 처리 결과. 앱이 열려 있는 동안 받는다(명령은 화면을 보며 누르는 것이라 충분하다). */
    override val acks: Flow<CommandAck> = callbackFlow {
        val client = Wearable.getMessageClient(context)
        val listener = MessageClient.OnMessageReceivedListener { event: MessageEvent ->
            CommandAck.parse(event.path, event.data)?.let { trySend(it) }
        }
        client.addListener(listener).await()
        awaitClose { client.removeListener(listener) }
    }

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
        // 등록하지 못하면(Play 서비스가 아직 준비 안 됨 등) 흐름이 실패하고, 받는 쪽이 잠시 뒤 다시 시도한다.
        // 결과를 기다리지 않으면 실패를 알 수 없어서 영영 상태를 못 받는다.
        dataClient.addListener(listener).await()

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

    override suspend fun send(command: WatchCommand, payload: ByteArray): Boolean = try {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        var delivered = false
        val messages = Wearable.getMessageClient(context)
        for (node in nodes) {
            try {
                messages.sendMessage(node.id, command.path, payload).await()
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
