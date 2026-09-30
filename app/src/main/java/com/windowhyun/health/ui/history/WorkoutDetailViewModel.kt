package com.windowhyun.health.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.core.model.PersonalRecord
import com.windowhyun.health.di.ApplicationScope
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.model.WorkoutSet
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.domain.repository.WorkoutRepository
import com.windowhyun.health.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
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
    /**
     * 메모 저장은 화면이 닫히는 순간에도 끝까지 가야 한다. viewModelScope 는 뒤로 가기와
     * 함께 취소되므로 쓰기는 앱 수명의 스코프에서 한다.
     */
    @ApplicationScope private val appScope: CoroutineScope,
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

    /**
     * DB 에서 불러온 메모. null 이면 아직 불러오지 않았다는 뜻이다.
     *
     * 불러오기 전에 화면을 나가면 빈 메모를 저장해 원래 메모를 지워 버렸다. 불러온 뒤에,
     * 그리고 바뀐 경우에만 저장한다.
     */
    private var savedMemo: String? = null

    init {
        viewModelScope.launch {
            val stored = workoutRepository.getWorkout(workoutId)?.memo.orEmpty()
            savedMemo = stored
            memoState.compareAndSet(null, stored)
        }
    }

    fun setMemo(memo: String) {
        memoState.value = memo
    }

    /** 메모가 바뀌었으면 저장한다. 여러 번 불려도 한 번만 쓴다. */
    fun saveMemo() {
        val stored = savedMemo ?: return
        val current = memoState.value ?: return
        if (current == stored) return
        savedMemo = current
        appScope.launch { workoutRepository.updateMemo(workoutId, current) }
    }

    /** 화면이 닫힐 때(폰의 뒤로 가기 포함) 쓰던 메모를 저장한다. */
    override fun onCleared() {
        saveMemo()
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
