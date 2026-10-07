package com.windowhyun.health.ui

import android.app.AlarmManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.notification.RestTimerNotifier
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RunRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.LocationSample
import com.windowhyun.health.domain.model.RunGoal
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.repository.StepCounter
import com.windowhyun.health.ui.history.HistoryViewModel
import com.windowhyun.health.ui.navigation.Routes
import com.windowhyun.health.ui.session.WorkoutSessionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.LocalDate

/**
 * 화면이 꺼져 기기가 잠들어도 시간이 맞는지, 기록 목록이 옛 기록을 잃지 않는지 확인한다.
 *
 * 잠든 시간은 "시계만 앞서가고 타이머는 돌지 않은" 상태로 흉내 낸다([skewMillis]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReliabilityFixesTest {

    private val dispatcher = StandardTestDispatcher()

    /** 타이머는 돌지 않았지만 시계는 흐른 시간. */
    private var skewMillis = 0L
    private val clock: () -> Long = { dispatcher.scheduler.currentTime + skewMillis }

    private lateinit var context: Context
    private lateinit var db: HealthDatabase
    private lateinit var workouts: WorkoutRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var runs: RunRepositoryImpl
    private lateinit var settings: SettingsRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .allowMainThreadQueries()
            .build().also { it.openHelper.writableDatabase } // 먼저 열어 둔다: 닫을 때 여는 중이면 서로 기다려 멈춘다
        workouts = WorkoutRepositoryImpl(db.workoutDao(), db.routineDao(), db.exerciseDao(), db.personalRecordDao())
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        runs = RunRepositoryImpl(db.runDao())
        val file = File(context.cacheDir, "reliability-${System.nanoTime()}.preferences_pb")
        settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher)) { file },
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** DB · 설정 읽기는 다른 스레드에서 돈다. 기다리며 테스트 시계를 조금씩 돌린다. */
    private fun TestScope.settle() {
        repeat(30) {
            advanceTimeBy(10)
            Thread.sleep(10)
        }
    }

    // ----- 휴식 타이머 -----

    private fun sessionViewModel() = WorkoutSessionViewModel(
        workoutRepository = workouts,
        exerciseRepository = exercises,
        settingsRepository = settings,
        restTimerNotifier = RestTimerNotifier(context),
        savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_WORKOUT_ID to 1L)),
        clock = clock,
    )

    /**
     * 화면의 경과 시간 타이머는 끝나지 않는다. 단언이 실패해도 정리해야 테스트가 멈추지 않는다.
     */
    private suspend fun withSession(block: suspend (WorkoutSessionViewModel) -> Unit) {
        val viewModel = sessionViewModel()
        try {
            block(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    /** 기기가 잠든 사이 타이머 틱이 빠져도 남은 시간은 시계대로 줄어 있다. */
    @Test
    fun `rest timer follows the clock while the device sleeps`() = runTest(dispatcher) {
        withSession { viewModel ->
            viewModel.startRestTimer(90)
            advanceTimeBy(1_000)

            skewMillis += 60_000 // 1분 동안 잠들었다
            advanceTimeBy(300)

            assertThat(viewModel.restTimer.value.remainingSeconds).isAtMost(30)
            assertThat(viewModel.restTimer.value.remainingSeconds).isAtLeast(28)
        }
    }

    /** 휴식이 끝난 직후 +15초를 누르면 멈추지 않고 다시 센다. */
    @Test
    fun `plus fifteen right after the rest ends keeps counting`() = runTest(dispatcher) {
        withSession { viewModel ->
            viewModel.startRestTimer(1)
            advanceTimeBy(1_300)
            assertThat(viewModel.restTimer.value.remainingSeconds).isEqualTo(0)
            assertThat(viewModel.restTimer.value.visible).isTrue()

            advanceTimeBy(1_500) // 끝난 지 1.5초. 아직 막대가 남아 있는 동안이다.
            viewModel.adjustRestTimer(15)
            advanceTimeBy(5_000)

            assertThat(viewModel.restTimer.value.visible).isTrue()
            assertThat(viewModel.restTimer.value.remainingSeconds).isAtLeast(10)
            assertThat(viewModel.restTimer.value.remainingSeconds).isAtMost(11)
        }
    }

    /** 일시정지한 동안은 시계가 흘러도 남은 시간이 줄지 않는다. */
    @Test
    fun `paused rest timer keeps its remaining time`() = runTest(dispatcher) {
        withSession { viewModel ->
            viewModel.startRestTimer(60)
            advanceTimeBy(10_000)
            viewModel.toggleRestPause()
            val paused = viewModel.restTimer.value.remainingSeconds

            skewMillis += 30_000
            advanceTimeBy(30_000)
            assertThat(viewModel.restTimer.value.remainingSeconds).isEqualTo(paused)

            viewModel.toggleRestPause()
            advanceTimeBy(5_000)
            assertThat(viewModel.restTimer.value.remainingSeconds).isAtLeast(paused - 6)
            assertThat(viewModel.restTimer.value.remainingSeconds).isAtMost(paused - 4)
        }
    }

    /** 끝 알림은 시스템 알람이 맡는다. 시작하면 맞추고, 건너뛰면 지운다. */
    @Test
    fun `rest timer sets a system alarm and clears it on skip`() = runTest(dispatcher) {
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        withSession { viewModel ->
            viewModel.startRestTimer(90)
            settle()
            // peek 은 알람을 꺼내지 않는다. next 는 꺼내 버려서 아래 확인이 의미가 없어진다.
            assertThat(alarms.peekNextScheduledAlarm()).isNotNull()

            viewModel.stopRestTimer()
            assertThat(alarms.peekNextScheduledAlarm()).isNull()
        }
    }

    // ----- 러닝 시간 -----

    private val noSteps = object : StepCounter {
        override fun isAvailable() = false
        override fun hasPermission() = false
        override fun cumulativeSteps() = emptyFlow<Long>()
    }

    private fun tracker() = RunTracker(runs, settings, noSteps, clock)

    private fun sample(lat: Double, accuracy: Float = 5f, at: Long = 0) =
        LocationSample(latitude = lat, longitude = 127.0, accuracyMeters = accuracy, timestamp = at)

    /** 기기가 잠들어 타이머가 한참 빠져도 달린 시간은 시계대로 쌓인다. */
    @Test
    fun `run duration follows the clock when ticks are missed`() = runTest(dispatcher) {
        val tracker = tracker()
        tracker.start(RunGoal(RunGoalType.FREE, 0.0))
        skewMillis += 1_000
        tracker.tick()

        skewMillis += 600_000 // 10분 동안 틱이 없었다
        tracker.tick()

        assertThat(tracker.state.value.durationSeconds).isEqualTo(601)
        tracker.discard()
    }

    /** 위치가 들어올 때도 그 시점까지의 시간이 먼저 반영돼, 늦은 틱이 페이스를 빠르게 만들지 않는다. */
    @Test
    fun `a location after a long sleep uses the elapsed time`() = runTest(dispatcher) {
        val tracker = tracker()
        tracker.start(RunGoal(RunGoalType.FREE, 0.0))
        tracker.onLocation(sample(37.5, at = 0))

        skewMillis += 300_000 // 5분, 틱 없음
        tracker.onLocation(sample(37.5 + 0.008993, at = 300_000)) // 약 1km

        assertThat(tracker.state.value.distanceMeters).isWithin(15.0).of(1_000.0)
        assertThat(tracker.state.value.averagePaceSecPerKm).isWithin(10.0).of(300.0)
        tracker.discard()
    }

    /** 일시정지한 시간은 세지 않는다. */
    @Test
    fun `run duration excludes paused time`() = runTest(dispatcher) {
        val tracker = tracker()
        tracker.start(RunGoal(RunGoalType.FREE, 0.0))
        skewMillis += 10_000
        tracker.tick()

        tracker.pause()
        skewMillis += 60_000
        tracker.resume()
        skewMillis += 5_000
        tracker.tick()

        assertThat(tracker.state.value.durationSeconds).isEqualTo(15)
        tracker.discard()
    }

    /** 위치를 한동안 못 받으면 알리고, 다시 받으면 거둔다. */
    @Test
    fun `run reports lost signal and recovers`() = runTest(dispatcher) {
        val tracker = tracker()
        tracker.start(RunGoal(RunGoalType.FREE, 0.0))
        tracker.onLocation(sample(37.5, at = 0))
        skewMillis += 5_000
        tracker.tick()
        assertThat(tracker.state.value.signalLost).isFalse()

        skewMillis += 20_000
        tracker.tick()
        assertThat(tracker.state.value.signalLost).isTrue()

        tracker.onLocation(sample(37.5005, at = 25_000))
        assertThat(tracker.state.value.signalLost).isFalse()
        tracker.discard()
    }

    /** 일시정지 중에는 위치가 안 와도 신호 끊김이 아니고, 재개 직후에도 바로 끊김이 되지 않는다. */
    @Test
    fun `pausing does not count as lost signal`() = runTest(dispatcher) {
        val tracker = tracker()
        tracker.start(RunGoal(RunGoalType.FREE, 0.0))
        tracker.onLocation(sample(37.5, at = 0))
        tracker.pause()
        skewMillis += 120_000
        tracker.tick()
        assertThat(tracker.state.value.signalLost).isFalse()

        tracker.resume()
        skewMillis += 2_000
        tracker.tick()
        assertThat(tracker.state.value.signalLost).isFalse()
        tracker.discard()
    }

    // ----- 기록 목록 -----

    /** 1년보다 오래된 기록도 개수로 알려 주고, "더 보기"로 불러온다. */
    @Test
    fun `history keeps records older than a year reachable`() = runTest(dispatcher) {
        val squat = exercises.addExercise(
            Exercise(id = 0, name = "스쿼트", category = ExerciseCategory.BARBELL, bodyPart = BodyPart.LEG),
        )
        val workoutId = workouts.startWorkout(null)
        val weId = workouts.addExerciseToWorkout(workoutId, squat)
        workouts.setCompleted(db.workoutDao().getSets(weId).first().id, 100.0, 5, true)
        workouts.finishWorkout(workoutId)
        val stored = db.workoutDao().getWorkout(workoutId)!!
        val shift = 400L * 86_400_000L
        db.workoutDao().updateWorkout(
            stored.copy(date = stored.date - 400, startTime = stored.startTime - shift, endTime = stored.endTime!! - shift),
        )
        val viewModel = HistoryViewModel(workouts, runs, settings, flowOf(LocalDate.now()))

        val initial = viewModel.uiState.first { !it.loading }
        assertThat(initial.entries).isEmpty()
        assertThat(initial.gymCount).isEqualTo(1) // 칩 개수에는 옛 기록도 센다
        assertThat(initial.olderCount).isEqualTo(1)

        viewModel.loadOlder()
        val loaded = viewModel.uiState.first { it.entries.isNotEmpty() }
        assertThat(loaded.entries).hasSize(1)
        assertThat(loaded.olderCount).isEqualTo(0)
        viewModel.viewModelScope.cancel()
    }
}
