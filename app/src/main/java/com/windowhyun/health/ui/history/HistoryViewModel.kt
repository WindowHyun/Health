package com.windowhyun.health.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.core.util.currentDateFlow
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    // 날짜를 고정하면 자정을 넘겼을 때 오늘 기록이 조회 범위 밖으로 밀려난다.
    val uiState: StateFlow<HistoryUiState> = currentDateFlow().flatMapLatest { today ->
        combine(
            workoutRepository.observeWorkoutsBetween(
                from = today.minusYears(1),
                to = today,
            ),
            settingsRepository.settings,
        ) { workouts, settings ->
            HistoryUiState(workouts = workouts, settings = settings, loading = false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
