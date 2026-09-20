package com.windowhyun.health.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * 걸음 수 공급자.
 *
 * 기기의 하드웨어 걸음 센서(TYPE_STEP_COUNTER)는 **부팅 이후 누적값**을 준다.
 * 이번 러닝의 걸음 수는 시작 시점 값과의 차이로 구한다(계산은 누적기가 담당).
 */
interface StepCounter {

    /** 이 기기에 걸음 센서가 있는지. 없으면 걸음 수 기능을 숨긴다. */
    fun isAvailable(): Boolean

    /** 신체활동 권한(ACTIVITY_RECOGNITION)이 있는지. */
    fun hasPermission(): Boolean

    /** 부팅 이후 누적 걸음 수 스트림. 구독을 끊으면 센서도 해제된다. */
    fun cumulativeSteps(): Flow<Long>
}
