package com.windowhyun.health.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.core.model.PersonalRecord
import com.windowhyun.health.di.ApplicationScope
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.domain.repository.WorkoutRepository
import com.windowhyun.health.ui.components.MemoDraft
import com.windowhyun.health.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    val uiState: StateFlow<WorkoutSummaryUiState> = combine(
        workoutRepository.observeWorkoutDetail(workoutId),
        workoutRepository.observeWorkoutRecords(workoutId),
        settingsRepository.settings,
        memoDraft.text,
    ) { workout, records, settings, memo ->
        WorkoutSummaryUiState(
            workout = workout,
            personalRecords = records,
            settings = settings,
            memo = memo.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutSummaryUiState())

    fun setMemo(memo: String) = memoDraft.set(memo)

    /** 메모가 바뀌었으면 저장한다. 여러 번 불려도 한 번만 쓴다. */
    fun saveMemo() = memoDraft.save()

    /** 화면이 닫힐 때(폰의 뒤로 가기 포함) 쓰던 메모를 저장한다. */
    override fun onCleared() {
        saveMemo()
    }
}
