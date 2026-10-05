package com.windowhyun.health.data.tracking

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.RunRepositoryImpl
import com.windowhyun.health.domain.model.LocationSample
import com.windowhyun.health.domain.model.RunCue
import com.windowhyun.health.domain.model.RunGoal
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.RunStatus
import com.windowhyun.health.domain.repository.StepCounter
import com.windowhyun.health.service.RunNotificationAction
import com.windowhyun.health.service.runNotificationActions
import com.windowhyun.health.service.vibrationPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * 러닝 중 폰을 안 보게 해 주는 기능: 자동 일시정지 · 진동 안내 · 알림 버튼.
 *
 * 시계는 직접 넣어서(skew) 기다리지 않고 시간을 흘린다. 위치는 위도만 바꿔서 미터로 옮긴다
 * (위도 0.000009도 ≈ 1m).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RunAssistTest {

    private lateinit var db: HealthDatabase
    private lateinit var runs: RunRepositoryImpl
    private lateinit var settings: SettingsRepositoryImpl
    private var now = 0L

    private val noSteps = object : StepCounter {
        override fun isAvailable() = false
        override fun hasPermission() = false
        override fun cumulativeSteps() = emptyFlow<Long>()
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).allowMainThreadQueries().build()
        runs = RunRepositoryImpl(db.runDao())
        settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) {
                File(context.cacheDir, "assist-${System.nanoTime()}.preferences_pb")
            },
        )
    }

    @After
    fun tearDown() = db.close()

    private fun tracker() = RunTracker(runs, settings, noSteps) { now }

    private fun at(meters: Double, accuracy: Float = 5f) =
        LocationSample(latitude = 37.5 + meters * 0.000009, longitude = 127.0, accuracyMeters = accuracy, timestamp = now)

    private suspend fun RunTracker.begin(goal: RunGoal = RunGoal(RunGoalType.FREE, 0.0)) = start(goal)

    // ----- 자동 일시정지 -----

    /** 한자리에 8초 넘게 서 있으면 저절로 멈추고, 멈춘 시간은 세지 않는다. */
    @Test
    fun `pauses by itself after standing still`() = runTest {
        val tracker = tracker()
        tracker.begin()
        tracker.onLocation(at(0.0))
        now += 9_000
        tracker.tick()

        assertThat(tracker.state.value.status).isEqualTo(RunStatus.PAUSED)
        assertThat(tracker.state.value.autoPaused).isTrue()
        assertThat(tracker.state.value.durationSeconds).isEqualTo(9)

        now += 30_000
        tracker.tick()
        assertThat(tracker.state.value.durationSeconds).isEqualTo(9)
        tracker.discard()
    }

    /** 첫 위치를 받기 전에는 멈춘 것인지 알 수 없다(GPS 를 잡는 중). */
    @Test
    fun `does not pause before the first location`() = runTest {
        val tracker = tracker()
        tracker.begin()
        // 신호 끊김(15초)으로 걸러지지 않는 길이여야 이 조건만 시험된다.
        now += 10_000
        tracker.tick()

        assertThat(tracker.state.value.signalLost).isFalse()
        assertThat(tracker.state.value.status).isEqualTo(RunStatus.TRACKING)
        tracker.discard()
    }

    /** 직접 눌러 이어 간 직후에는, 그전에 한참 멈춰 있었어도 곧바로 다시 멈추지 않는다. */
    @Test
    fun `manual resume does not pause again right away`() = runTest {
        val tracker = tracker()
        tracker.begin()
        tracker.onLocation(at(0.0))
        tracker.pause()
        now += 20_000
        tracker.resume()

        now += 2_000
        tracker.tick()

        assertThat(tracker.state.value.status).isEqualTo(RunStatus.TRACKING)
        tracker.discard()
    }

    /** 멈춘 자리에서 12m 넘게 벗어나면 이어 가고, 그 순간부터 다시 센다. */
    @Test
    fun `resumes by itself when moving away`() = runTest {
        val tracker = tracker()
        tracker.begin()
        tracker.onLocation(at(0.0))
        now += 9_000
        tracker.tick()
        assertThat(tracker.state.value.autoPaused).isTrue()

        now += 20_000 // 한참 서 있었다
        tracker.onLocation(at(20.0))
        assertThat(tracker.state.value.status).isEqualTo(RunStatus.TRACKING)
        assertThat(tracker.state.value.autoPaused).isFalse()

        now += 2_000
        tracker.tick()
        assertThat(tracker.state.value.durationSeconds).isEqualTo(11)
        // 이어 간 직후에 곧바로 다시 멈추지 않는다.
        assertThat(tracker.state.value.status).isEqualTo(RunStatus.TRACKING)
        tracker.discard()
    }

    /** 흔들림 정도(12m 미만)로는 이어 가지 않는다. */
    @Test
    fun `stays paused on small gps jitter`() = runTest {
        val tracker = tracker()
        tracker.begin()
        tracker.onLocation(at(0.0))
        now += 9_000
        tracker.tick()

        now += 3_000
        tracker.onLocation(at(6.0))

        assertThat(tracker.state.value.status).isEqualTo(RunStatus.PAUSED)
        tracker.discard()
    }

    /** 직접 누른 일시정지는 움직여도 이어지지 않는다. */
    @Test
    fun `manual pause is never resumed automatically`() = runTest {
        val tracker = tracker()
        tracker.begin()
        tracker.onLocation(at(0.0))
        tracker.pause()
        assertThat(tracker.state.value.autoPaused).isFalse()

        now += 5_000
        tracker.onLocation(at(100.0))

        assertThat(tracker.state.value.status).isEqualTo(RunStatus.PAUSED)
        tracker.discard()
    }

    /** 달리는 동안에는 멈추지 않는다. */
    @Test
    fun `keeps running while moving`() = runTest {
        val tracker = tracker()
        tracker.begin()
        tracker.onLocation(at(0.0))
        repeat(15) { i ->
            now += 2_000
            tracker.onLocation(at((i + 1) * 5.0))
            tracker.tick()
        }

        assertThat(tracker.state.value.status).isEqualTo(RunStatus.TRACKING)
        tracker.discard()
    }

    @Test
    fun `can be turned off in settings`() = runTest {
        settings.update { it.copy(autoPauseRun = false) }
        val tracker = tracker()
        tracker.begin()
        tracker.onLocation(at(0.0))
        now += 60_000
        tracker.tick()

        assertThat(tracker.state.value.status).isEqualTo(RunStatus.TRACKING)
        tracker.discard()
    }

    /** 위치를 못 받는 중에는 멈춘 것인지 알 수 없다. 신호 끊김으로만 알린다. */
    @Test
    fun `does not auto pause while the signal is lost`() = runTest {
        val tracker = tracker()
        tracker.begin()
        tracker.onLocation(at(0.0))
        now += 20_000
        tracker.tick()

        assertThat(tracker.state.value.signalLost).isTrue()
        assertThat(tracker.state.value.status).isEqualTo(RunStatus.TRACKING)
        tracker.discard()
    }

    // ----- 진동 안내 -----

    private fun TestScopeCues(scope: kotlinx.coroutines.test.TestScope, tracker: RunTracker): MutableList<RunCue> {
        val cues = mutableListOf<RunCue>()
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
            tracker.cues.collect { cues += it }
        }
        return cues
    }

    @Test
    fun `announces auto pause and auto resume`() = runTest {
        val tracker = tracker()
        val cues = TestScopeCues(this, tracker)
        tracker.begin()
        tracker.onLocation(at(0.0))
        now += 9_000
        tracker.tick()
        tracker.onLocation(at(30.0))

        assertThat(cues).containsExactly(RunCue.AutoPaused, RunCue.AutoResumed).inOrder()
        tracker.discard()
    }

    /** 1km 를 넘을 때마다 구간 진동, 목표를 채우면 한 번만 목표 진동. */
    @Test
    fun `announces each lap and the goal once`() = runTest {
        val tracker = tracker()
        val cues = TestScopeCues(this, tracker)
        tracker.begin(RunGoal(RunGoalType.DISTANCE, 1_000.0))
        tracker.onLocation(at(0.0))
        repeat(11) { i ->
            now += 33_000
            tracker.onLocation(at((i + 1) * 100.0))
            tracker.tick()
        }
        now += 1_000
        tracker.tick()

        assertThat(cues.filterIsInstance<RunCue.LapCompleted>().map { it.lapNumber }).containsExactly(1)
        assertThat(cues.count { it == RunCue.GoalReached }).isEqualTo(1)
        tracker.discard()
    }

    @Test
    fun `each cue has its own pattern`() {
        val patterns = listOf(
            RunCue.LapCompleted(1), RunCue.AutoResumed, RunCue.AutoPaused, RunCue.GoalReached,
        ).map { it.vibrationPattern().toList() }
        assertThat(patterns.toSet()).hasSize(4)
        patterns.forEach { assertThat(it.first()).isEqualTo(0L) }
    }

    // ----- 알림 버튼 -----

    @Test
    fun `notification offers pause or resume plus stop`() {
        assertThat(runNotificationActions(paused = false))
            .containsExactly(RunNotificationAction.PAUSE, RunNotificationAction.STOP).inOrder()
        assertThat(runNotificationActions(paused = true))
            .containsExactly(RunNotificationAction.RESUME, RunNotificationAction.STOP).inOrder()
    }
}
