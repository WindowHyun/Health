package com.windowhyun.health.data.location

import com.google.android.gms.common.api.ApiException

/**
 * 위치를 받지 못할 때 화면에 보여 줄 원인. 원인을 모르면 사용자도 개발자도 "GPS 가 안 잡힌다" 밖에 알 수 없어서,
 * 가능한 한 무엇을 고치면 되는지 함께 적는다.
 */
internal fun describeLocationError(error: Throwable): String = when (error) {
    is SecurityException ->
        "위치 권한을 쓸 수 없어요. 설정 > 앱 > 권한 > 위치에서 '정확한 위치'를 켜고 '앱 사용 중에만 허용' 이상으로 바꿔 주세요."

    is ApiException -> when (error.statusCode) {
        6, 8502 -> "기기의 위치 설정이 꺼져 있거나 맞지 않아요. 위치(GPS)를 켜 주세요."
        7 -> "네트워크 오류로 Google 위치 서비스를 쓰지 못했어요(코드 7)."
        17 -> "Google Play 서비스에 연결하지 못했어요(코드 17). Play 스토어에서 Google Play 서비스를 업데이트해 주세요."
        else -> "Google 위치 서비스 오류예요(코드 ${error.statusCode}${error.message?.let { " · $it" }.orEmpty()})."
    }

    else -> "위치를 받는 중 오류가 났어요(${error.message ?: error::class.simpleName})."
}

/** Google 위치 서비스가 조용할 때(실패도 응답도 없음) 기기 GPS 로 직접 받는다는 안내. */
internal const val FALLBACK_NOTICE = "Google 위치 서비스에서 위치가 오지 않아 기기 GPS 로 직접 받는 중이에요."

/** Google 위치 서비스에서 이만큼 위치가 하나도 안 오면 기기 GPS 로도 받기 시작한다. */
internal const val FUSED_SILENCE_MILLIS = 20_000L
