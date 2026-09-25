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

    // null 은 "아직 불러오지 않음". 사용자가 먼저 입력하면 덮어쓰지 않는다.
    private val memoState = MutableStateFlow<String?>(null)
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
            memo = memo.orEmpty(),
            deleted = deleted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDetailUiState())

    init {
        viewModelScope.launch {
            memoState.compareAndSet(null, workoutRepository.getWorkout(workoutId)?.memo.orEmpty())
        }
    }

    fun setMemo(memo: String) {
        memoState.value = memo
    }

    fun saveMemo() {
        viewModelScope.launch { workoutRepository.updateMemo(workoutId, memoState.value.orEmpty()) }
    }

    /** 잘못 기록한 세트를 고친다. */
    fun updateSet(set: WorkoutSet, weightKg: Double, reps: Int, durationSeconds: Int) {
        viewModelScope.launch {
            workoutRepository.setCompleted(set.id, weightKg, reps, set.completed, durationSeconds)
        }
    }

    /** 지난 기록에서 워밍업을 뒤늦게 표시할 수 있게 한다. */
    fun cycleSetType(set: WorkoutSet) {
        viewModelScope.launch {
            workoutRepository.setSetType(set.id, set.setType.next())
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
