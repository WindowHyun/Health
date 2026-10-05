package com.windowhyun.health.domain.repository

import com.windowhyun.health.domain.model.LocationSample
import kotlinx.coroutines.flow.Flow

/** GPS 위치 공급자. 구현을 바꿔 끼울 수 있도록 인터페이스로 둔다. */
interface LocationTracker {

    /** 위치 권한이 있고 GPS 가 켜져 있는지. */
    fun isLocationAvailable(): Boolean

    fun hasLocationPermission(): Boolean

    /**
     * 위치 업데이트 스트림. 구독을 끊으면 업데이트도 멈춘다.
     *
     * @param intervalMillis 원하는 수신 주기
     */
    fun locationUpdates(intervalMillis: Long): Flow<LocationSample>
}
