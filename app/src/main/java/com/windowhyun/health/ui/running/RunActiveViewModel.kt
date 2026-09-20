package com.windowhyun.health.ui.running

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.RunTrackingState
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.service.RunServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RunActiveUiState(
    val tracking: RunTrackingState = RunTrackingState(),
    val settings: AppSettings = AppSettings(),
)

/**
 * 러닝 진행 화면.
 *
 * 상태는 [RunTracker] 가 가지고 있어서 화면이 사라졌다 돌아와도 그대로다.
 * 실제 시작/정지는 Foreground Service 에 맡긴다.
 */
@HiltViewModel
class RunActiveViewModel @Inject constructor(
    runTracker: RunTracker,
    settingsRepository: SettingsRepository,
    private val runServiceController: RunServiceController,
) : ViewModel() {

    val uiState: StateFlow<RunActiveUiState> = combine(
        runTracker.state,
        settingsRepository.settings,
    ) { tracking, settings ->
        RunActiveUiState(tracking = tracking, settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RunActiveUiState())

    fun pause() = runServiceController.pause()

    fun resume() = runServiceController.resume()

    /** 종료하면 서비스가 마지막 Lap 을 만들고 기록을 마감한 뒤 스스로 멈춘다. */
    fun finish() = runServiceController.stop()
}
