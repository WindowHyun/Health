package com.windowhyun.health.wear

import android.app.Application
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.shared.CommandAck
import com.windowhyun.health.shared.RestSnapshot
import com.windowhyun.health.shared.RunSnapshot
import com.windowhyun.health.shared.WatchCommand
import com.windowhyun.health.shared.WearCodec
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 시계가 손목을 울리는 곳. 테스트에서는 가짜로 바꾼다. */
interface WatchHaptics {
    /** 휴식이 끝났다. [endsAtMillis] 는 그 휴식이 끝나는 시각(같은 휴식에 두 번 울리지 않게 구분하는 데 쓴다). */
    fun restFinished(endsAtMillis: Long)

    /** 폰이 명령을 받아들였다. 버튼이 먹혔다는 짧은 느낌. */
    fun commandAccepted() {}

    /** 폰이 명령을 받아들이지 않았다. */
    fun commandRejected() {}
}

/** 폰에서 받은 상태를 화면에 올리고, 버튼 입력을 폰으로 보낸다. */
class WatchViewModel internal constructor(
    private val source: WatchDataSource,
    private val haptics: WatchHaptics,
    private val clock: () -> Long,
) : ViewModel() {

    /** 앱에서 쓰는 생성자. */
    constructor(application: Application, source: WatchDataSource) :
        this(source, SystemWatchHaptics(application), System::currentTimeMillis)

    private val _state = MutableStateFlow(WatchUiState(nowMillis = clock()))
    val state: StateFlow<WatchUiState> = _state.asStateFlow()

    private var stopConfirmJob: Job? = null

    /** 휴식 끝 진동을 이미 울린 휴식. 같은 휴식에 두 번 울리지 않게 끝나는 시각으로 구분한다. */
    private var vibratedRestEndsAt: Long = -1

    /** 화면이 보이는 중인지. 보이지 않을 때는 초를 새로 세지 않는다(손목 배터리). */
    private val screenVisible = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            source.run.keepListening().collect { snapshot ->
                _state.update { it.copy(run = snapshot, nowMillis = clock()) }
            }
        }
        viewModelScope.launch {
            source.rest.keepListening().collect { snapshot ->
                _state.update { it.copy(rest = snapshot, nowMillis = clock()) }
            }
        }
        viewModelScope.launch {
            source.workout.keepListening().collect { snapshot ->
                _state.update { it.copy(workout = snapshot, nowMillis = clock()) }
            }
        }
        viewModelScope.launch {
            source.acks.keepListening().collect { ack -> onAck(ack) }
        }
        viewModelScope.launch {
            // 화면이 보일 때만 초를 센다. 손목을 내려 화면이 꺼진 동안에도 돌면 배터리만 쓴다.
            // 그때의 휴식 끝 진동은 알람([RestFinishAlarm])이 맡는다.
            screenVisible.collectLatest { visible ->
                if (!visible) return@collectLatest
                tick()
                while (true) {
                    delay(TICK_MILLIS)
                    tick()
                }
            }
        }
    }

    /** 화면이 보이기 시작하거나 안 보이게 됐다. */
    fun setScreenVisible(visible: Boolean) {
        screenVisible.value = visible
    }

    /**
     * 폰과의 연결이 일시적으로 끊기거나 Play 서비스가 아직 준비되지 않아 흐름이 실패해도,
     * 잠시 뒤 다시 듣는다. 한 번 실패했다고 앱을 다시 열 때까지 상태를 못 받으면 안 된다.
     */
    private fun <T> Flow<T>.keepListening(): Flow<T> = retryWhen { _, attempt ->
        delay(retryDelayMillis(attempt))
        true
    }

    /** 폰이 명령을 처리한 결과를 받았다. */
    private fun onAck(ack: CommandAck) {
        if (ack.accepted) {
            haptics.commandAccepted()
            _state.update { it.copy(rejectedCommand = null) }
        } else {
            haptics.commandRejected()
            _state.update {
                it.copy(
                    rejectedCommand = ack.command,
                    rejectedAtMillis = clock(),
                    // 폰이 시작을 거절했다면 알림에서 누르라고 안내할 이유가 없다.
                    startRequestedAtMillis = if (ack.command == WatchCommand.RUN_START) 0 else it.startRequestedAtMillis,
                    nowMillis = clock(),
                )
            }
        }
    }

    /** 시계 화면이 시간을 새로 계산하게 하고, 휴식이 끝났으면 손목을 울린다. */
    internal fun tick() {
        val now = clock()
        val rest = _state.value.rest
        // 끝난 지 한참 지난 휴식(앱을 늦게 열었다)에는 울리지 않는다. 그 순간에는 알람이 이미 울렸다.
        if (rest.isFinished(now) && now - rest.endsAtMillis <= REST_BUZZ_WINDOW_MILLIS &&
            vibratedRestEndsAt != rest.endsAtMillis
        ) {
            vibratedRestEndsAt = rest.endsAtMillis
            haptics.restFinished(rest.endsAtMillis)
        }
        _state.update { it.copy(nowMillis = now) }
    }

    fun send(command: WatchCommand) {
        viewModelScope.launch {
            val delivered = source.send(command)
            _state.update { it.withSendResult(delivered, clock()) }
        }
    }

    /**
     * 지금 보이는 세트를 완료한다. 어느 세트인지 번호를 함께 보내, 그사이 폰에서 먼저 끝냈다면
     * 폰이 무시하게 한다(다음 세트가 엉뚱하게 끝나지 않도록).
     */
    fun completeSet() {
        val workout = _state.value.workout
        if (!workout.canComplete) return
        viewModelScope.launch {
            val delivered = source.send(WatchCommand.SET_COMPLETE, WearCodec.encodeSetId(workout.setId))
            _state.update { it.withSendResult(delivered, clock()) }
        }
    }

    /** 폰에서 자유 러닝을 시작해 달라고 요청한다. 폰이 눈앞에 없으면 폰 알림에서 확인을 기다린다. */
    fun startRun() {
        viewModelScope.launch {
            val delivered = source.send(WatchCommand.RUN_START)
            _state.update {
                it.withSendResult(delivered, clock()).copy(startRequestedAtMillis = if (delivered) clock() else 0)
            }
        }
    }

    /** 종료는 실수로 누르기 쉬우니 한 번 더 확인받는다. 몇 초 안에 확인하지 않으면 취소된다. */
    fun requestStop() {
        _state.update { it.copy(confirmingStop = true) }
        stopConfirmJob?.cancel()
        stopConfirmJob = viewModelScope.launch {
            delay(STOP_CONFIRM_MILLIS)
            _state.update { it.copy(confirmingStop = false) }
        }
    }

    fun confirmStop() {
        stopConfirmJob?.cancel()
        _state.update { it.copy(confirmingStop = false) }
        send(WatchCommand.RUN_STOP)
    }

    fun cancelStop() {
        stopConfirmJob?.cancel()
        _state.update { it.copy(confirmingStop = false) }
    }

    fun dismissSendFailure() {
        _state.update { it.copy(sendFailed = false) }
    }

    private fun WatchUiState.withSendResult(delivered: Boolean, now: Long): WatchUiState =
        // 새 명령을 보내면 이전에 거절당한 안내는 치운다.
        copy(
            sendFailed = !delivered,
            sendFailedAtMillis = if (delivered) 0 else now,
            rejectedCommand = null,
            nowMillis = now,
        )

    companion object {
        const val TICK_MILLIS = 250L
        const val STOP_CONFIRM_MILLIS = 5_000L

        /** 휴식이 끝난 뒤 이 시간 안에만 손목을 울린다. */
        const val REST_BUZZ_WINDOW_MILLIS = 5_000L

        private const val RETRY_BASE_MILLIS = 1_000L
        private const val RETRY_MAX_MILLIS = 30_000L

        /** 다시 듣기까지 기다리는 시간: 1초, 2초, 4초 … 최대 30초. */
        internal fun retryDelayMillis(attempt: Long): Long =
            (RETRY_BASE_MILLIS shl attempt.coerceIn(0, 5).toInt()).coerceAtMost(RETRY_MAX_MILLIS)
    }
}

/**
 * 손목을 울린다. 휴식 끝은 알람([RestFinishReceiver])과 같은 곳을 거쳐 같은 휴식에 두 번 울리지 않는다.
 * 진동이 없는 기기에서는 아무 일도 없다.
 */
internal class SystemWatchHaptics(private val application: Application) : WatchHaptics {
    private val vibrator: Vibrator? = if (android.os.Build.VERSION.SDK_INT >= 31) {
        application.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Vibrator::class.java)
    }

    override fun restFinished(endsAtMillis: Long) {
        RestVibration.vibrateOnce(application, endsAtMillis)
    }

    override fun commandAccepted() = tick(longArrayOf(0, 25))

    override fun commandRejected() = tick(longArrayOf(0, 70, 70, 70))

    private fun tick(pattern: LongArray) {
        vibrator?.takeIf { it.hasVibrator() }?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
