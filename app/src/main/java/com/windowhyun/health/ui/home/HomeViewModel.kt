package com.windowhyun.health.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.core.util.currentDateFlow
import com.windowhyun.health.core.util.endOfWeek
import com.windowhyun.health.core.util.startOfWeek
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.RunTrackingState
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.repository.RoutineRepository
import com.windowhyun.health.domain.repository.RunRepository
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** 이번 주 요약 지표. */
data class WeeklySummary(
    val workoutCount: Int = 0,
    val runDistanceMeters: Double = 0.0,
    val totalDurationSeconds: Long = 0,
)

/** 홈 화면 상태. 상세 통계는 기록 탭에 두고 홈에는 최소한만 보여 준다. */
data class HomeUiState(
    val today: LocalDate = LocalDate.now(),
    val weekly: WeeklySummary = WeeklySummary(),
    val todayRoutines: List<Routine> = emptyList(),
    val recentWorkouts: List<Workout> = emptyList(),
    val recentRuns: List<Run> = emptyList(),
    val activeWorkout: Workout? = null,
    /** 기록 중인 러닝이 있으면 홈에서도 바로 들어갈 수 있게 한다. */
    val activeRun: RunTrackingState = RunTrackingState(),
    val settings: AppSettings = AppSettings(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    private val runRepository: RunRepository,
    private val settingsRepository: SettingsRepository,
    private val runTracker: RunTracker,
) : ViewModel() {

    /** 운동을 시작하면 화면 전환에 쓸 workoutId 를 흘려 보낸다. */
    private val _startedWorkoutId = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val startedWorkoutId = _startedWorkoutId.asSharedFlow()

    // 날짜는 고정하지 않는다. 앱을 켜 둔 채 자정을 넘기면 주간 범위와
    // "오늘의 루틴"이 어제 기준으로 남기 때문이다.
    private val todayFlow = currentDateFlow()

    private fun weeklyFlow(today: LocalDate): Flow<WeeklySummary> {
        val weekStart = today.startOfWeek()
        val weekEnd = today.endOfWeek()
        return combine(
            workoutRepository.observeWorkoutCountBetween(weekStart, weekEnd),
            runRepository.observeDistanceBetween(weekStart, weekEnd),
            workoutRepository.observeWorkoutDurationBetween(weekStart, weekEnd),
            runRepository.observeDurationBetween(weekStart, weekEnd),
        ) { count, distance, gymSeconds, runSeconds ->
            WeeklySummary(
                workoutCount = count,
                runDistanceMeters = distance,
                totalDurationSeconds = gymSeconds + runSeconds,
            )
        }
    }

    private val recentFlow: Flow<Triple<List<Workout>, List<Run>, Workout?>> = combine(
        workoutRepository.observeRecentWorkouts(RECENT_LIMIT),
        runRepository.observeRecentRuns(RECENT_LIMIT),
        workoutRepository.observeActiveWorkout(),
    ) { workouts, runs, active -> Triple(workouts, runs, active) }

    val uiState: StateFlow<HomeUiState> = todayFlow.flatMapLatest { today ->
        combine(
            weeklyFlow(today),
            recentFlow,
            routineRepository.observeRoutinesForDay(today.dayOfWeek),
            settingsRepository.settings,
            runTracker.state,
        ) { weekly, recent, todayRoutines, settings, activeRun ->
            HomeUiState(
                today = today,
                weekly = weekly,
                todayRoutines = todayRoutines,
                recentWorkouts = recent.first,
                recentRuns = recent.second,
                activeWorkout = recent.third,
                activeRun = activeRun,
                settings = settings,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /**
     * 루틴으로 운동을 시작한다. routineId 가 null 이면 자유 운동.
     * 이미 진행 중인 세션이 있으면 새로 만들지 않고 그 세션을 이어서 연다.
     */
    fun startWorkout(routineId: Long?) {
        viewModelScope.launch {
            val activeId = uiState.value.activeWorkout?.id
            val workoutId = activeId ?: workoutRepository.startWorkout(routineId)
            _startedWorkoutId.emit(workoutId)
        }
    }

    companion object {
        private const val RECENT_LIMIT = 3
    }
}
