package com.windowhyun.health.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.core.util.currentDateFlow
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.ExerciseHistorySummary
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.repository.RunRepository
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** 기록 목록에 들어가는 한 줄. 헬스와 러닝을 시간순으로 섞어 보여 준다. */
sealed interface HistoryEntry {
    val date: LocalDate

    /** 같은 날 안에서의 정렬 기준(시작 시각). */
    val startTime: Long

    /** LazyColumn key. 헬스와 러닝의 id 가 겹치므로 접두사를 붙인다. */
    val key: String

    data class Gym(val workout: Workout) : HistoryEntry {
        override val date: LocalDate get() = workout.date
        override val startTime: Long get() = workout.startTime
        override val key: String get() = "workout-${workout.id}"
    }

    data class Running(val run: Run) : HistoryEntry {
        override val date: LocalDate get() = run.date
        override val startTime: Long get() = run.startTime
        override val key: String get() = "run-${run.id}"
    }
}

/** 기록 탭의 보기. */
enum class HistoryMode(val label: String) {
    LIST("목록"),
    CALENDAR("캘린더"),
    EXERCISES("종목"),
}

/** 캘린더 한 달치. 날짜별 기록을 미리 묶어 두어 칸마다 다시 찾지 않는다. */
data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate? = LocalDate.now(),
    val entriesByDate: Map<LocalDate, List<HistoryEntry>> = emptyMap(),
    val loading: Boolean = true,
) {
    val selectedEntries: List<HistoryEntry>
        get() = selectedDate?.let { entriesByDate[it] }.orEmpty()

    val workoutCount: Int
        get() = entriesByDate.values.sumOf { day -> day.count { it is HistoryEntry.Gym } }

    val runCount: Int
        get() = entriesByDate.values.sumOf { day -> day.count { it is HistoryEntry.Running } }

    val runDistanceMeters: Double
        get() = entriesByDate.values.flatten().filterIsInstance<HistoryEntry.Running>()
            .sumOf { it.run.distanceMeters }

    /** 미래 달로는 넘어가지 않는다. 기록이 있을 수 없다. */
    val canGoNext: Boolean get() = month < YearMonth.now()
}

/** 헬스와 러닝을 한 목록으로. 날짜 최신순, 같은 날이면 늦게 시작한 것이 위. */
internal fun mergeEntries(workouts: List<Workout>, runs: List<Run>): List<HistoryEntry> =
    (workouts.map(HistoryEntry::Gym) + runs.map(HistoryEntry::Running))
        .sortedWith(compareByDescending<HistoryEntry> { it.date }.thenByDescending { it.startTime })

/** 기록 탭 필터. 개인 앱이라 종류가 둘뿐이므로 칩 3개면 충분하다. */
enum class HistoryFilter(val label: String) {
    ALL("전체"),
    GYM("헬스"),
    RUNNING("러닝"),
}

data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    val gymCount: Int = 0,
    val runCount: Int = 0,
    val settings: AppSettings = AppSettings(),
    val loading: Boolean = true,
) {
    /** 필터를 적용한 목록. 화면은 이것만 그린다. */
    val visibleEntries: List<HistoryEntry>
        get() = when (filter) {
            HistoryFilter.ALL -> entries
            HistoryFilter.GYM -> entries.filterIsInstance<HistoryEntry.Gym>()
            HistoryFilter.RUNNING -> entries.filterIsInstance<HistoryEntry.Running>()
        }
}

/**
 * 기록 탭. 헬스와 러닝을 하나의 시간순 목록으로 합친다.
 * Phase 3 에서 캘린더 / 통계 영역이 여기에 붙는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val runRepository: RunRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    // 날짜를 고정하면 자정을 넘겼을 때 오늘 기록이 조회 범위 밖으로 밀려난다.
    val uiState: StateFlow<HistoryUiState> = currentDateFlow().flatMapLatest { today ->
        val from = today.minusYears(1)
        combine(
            workoutRepository.observeWorkoutsBetween(from = from, to = today),
            runRepository.observeRunsBetween(from = from, to = today),
            settingsRepository.settings,
            filter,
        ) { workouts, runs, settings, selected ->
            val entries = mergeEntries(workouts, runs)

            HistoryUiState(
                entries = entries,
                filter = selected,
                gymCount = workouts.size,
                runCount = runs.size,
                settings = settings,
                loading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(value: HistoryFilter) {
        filter.value = value
    }

    // ----- 보기 전환 -----

    private val _mode = MutableStateFlow(HistoryMode.LIST)
    val mode: StateFlow<HistoryMode> = _mode.asStateFlow()

    fun setMode(value: HistoryMode) {
        _mode.value = value
    }

    // ----- 캘린더 -----

    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow<LocalDate?>(LocalDate.now())

    val calendar: StateFlow<CalendarUiState> = month.flatMapLatest { shown ->
        combine(
            workoutRepository.observeWorkoutsBetween(shown.atDay(1), shown.atEndOfMonth()),
            runRepository.observeRunsBetween(shown.atDay(1), shown.atEndOfMonth()),
            selectedDate,
        ) { workouts, runs, selected ->
            CalendarUiState(
                month = shown,
                selectedDate = selected,
                entriesByDate = mergeEntries(workouts, runs).groupBy { it.date },
                loading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun showPreviousMonth() = moveMonth(month.value.minusMonths(1))

    fun showNextMonth() {
        if (month.value < YearMonth.now()) moveMonth(month.value.plusMonths(1))
    }

    fun showThisMonth() {
        moveMonth(YearMonth.now())
    }

    /** 달을 넘기면 그 달에 오늘이 있을 때만 오늘을 고른다. 아니면 고른 날 없이 시작한다. */
    private fun moveMonth(target: YearMonth) {
        val today = LocalDate.now()
        selectedDate.value = if (YearMonth.from(today) == target) today else null
        month.value = target
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = if (selectedDate.value == date) null else date
    }

    // ----- 종목 -----

    /** 기록이 있는 종목. 최근에 한 종목이 위. */
    val exercises: StateFlow<List<ExerciseHistorySummary>?> = workoutRepository.observeExercisesWithHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
