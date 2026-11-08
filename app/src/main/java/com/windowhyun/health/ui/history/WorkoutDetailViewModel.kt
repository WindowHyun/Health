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
import com.windowhyun.health.ui.components.MemoDraft
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
    /** 메모 저장용. 화면이 닫혀도 끝까지 가야 한다([MemoDraft]). */
    @ApplicationScope appScope: CoroutineScope,
) : ViewModel() {

    private val workoutId: Long = savedStateHandle[Routes.ARG_WORKOUT_ID] ?: 0L

    private val memoDraft = MemoDraft(
        loadScope = viewModelScope,
        appScope = appScope,
        load = { workoutRepository.getWorkout(workoutId)?.memo.orEmpty() },
        store = { workoutRepository.updateMemo(workoutId, it) },
    )
    private val deletedState = MutableStateFlow(false)

    val uiState: StateFlow<WorkoutDetailUiState> = combine(
        workoutRepository.observeWorkoutDetail(workoutId),
        workoutRepository.observeWorkoutRecords(workoutId),
        settingsRepository.settings,
        memoDraft.text,
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

    fun setMemo(memo: String) = memoDraft.set(memo)

    /** 메모가 바뀌었으면 저장한다. 여러 번 불려도 한 번만 쓴다. */
    fun saveMemo() = memoDraft.save()

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

    /** 세트를 지웠다는 신호. 화면이 "실행 취소" 안내를 잠깐 보여 준다. */
    private val _setRemoved = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val setRemoved: kotlinx.coroutines.flow.SharedFlow<Unit> = _setRemoved

    /** 방금 지운 세트(어느 종목의 어느 세트였는지 포함). */
    private var lastRemovedSet: Pair<Long, WorkoutSet>? = null

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            // 지우기 전에 저장된 모습을 읽어 둔다.
            val owner = workoutRepository.getWorkout(workoutId)?.exercises
                ?.firstOrNull { record -> record.sets.any { it.id == setId } }
            val set = owner?.sets?.firstOrNull { it.id == setId }
            workoutRepository.removeSet(setId)
            if (owner != null && set != null) {
                lastRemovedSet = owner.id to set
                _setRemoved.emit(Unit)
            }
        }
    }

    /** 잘못 지운 세트를 되살린다. */
    fun undoDeleteSet() {
        val (workoutExerciseId, set) = lastRemovedSet ?: return
        lastRemovedSet = null
        viewModelScope.launch { workoutRepository.restoreSet(workoutExerciseId, set) }
    }

    fun deleteWorkout() {
        viewModelScope.launch {
            workoutRepository.deleteWorkout(workoutId)
            deletedState.value = true
        }
    }
}
