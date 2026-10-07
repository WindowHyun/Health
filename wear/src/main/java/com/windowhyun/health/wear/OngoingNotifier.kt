package com.windowhyun.health.wear

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status

/**
 * 워치페이스 아래 "진행 중" 칩. 러닝이나 세트를 하는 동안 앱을 닫고 화면이 꺼져도, 시계를 켜면 칩이 보이고
 * 누르면 앱으로 돌아온다. 시간은 시스템이 알아서 흘려서 폰 소식이 없어도 숫자가 멈추지 않는다.
 *
 * 알림 권한이 없으면 조용히 건너뛴다(앱 기능에는 영향이 없다).
 */
internal class OngoingNotifier(private val context: Context) {

    /** [content] 가 null 이면 칩을 치운다. */
    fun show(content: OngoingContent?, nowMillis: Long = System.currentTimeMillis()) {
        if (content == null) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        // 권한 확인은 lint 가 알아볼 수 있도록 이 함수 안에서 직접 한다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel()

        val touch = PendingIntent.getActivity(
            context,
            0,
            Intent(context, WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(content.title)
            .setContentText(summaryText(content, nowMillis))
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(touch)
            // 폰 소식이 끊기면(폰 앱이 죽었다) 칩이 스스로 사라진다. 폰은 진행 중에 몇 초마다 소식을 보낸다.
            .setTimeoutAfter(timeoutMillis(content, nowMillis))
        applyClock(builder, content)

        val ongoing = OngoingActivity.Builder(context, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_launcher_foreground)
            .setTouchIntent(touch)
            .setStatus(status(content))
            .build()
        ongoing.apply(context)

        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    /** 알림 본문에 흐르는 시계를 같이 보여 준다(칩을 지원하지 않는 화면에서도). */
    private fun applyClock(builder: NotificationCompat.Builder, content: OngoingContent) {
        when (val clock = content.clock) {
            is OngoingContent.Clock.Stopwatch -> builder.setShowWhen(true).setWhen(clock.zeroMillis).setUsesChronometer(true)
            is OngoingContent.Clock.Countdown ->
                builder.setShowWhen(true).setWhen(clock.endMillis).setUsesChronometer(true).setChronometerCountDown(true)
            is OngoingContent.Clock.Frozen, null -> Unit
        }
    }

    private fun status(content: OngoingContent): Status {
        val builder = Status.Builder()
        when (val clock = content.clock) {
            is OngoingContent.Clock.Stopwatch -> builder
                .addTemplate("#text# #time#")
                .addPart("text", Status.TextPart(content.text))
                .addPart("time", Status.StopwatchPart(clock.zeroMillis))
            is OngoingContent.Clock.Countdown -> builder
                .addTemplate("#title# #time#")
                .addPart("title", Status.TextPart(content.title))
                .addPart("time", Status.TimerPart(clock.endMillis))
            is OngoingContent.Clock.Frozen -> builder
                .addTemplate("#title# #time#")
                .addPart("title", Status.TextPart(content.title))
                .addPart("time", Status.TextPart(clock.text))
            null -> builder
                .addTemplate("#title# #text#")
                .addPart("title", Status.TextPart(content.title))
                .addPart("text", Status.TextPart(content.text))
        }
        return builder.build()
    }

    private fun summaryText(content: OngoingContent, nowMillis: Long): String = when (val clock = content.clock) {
        is OngoingContent.Clock.Stopwatch -> content.text
        is OngoingContent.Clock.Countdown ->
            WatchFormat.restClock(((clock.endMillis - nowMillis).coerceAtLeast(0) + 999).div(1_000).toInt())
        is OngoingContent.Clock.Frozen -> "${clock.text} · ${content.text}".trimEnd(' ', '·')
        null -> content.text
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "진행 중인 운동", NotificationManager.IMPORTANCE_LOW).apply {
                description = "러닝 · 헬스 중 워치페이스에 현재 상황을 보여 줍니다."
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "ongoing_workout"

        /** 소식이 이만큼 끊기면 칩을 치운다. 폰이 진행 중에 보내는 간격(15초)보다 충분히 길다. */
        internal const val QUIET_TIMEOUT_MILLIS = 90_000L

        /** 칩이 스스로 사라질 때까지의 시간. 휴식은 끝나는 시각에 맞춰 사라진다. */
        internal fun timeoutMillis(content: OngoingContent, nowMillis: Long): Long =
            when (val clock = content.clock) {
                is OngoingContent.Clock.Countdown -> (clock.endMillis - nowMillis).coerceAtLeast(0) + REST_GRACE_MILLIS
                else -> QUIET_TIMEOUT_MILLIS
            }

        private const val REST_GRACE_MILLIS = 2_000L
    }
}
