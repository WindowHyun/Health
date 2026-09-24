package com.windowhyun.health.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.core.model.PersonalRecord
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.domain.repository.WorkoutRepository
import com.windowhyun.health.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkoutSummaryUiState(
    val workout: Workout? = null,
    val personalRecords: List<PersonalRecord> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val memo: String = "",
)

/**
 * 운동 종료 화면. PR 은 세션 종료 시 DB 에 저장된 값을 그대로 읽는다.
 */
@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val workoutId: Long = savedStateHandle[Routes.ARG_WORKOUT_ID] ?: 0L

    // null 은 "아직 불러오지 않음". 사용자가 먼저 입력하면 덮어쓰지 않는다.
    private val memoState = MutableStateFlow<String?>(null)

    val uiState: StateFlow<WorkoutSummaryUiState> = combine(
        workoutRepository.observeWorkoutDetail(workoutId),
        workoutRepository.observeWorkoutRecords(workoutId),
        settingsRepository.settings,
        memoState,
    ) { workout, records, settings, memo ->
        WorkoutSummaryUiState(
            workout = workout,
            personalRecords = records,
            settings = settings,
            memo = memo.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutSummaryUiState())

    init {
        viewModelScope.launch {
            memoState.compareAndSet(null, workoutRepository.getWorkout(workoutId)?.memo.orEmpty())
        }
    }

    fun setMemo(memo: String) {
        memoState.value = memo
    }

    /** 메모는 화면을 떠날 때 저장한다. */
    fun saveMemo() {
        viewModelScope.launch { workoutRepository.updateMemo(workoutId, memoState.value.orEmpty()) }
    }
}
