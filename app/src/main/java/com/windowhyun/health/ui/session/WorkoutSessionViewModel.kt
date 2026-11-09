package com.windowhyun.health.ui.session

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.core.notification.RestTimerNotifier
import com.windowhyun.health.domain.usecase.CurrentSetFinder
import com.windowhyun.health.domain.usecase.SetCarryOver
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
import com.windowhyun.health.shared.WatchCommand
import com.windowhyun.health.shared.WorkoutSnapshot
import com.windowhyun.health.wear.toSnapshot
import com.windowhyun.health.ui.navigation.Routes
import com.windowhyun.health.wear.WatchLink
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** 세트를 지웠다. 화면은 "실행 취소" 를 눌러 되돌릴 수 있는 안내를 잠깐 보여 준다. */
    data object SetRemoved : WorkoutSessionEvent
}

/**
 * 헬스 운동 진행 화면의 상태와 동작을 담당한다.
 *
 * 모든 변경은 즉시 DB 에 반영된다. 화면은 DB Flow 만 보고 그린다.
 */
@HiltViewModel
@OptIn(kotlinx.coroutines.FlowPreview::class)
class WorkoutSessionViewModel internal constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val restTimerNotifier: RestTimerNotifier,
    private val savedStateHandle: SavedStateHandle,
    /**
     * 휴식 타이머가 쓰는 시계(밀리초). 기기가 잠든 시간도 세는 시계여야 한다.
     * 테스트에서는 직접 넣는다.
     */
    private val clock: () -> Long,
    /** 휴식 타이머를 시계에 보여 주고, 시계에서 온 조작을 받는 다리. */
    private val watchLink: WatchLink = WatchLink(),
) : ViewModel() {

    @Inject
    constructor(
        workoutRepository: WorkoutRepository,
        exerciseRepository: ExerciseRepository,
        settingsRepository: SettingsRepository,
        restTimerNotifier: RestTimerNotifier,
        savedStateHandle: SavedStateHandle,
        watchLink: WatchLink,
    ) : this(
        workoutRepository,
        exerciseRepository,
        settingsRepository,
        restTimerNotifier,
        savedStateHandle,
        SystemClock::elapsedRealtime,
        watchLink,
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
        watchLink.attachSession()
        loadLastPerformance()
        startElapsedTicker()
        listenToWatch()
        publishWorkoutToWatch()
    }

    /** 지금 할 세트를 시계에 알린다. 세트를 끝내거나 값이 바뀔 때마다 새로 보낸다. */
    private fun publishWorkoutToWatch() {
        viewModelScope.launch {
            combine(
                workoutRepository.observeWorkoutDetail(workoutId),
                settingsRepository.settings.map { it.weightUnit == WeightUnit.LB }.distinctUntilChanged(),
            ) { workout, useLb -> workout?.toSnapshot(useLb) ?: WorkoutSnapshot.None }
                // 무게를 한 글자씩 칠 때마다 시계로 보내지 않는다. 멈춘 값만 보낸다.
                .debounce(WATCH_PUBLISH_DEBOUNCE_MILLIS)
                .collect { snapshot ->
                    if (snapshot.active) watchLink.publishWorkout(snapshot) else watchLink.clearWorkout()
                }
        }
        viewModelScope.launch {
            watchLink.setCompletions.collect { setId -> completeSetFromWatch(setId) }
        }
    }

    /**
     * 시계에서 누른 세트 완료. 폰에서 완료 버튼을 누른 것과 똑같이 처리해, 쉬는 시간도 그대로 시작된다.
     * 시계가 보고 있던 세트가 지금 할 세트가 아니면(그사이 폰에서 끝냈다) 아무것도 하지 않는다.
     */
    private suspend fun completeSetFromWatch(setId: Long) {
        val workout = workoutRepository.getWorkout(workoutId) ?: return
        val current = CurrentSetFinder.find(workout.exercises) ?: return
        val set = current.set
        if (set.id != setId) return
        val hasValue = when (current.record.exercise.trackingType) {
            ExerciseTrackingType.TIME -> set.durationSeconds > 0
            else -> set.reps > 0
        }
        if (!hasValue) return
        toggleSetCompleted(set, set.weightKg, set.reps, set.durationSeconds, current.record.restSeconds)
    }

    /** 시계에서 누른 휴식 타이머 버튼을 폰 화면에서 누른 것과 똑같이 처리한다. */
    private fun listenToWatch() {
        viewModelScope.launch {
            watchLink.restCommands.collect { command ->
                when (command) {
                    WatchCommand.REST_SKIP -> stopRestTimer()
                    WatchCommand.REST_ADD -> adjustRestTimer(REST_WATCH_STEP_SECONDS)
                    WatchCommand.REST_SUB -> adjustRestTimer(-REST_WATCH_STEP_SECONDS)
                    WatchCommand.REST_TOGGLE_PAUSE -> toggleRestPause()
                    else -> Unit
                }
            }
        }
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

    // 글자를 칠 때마다 값이 들어오므로, 앞 입력의 저장이 끝난 뒤에 다음 것을 처리해야 뒤 세트가 순서대로 따라간다.
    private val setEditLock = Mutex()

    /**
     * 중량/횟수/시간만 갱신(완료 상태는 그대로).
     * 아직 안 한 세트의 무게를 바꾸면, 같은 무게였던 뒤 세트도 같이 바뀐다([SetCarryOver]).
     */
    fun updateSetValues(set: WorkoutSet, weightKg: Double, reps: Int, durationSeconds: Int) {
        viewModelScope.launch {
            setEditLock.withLock {
                // 화면에서 넘어온 set 은 한 글자 전 값일 수 있어서 저장된 값을 새로 읽는다.
                val stored = workoutRepository.getWorkout(workoutId)
                    ?.exercises?.firstOrNull { record -> record.sets.any { it.id == set.id } }
                    ?.sets
                val before = stored?.firstOrNull { it.id == set.id } ?: set
                workoutRepository.setCompleted(set.id, weightKg, reps, before.completed, durationSeconds)
                SetCarryOver.followers(stored.orEmpty(), before, weightKg).forEach { later ->
                    workoutRepository.setCompleted(later.id, weightKg, later.reps, false, later.durationSeconds)
                }
            }
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

    /** 방금 지운 세트. 되돌리려면 어느 종목의 몇 번째였는지와 값을 알아야 한다. */
    private var lastRemovedSet: Pair<Long, WorkoutSet>? = null

    fun removeSet(setId: Long) {
        viewModelScope.launch {
            // 지우기 전에 DB 에서 원래 모습을 읽어 둔다(화면 상태가 아니라 저장된 값을 되돌린다).
            val owner = workoutRepository.getWorkout(workoutId)?.exercises
                ?.firstOrNull { record -> record.sets.any { it.id == setId } } ?: return@launch
            val set = owner.sets.first { it.id == setId }
            workoutRepository.removeSet(setId)
            lastRemovedSet = owner.id to set
            _events.emit(WorkoutSessionEvent.SetRemoved)
        }
    }

    /** 방금 지운 세트를 되살린다. 땀 젖은 손으로 X 를 잘못 눌러도 복구할 수 있게 한다. */
    fun undoRemoveSet() {
        val (workoutExerciseId, set) = lastRemovedSet ?: return
        lastRemovedSet = null
        viewModelScope.launch { workoutRepository.restoreSet(workoutExerciseId, set) }
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

    // 위의 상태 변수가 모두 만들어진 다음에 복원한다(그 전에 하면 초기값이 덮어쓴다).
    init {
        restoreRest()
    }

    fun startRestTimer(seconds: Int) {
        val total = seconds.coerceAtLeast(1)
        restTimerNotifier.cancel()
        restEndsAt = clock() + total * 1_000L
        restDoneSince = null
        _restTimer.value = RestTimerState(visible = true, totalSeconds = total, remainingSeconds = total)
        scheduleRestAlarm()
        runRestLoop()
        publishRestToWatch()
        persistRest()
    }

    /**
     * 휴식 타이머를 저장해 둔다. 앱이 백그라운드에서 정리돼도(알람은 남아 알림이 울린다) 돌아오면 타이머 바가 이어진다.
     * 기기를 다시 켠 뒤에는 시계가 처음부터 다시 세므로, 말이 안 되는 값은 복원하지 않는다.
     */
    private fun persistRest() {
        val state = _restTimer.value
        if (!state.visible) {
            clearSavedRest()
            return
        }
        savedStateHandle[KEY_REST_TOTAL] = state.totalSeconds
        savedStateHandle[KEY_REST_ENDS_AT] = restEndsAt
        savedStateHandle[KEY_REST_PAUSED] = state.paused
        savedStateHandle[KEY_REST_PAUSED_LEFT] = restPausedLeftMillis
    }

    private fun restoreRest() {
        val total = savedStateHandle.get<Int>(KEY_REST_TOTAL) ?: return
        val endsAt = savedStateHandle.get<Long>(KEY_REST_ENDS_AT) ?: return
        val paused = savedStateHandle.get<Boolean>(KEY_REST_PAUSED) ?: false
        if (paused) {
            restPausedLeftMillis = (savedStateHandle.get<Long>(KEY_REST_PAUSED_LEFT) ?: 0L).coerceIn(0, total * 1_000L)
            _restTimer.value = RestTimerState(visible = true, totalSeconds = total, remainingSeconds = ceilSeconds(restPausedLeftMillis), paused = true)
            runRestLoop()
            return
        }
        val left = endsAt - clock()
        // 이미 오래전에 끝났거나, 기기를 다시 켜서 시계가 되돌아간 값이면 버린다.
        if (left < -REST_AFTERGLOW_MILLIS || left > total * 1_000L) {
            clearSavedRest()
            return
        }
        restEndsAt = endsAt
        restDoneSince = null
        _restTimer.value = RestTimerState(visible = true, totalSeconds = total, remainingSeconds = ceilSeconds(left.coerceAtLeast(0)))
        if (left > 0) scheduleRestAlarm()
        runRestLoop()
    }

    private fun clearSavedRest() {
        listOf(KEY_REST_TOTAL, KEY_REST_ENDS_AT, KEY_REST_PAUSED, KEY_REST_PAUSED_LEFT).forEach { savedStateHandle.remove<Any>(it) }
    }

    /** 지금 휴식 타이머 상태를 시계에 알린다. 타이머가 없으면 "없음"을 알려 시계 화면을 치운다. */
    private fun publishRestToWatch() {
        val state = _restTimer.value
        if (!state.visible) {
            watchLink.clearRest()
            return
        }
        val remaining = if (state.paused) restPausedLeftMillis else restEndsAt - clock()
        watchLink.publishRest(state.totalSeconds, remaining, state.paused)
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
                        publishRestToWatch()
                        persistRest()
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
        publishRestToWatch()
        persistRest()
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
        publishRestToWatch()
        persistRest()
    }

    fun stopRestTimer() {
        restTimerJob?.cancel()
        restTimerJob = null
        restTimerNotifier.cancel()
        _restTimer.value = RestTimerState()
        publishRestToWatch()
        persistRest()
    }

    private fun ceilSeconds(millis: Long): Int = ((millis + 999) / 1_000).toInt()

    // ---------- 세션 종료 ----------

    /** 종료 처리 중인지. 두 번 눌러 결과 화면이 두 번 열리지 않게 한다. */
    private var finishing = false

    /** 알림 권한을 물어봤다고 기억한다(다시 묻지 않는다). */
    fun markNotificationPrompted() {
        viewModelScope.launch { settingsRepository.update { it.copy(notificationPromptShown = true) } }
    }

    /**
     * 운동을 마친다. [completeUnchecked] 가 true 면, 값(무게·횟수·시간)이 들어 있는데 체크하지 않은 세트를 먼저 완료로 바꾼다
     * (마지막 세트를 치고 체크를 깜빡한 채 종료해도 기록이 사라지지 않게). 값이 비어 있는 세트는 그대로 버린다.
     */
    fun finishWorkout(completeUnchecked: Boolean = false) {
        if (finishing) return
        finishing = true
        viewModelScope.launch {
            stopRestTimer()
            lastRemovedSet = null
            if (completeUnchecked) {
                workoutRepository.getWorkout(workoutId)?.exercises.orEmpty()
                    .flatMap { it.sets }
                    .filter { !it.completed && it.hasValues }
                    .forEach { workoutRepository.setCompleted(it.id, it.weightKg, it.reps, true, it.durationSeconds) }
            }
            // 한 세트도 완료하지 않았다면 남길 기록이 없다. 빈 기록이 저장되어 주간 횟수에 들어가지 않게 버린다.
            val anythingDone = workoutRepository.getWorkout(workoutId)?.exercises?.any { it.completedSets.isNotEmpty() } == true
            if (!anythingDone) {
                workoutRepository.discardWorkout(workoutId)
                _events.emit(WorkoutSessionEvent.Discarded)
                return@launch
            }
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
        // 화면이 사라지면 타이머도 멈추므로 시계에 남은 휴식 화면과 세트도 치운다.
        watchLink.clearRest()
        watchLink.clearWorkout()
        // 화면이 없어진 뒤에는 앱 전역 다리가 시계와 이어 준다.
        watchLink.detachSession()
    }

    private companion object {
        const val REST_TICK_MILLIS = 250L
        const val KEY_REST_TOTAL = "rest_total_seconds"
        const val KEY_REST_ENDS_AT = "rest_ends_at"
        const val KEY_REST_PAUSED = "rest_paused"
        const val KEY_REST_PAUSED_LEFT = "rest_paused_left"
        const val REST_AFTERGLOW_MILLIS = 2_000L

        /** 세트 값이 이만큼 가만히 있어야 시계로 보낸다. */
        const val WATCH_PUBLISH_DEBOUNCE_MILLIS = 400L

        /** 시계의 +15초 / -15초 버튼이 더하고 빼는 만큼. */
        const val REST_WATCH_STEP_SECONDS = 15
    }
}
