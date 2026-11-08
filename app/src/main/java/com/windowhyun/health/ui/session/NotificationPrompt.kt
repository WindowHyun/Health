package com.windowhyun.health.ui.session

/**
 * 헬스 운동 화면에서 알림 권한을 물어볼까. Android 13 이상에서 아직 허용하지 않았고, 물어본 적이 없을 때 한 번만 묻는다.
 * (거절한 사람에게 운동할 때마다 다시 묻지 않는다. 휴식 종료는 진동으로도 알린다.)
 */
internal fun shouldAskNotificationPermission(sdkInt: Int, granted: Boolean, alreadyAsked: Boolean): Boolean =
    sdkInt >= 33 && !granted && !alreadyAsked
