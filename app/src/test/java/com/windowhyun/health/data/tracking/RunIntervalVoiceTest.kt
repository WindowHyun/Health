package com.windowhyun.health.data.tracking

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.RunRepositoryImpl
import com.windowhyun.health.domain.model.IntervalPhase
import com.windowhyun.health.domain.model.LocationSample
import com.windowhyun.health.domain.model.RunCue
import com.windowhyun.health.domain.model.RunGoal
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.RunInterval
import com.windowhyun.health.domain.model.RunLap
import com.windowhyun.health.domain.model.RunStatus
import com.windowhyun.health.domain.repository.StepCounter
import com.windowhyun.health.service.RunTrackingService
import com.windowhyun.health.service.RunVoiceScript
import com.windowhyun.health.service.vibrationPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** 러닝 인터벌(달리기/걷기 구간)과 음성 안내 문장. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RunIntervalVoiceTest {

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
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).allowMainThreadQueries().build().also { it.openHelper.writableDatabase } // 먼저 열어 둔다: 닫을 때 여는 중이면 서로 기다려 멈춘다
        runs = RunRepositoryImpl(db.runDao())
        settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) {
                File(context.cacheDir, "interval-${System.nanoTime()}.preferences_pb")
            },
        )
    }

    @After
    fun tearDown() = db.close()

    private fun tracker() = RunTracker(runs, settings, noSteps) { now }

    private fun at(meters: Double) =
        LocationSample(latitude = 37.5 + meters * 0.000009, longitude = 127.0, accuracyMeters = 5f, timestamp = now)

    private fun cuesOf(scope: TestScope, tracker: RunTracker): MutableList<RunCue> {
        val cues = mutableListOf<RunCue>()
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
            tracker.cues.collect { cues += it }
        }
        return cues
    }

    // ----- 구간 계산 -----

    @Test
    fun `progress follows run then walk for each round`() {
        val plan = RunInterval(runSeconds = 10, walkSeconds = 20, rounds = 2)

        val first = plan.progressAt(0)
        assertThat(first.phase).isEqualTo(IntervalPhase.RUN)
        assertThat(first.round).isEqualTo(1)
        assertThat(first.secondsLeft).isEqualTo(10)

        assertThat(plan.progressAt(9).secondsLeft).isEqualTo(1)
        assertThat(plan.progressAt(10).phase).isEqualTo(IntervalPhase.WALK)
        assertThat(plan.progressAt(10).secondsLeft).isEqualTo(20)
        assertThat(plan.progressAt(29).secondsLeft).isEqualTo(1)

        val second = plan.progressAt(30)
        assertThat(second.phase).isEqualTo(IntervalPhase.RUN)
        assertThat(second.round).isEqualTo(2)

        assertThat(plan.progressAt(59).finished).isFalse()
        assertThat(plan.progressAt(60).finished).isTrue()
        assertThat(plan.progressAt(10_000).finished).isTrue()
        assertThat(plan.totalSeconds).isEqualTo(60)
    }

    // ----- 트래커 -----

    /** 구간이 바뀔 때마다 한 번씩만 알리고, 정해 둔 라운드를 마치면 끝났다고 한 번 알린다. */
    @Test
    fun `announces every phase change once and the end once`() = runTest {
        settings.update { it.copy(autoPauseRun = false) }
        val tracker = tracker()
        val cues = cuesOf(this, tracker)
        tracker.start(RunGoal(RunGoalType.FREE, 0.0), RunInterval(10, 20, 2))
        tracker.onLocation(at(0.0))
        repeat(70) {
            now += 1_000
            tracker.tick()
        }

        assertThat(cues.filter { it is RunCue.IntervalChanged || it == RunCue.IntervalsFinished }).containsExactly(
            RunCue.IntervalChanged(IntervalPhase.RUN, 1, 2),
            RunCue.IntervalChanged(IntervalPhase.WALK, 1, 2),
            RunCue.IntervalChanged(IntervalPhase.RUN, 2, 2),
            RunCue.IntervalChanged(IntervalPhase.WALK, 2, 2),
            RunCue.IntervalsFinished,
        ).inOrder()
        // 끝난 뒤에도 러닝은 그대로 이어진다.
        assertThat(tracker.state.value.status).isEqualTo(RunStatus.TRACKING)
        assertThat(tracker.state.value.intervalProgress!!.finished).isTrue()
        tracker.discard()
    }

    /** 일시정지한 동안은 구간 시간이 흐르지 않는다. */
    @Test
    fun `pausing freezes the interval`() = runTest {
        settings.update { it.copy(autoPauseRun = false) }
        val tracker = tracker()
        tracker.start(RunGoal(RunGoalType.FREE, 0.0), RunInterval(60, 60, 3))
        tracker.onLocation(at(0.0))
        repeat(5) {
            now += 1_000
            tracker.tick()
        }
        tracker.pause()
        now += 100_000
        tracker.tick()
        tracker.resume()
        now += 1_000
        tracker.tick()

        val progress = tracker.state.value.intervalProgress!!
        assertThat(progress.phase).isEqualTo(IntervalPhase.RUN)
        assertThat(progress.round).isEqualTo(1)
        assertThat(progress.secondsLeft).isEqualTo(54)
        tracker.discard()
    }

    @Test
    fun `a normal run has no interval`() = runTest {
        val tracker = tracker()
        val cues = cuesOf(this, tracker)
        tracker.start(RunGoal(RunGoalType.FREE, 0.0))
        tracker.onLocation(at(0.0))
        now += 1_000
        tracker.tick()

        assertThat(tracker.state.value.interval).isNull()
        assertThat(tracker.state.value.intervalProgress).isNull()
        assertThat(cues.filter { it is RunCue.IntervalChanged }).isEmpty()
        tracker.discard()
    }

    /** 음성 안내가 읽을 값(구간 기록과 누적)이 구간 알림에 실려 온다. */
    @Test
    fun `lap cue carries what the voice needs`() = runTest {
        settings.update { it.copy(autoPauseRun = false) }
        val tracker = tracker()
        val cues = cuesOf(this, tracker)
        tracker.start(RunGoal(RunGoalType.FREE, 0.0))
        tracker.onLocation(at(0.0))
        repeat(11) { i ->
            now += 33_000
            tracker.onLocation(at((i + 1) * 100.0))
            tracker.tick()
        }

        val lap = cues.filterIsInstance<RunCue.LapCompleted>().single()
        assertThat(lap.lap).isNotNull()
        assertThat(lap.lap!!.distanceMeters).isWithin(30.0).of(1_000.0)
        assertThat(lap.totalDistanceMeters).isAtLeast(1_000.0)
        assertThat(lap.totalDurationSeconds).isGreaterThan(0)
        tracker.discard()
    }

    // ----- 서비스 시작 인텐트 -----

    @Test
    fun `interval survives the start intent round trip`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val plan = RunInterval(45, 75, 6)

        val withInterval = RunTrackingService.startIntent(context, RunGoal(RunGoalType.DISTANCE, 5_000.0), plan)
        assertThat(RunTrackingService.intervalFrom(withInterval)).isEqualTo(plan)

        val without = RunTrackingService.startIntent(context, RunGoal(RunGoalType.FREE, 0.0), null)
        assertThat(RunTrackingService.intervalFrom(without)).isNull()
    }

    // ----- 음성 문장 -----

    private fun lapCue(totalMeters: Double = 3_000.0, totalSeconds: Long = 990, pace: Double = 330.0) =
        RunCue.LapCompleted(
            lapNumber = 3,
            lap = RunLap(lapNumber = 3, distanceMeters = 1_000.0, durationSeconds = pace.toLong(), paceSecPerKm = pace),
            totalDistanceMeters = totalMeters,
            totalDurationSeconds = totalSeconds,
        )

    @Test
    fun `lap sentence reads distance pace and time`() {
        val text = RunVoiceScript.sentenceFor(lapCue(), DistanceUnit.KM)!!

        assertThat(text).contains("3킬로미터")
        assertThat(text).contains("페이스 5분 30초")
        assertThat(text).contains("총 시간 16분 30초")
    }

    @Test
    fun `lap sentence converts pace and distance to miles`() {
        // 1609m 를 5분 30초 · 1km 가 아니라 1마일 기준으로 읽는다(330초/km -> 약 8분 51초/마일).
        val text = RunVoiceScript.sentenceFor(lapCue(totalMeters = 1_609.344), DistanceUnit.MILE)!!

        assertThat(text).contains("1마일")
        assertThat(text).contains("페이스 8분 51초")
    }

    @Test
    fun `lap sentence skips pace when it is unknown and does not read half a sentence`() {
        val text = RunVoiceScript.sentenceFor(lapCue(pace = 0.0), DistanceUnit.KM)!!
        assertThat(text).doesNotContain("페이스")

        // 구간 정보가 없는 알림은 읽지 않는다(진동만).
        assertThat(RunVoiceScript.sentenceFor(RunCue.LapCompleted(1), DistanceUnit.KM)).isNull()
    }

    @Test
    fun `numbers are read the way people say them`() {
        assertThat(RunVoiceScript.spokenDuration(330)).isEqualTo("5분 30초")
        assertThat(RunVoiceScript.spokenDuration(3_600)).isEqualTo("1시간")
        assertThat(RunVoiceScript.spokenDuration(3_725)).isEqualTo("1시간 2분 5초")
        assertThat(RunVoiceScript.spokenDuration(45)).isEqualTo("45초")
        assertThat(RunVoiceScript.spokenDuration(0)).isEqualTo("0초")
        assertThat(RunVoiceScript.spokenDistance(1_500.0, DistanceUnit.KM)).isEqualTo("1.5킬로미터")
        assertThat(RunVoiceScript.spokenDistance(1_000.0, DistanceUnit.KM)).isEqualTo("1킬로미터")
        assertThat(RunVoiceScript.spokenDistance(1_250.0, DistanceUnit.KM)).isEqualTo("1.25킬로미터")
    }

    @Test
    fun `interval and goal cues have sentences`() {
        assertThat(RunVoiceScript.sentenceFor(RunCue.IntervalChanged(IntervalPhase.RUN, 2, 8), DistanceUnit.KM))
            .isEqualTo("달리기 2번째, 전체 8번 중")
        assertThat(RunVoiceScript.sentenceFor(RunCue.IntervalChanged(IntervalPhase.WALK, 2, 8), DistanceUnit.KM))
            .isEqualTo("걷기")
        assertThat(RunVoiceScript.sentenceFor(RunCue.IntervalsFinished, DistanceUnit.KM)).contains("끝났어요")
        assertThat(RunVoiceScript.sentenceFor(RunCue.GoalReached, DistanceUnit.KM)).contains("달성")
        assertThat(RunVoiceScript.sentenceFor(RunCue.AutoPaused, DistanceUnit.KM)).isNotNull()
        assertThat(RunVoiceScript.sentenceFor(RunCue.AutoResumed, DistanceUnit.KM)).isNotNull()
    }

    // ----- 진동 -----

    @Test
    fun `interval cues vibrate differently from the others`() {
        val cues = listOf(
            RunCue.LapCompleted(1), RunCue.AutoResumed, RunCue.AutoPaused, RunCue.GoalReached,
            RunCue.IntervalChanged(IntervalPhase.RUN, 1, 2), RunCue.IntervalChanged(IntervalPhase.WALK, 1, 2),
            RunCue.IntervalsFinished,
        )
        val patterns = cues.map { it.vibrationPattern().toList() }
        assertThat(patterns.toSet()).hasSize(cues.size)
    }
}
