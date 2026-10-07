package com.windowhyun.health.wear

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.windowhyun.health.HealthApplication
import com.windowhyun.health.MainActivity
import com.windowhyun.health.R
import com.windowhyun.health.domain.model.RunGoal
import com.windowhyun.health.domain.repository.LocationTracker
import com.windowhyun.health.service.RunServiceController
import com.windowhyun.health.service.RunTrackingService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 시계에서 온 "러닝 시작"을 처리하는 쪽. 테스트에서는 가짜로 바꾼다. */
fun interface RunStarter {
    /** 시작했거나 사용자에게 시작을 부탁했으면 true. 할 수 없는 상태면 false. */
    fun requestStart(): Boolean

    companion object {
        val None = RunStarter { false }
    }
}

/**
 * 러닝을 시작한다.
 *
 * - 폰 앱이 눈앞에 있으면 바로 시작한다.
 * - 아니면(주머니 속, 화면 꺼짐) 안드로이드가 위치 서비스를 몰래 시작하지 못하게 막는다. 대신 "러닝 시작" 버튼이 있는
 *   알림을 띄운다. 사용자가 그 버튼을 누르면 시작된다(알림 버튼을 누른 것은 사용자의 조작이라 허용된다).
 * - 위치 권한이 없으면 시작할 수 없어서, 앱을 열어 허용하라는 알림을 띄운다.
 */
@Singleton
class WatchRunStarter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationTracker: LocationTracker,
    private val runServiceController: RunServiceController,
) : RunStarter {

    override fun requestStart(): Boolean {
        if (!locationTracker.hasLocationPermission()) {
            notifyUser(
                text = context.getString(R.string.watch_run_needs_permission),
                withStartButton = false,
            )
            return false
        }
        if (AppForeground.isForeground) {
            runServiceController.start(RunGoal())
            return true
        }
        notifyUser(text = context.getString(R.string.watch_run_request_text), withStartButton = true)
        return true
    }

    private fun notifyUser(text: String, withStartButton: Boolean) {
        // 알림 권한이 없으면 알릴 방법이 없다. 시계에는 "폰에서 시작해 주세요"가 남는다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, HealthApplication.CHANNEL_WATCH_REQUEST)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.watch_run_request_title))
            .setContentText(text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setTimeoutAfter(REQUEST_TIMEOUT_MILLIS)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (withStartButton) {
            val start = PendingIntent.getForegroundService(
                context,
                REQUEST_CODE_START,
                RunTrackingService.startIntent(context, RunGoal(), null),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_play,
                    context.getString(R.string.watch_run_request_action),
                    start,
                ).build(),
            )
        }
        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    companion object {
        const val NOTIFICATION_ID = 2002
        private const val REQUEST_CODE_START = 20
        private const val REQUEST_TIMEOUT_MILLIS = 60_000L
    }
}
