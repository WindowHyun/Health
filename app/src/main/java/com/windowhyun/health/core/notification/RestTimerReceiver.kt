package com.windowhyun.health.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 휴식 타이머 알람이 울리면 알림과 진동을 낸다.
 *
 * 앱이 백그라운드에 있거나 정리된 뒤에도 불리므로 앱 화면의 상태에 기대지 않는다.
 */
class RestTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        RestTimerNotifier(context.applicationContext)
            .notifyRestFinished(intent.getBooleanExtra(EXTRA_VIBRATE, true))
    }

    companion object {
        const val EXTRA_VIBRATE = "vibrate"
    }
}
