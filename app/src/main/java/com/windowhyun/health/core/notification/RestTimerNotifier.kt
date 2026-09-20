package com.windowhyun.health.core.notification

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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

    fun cancel() {
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
