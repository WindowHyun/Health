package com.windowhyun.health

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

/** Hilt 엔트리 포인트. 알림 채널도 여기서 한 번만 만든다. */
@HiltAndroidApp
class HealthApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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
        manager.createNotificationChannel(restChannel)
    }

    companion object {
        const val CHANNEL_REST_TIMER = "rest_timer"
    }
}
