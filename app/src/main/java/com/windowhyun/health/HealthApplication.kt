package com.windowhyun.health

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.windowhyun.health.data.healthconnect.HealthConnectSyncer
import com.windowhyun.health.wear.AppForeground
import com.windowhyun.health.wear.WatchStatePublisher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Hilt 엔트리 포인트. 알림 채널도 여기서 한 번만 만든다. */
@HiltAndroidApp
class HealthApplication : Application() {

    @Inject
    lateinit var watchStatePublisher: WatchStatePublisher

    @Inject
    lateinit var healthConnectSyncer: HealthConnectSyncer

    override fun onCreate() {
        super.onCreate()
        AppForeground.install(this)
        createNotificationChannels()
        // 시계가 있으면 러닝 상태와 휴식 타이머를 계속 올려 보낸다. 없어도 아무 일 없다.
        watchStatePublisher.start()
        // 연동을 켰다면 끝난 기록을 Health Connect 로 맞춘다. 꺼져 있으면 아무 일도 하지 않는다.
        healthConnectSyncer.start()
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

        val watchChannel = NotificationChannel(
            CHANNEL_WATCH_REQUEST,
            getString(R.string.watch_request_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.watch_request_channel_description)
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(restChannel, runChannel, watchChannel))
    }

    companion object {
        const val CHANNEL_REST_TIMER = "rest_timer"
        const val CHANNEL_RUN_TRACKING = "run_tracking"
        const val CHANNEL_WATCH_REQUEST = "watch_request"
    }
}
