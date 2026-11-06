package com.windowhyun.health.domain.repository

import com.windowhyun.health.domain.model.LocationSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    /**
     * 위치를 못 받는 원인(사람이 읽는 문장). 받고 있으면 null.
     * 위치 서비스가 요청을 거절했거나 조용할 때 화면에 이유를 보여 주려는 것이다. 구현이 따로 알리지 않으면 늘 null.
     */
    val problem: StateFlow<String?> get() = NO_PROBLEM
}

internal val NO_PROBLEM: StateFlow<String?> = MutableStateFlow(null)
