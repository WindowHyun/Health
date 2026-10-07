package com.windowhyun.health.wear

import android.app.Application
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.shared.RestSnapshot
import com.windowhyun.health.shared.RunSnapshot
import com.windowhyun.health.shared.WatchCommand
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 시계가 손목을 울리는 곳. 테스트에서는 가짜로 바꾼다. */
fun interface WatchHaptics {
    fun restFinished()
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

    init {
        viewModelScope.launch {
            source.run.catch { }.collect { snapshot ->
                _state.update { it.copy(run = snapshot, nowMillis = clock()) }
            }
        }
        viewModelScope.launch {
            source.rest.catch { }.collect { snapshot ->
                _state.update { it.copy(rest = snapshot, nowMillis = clock()) }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(TICK_MILLIS)
                tick()
            }
        }
    }

    /** 시계 화면이 시간을 새로 계산하게 하고, 휴식이 끝났으면 손목을 울린다. */
    internal fun tick() {
        val now = clock()
        val rest = _state.value.rest
        if (rest.isFinished(now) && vibratedRestEndsAt != rest.endsAtMillis) {
            vibratedRestEndsAt = rest.endsAtMillis
            haptics.restFinished()
        }
        _state.update { it.copy(nowMillis = now) }
    }

    fun send(command: WatchCommand) {
        viewModelScope.launch {
            val delivered = source.send(command)
            _state.update { it.copy(sendFailed = !delivered) }
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

    companion object {
        const val TICK_MILLIS = 250L
        const val STOP_CONFIRM_MILLIS = 5_000L
    }
}

/** 휴식이 끝났을 때 짧게 두 번 울린다. 진동이 없는 기기에서는 아무 일도 없다. */
internal class SystemWatchHaptics(application: Application) : WatchHaptics {
    private val vibrator: Vibrator? = if (android.os.Build.VERSION.SDK_INT >= 31) {
        application.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Vibrator::class.java)
    }

    override fun restFinished() {
        vibrator?.takeIf { it.hasVibrator() }
            ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 120, 200), -1))
    }
}
