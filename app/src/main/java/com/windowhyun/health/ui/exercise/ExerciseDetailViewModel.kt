package com.windowhyun.health.ui.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.core.model.PersonalRecord
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.ExerciseSession
import com.windowhyun.health.domain.model.ProgressMetric
import com.windowhyun.health.domain.model.ProgressPoint
import com.windowhyun.health.domain.model.progressPoints
import com.windowhyun.health.domain.repository.ExerciseRepository
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.domain.repository.WorkoutRepository
import com.windowhyun.health.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ExerciseDetailUiState(
    val exercise: Exercise? = null,
    /** 최신순. */
    val sessions: List<ExerciseSession> = emptyList(),
    val records: List<PersonalRecord> = emptyList(),
    val metrics: List<ProgressMetric> = emptyList(),
    val metric: ProgressMetric? = null,
    val settings: AppSettings = AppSettings(),
    val loading: Boolean = true,
) {
    /** 그래프 점(오래된 순). 단위 변환은 화면에서 한다. */
    val points: List<ProgressPoint>
        get() = metric?.let { sessions.progressPoints(it) }.orEmpty()
}

/**
 * 종목 하나의 성장 그래프와 전체 기록.
 *
 * 기록 상세에서 세트를 고치거나 지우면 목록과 그래프가 바로 바뀌도록 Flow 로 받는다.
 */
@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    exerciseRepository: ExerciseRepository,
    settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val exerciseId: Long = savedStateHandle[Routes.ARG_EXERCISE_ID] ?: 0L

    /** 사용자가 고른 지표. null 이면 기록 방식의 첫 지표를 쓴다. */
    private val chosenMetric = MutableStateFlow<ProgressMetric?>(null)

    /**
     * 기록과 PR 을 함께 받는다. PR 은 기록이 바뀔 때만 다시 계산한다(끝난 기록을 고치면
     * PR 도 다시 계산되기 때문). 그래프 지표 칩이나 설정이 바뀔 때는 다시 읽지 않는다.
     */
    private val sessionsWithRecords = workoutRepository.observeExerciseHistory(exerciseId)
        .map { sessions -> sessions to workoutRepository.getPersonalRecords(exerciseId) }

    val uiState: StateFlow<ExerciseDetailUiState> = combine(
        flow { emit(exerciseRepository.getExercise(exerciseId)) },
        sessionsWithRecords,
        settingsRepository.settings,
        chosenMetric,
    ) { exercise, (sessions, records), settings, chosen ->
        val metrics = exercise?.let { ProgressMetric.forTrackingType(it.trackingType) }.orEmpty()
        ExerciseDetailUiState(
            exercise = exercise,
            sessions = sessions,
            records = if (exercise == null) emptyList() else records,
            metrics = metrics,
            metric = chosen?.takeIf { it in metrics } ?: metrics.firstOrNull(),
            settings = settings,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState())

    fun selectMetric(metric: ProgressMetric) {
        chosenMetric.value = metric
    }
}
