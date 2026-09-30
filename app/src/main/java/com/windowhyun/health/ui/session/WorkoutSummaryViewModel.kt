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
    /**
     * 메모 저장은 화면이 닫히는 순간에도 끝까지 가야 한다. viewModelScope 는 뒤로 가기와
     * 함께 취소되므로 쓰기는 앱 수명의 스코프에서 한다.
     */
    @ApplicationScope private val appScope: CoroutineScope,
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
}
