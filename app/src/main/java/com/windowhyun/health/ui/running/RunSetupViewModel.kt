package com.windowhyun.health.ui.running

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.data.tracking.ActiveCourse
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.model.Course
import com.windowhyun.health.domain.repository.CourseRepository
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.RunGoal
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.RunInterval
import com.windowhyun.health.domain.model.RunTrackingState
import com.windowhyun.health.domain.repository.LocationTracker
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.domain.repository.StepCounter
import com.windowhyun.health.service.RunServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 러닝 시작 화면에서 고르는 값들. */
data class RunSetupUiState(
    val goalType: RunGoalType = RunGoalType.FREE,
    /** 목표 거리(km). */
    val goalDistanceKm: Double = 5.0,
    /** 목표 시간(분). */
    val goalDurationMinutes: Int = 30,
    /** 달리기/걷기를 번갈아 하는 인터벌 러닝. 끄면 일반 러닝. */
    val intervalEnabled: Boolean = false,
    val interval: RunInterval = RunInterval(),
    val hasLocationPermission: Boolean = false,
    val gpsEnabled: Boolean = false,
    /** 시작 화면에서 미리 받아 본 GPS 정확도. null 이면 아직 첫 신호를 못 받았다. */
    val gpsAccuracyMeters: Float? = null,
    val stepSensorAvailable: Boolean = false,
    val stepPermissionGranted: Boolean = false,
    /** 저장해 둔 코스들과, 따라갈 코스(null 이면 코스 없이 달린다). */
    val courses: List<Course> = emptyList(),
    val selectedCourseId: Long? = null,
    val settings: AppSettings = AppSettings(),
    val tracking: RunTrackingState = RunTrackingState(),
) {
    val canStart: Boolean get() = hasLocationPermission && gpsEnabled && !tracking.isActive

    val selectedCourse: Course? get() = courses.firstOrNull { it.id == selectedCourseId }

    /** 인터벌을 켰을 때만 계획을 돌려준다. */
    fun toInterval(): RunInterval? = interval.takeIf { intervalEnabled }

    fun toGoal(): RunGoal = when (goalType) {
        RunGoalType.FREE -> RunGoal(RunGoalType.FREE, 0.0)
        RunGoalType.DISTANCE -> RunGoal(RunGoalType.DISTANCE, goalDistanceKm * 1000.0)
        RunGoalType.DURATION -> RunGoal(RunGoalType.DURATION, goalDurationMinutes * 60.0)
    }
}

@HiltViewModel
class RunSetupViewModel @Inject constructor(
    private val locationTracker: LocationTracker,
    private val stepCounter: StepCounter,
    private val runServiceController: RunServiceController,
    runTracker: RunTracker,
    settingsRepository: SettingsRepository,
    private val courseRepository: CourseRepository,
    private val activeCourse: ActiveCourse,
) : ViewModel() {

    private val selection = MutableStateFlow(RunSetupUiState())

    val uiState: StateFlow<RunSetupUiState> = combine(
        selection,
        settingsRepository.settings,
        runTracker.state,
        courseRepository.observeAll(),
    ) { current, settings, tracking, courses ->
        // 지운 코스를 고른 채로 남겨 두지 않는다.
        current.copy(
            settings = settings,
            tracking = tracking,
            courses = courses,
            selectedCourseId = current.selectedCourseId?.takeIf { id -> courses.any { it.id == id } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RunSetupUiState())

    init {
        refreshLocationStatus()
    }

    /** 화면에 다시 들어올 때마다 권한과 GPS 상태를 다시 본다. */
    fun refreshLocationStatus() = selection.update {
        it.copy(
            hasLocationPermission = locationTracker.hasLocationPermission(),
            gpsEnabled = locationTracker.isLocationAvailable(),
            stepSensorAvailable = stepCounter.isAvailable(),
            stepPermissionGranted = stepCounter.hasPermission(),
        )
    }

    /**
     * 시작 화면이 보이는 동안 GPS 를 미리 켜 둔다. GPS 는 처음 켜서 신호를 잡기까지 10~40초가 걸리는데,
     * 눌러서 시작한 뒤에 기다리면 그 시간이 러닝 시간에 들어가고 빨간 경고도 뜬다. 화면이 보이는 동안만 받고,
     * 화면을 벗어나면(수집이 취소되면) 멈춘다. 러닝 중에는 서비스가 받으므로 하지 않는다.
     */
    suspend fun watchGps() {
        if (uiState.value.tracking.isActive || !locationTracker.hasLocationPermission() || !locationTracker.isLocationAvailable()) {
            selection.update { it.copy(gpsAccuracyMeters = null) }
            return
        }
        try {
            locationTracker.locationUpdates(WARM_UP_INTERVAL_MILLIS)
                .collect { sample -> selection.update { it.copy(gpsAccuracyMeters = sample.accuracyMeters) } }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 미리 보는 것일 뿐이다. 실패해도 시작은 막지 않는다(시작하면 서비스가 원인을 화면에 알린다).
        } finally {
            selection.update { it.copy(gpsAccuracyMeters = null) }
        }
    }

    fun selectCourse(id: Long?) = selection.update { it.copy(selectedCourseId = id) }

    fun deleteCourse(id: Long) {
        viewModelScope.launch { courseRepository.delete(id) }
    }

    fun setGoalType(type: RunGoalType) = selection.update { it.copy(goalType = type) }

    fun changeGoalDistance(deltaKm: Double) = selection.update {
        it.copy(goalDistanceKm = (it.goalDistanceKm + deltaKm).coerceIn(1.0, 100.0))
    }

    fun changeGoalDuration(deltaMinutes: Int) = selection.update {
        it.copy(goalDurationMinutes = (it.goalDurationMinutes + deltaMinutes).coerceIn(5, 600))
    }

    fun setIntervalEnabled(enabled: Boolean) = selection.update { it.copy(intervalEnabled = enabled) }

    fun changeIntervalRun(deltaSeconds: Int) = selection.update {
        it.copy(interval = it.interval.copy(runSeconds = (it.interval.runSeconds + deltaSeconds).coerceIn(RunInterval.MIN_SECONDS, RunInterval.MAX_SECONDS)))
    }

    fun changeIntervalWalk(deltaSeconds: Int) = selection.update {
        it.copy(interval = it.interval.copy(walkSeconds = (it.interval.walkSeconds + deltaSeconds).coerceIn(RunInterval.MIN_SECONDS, RunInterval.MAX_SECONDS)))
    }

    fun changeIntervalRounds(delta: Int) = selection.update {
        it.copy(interval = it.interval.copy(rounds = (it.interval.rounds + delta).coerceIn(RunInterval.MIN_ROUNDS, RunInterval.MAX_ROUNDS)))
    }

    /** Foreground Service 를 띄워 기록을 시작한다. */
    fun startRun() {
        val state = uiState.value
        if (!state.canStart) return
        // 서비스가 시작할 때 이 코스를 읽어 간다.
        activeCourse.select(state.selectedCourse)
        runServiceController.start(state.toGoal(), state.toInterval())
    }

    private companion object {
        const val WARM_UP_INTERVAL_MILLIS = 2_000L
    }
}
