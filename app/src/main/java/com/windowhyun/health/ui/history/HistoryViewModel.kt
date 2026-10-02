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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    /** 종류별 전체 기록 수(목록에 아직 불러오지 않은 옛 기록 포함). */
    val gymCount: Int = 0,
    val runCount: Int = 0,
    /** 불러온 기간보다 오래된 기록 수. */
    val olderGymCount: Int = 0,
    val olderRunCount: Int = 0,
    val settings: AppSettings = AppSettings(),
    val loading: Boolean = true,
) {
    val totalCount: Int get() = gymCount + runCount

    /** 선택한 필터에서 아직 불러오지 않은 옛 기록 수. */
    val olderCount: Int
        get() = when (filter) {
            HistoryFilter.ALL -> olderGymCount + olderRunCount
            HistoryFilter.GYM -> olderGymCount
            HistoryFilter.RUNNING -> olderRunCount
        }

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
class HistoryViewModel internal constructor(
    private val workoutRepository: WorkoutRepository,
    private val runRepository: RunRepository,
    settingsRepository: SettingsRepository,
    /** 오늘 날짜. 자정을 넘기면 새 날짜를 흘려보낸다. 테스트에서는 직접 넣는다. */
    today: Flow<LocalDate>,
) : ViewModel() {

    @Inject
    constructor(
        workoutRepository: WorkoutRepository,
        runRepository: RunRepository,
        settingsRepository: SettingsRepository,
    ) : this(workoutRepository, runRepository, settingsRepository, currentDateFlow())

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    // 날짜를 고정하면 자정을 넘겼을 때 오늘 기록이 조회 범위 밖으로 밀려난다.
    //
    // 기록을 전부 읽어 오면 쌓일수록 느려지고, 기간을 잘라 버리면 옛 기록이 목록에서 사라진다.
    // 최근 1년만 읽고, 그보다 오래된 기록은 개수를 알려 주며 "더 보기"로 1년씩 늘린다.
    private val windowYears = MutableStateFlow(1)

    val uiState: StateFlow<HistoryUiState> = today.flatMapLatest { today ->
        windowYears.flatMapLatest { years ->
            val from = today.minusYears(years.toLong())
            combine(
                workoutRepository.observeWorkoutsBetween(from = from, to = today),
                runRepository.observeRunsBetween(from = from, to = today),
                combine(
                    workoutRepository.observeWorkoutCountBefore(from),
                    runRepository.observeRunCountBefore(from),
                ) { gym, run -> gym to run },
                settingsRepository.settings,
                filter,
            ) { workouts, runs, older, settings, selected ->
                HistoryUiState(
                    entries = mergeEntries(workouts, runs),
                    filter = selected,
                    gymCount = workouts.size + older.first,
                    runCount = runs.size + older.second,
                    olderGymCount = older.first,
                    olderRunCount = older.second,
                    settings = settings,
                    loading = false,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(value: HistoryFilter) {
        filter.value = value
    }

    /** 목록이 보여 주는 기간을 1년 늘린다. */
    fun loadOlder() {
        windowYears.update { it + 1 }
    }

    // ----- 보기 전환 -----

    private val _mode = MutableStateFlow(HistoryMode.LIST)
    val mode: StateFlow<HistoryMode> = _mode.asStateFlow()

    fun setMode(value: HistoryMode) {
        _mode.value = value
    }

    // ----- 캘린더 -----

    /**
     * 캘린더가 보여 줄 달과 고른 날.
     *
     * 사용자가 손대지 않았으면 "오늘"을 따라간다. 앱을 켜 둔 채 자정이나 월말을 넘겨도
     * 어제에 머물지 않도록, 목록 보기처럼 날짜 변화를 받아 다시 계산한다.
     * 다른 달로 넘기거나 날짜를 고르면 그때부터는 그 자리에 머문다.
     */
    private sealed interface CalendarView {
        data object FollowToday : CalendarView
        data class Pinned(val month: YearMonth, val selected: LocalDate?) : CalendarView
    }

    private val calendarView = MutableStateFlow<CalendarView>(CalendarView.FollowToday)

    val calendar: StateFlow<CalendarUiState> = combine(today, calendarView) { today, view ->
        when (view) {
            CalendarView.FollowToday -> YearMonth.from(today) to today
            is CalendarView.Pinned -> view.month to view.selected
        }
    }.distinctUntilChanged().flatMapLatest { (shown, selected) ->
        combine(
            workoutRepository.observeWorkoutsBetween(shown.atDay(1), shown.atEndOfMonth()),
            runRepository.observeRunsBetween(shown.atDay(1), shown.atEndOfMonth()),
        ) { workouts, runs ->
            CalendarUiState(
                month = shown,
                selectedDate = selected,
                entriesByDate = mergeEntries(workouts, runs).groupBy { it.date },
                loading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun showPreviousMonth() = moveMonth(calendar.value.month.minusMonths(1))

    fun showNextMonth() {
        val shown = calendar.value.month
        if (shown < YearMonth.now()) moveMonth(shown.plusMonths(1))
    }

    fun showThisMonth() {
        calendarView.value = CalendarView.FollowToday
    }

    /** 이번 달로 돌아오면 다시 오늘을 따라가고, 다른 달이면 고른 날 없이 그 달에 머문다. */
    private fun moveMonth(target: YearMonth) {
        calendarView.value = if (target == YearMonth.now()) {
            CalendarView.FollowToday
        } else {
            CalendarView.Pinned(target, selected = null)
        }
    }

    /** 날짜를 고르면 그 달에 머문다. 같은 날을 다시 누르면 선택을 푼다. */
    fun selectDate(date: LocalDate) {
        val current = calendar.value
        calendarView.value = CalendarView.Pinned(
            month = YearMonth.from(date),
            selected = if (current.selectedDate == date) null else date,
        )
    }

    // ----- 종목 -----

    /** 기록이 있는 종목. 최근에 한 종목이 위. */
    val exercises: StateFlow<List<ExerciseHistorySummary>?> = workoutRepository.observeExercisesWithHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
