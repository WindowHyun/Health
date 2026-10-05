package com.windowhyun.health.core.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.windowhyun.health.HealthApplication
import com.windowhyun.health.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 휴식 타이머 종료 알림/진동.
 *
 * 알림 권한이 없어도 앱이 깨지지 않도록 모두 방어적으로 처리한다.
 */
@Singleton
class RestTimerNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun notifyRestFinished(vibrationEnabled: Boolean) {
        if (vibrationEnabled) vibrate()
        showNotification()
    }

    /**
     * [delayMillis] 뒤에 휴식 끝 알림이 울리게 시스템 알람을 맞춘다. 이미 맞춰 둔 알람은 바꾼다.
     *
     * 앱 안에서 초를 세는 방식은 화면이 꺼지고 기기가 잠들면 멈춘다. 휴식 타이머는 폰을 안
     * 보는 동안 알려 주는 것이 목적이라, 시각은 시스템 알람이 지킨다.
     */
    fun scheduleFinish(delayMillis: Long, vibrationEnabled: Boolean) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val trigger = SystemClock.elapsedRealtime() + delayMillis.coerceAtLeast(0)
        val intent = alarmIntent(vibrationEnabled)
        runCatching {
            // 정확한 알람은 설치 직후 허용돼 있는 것이 보통이다. 막혀 있으면 조금 늦더라도 울리게 한다.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, intent)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, intent)
            }
        }
    }

    private fun alarmIntent(vibrationEnabled: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, RestTimerReceiver::class.java)
                .putExtra(RestTimerReceiver.EXTRA_VIBRATE, vibrationEnabled),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return

        val pattern = longArrayOf(0, 250, 150, 250)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun showNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, HealthApplication.CHANNEL_REST_TIMER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.rest_timer_finished_title))
            .setContentText(context.getString(R.string.rest_timer_finished_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setTimeoutAfter(30_000)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    /** 맞춰 둔 알람과 이미 뜬 알림을 모두 지운다. */
    fun cancel() {
        runCatching {
            context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(vibrationEnabled = false))
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val ALARM_REQUEST_CODE = 1001
    }
}
