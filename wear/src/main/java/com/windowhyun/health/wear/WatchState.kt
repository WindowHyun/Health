package com.windowhyun.health.wear

import com.windowhyun.health.shared.RestSnapshot
import com.windowhyun.health.shared.RunSnapshot
import com.windowhyun.health.shared.WatchCommand
import kotlinx.coroutines.flow.Flow

/** 시계 앱이 폰과 주고받는 통로. Google Play 서비스에 기대는 부분을 가둬 테스트에서는 가짜로 바꾼다. */
interface WatchDataSource {
    /** 폰이 올린 러닝 상태. 받은 적이 없으면 아무것도 흘러오지 않는다. */
    val run: Flow<RunSnapshot>

    /** 폰이 올린 휴식 타이머 상태. */
    val rest: Flow<RestSnapshot>

    /** 폰에 명령을 보낸다. 연결된 폰이 없거나 보내지 못하면 false. */
    suspend fun send(command: WatchCommand): Boolean
}

/** 시계가 지금 보여 줄 화면. */
enum class WatchScreen {
    /** 휴식 타이머. 헬스 중에는 이것이 가장 급하다. */
    REST,

    /** 러닝 진행. */
    RUN,

    /** 보여 줄 것이 없다. */
    IDLE,
}

/**
 * 시계 화면에 쓰는 상태. 현재 시각은 밖에서 넣어 주어, 시간이 흐르는 화면도 값만으로 검증한다.
 */
data class WatchUiState(
    val run: RunSnapshot = RunSnapshot(),
    val rest: RestSnapshot = RestSnapshot.None,
    val nowMillis: Long = 0,
    /** 종료 버튼을 한 번 눌러 "정말 끝낼까요?" 를 묻는 중. */
    val confirmingStop: Boolean = false,
    /** 마지막 명령을 폰에 보내지 못했다. */
    val sendFailed: Boolean = false,
) {
    val restVisible: Boolean get() = rest.active
    val runVisible: Boolean get() = run.isActive

    /**
     * 휴식이 있으면 휴식을 먼저 보여 준다. 헬스 중 휴식은 곧 끝나고 그 사이 눌러야 할 것이 있다.
     * (러닝과 헬스를 동시에 하는 일은 없어서 둘 중 하나만 고르면 된다. 휴식이 끝나면 러닝 화면이 나온다.)
     */
    val screen: WatchScreen
        get() = when {
            restVisible -> WatchScreen.REST
            runVisible -> WatchScreen.RUN
            else -> WatchScreen.IDLE
        }

    val runElapsedSeconds: Long get() = run.elapsedAt(nowMillis)
    val restRemainingSeconds: Int get() = rest.remainingSeconds(nowMillis)
    val runStale: Boolean get() = run.isStale(nowMillis)
}
