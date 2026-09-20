package com.windowhyun.health.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.windowhyun.health.HealthApplication
import com.windowhyun.health.MainActivity
import com.windowhyun.health.R
import com.windowhyun.health.core.util.formatDistance
import com.windowhyun.health.core.util.formatDuration
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.model.RunGoal
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.RunStatus
import com.windowhyun.health.domain.repository.LocationTracker
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.domain.repository.StepCounter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 러닝 기록용 Foreground Service.
 *
 * 화면이 꺼져 있어도 GPS 수신과 시간 누적이 이어지도록 한다.
 * 상태 자체는 [RunTracker] 가 들고 있으므로 화면은 서비스에 바인딩하지 않는다.
 */
@AndroidEntryPoint
class RunTrackingService : LifecycleService() {

    @Inject lateinit var runTracker: RunTracker

    @Inject lateinit var locationTracker: LocationTracker

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var stepCounter: StepCounter

    private var locationJob: Job? = null
    private var stepJob: Job? = null
    private var tickerJob: Job? = null
    private var notificationJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                val goalType = intent.getStringExtra(EXTRA_GOAL_TYPE)
                    ?.let { runCatching { RunGoalType.valueOf(it) }.getOrNull() }
                    ?: RunGoalType.FREE
                val goalValue = intent.getDoubleExtra(EXTRA_GOAL_VALUE, 0.0)
                startTracking(RunGoal(goalType, goalValue))
            }

            ACTION_PAUSE -> lifecycleScope.launch { runTracker.pause() }
            ACTION_RESUME -> lifecycleScope.launch { runTracker.resume() }
            // 기록을 마감한 뒤에 서비스를 내린다. 순서가 바뀌면 endTime 과
            // 마지막 Lap 이 기록되지 않는다.
            ACTION_STOP -> lifecycleScope.launch {
                runTracker.finish()
                stopTracking()
            }
            else -> Unit
        }
        // 시스템이 서비스를 죽였을 때 마지막 Intent 없이 되살리면 상태가 어긋나므로
        // 재시작하지 않는다. 이미 달린 구간은 DB 에 남아 있다.
        return START_NOT_STICKY
    }

    private fun startTracking(goal: RunGoal) {
        if (locationJob != null) return

        startForegroundWithNotification(buildNotification(distanceText = "0.00km", durationText = "0:00"))

        lifecycleScope.launch { runTracker.start(goal) }

        locationJob = lifecycleScope.launch {
            locationTracker.locationUpdates(LOCATION_INTERVAL_MILLIS)
                .catch { /* 권한이 사라지거나 GPS 가 꺼지면 스트림이 끊긴다. 기록은 유지한다. */ }
                .collect { sample -> runTracker.onLocation(sample) }
        }

        // 걸음 센서가 없거나 권한이 없으면 스트림이 비어 있어 아무 일도 하지 않는다.
        stepJob = lifecycleScope.launch {
            stepCounter.cumulativeSteps()
                .catch { /* 센서 오류는 러닝 기록을 막지 않는다. */ }
                .collect { raw -> runTracker.onStepCount(raw) }
        }

        tickerJob = lifecycleScope.launch {
            while (isActive) {
                delay(1_000)
                runTracker.tick()
                stopIfGoalReached()
            }
        }

        notificationJob = lifecycleScope.launch {
            val distanceUnit = settingsRepository.settings.first().distanceUnit
            runTracker.state.collect { state ->
                if (!state.isActive) return@collect
                updateNotification(
                    distanceText = formatDistance(state.distanceMeters, distanceUnit),
                    durationText = formatDuration(state.durationSeconds),
                    paused = state.status == RunStatus.PAUSED,
                )
            }
        }
    }

    /** 목표 거리/시간을 채우면 자동으로 종료한다(조작을 한 번 줄인다). */
    private suspend fun stopIfGoalReached() {
        val state = runTracker.state.value
        if (state.status != RunStatus.TRACKING) return
        if (state.goal.isReached(state.distanceMeters, state.durationSeconds)) {
            runTracker.finish()
            stopTracking()
        }
    }

    private fun stopTracking() {
        locationJob?.cancel()
        locationJob = null
        stepJob?.cancel()
        stepJob = null
        tickerJob?.cancel()
        tickerJob = null
        notificationJob?.cancel()
        notificationJob = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        locationJob?.cancel()
        stepJob?.cancel()
        tickerJob?.cancel()
        notificationJob?.cancel()
        super.onDestroy()
    }

    private fun startForegroundWithNotification(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    /**
     * 진행 상황을 알림에 반영한다.
     *
     * 알림 권한이 없어도 기록 자체는 계속되어야 하므로 조용히 건너뛴다.
     * (Foreground Service 자체는 권한 없이도 동작한다.)
     */
    private fun updateNotification(distanceText: String, durationText: String, paused: Boolean) {
        // 권한 확인은 lint 가 알아볼 수 있도록 이 함수 안에서 직접 한다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        runCatching {
            manager.notify(
                NOTIFICATION_ID,
                buildNotification(distanceText, durationText, paused),
            )
        }
    }

    private fun buildNotification(
        distanceText: String,
        durationText: String,
        paused: Boolean = false,
    ): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, HealthApplication.CHANNEL_RUN_TRACKING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (paused) getString(R.string.run_paused) else getString(R.string.run_in_progress))
            .setContentText("$distanceText · $durationText")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val LOCATION_INTERVAL_MILLIS = 2_000L

        const val ACTION_START = "com.windowhyun.health.action.RUN_START"
        const val ACTION_PAUSE = "com.windowhyun.health.action.RUN_PAUSE"
        const val ACTION_RESUME = "com.windowhyun.health.action.RUN_RESUME"
        const val ACTION_STOP = "com.windowhyun.health.action.RUN_STOP"

        private const val EXTRA_GOAL_TYPE = "goal_type"
        private const val EXTRA_GOAL_VALUE = "goal_value"

        fun start(context: Context, goal: RunGoal) {
            val intent = Intent(context, RunTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_GOAL_TYPE, goal.type.name)
                putExtra(EXTRA_GOAL_VALUE, goal.value)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun sendAction(context: Context, action: String) {
            val intent = Intent(context, RunTrackingService::class.java).apply {
                this.action = action
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
