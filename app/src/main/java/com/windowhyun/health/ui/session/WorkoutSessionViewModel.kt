package com.windowhyun.health.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.notification.RestTimerNotifier
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.model.WorkoutSet
import com.windowhyun.health.domain.repository.ExerciseRepository
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.domain.repository.WorkoutRepository
import com.windowhyun.health.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 운동 진행 화면 상태. */
data class WorkoutSessionUiState(
    val workout: Workout? = null,
    /** 종목별 "지난 운동" 세트 목록. */
    val lastPerformance: Map<Long, List<WorkoutSet>> = emptyMap(),
    val settings: AppSettings = AppSettings(),
    val elapsedSeconds: Long = 0,
    val loading: Boolean = true,
)

/** 화면 전환이 필요한 일회성 이벤트. */
sealed interface WorkoutSessionEvent {
    data class Finished(val workoutId: Long) : WorkoutSessionEvent
    data object Discarded : WorkoutSessionEvent
}

/**
 * 헬스 운동 진행 화면의 상태와 동작을 담당한다.
 *
 * 모든 변경은 즉시 DB 에 반영된다. 화면은 DB Flow 만 보고 그린다.
 */
@HiltViewModel
class WorkoutSessionViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val restTimerNotifier: RestTimerNotifier,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val workoutId: Long = savedStateHandle[Routes.ARG_WORKOUT_ID] ?: 0L

    private val lastPerformance = MutableStateFlow<Map<Long, List<WorkoutSet>>>(emptyMap())
    private val elapsedSeconds = MutableStateFlow(0L)

    private val _restTimer = MutableStateFlow(RestTimerState())
    val restTimer: StateFlow<RestTimerState> = _restTimer.asStateFlow()

    private val _events = MutableSharedFlow<WorkoutSessionEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    /** 운동 추가 시트에서 사용할 전체 종목. */
    val exercises: StateFlow<List<Exercise>> = exerciseRepository.observeExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var restTimerJob: Job? = null
    private var tickerJob: Job? = null

    val uiState: StateFlow<WorkoutSessionUiState> = combine(
        workoutRepository.observeWorkoutDetail(workoutId),
        lastPerformance,
        settingsRepository.settings,
        elapsedSeconds,
    ) { workout, last, settings, elapsed ->
        WorkoutSessionUiState(
            workout = workout,
            lastPerformance = last,
            settings = settings,
            elapsedSeconds = elapsed,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutSessionUiState())

    init {
        loadLastPerformance()
        startElapsedTicker()
    }

    /** 세션에 들어 있는 각 종목의 지난 기록을 한 번 읽어 둔다. */
    private fun loadLastPerformance() {
        viewModelScope.launch {
            val workout = workoutRepository.getWorkout(workoutId) ?: return@launch
            val map = workout.exercises.associate { record ->
                record.exercise.id to workoutRepository.getLastPerformance(record.exercise.id, workoutId)
            }
            lastPerformance.value = map
        }
    }

    private fun startElapsedTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            val workout = workoutRepository.getWorkout(workoutId)
            val startTime = workout?.startTime ?: System.currentTimeMillis()
            while (true) {
                elapsedSeconds.value = ((System.currentTimeMillis() - startTime) / 1000).coerceAtLeast(0)
                delay(1000)
            }
        }
    }

    // ---------- 세트 조작 ----------

    /** 중량/횟수만 갱신(완료 상태는 그대로). */
    fun updateSetValues(set: WorkoutSet, weightKg: Double, reps: Int) {
        viewModelScope.launch {
            workoutRepository.setCompleted(set.id, weightKg, reps, set.completed)
        }
    }

    /**
     * 세트 완료 버튼. 완료로 바뀌면 설정에 따라 휴식 타이머를 자동 시작한다.
     * [restSeconds] 는 운동별 휴식시간(없으면 앱 기본값).
     */
    fun toggleSetCompleted(set: WorkoutSet, weightKg: Double, reps: Int, restSeconds: Int?) {
        viewModelScope.launch {
            val nowCompleted = !set.completed
            workoutRepository.setCompleted(set.id, weightKg, reps, nowCompleted)
            val settings = settingsRepository.current()
            if (nowCompleted && settings.restTimerAutoStart) {
                startRestTimer(restSeconds ?: settings.defaultRestSeconds)
            }
        }
    }

    fun addSet(workoutExerciseId: Long) {
        viewModelScope.launch { workoutRepository.addSet(workoutExerciseId) }
    }

    fun removeSet(setId: Long) {
        viewModelScope.launch { workoutRepository.removeSet(setId) }
    }

    fun addExercise(exerciseId: Long) {
        viewModelScope.launch {
            workoutRepository.addExerciseToWorkout(workoutId, exerciseId)
            lastPerformance.update { current ->
                current + (exerciseId to workoutRepository.getLastPerformance(exerciseId, workoutId))
            }
        }
    }

    /** 사전에 없는 종목을 즉석에서 만들어 이번 세션에 바로 넣는다. */
    fun createAndAddExercise(name: String, category: ExerciseCategory, bodyPart: BodyPart) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val exerciseId = exerciseRepository.addExercise(
                com.windowhyun.health.domain.model.Exercise(
                    id = 0,
                    name = trimmed,
                    category = category,
                    bodyPart = bodyPart,
                ),
            )
            workoutRepository.addExerciseToWorkout(workoutId, exerciseId)
            lastPerformance.update { current ->
                current + (exerciseId to workoutRepository.getLastPerformance(exerciseId, workoutId))
            }
        }
    }

    fun removeExercise(workoutExerciseId: Long) {
        viewModelScope.launch { workoutRepository.removeWorkoutExercise(workoutExerciseId) }
    }

    // ---------- 휴식 타이머 ----------

    fun startRestTimer(seconds: Int) {
        val total = seconds.coerceAtLeast(1)
        restTimerJob?.cancel()
        restTimerNotifier.cancel()
        _restTimer.value = RestTimerState(visible = true, totalSeconds = total, remainingSeconds = total)
        restTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val state = _restTimer.value
                if (!state.visible) return@launch
                if (state.paused) continue
                val remaining = state.remainingSeconds - 1
                if (remaining <= 0) {
                    _restTimer.value = state.copy(remainingSeconds = 0)
                    restTimerNotifier.notifyRestFinished(settingsRepository.current().vibrationEnabled)
                    delay(2000)
                    _restTimer.update { if (it.remainingSeconds <= 0) it.copy(visible = false) else it }
                    return@launch
                }
                _restTimer.value = state.copy(remainingSeconds = remaining)
            }
        }
    }

    fun adjustRestTimer(deltaSeconds: Int) = _restTimer.update { state ->
        if (!state.visible) return@update state
        val remaining = (state.remainingSeconds + deltaSeconds).coerceAtLeast(0)
        state.copy(
            remainingSeconds = remaining,
            totalSeconds = maxOf(state.totalSeconds, remaining),
        )
    }

    fun toggleRestPause() = _restTimer.update { it.copy(paused = !it.paused) }

    fun stopRestTimer() {
        restTimerJob?.cancel()
        restTimerJob = null
        restTimerNotifier.cancel()
        _restTimer.value = RestTimerState()
    }

    // ---------- 세션 종료 ----------

    fun finishWorkout() {
        viewModelScope.launch {
            stopRestTimer()
            workoutRepository.finishWorkout(workoutId)
            _events.emit(WorkoutSessionEvent.Finished(workoutId))
        }
    }

    fun discardWorkout() {
        viewModelScope.launch {
            stopRestTimer()
            workoutRepository.discardWorkout(workoutId)
            _events.emit(WorkoutSessionEvent.Discarded)
        }
    }

    override fun onCleared() {
        super.onCleared()
        restTimerJob?.cancel()
        tickerJob?.cancel()
    }
}
