package com.windowhyun.health.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/** 시계로 상태를 올리는 통로. Google Play 서비스에 기대는 부분을 한곳에 가둬 테스트에서는 가짜로 바꾼다. */
interface WatchTransport {
    /** [path] 의 데이터 항목을 [bytes] 로 바꾼다. 같은 내용이면 시계에는 아무 일도 일어나지 않는다. */
    suspend fun put(path: String, bytes: ByteArray)
}

/** Wearable Data Layer 로 보낸다. 시계가 없거나 Play 서비스가 없으면 예외를 던진다(받는 쪽이 삼킨다). */
class PlayServicesWatchTransport(private val context: Context) : WatchTransport {
    private val dataClient by lazy { Wearable.getDataClient(context) }

    override suspend fun put(path: String, bytes: ByteArray) {
        val request = PutDataRequest.create(path).setData(bytes).setUrgent()
        dataClient.putDataItem(request).await()
    }
}
