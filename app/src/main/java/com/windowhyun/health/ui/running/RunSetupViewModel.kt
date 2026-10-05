package com.windowhyun.health.ui.running

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.RunGoal
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.RunTrackingState
import com.windowhyun.health.domain.repository.LocationTracker
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.domain.repository.StepCounter
import com.windowhyun.health.service.RunServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** 러닝 시작 화면에서 고르는 값들. */
data class RunSetupUiState(
    val goalType: RunGoalType = RunGoalType.FREE,
    /** 목표 거리(km). */
    val goalDistanceKm: Double = 5.0,
    /** 목표 시간(분). */
    val goalDurationMinutes: Int = 30,
    val hasLocationPermission: Boolean = false,
    val gpsEnabled: Boolean = false,
    val stepSensorAvailable: Boolean = false,
    val stepPermissionGranted: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val tracking: RunTrackingState = RunTrackingState(),
) {
    val canStart: Boolean get() = hasLocationPermission && gpsEnabled && !tracking.isActive

    fun toGoal(): RunGoal = when (goalType) {
        RunGoalType.FREE -> RunGoal(RunGoalType.FREE, 0.0)
        RunGoalType.DISTANCE -> RunGoal(RunGoalType.DISTANCE, goalDistanceKm * 1000.0)
        RunGoalType.DURATION -> RunGoal(RunGoalType.DURATION, goalDurationMinutes * 60.0)
    }
}

@HiltViewModel
class RunSetupViewModel @Inject constructor(
    private val locationTracker: LocationTracker,
    private val stepCounter: StepCounter,
    private val runServiceController: RunServiceController,
    runTracker: RunTracker,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val selection = MutableStateFlow(RunSetupUiState())

    val uiState: StateFlow<RunSetupUiState> = combine(
        selection,
        settingsRepository.settings,
        runTracker.state,
    ) { current, settings, tracking ->
        current.copy(settings = settings, tracking = tracking)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RunSetupUiState())

    init {
        refreshLocationStatus()
    }

    /** 화면에 다시 들어올 때마다 권한과 GPS 상태를 다시 본다. */
    fun refreshLocationStatus() = selection.update {
        it.copy(
            hasLocationPermission = locationTracker.hasLocationPermission(),
            gpsEnabled = locationTracker.isLocationAvailable(),
            stepSensorAvailable = stepCounter.isAvailable(),
            stepPermissionGranted = stepCounter.hasPermission(),
        )
    }

    fun setGoalType(type: RunGoalType) = selection.update { it.copy(goalType = type) }

    fun changeGoalDistance(deltaKm: Double) = selection.update {
        it.copy(goalDistanceKm = (it.goalDistanceKm + deltaKm).coerceIn(1.0, 100.0))
    }

    fun changeGoalDuration(deltaMinutes: Int) = selection.update {
        it.copy(goalDurationMinutes = (it.goalDurationMinutes + deltaMinutes).coerceIn(5, 600))
    }

    /** Foreground Service 를 띄워 기록을 시작한다. */
    fun startRun() {
        val state = uiState.value
        if (!state.canStart) return
        runServiceController.start(state.toGoal())
    }
}
