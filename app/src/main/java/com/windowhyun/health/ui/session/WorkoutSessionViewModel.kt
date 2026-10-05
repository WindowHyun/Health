package com.windowhyun.health.ui.session

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.notification.RestTimerNotifier
import com.windowhyun.health.domain.usecase.SupersetGroups
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.model.WorkoutSet
import com.windowhyun.health.domain.repository.ExerciseRepository
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.domain.repository.WorkoutRepository
import com.windowhyun.health.ui.gym.ExerciseManager
import com.windowhyun.health.ui.gym.RepositoryExerciseManager
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
class WorkoutSessionViewModel internal constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val restTimerNotifier: RestTimerNotifier,
    savedStateHandle: SavedStateHandle,
    /**
     * 휴식 타이머가 쓰는 시계(밀리초). 기기가 잠든 시간도 세는 시계여야 한다.
     * 테스트에서는 직접 넣는다.
     */
    private val clock: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        workoutRepository: WorkoutRepository,
        exerciseRepository: ExerciseRepository,
        settingsRepository: SettingsRepository,
        restTimerNotifier: RestTimerNotifier,
        savedStateHandle: SavedStateHandle,
    ) : this(
        workoutRepository,
        exerciseRepository,
        settingsRepository,
        restTimerNotifier,
        savedStateHandle,
        SystemClock::elapsedRealtime,
    )

    private val workoutId: Long = savedStateHandle[Routes.ARG_WORKOUT_ID] ?: 0L

    private val lastPerformance = MutableStateFlow<Map<Long, List<WorkoutSet>>>(emptyMap())
    private val elapsedSeconds = MutableStateFlow(0L)

    private val _restTimer = MutableStateFlow(RestTimerState())
    val restTimer: StateFlow<RestTimerState> = _restTimer.asStateFlow()

    private val _events = MutableSharedFlow<WorkoutSessionEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    /** 운동 추가 시트에서 직접 만든 종목을 고치고 지우는 창구. */
    val exerciseManager: ExerciseManager = RepositoryExerciseManager(exerciseRepository)

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

    /** 중량/횟수/시간만 갱신(완료 상태는 그대로). */
    fun updateSetValues(set: WorkoutSet, weightKg: Double, reps: Int, durationSeconds: Int) {
        viewModelScope.launch {
            workoutRepository.setCompleted(set.id, weightKg, reps, set.completed, durationSeconds)
        }
    }

    /** 세트 번호를 누르면 종류가 순환한다(본세트 → 워밍업 → 드롭 → 실패). */
    fun cycleSetType(set: WorkoutSet) {
        viewModelScope.launch {
            workoutRepository.setSetType(set.id, set.setType.next())
        }
    }

    /**
     * 세트 완료 버튼. 완료로 바뀌면 설정에 따라 휴식 타이머를 자동 시작한다.
     * [restSeconds] 는 운동별 휴식시간(없으면 앱 기본값).
     */
    fun toggleSetCompleted(
        set: WorkoutSet,
        weightKg: Double,
        reps: Int,
        durationSeconds: Int,
        restSeconds: Int?,
    ) {
        viewModelScope.launch {
            val nowCompleted = !set.completed
            workoutRepository.setCompleted(set.id, weightKg, reps, nowCompleted, durationSeconds)
            val settings = settingsRepository.current()
            if (nowCompleted && settings.restTimerAutoStart && restsAfter(set.id)) {
                startRestTimer(restSeconds ?: settings.defaultRestSeconds)
            }
        }
    }

    /**
     * 이 세트를 마친 뒤 쉬어야 하는가. 슈퍼셋 묶음 중간 운동이면 쉬지 않고 바로 다음 운동으로 간다.
     * 묶음의 마지막 운동(또는 묶음이 아닌 운동)에서만 휴식 타이머가 돈다.
     */
    private suspend fun restsAfter(setId: Long): Boolean {
        val exercises = workoutRepository.getWorkout(workoutId)?.exercises ?: return true
        val index = exercises.indexOfFirst { record -> record.sets.any { it.id == setId } }
        if (index < 0) return true
        return SupersetGroups.restAfter(exercises.map { it.supersetGroup }, index)
    }

    fun linkSuperset(workoutExerciseId: Long) {
        viewModelScope.launch { workoutRepository.linkSupersetWithPrevious(workoutExerciseId) }
    }

    fun unlinkSuperset(workoutExerciseId: Long) {
        viewModelScope.launch { workoutRepository.unlinkSuperset(workoutExerciseId) }
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
    fun createAndAddExercise(
        name: String,
        category: ExerciseCategory,
        bodyPart: BodyPart,
        trackingType: ExerciseTrackingType,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val exerciseId = exerciseRepository.addExercise(
                com.windowhyun.health.domain.model.Exercise(
                    id = 0,
                    name = trimmed,
                    category = category,
                    bodyPart = bodyPart,
                    trackingType = trackingType,
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

    // 남은 시간을 1초씩 빼며 세면 화면이 꺼져 기기가 잠든 동안 멈춘다. 끝나는 시각을 정해 두고
    // 남은 시간을 시계에서 매번 다시 계산한다. 알림은 시스템 알람이 시각에 맞춰 울린다.
    private var restEndsAt = 0L
    private var restPausedLeftMillis = 0L
    private var restDoneSince: Long? = null

    fun startRestTimer(seconds: Int) {
        val total = seconds.coerceAtLeast(1)
        restTimerNotifier.cancel()
        restEndsAt = clock() + total * 1_000L
        restDoneSince = null
        _restTimer.value = RestTimerState(visible = true, totalSeconds = total, remainingSeconds = total)
        scheduleRestAlarm()
        runRestLoop()
    }

    /** 화면에 보이는 숫자만 갱신한다. 알림과 진동은 알람이 맡는다. */
    private fun runRestLoop() {
        restTimerJob?.cancel()
        restTimerJob = viewModelScope.launch {
            while (true) {
                delay(REST_TICK_MILLIS)
                val state = _restTimer.value
                if (!state.visible) return@launch
                if (state.paused) continue
                val now = clock()
                val leftMillis = restEndsAt - now
                if (leftMillis > 0) {
                    restDoneSince = null
                    _restTimer.update { it.copy(remainingSeconds = ceilSeconds(leftMillis)) }
                } else {
                    // 끝난 뒤에도 잠깐 0 을 보여 준다. 그사이 +15초를 누르면 다시 센다.
                    val since = restDoneSince ?: now.also { restDoneSince = it }
                    _restTimer.update { it.copy(remainingSeconds = 0) }
                    if (now - since >= REST_AFTERGLOW_MILLIS) {
                        _restTimer.update { it.copy(visible = false) }
                        return@launch
                    }
                }
            }
        }
    }

    /** 끝나는 시각에 알림이 울리도록 알람을 맞춘다(이미 맞춘 것은 바뀐다). */
    private fun scheduleRestAlarm() {
        viewModelScope.launch {
            val vibrate = settingsRepository.current().vibrationEnabled
            val state = _restTimer.value
            // 그사이 건너뛰거나 멈췄다면 맞추지 않는다.
            if (!state.visible || state.paused) return@launch
            restTimerNotifier.scheduleFinish(restEndsAt - clock(), vibrate)
        }
    }

    fun adjustRestTimer(deltaSeconds: Int) {
        val state = _restTimer.value
        if (!state.visible) return
        val deltaMillis = deltaSeconds * 1_000L
        val now = clock()
        val remainingSeconds: Int
        if (state.paused) {
            restPausedLeftMillis = (restPausedLeftMillis + deltaMillis).coerceAtLeast(0)
            remainingSeconds = ceilSeconds(restPausedLeftMillis)
        } else {
            // 이미 끝난 뒤라면 지금부터 센다. 끝난 시각에 더하면 곧바로 끝나 버린다.
            restEndsAt = (maxOf(restEndsAt, now) + deltaMillis).coerceAtLeast(now)
            restDoneSince = null
            remainingSeconds = ceilSeconds(restEndsAt - now)
            // 이미 뜬 "휴식 끝" 알림은 치우고, 알람은 새 시각으로 다시 맞춘다.
            restTimerNotifier.cancel()
            scheduleRestAlarm()
        }
        _restTimer.update {
            it.copy(
                remainingSeconds = remainingSeconds,
                totalSeconds = maxOf(it.totalSeconds, remainingSeconds),
            )
        }
    }

    fun toggleRestPause() {
        val state = _restTimer.value
        // 이미 끝난 타이머는 멈출 것이 없다.
        if (!state.visible || state.remainingSeconds <= 0) return
        val now = clock()
        if (!state.paused) {
            restPausedLeftMillis = (restEndsAt - now).coerceAtLeast(0)
            restTimerNotifier.cancel()
            _restTimer.update { it.copy(paused = true) }
        } else {
            restEndsAt = now + restPausedLeftMillis
            restDoneSince = null
            _restTimer.update { it.copy(paused = false) }
            scheduleRestAlarm()
        }
    }

    fun stopRestTimer() {
        restTimerJob?.cancel()
        restTimerJob = null
        restTimerNotifier.cancel()
        _restTimer.value = RestTimerState()
    }

    private fun ceilSeconds(millis: Long): Int = ((millis + 999) / 1_000).toInt()

    // ---------- 세션 종료 ----------

    /** 종료 처리 중인지. 두 번 눌러 결과 화면이 두 번 열리지 않게 한다. */
    private var finishing = false

    fun finishWorkout() {
        if (finishing) return
        finishing = true
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

    private companion object {
        const val REST_TICK_MILLIS = 250L
        const val REST_AFTERGLOW_MILLIS = 2_000L
    }
}
