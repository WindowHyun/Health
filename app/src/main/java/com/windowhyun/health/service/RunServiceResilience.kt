package com.windowhyun.health.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen

/**
 * 위치 수신이 끊겼을 때 다시 붙기까지 기다리는 시간(ms). 처음엔 짧게, 계속 실패하면 최대 30초까지 늘린다.
 * GPS 를 잠깐 껐다 켜거나 권한 화면을 다녀오는 정도는 러닝을 포기하지 않고 이어 가려는 것이다.
 */
internal fun locationRetryDelayMillis(attempt: Long): Long =
    ((attempt + 1) * LOCATION_RETRY_STEP_MILLIS).coerceAtMost(LOCATION_RETRY_MAX_MILLIS)

internal const val LOCATION_RETRY_STEP_MILLIS = 2_000L
internal const val LOCATION_RETRY_MAX_MILLIS = 30_000L

/**
 * 위쪽 흐름(위치 수신)이 실패하면 기다렸다가 다시 시작한다. 수집하는 쪽에서 난 오류는 건드리지 않는다.
 * 러닝이 끝나 수집이 취소되면 함께 멈춘다. [onError] 로 실패를 알린다.
 */
internal fun <T> Flow<T>.retryForever(onError: (Throwable) -> Unit = {}): Flow<T> =
    retryWhen { cause, attempt ->
        onError(cause)
        delay(locationRetryDelayMillis(attempt))
        true
    }
