package com.windowhyun.health.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.core.model.PersonalRecord
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.model.WorkoutSet
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

data class WorkoutDetailUiState(
    val workout: Workout? = null,
    val personalRecords: List<PersonalRecord> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val memo: String = "",
    val deleted: Boolean = false,
)

/** 과거 기록 상세: 조회 / 메모 수정 / 세트 값 수정 / 삭제. */
@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val workoutId: Long = savedStateHandle[Routes.ARG_WORKOUT_ID] ?: 0L

    private val memoState = MutableStateFlow("")
    private val deletedState = MutableStateFlow(false)

    val uiState: StateFlow<WorkoutDetailUiState> = combine(
        workoutRepository.observeWorkoutDetail(workoutId),
        workoutRepository.observeWorkoutRecords(workoutId),
        settingsRepository.settings,
        memoState,
        deletedState,
    ) { workout, records, settings, memo, deleted ->
        WorkoutDetailUiState(
            workout = workout,
            personalRecords = records,
            settings = settings,
            memo = memo,
            deleted = deleted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDetailUiState())

    init {
        viewModelScope.launch {
            memoState.value = workoutRepository.getWorkout(workoutId)?.memo.orEmpty()
        }
    }

    fun setMemo(memo: String) {
        memoState.value = memo
    }

    fun saveMemo() {
        viewModelScope.launch { workoutRepository.updateMemo(workoutId, memoState.value) }
    }

    /** 잘못 기록한 세트를 고친다. */
    fun updateSet(set: WorkoutSet, weightKg: Double, reps: Int) {
        viewModelScope.launch {
            workoutRepository.setCompleted(set.id, weightKg, reps, set.completed)
        }
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch { workoutRepository.removeSet(setId) }
    }

    fun deleteWorkout() {
        viewModelScope.launch {
            workoutRepository.deleteWorkout(workoutId)
            deletedState.value = true
        }
    }
}
