package com.windowhyun.health.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.PipSpace
import com.windowhyun.health.domain.model.PipSpacePosition
import com.windowhyun.health.domain.model.ThemeMode
import com.windowhyun.health.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun setDefaultRestSeconds(seconds: Int) =
        update { it.copy(defaultRestSeconds = seconds.coerceIn(5, 600)) }

    fun setWeightUnit(unit: WeightUnit) = update { it.copy(weightUnit = unit) }

    fun setDistanceUnit(unit: DistanceUnit) = update { it.copy(distanceUnit = unit) }

    fun setVibration(enabled: Boolean) = update { it.copy(vibrationEnabled = enabled) }

    fun setRestAutoStart(enabled: Boolean) = update { it.copy(restTimerAutoStart = enabled) }

    fun setAutoLapMeters(meters: Int) = update { it.copy(autoLapMeters = meters.coerceIn(100, 10_000)) }

    fun setBodyWeightKg(weightKg: Double) =
        update { it.copy(bodyWeightKg = weightKg.coerceIn(20.0, 250.0)) }

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun setKeepScreenOn(enabled: Boolean) = update { it.copy(keepScreenOnDuringWorkout = enabled) }

    fun setRunVibrationCues(enabled: Boolean) = update { it.copy(runVibrationCues = enabled) }

    fun setRunVoiceCues(enabled: Boolean) = update { it.copy(runVoiceCues = enabled) }

    fun setWeeklyWorkoutGoal(count: Int) =
        update { it.copy(weeklyWorkoutGoal = count.coerceIn(0, MAX_WEEKLY_WORKOUTS)) }

    fun setWeeklyRunGoalMeters(meters: Int) =
        update { it.copy(weeklyRunGoalMeters = meters.coerceIn(0, MAX_WEEKLY_RUN_METERS)) }

    fun setPipSpaceEnabled(enabled: Boolean) = update { it.copy(pipSpaceEnabled = enabled) }

    fun setPipSpacePosition(position: PipSpacePosition) = update { it.copy(pipSpacePosition = position) }

    fun setPipSpaceHeightDp(heightDp: Int) = update { it.copy(pipSpaceHeightDp = PipSpace.clampHeight(heightDp)) }

    fun setAutoPauseRun(enabled: Boolean) = update { it.copy(autoPauseRun = enabled) }

    companion object {
        const val MAX_WEEKLY_WORKOUTS = 14
        const val MAX_WEEKLY_RUN_METERS = 500_000
    }
}
