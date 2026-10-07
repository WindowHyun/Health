package com.windowhyun.health.ui

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.model.PersonalRecord
import com.windowhyun.health.core.model.PersonalRecordType
import com.windowhyun.health.core.model.SetType
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.core.util.formatVolume
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RunRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.ProgressMetric
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.progressPoints
import com.windowhyun.health.domain.repository.WorkoutRepository
import com.windowhyun.health.ui.exercise.ExerciseDetailViewModel
import com.windowhyun.health.ui.exercise.format
import com.windowhyun.health.ui.history.HistoryEntry
import com.windowhyun.health.ui.history.HistoryViewModel
import com.windowhyun.health.ui.navigation.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 캘린더 · 종목별 전체 기록 · 성장 그래프.
 *
 * 화면이 보여 주는 숫자는 모두 이 조회에서 나온다. 워밍업이 섞이거나 진행 중인
 * 운동이 끼면 그래프가 거짓말을 하므로, 무엇을 넣고 무엇을 빼는지 여기서 확인한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExerciseHistoryTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var db: HealthDatabase
    private lateinit var workouts: WorkoutRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var runs: RunRepositoryImpl
    private lateinit var settings: SettingsRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .allowMainThreadQueries()
            .build().also { it.openHelper.writableDatabase } // 먼저 열어 둔다: 닫을 때 여는 중이면 서로 기다려 멈춘다
        workouts = WorkoutRepositoryImpl(
            workoutDao = db.workoutDao(),
            routineDao = db.routineDao(),
            exerciseDao = db.exerciseDao(),
            personalRecordDao = db.personalRecordDao(),
        )
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        runs = RunRepositoryImpl(db.runDao())
        val file = File(context.cacheDir, "exercise-history-${System.nanoTime()}.preferences_pb")
        settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher)) { file },
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun addExercise(name: String, type: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_REPS) =
        exercises.addExercise(
            Exercise(id = 0, name = name, category = ExerciseCategory.BARBELL, bodyPart = BodyPart.LEG, trackingType = type),
        )

    /**
     * 운동 하나를 끝낸다. [sets] 는 (중량, 횟수, 종류). [daysAgo] 만큼 이전 날짜로 옮긴다.
     */
    private suspend fun session(
        exerciseId: Long,
        vararg sets: Triple<Double, Int, SetType>,
        daysAgo: Long = 0,
        finish: Boolean = true,
    ): Long {
        val workoutId = workouts.startWorkout(null)
        val weId = workouts.addExerciseToWorkout(workoutId, exerciseId)
        repeat(sets.size - 1) { workouts.addSet(weId) }
        val ids = db.workoutDao().getSets(weId).map { it.id }
        sets.forEachIndexed { i, (kg, reps, type) ->
            workouts.setSetType(ids[i], type)
            workouts.setCompleted(ids[i], kg, reps, true)
        }
        if (finish) workouts.finishWorkout(workoutId)
        if (daysAgo != 0L) {
            val stored = db.workoutDao().getWorkout(workoutId)!!
            val shift = daysAgo * 86_400_000L
            db.workoutDao().updateWorkout(
                stored.copy(
                    date = stored.date - daysAgo,
                    startTime = stored.startTime - shift,
                    endTime = stored.endTime?.minus(shift),
                ),
            )
        }
        return workoutId
    }

    private fun normal(kg: Double, reps: Int) = Triple(kg, reps, SetType.NORMAL)
    private fun warmup(kg: Double, reps: Int) = Triple(kg, reps, SetType.WARMUP)

    // ----- 종목별 전체 기록 -----

    /** 최신 기록이 위에 오고, 세션마다 세트가 묶인다. */
    @Test
    fun `lists sessions newest first with their sets`() = runTest(dispatcher) {
        val squat = addExercise("스쿼트")
        val old = session(squat, normal(100.0, 5), daysAgo = 7)
        val recent = session(squat, warmup(60.0, 8), normal(110.0, 5))

        val history = workouts.observeExerciseHistory(squat).first()

        assertThat(history.map { it.workoutId }).containsExactly(recent, old).inOrder()
        assertThat(history.first().sets.map { it.setType }).containsExactly(SetType.WARMUP, SetType.NORMAL).inOrder()
        assertThat(history.first().date).isEqualTo(LocalDate.now())
        assertThat(history.last().date).isEqualTo(LocalDate.now().minusDays(7))
    }

    /** 워밍업은 목록에는 보이지만 최고 중량 · 볼륨 · 1RM 에는 들어가지 않는다. */
    @Test
    fun `session metrics ignore warmups`() = runTest(dispatcher) {
        val squat = addExercise("스쿼트")
        // 워밍업에 잘못 넣은 200kg 가 최고 중량이 되면 안 된다.
        session(squat, warmup(200.0, 1), normal(100.0, 5), normal(100.0, 5))

        val s = workouts.observeExerciseHistory(squat).first().single()

        assertThat(s.sets).hasSize(3)
        assertThat(s.maxWeightKg).isWithin(0.001).of(100.0)
        assertThat(s.volumeKg).isWithin(0.001).of(1_000.0)
        assertThat(s.bestOneRepMaxKg).isWithin(0.01).of(100.0 * (1 + 5 / 30.0))
    }

    /** 진행 중인 운동은 끝나기 전까지 기록에 나오지 않는다. */
    @Test
    fun `leaves out a workout in progress`() = runTest(dispatcher) {
        val squat = addExercise("스쿼트")
        session(squat, normal(100.0, 5))
        session(squat, normal(120.0, 5), finish = false)

        assertThat(workouts.observeExerciseHistory(squat).first()).hasSize(1)
        assertThat(workouts.observeExercisesWithHistory().first().single().sessionCount).isEqualTo(1)
    }

    /** 기록 상세에서 세트를 고치면 종목 기록도 바로 바뀐다(Flow). */
    @Test
    fun `updates when a finished set is edited`() = runTest(dispatcher) {
        val squat = addExercise("스쿼트")
        val workoutId = session(squat, normal(100.0, 5))
        val setId = db.workoutDao().getWorkoutDetail(workoutId)!!.exercises.single().sets.single().id

        workouts.setCompleted(setId, 105.0, 5, true)

        assertThat(workouts.observeExerciseHistory(squat).first().single().maxWeightKg).isWithin(0.001).of(105.0)
    }

    /** 종목 목록: 기록이 있는 종목만, 최근에 한 종목이 위. 세션 수를 센다. */
    @Test
    fun `lists exercises with history, most recent first`() = runTest(dispatcher) {
        val squat = addExercise("스쿼트")
        val bench = addExercise("벤치프레스")
        addExercise("안 해 본 운동")
        session(squat, normal(100.0, 5), daysAgo = 3)
        session(squat, normal(100.0, 5), daysAgo = 2)
        session(bench, normal(60.0, 10))

        val list = workouts.observeExercisesWithHistory().first()

        assertThat(list.map { it.name }).containsExactly("벤치프레스", "스쿼트").inOrder()
        assertThat(list.first { it.name == "스쿼트" }.sessionCount).isEqualTo(2)
        assertThat(list.first { it.name == "스쿼트" }.lastDate).isEqualTo(LocalDate.now().minusDays(2))
    }

    // ----- 성장 그래프 -----

    /** 그래프 점은 오래된 순이고, 값이 없는 세션(워밍업만 한 날)은 빠진다. */
    @Test
    fun `progress points are oldest first and skip empty sessions`() = runTest(dispatcher) {
        val squat = addExercise("스쿼트")
        session(squat, normal(100.0, 5), daysAgo = 10)
        session(squat, warmup(60.0, 10), daysAgo = 5)
        session(squat, normal(110.0, 5))

        val points = workouts.observeExerciseHistory(squat).first().progressPoints(ProgressMetric.MAX_WEIGHT)

        assertThat(points.map { it.value }).containsExactly(100.0, 110.0).inOrder()
    }

    /** 기록 방식마다 그래프 지표가 다르다. 시간 운동에 "중량"을 그리지 않는다. */
    @Test
    fun `offers metrics by tracking type`() = runTest(dispatcher) {
        val plank = addExercise("플랭크", ExerciseTrackingType.TIME)
        val workoutId = workouts.startWorkout(null)
        val weId = workouts.addExerciseToWorkout(workoutId, plank)
        workouts.setCompleted(db.workoutDao().getSets(weId).first().id, 0.0, 0, true, durationSeconds = 90)
        workouts.finishWorkout(workoutId)

        val viewModel = ExerciseDetailViewModel(
            workoutRepository = workouts,
            exerciseRepository = exercises,
            settingsRepository = settings,
            savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_EXERCISE_ID to plank)),
        )
        val state = viewModel.uiState.first { !it.loading }

        assertThat(state.metrics).containsExactly(ProgressMetric.MAX_DURATION)
        assertThat(state.metric).isEqualTo(ProgressMetric.MAX_DURATION)
        assertThat(state.points.single().value).isWithin(0.001).of(90.0)
        assertThat(state.records.map { it.type }).containsExactly(PersonalRecordType.MAX_DURATION)
    }

    /** 지표를 바꾸면 그래프 점이 그 지표로 바뀐다. */
    @Test
    fun `switches the plotted metric`() = runTest(dispatcher) {
        val squat = addExercise("스쿼트")
        session(squat, normal(100.0, 5), normal(100.0, 5), daysAgo = 7)
        session(squat, normal(110.0, 5))
        val viewModel = ExerciseDetailViewModel(
            workoutRepository = workouts,
            exerciseRepository = exercises,
            settingsRepository = settings,
            savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_EXERCISE_ID to squat)),
        )
        val initial = viewModel.uiState.first { !it.loading }
        assertThat(initial.metric).isEqualTo(ProgressMetric.ESTIMATED_ONE_RM)

        viewModel.selectMetric(ProgressMetric.VOLUME)
        val volume = viewModel.uiState.first { it.metric == ProgressMetric.VOLUME }

        assertThat(volume.points.map { it.value }).containsExactly(1_000.0, 550.0).inOrder()
    }

    // ----- 캘린더 -----

    private fun historyViewModel() = HistoryViewModel(workouts, runs, settings)

    /** 이번 달 기록이 날짜별로 묶이고, 헬스와 러닝이 같은 날에 함께 나온다. */
    @Test
    fun `groups this month's records by date`() = runTest(dispatcher) {
        val squat = addExercise("스쿼트")
        session(squat, normal(100.0, 5))
        val runId = runs.startRun(RunGoalType.FREE, 0.0)
        runs.finishRun(runId, System.currentTimeMillis(), 5_000.0, 1_800, 360.0, 330.0, 300, 5_000)

        val calendar = historyViewModel().calendar.first { !it.loading }

        val today = calendar.entriesByDate.getValue(LocalDate.now())
        assertThat(today.map { it::class }).containsExactly(HistoryEntry.Gym::class, HistoryEntry.Running::class)
        assertThat(calendar.workoutCount).isEqualTo(1)
        assertThat(calendar.runCount).isEqualTo(1)
        assertThat(calendar.runDistanceMeters).isWithin(0.001).of(5_000.0)
        // 처음에는 오늘이 골라져 있어 바로 오늘 기록이 보인다.
        assertThat(calendar.selectedEntries).hasSize(2)
    }

    /** 지난달로 가면 지난달 기록만 보이고, 미래 달로는 넘어가지 않는다. */
    @Test
    fun `moves between months but not into the future`() = runTest(dispatcher) {
        val squat = addExercise("스쿼트")
        val today = LocalDate.now()
        val lastMonthDay = today.minusMonths(1)
        session(squat, normal(100.0, 5), daysAgo = today.toEpochDay() - lastMonthDay.toEpochDay())
        val viewModel = historyViewModel()
        viewModel.calendar.first { !it.loading }

        viewModel.showNextMonth() // 이번 달이 끝이라 그대로
        assertThat(viewModel.calendar.first { !it.loading }.month).isEqualTo(YearMonth.now())

        viewModel.showPreviousMonth()
        val previous = viewModel.calendar.first { it.month == YearMonth.now().minusMonths(1) }
        assertThat(previous.entriesByDate.keys).containsExactly(lastMonthDay)
        // 오늘이 없는 달로 가면 고른 날이 없다.
        assertThat(previous.selectedDate).isNull()

        viewModel.selectDate(lastMonthDay)
        val selected = viewModel.calendar.first { it.selectedDate == lastMonthDay }
        assertThat(selected.selectedEntries).hasSize(1)
    }

    /** 같은 날을 다시 누르면 선택이 풀린다. */
    @Test
    fun `tapping the selected day clears it`() = runTest(dispatcher) {
        val viewModel = historyViewModel()
        viewModel.calendar.first { !it.loading }
        val day = LocalDate.now(ZoneId.systemDefault())

        viewModel.selectDate(day)

        assertThat(viewModel.calendar.first { it.selectedDate == null }.selectedDate).isNull()
    }

    // ----- 코드 리뷰 수정 -----

    /** 앱을 켜 둔 채 월말 자정을 넘기면 캘린더가 새 달 · 오늘로 넘어간다. */
    @Test
    fun `calendar follows the date past midnight`() = runTest(dispatcher) {
        val today = MutableStateFlow(LocalDate.of(2026, 9, 30))
        val viewModel = HistoryViewModel(workouts, runs, settings, today)
        assertThat(viewModel.calendar.first { !it.loading }.month).isEqualTo(YearMonth.of(2026, 9))

        today.value = LocalDate.of(2026, 10, 1)

        val next = viewModel.calendar.first { it.month == YearMonth.of(2026, 10) }
        assertThat(next.selectedDate).isEqualTo(LocalDate.of(2026, 10, 1))
    }

    /** 사용자가 날짜를 골라 둔 뒤에는 자정이 지나도 그 자리에 머문다. */
    @Test
    fun `calendar stays where the user picked`() = runTest(dispatcher) {
        val today = MutableStateFlow(LocalDate.of(2026, 9, 30))
        val viewModel = HistoryViewModel(workouts, runs, settings, today)
        viewModel.calendar.first { !it.loading }
        viewModel.selectDate(LocalDate.of(2026, 9, 12))
        viewModel.calendar.first { it.selectedDate == LocalDate.of(2026, 9, 12) }

        today.value = LocalDate.of(2026, 10, 1)

        val state = viewModel.calendar.first { !it.loading }
        assertThat(state.month).isEqualTo(YearMonth.of(2026, 9))
        assertThat(state.selectedDate).isEqualTo(LocalDate.of(2026, 9, 12))
    }

    /** 종목 목록의 최근 날짜는 상세 · 캘린더와 같은 "저장된 날짜"를 쓴다. */
    @Test
    fun `exercise list uses the stored date`() = runTest(dispatcher) {
        val squat = addExercise("스쿼트")
        val workoutId = session(squat, normal(100.0, 5))
        // 시간대가 바뀐 경우처럼, 시작 시각은 그대로 두고 저장된 날짜만 다르게 한다.
        val stored = db.workoutDao().getWorkout(workoutId)!!
        db.workoutDao().updateWorkout(stored.copy(date = stored.date - 3))

        val summary = workouts.observeExercisesWithHistory().first().single()
        val session = workouts.observeExerciseHistory(squat).first().single()

        assertThat(summary.lastDate).isEqualTo(LocalDate.now().minusDays(3))
        assertThat(summary.lastDate).isEqualTo(session.date)
    }

    /** 그래프 지표 칩을 눌러도 PR 을 다시 계산하지 않는다(기록이 바뀔 때만). */
    @Test
    fun `switching the metric does not reload records`() = runTest(dispatcher) {
        val squat = addExercise("스쿼트")
        session(squat, normal(100.0, 5), daysAgo = 3)
        session(squat, normal(110.0, 5))
        var recordLoads = 0
        val counting = object : WorkoutRepository by workouts {
            override suspend fun getPersonalRecords(exerciseId: Long): List<PersonalRecord> {
                recordLoads++
                return workouts.getPersonalRecords(exerciseId)
            }
        }
        val viewModel = ExerciseDetailViewModel(
            workoutRepository = counting,
            exerciseRepository = exercises,
            settingsRepository = settings,
            savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_EXERCISE_ID to squat)),
        )
        viewModel.uiState.first { !it.loading }
        val loadsAfterOpen = recordLoads

        viewModel.selectMetric(ProgressMetric.MAX_WEIGHT)
        viewModel.uiState.first { it.metric == ProgressMetric.MAX_WEIGHT }
        viewModel.selectMetric(ProgressMetric.VOLUME)
        viewModel.uiState.first { it.metric == ProgressMetric.VOLUME }

        assertThat(recordLoads).isEqualTo(loadsAfterOpen)
    }

    /** 그래프의 볼륨 표기는 기록 카드와 같다(1,000 미만은 소수까지). */
    @Test
    fun `chart volume label matches the session card`() {
        assertThat(ProgressMetric.VOLUME.format(850.5, WeightUnit.KG)).isEqualTo(formatVolume(850.5, WeightUnit.KG))
        assertThat(ProgressMetric.VOLUME.format(12_345.0, WeightUnit.KG)).isEqualTo(formatVolume(12_345.0, WeightUnit.KG))
    }

    /** 같은 날 두 번 한 기록도 그래프 점이 따로 있고, 시작 시각 순서다. */
    @Test
    fun `keeps two sessions on the same day as separate points`() = runTest(dispatcher) {
        val squat = addExercise("스쿼트")
        session(squat, normal(100.0, 5))
        session(squat, normal(105.0, 5))

        val points = workouts.observeExerciseHistory(squat).first().progressPoints(ProgressMetric.MAX_WEIGHT)

        assertThat(points.map { it.value }).containsExactly(100.0, 105.0).inOrder()
        assertThat(points[0].startTime).isLessThan(points[1].startTime)
        assertThat(points.map { it.date }.distinct()).hasSize(1)
    }
}
