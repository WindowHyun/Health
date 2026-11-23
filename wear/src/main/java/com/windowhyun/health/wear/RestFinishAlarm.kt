package com.windowhyun.health.wear

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.windowhyun.health.shared.RestSnapshot

/**
 * 휴식이 끝나는 시각에 손목을 울리는 시스템 알람.
 *
 * 앱 화면이 보일 때만 초를 세는 방식으로는 손목을 내린 뒤(화면이 꺼지고 앱이 멈춘 뒤)에 알릴 수 없다.
 * 휴식 시간은 폰이 알려 주므로, 시계는 그 시각에 맞춰 시스템 알람을 걸어 둔다. 알람은 앱이 꺼져 있어도 울린다.
 *
 * 폰이 휴식 상태를 바꿀 때마다([PhoneStateListenerService]) 새로 맞춘다: 시간을 더하거나 빼면 알람이 옮겨지고,
 * 멈추거나 건너뛰면 알람이 없어진다.
 */
internal class RestFinishAlarm(private val context: Context) {

    /** [rest] 에 맞춰 알람을 걸거나 치운다. */
    fun sync(rest: RestSnapshot, nowMillis: Long) {
        val endsAt = rest.endsAtMillis
        val pending = rest.active && !rest.paused && endsAt > nowMillis && !rest.isStale(nowMillis)
        if (pending) schedule(endsAt) else cancel()
    }

    private fun schedule(endsAtMillis: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pendingIntent(endsAtMillis)
        runCatching {
            // 정확한 알람이 막혀 있어도(허용되지 않은 기기) 조금 늦게라도 울리게 한다.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, intent)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, intent)
            }
        }
    }

    private fun cancel() {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { manager.cancel(pendingIntent(0)) }
    }

    private fun pendingIntent(endsAtMillis: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, RestFinishReceiver::class.java).putExtra(EXTRA_ENDS_AT, endsAtMillis),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal companion object {
        const val REQUEST_CODE = 3101
        const val EXTRA_ENDS_AT = "ends_at"
    }
}

/** 휴식 끝 알람이 울리면 손목을 울린다. 앱이 꺼져 있어도 시스템이 깨워 준다. */
class RestFinishReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val endsAt = intent.getLongExtra(RestFinishAlarm.EXTRA_ENDS_AT, 0)
        RestVibration.vibrateOnce(context.applicationContext, endsAt)
    }
}

/**
 * 휴식 끝 진동. 알람과 앱 화면(앱이 열려 있을 때 같은 순간에 센다) 둘 다 울리려 하므로,
 * 같은 휴식(끝나는 시각이 같은 것)에는 한 번만 울린다.
 */
internal object RestVibration {
    private var lastEndsAtMillis = -1L

    /** 처음 요청한 휴식이면 울리고 true, 이미 울렸으면 아무것도 하지 않고 false. */
    @Synchronized
    fun vibrateOnce(
        context: Context,
        endsAtMillis: Long,
        vibrate: (Context) -> Unit = ::vibrateNow,
    ): Boolean {
        if (endsAtMillis == lastEndsAtMillis) return false
        lastEndsAtMillis = endsAtMillis
        vibrate(context)
        return true
    }

    @Synchronized
    internal fun reset() {
        lastEndsAtMillis = -1L
    }

    /**
     * 짧게 두 번. 알림 용도로 표시해서, 화면이 꺼지고 앱이 백그라운드에 있을 때도 시스템이 막지 않는다.
     * 진동이 없는 기기에서는 아무 일도 없다.
     */
    @Suppress("DEPRECATION")
    private fun vibrateNow(context: Context) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 200, 120, 200), -1)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        vibrator.vibrate(effect, attributes)
    }
}
