package com.windowhyun.health.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class HistoryUiState(
    val workouts: List<Workout> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val loading: Boolean = true,
)

/**
 * 기록 탭 (Phase 1: 최근 기록 목록).
 * Phase 3 에서 캘린더 / 통계 영역이 여기에 붙는다.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = combine(
        workoutRepository.observeWorkoutsBetween(
            from = LocalDate.now().minusYears(1),
            to = LocalDate.now(),
        ),
        settingsRepository.settings,
    ) { workouts, settings ->
        HistoryUiState(workouts = workouts, settings = settings, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
