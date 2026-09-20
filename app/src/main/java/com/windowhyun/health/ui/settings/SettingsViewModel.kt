package com.windowhyun.health.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.domain.model.AppSettings
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
}
