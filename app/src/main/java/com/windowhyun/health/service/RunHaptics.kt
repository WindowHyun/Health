package com.windowhyun.health.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.windowhyun.health.domain.model.RunCue
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 러닝 중 진동 안내. 소리나 화면 없이 순간을 구분할 수 있게 패턴을 다르게 한다.
 *
 * - 구간(Lap): 짧게 두 번
 * - 이어 가기: 짧게 세 번
 * - 자동 일시정지: 길게 한 번
 * - 목표 달성: 길게 세 번(마지막이 가장 길다)
 */
@Singleton
class RunHaptics @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun vibrate(cue: RunCue) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return
        runCatching { vibrator.vibrate(VibrationEffect.createWaveform(cue.vibrationPattern(), -1)) }
    }
}

/** 진동 패턴(대기, 진동, 쉼, 진동 ... 밀리초). */
internal fun RunCue.vibrationPattern(): LongArray = when (this) {
    is RunCue.LapCompleted -> longArrayOf(0, 150, 100, 150)
    RunCue.AutoResumed -> longArrayOf(0, 120, 80, 120, 80, 120)
    RunCue.AutoPaused -> longArrayOf(0, 500)
    RunCue.GoalReached -> longArrayOf(0, 400, 150, 400, 150, 700)
}

/** 러닝 알림에 붙는 버튼. 기록 중이면 일시정지, 멈춰 있으면 계속, 그리고 항상 종료. */
internal enum class RunNotificationAction { PAUSE, RESUME, STOP }

internal fun runNotificationActions(paused: Boolean): List<RunNotificationAction> =
    listOf(if (paused) RunNotificationAction.RESUME else RunNotificationAction.PAUSE, RunNotificationAction.STOP)
