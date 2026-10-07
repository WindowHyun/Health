package com.windowhyun.health

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.windowhyun.health.wear.WatchStatePublisher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Hilt 엔트리 포인트. 알림 채널도 여기서 한 번만 만든다. */
@HiltAndroidApp
class HealthApplication : Application() {

    @Inject
    lateinit var watchStatePublisher: WatchStatePublisher

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // 시계가 있으면 러닝 상태와 휴식 타이머를 계속 올려 보낸다. 없어도 아무 일 없다.
        watchStatePublisher.start()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val restChannel = NotificationChannel(
            CHANNEL_REST_TIMER,
            getString(R.string.rest_timer_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.rest_timer_channel_description)
            enableVibration(true)
            setShowBadge(false)
        }
        val runChannel = NotificationChannel(
            CHANNEL_RUN_TRACKING,
            getString(R.string.run_tracking_channel_name),
            // 기록 중 내내 떠 있는 알림이므로 소리 없이 조용히.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.run_tracking_channel_description)
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(restChannel, runChannel))
    }

    companion object {
        const val CHANNEL_REST_TIMER = "rest_timer"
        const val CHANNEL_RUN_TRACKING = "run_tracking"
    }
}
