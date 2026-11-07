package com.windowhyun.health.ui.screenshot

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import com.windowhyun.health.ui.share.RunShareCard
import com.windowhyun.health.ui.share.RunCardData
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.model.LocationSample
import com.windowhyun.health.domain.model.RunGoal
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.RunLap
import com.windowhyun.health.domain.model.ThemeMode
import com.windowhyun.health.domain.repository.LocationTracker
import com.windowhyun.health.domain.repository.StepCounter
import com.windowhyun.health.service.RunServiceController
import com.windowhyun.health.ui.running.RunActiveScreen
import com.windowhyun.health.ui.running.RunActiveViewModel
import com.windowhyun.health.ui.running.RunHeadline
import com.windowhyun.health.ui.running.RunSetupScreen
import com.windowhyun.health.ui.running.RunSetupViewModel
import com.windowhyun.health.ui.running.RunStatGrid
import com.windowhyun.health.ui.running.runLapItems
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import androidx.compose.ui.test.performScrollToNode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 러닝 화면(시작 · 진행 · 결과)을 build/screenshots 에 남긴다. 눈으로 보는 용도다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class RunScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fixture: ScreenshotFixture
    private lateinit var context: Context
    private var now = 0L

    private val noSteps = object : StepCounter {
        override fun isAvailable() = true
        override fun hasPermission() = true
        override fun cumulativeSteps() = emptyFlow<Long>()
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fixture = ScreenshotFixture(context)
        fixture.seed()
    }

    @After
    fun tearDown() = fixture.close()

    /** 약 2.4km 를 5'30" 페이스로 달린 상태(Lap 2개)를 만든다. */
    private fun runTracker(): RunTracker {
        val tracker = RunTracker(fixture.runs, fixture.settings, noSteps) { now }
        runBlocking {
            tracker.start(RunGoal(RunGoalType.DISTANCE, 5_000.0))
            tracker.onLocation(LocationSample(37.5, 127.0, accuracyMeters = 6f, timestamp = 0))
            repeat(24) { i ->
                now += 33_000
                tracker.onLocation(
                    LocationSample(37.5 + (i + 1) * 0.000899, 127.0, accuracyMeters = 6f, timestamp = now),
                )
            }
        }
        return tracker
    }

    private fun show(dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            HealthTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }

    private fun active(name: String, dark: Boolean) {
        val tracker = runTracker()
        val viewModel = RunActiveViewModel(tracker, fixture.settings, RunServiceController(context))
        show(dark) { RunActiveScreen(onFinished = {}, viewModel = viewModel) }
        waitFor { viewModel.uiState.value.tracking.distanceMeters > 0 }
        compose.saveScreenshot(name)
    }

    private fun setup(name: String, dark: Boolean, gpsOn: Boolean) {
        val location = object : LocationTracker {
            override fun isLocationAvailable() = gpsOn
            override fun hasLocationPermission() = true
            override fun locationUpdates(intervalMillis: Long) = emptyFlow<LocationSample>()
        }
        val viewModel = RunSetupViewModel(
            location, noSteps, RunServiceController(context),
            RunTracker(fixture.runs, fixture.settings, noSteps), fixture.settings,
            com.windowhyun.health.data.tracking.InMemoryCourses(), com.windowhyun.health.data.tracking.ActiveCourse(),
        )
        viewModel.setGoalType(RunGoalType.DISTANCE)
        show(dark) { RunSetupScreen(onRunStarted = {}, viewModel = viewModel) }
        waitFor { viewModel.uiState.value.goalType == RunGoalType.DISTANCE }
        compose.saveScreenshot(name)
    }

    /** 저장된 러닝의 결과 · 상세가 공유하는 부분(머리글 · 지표 · 구간). 지도는 빼고 그린다. */
    private val sampleCourse = com.windowhyun.health.domain.model.Course(
        id = 1, name = "한강 5km",
        points = (0..24).map { com.windowhyun.health.domain.model.CoursePoint(37.5 + it * 0.0018, 127.0 + if (it % 2 == 0) 0.0 else 0.0005) },
    )

    private fun setupWithCourses(name: String, dark: Boolean) {
        val location = object : LocationTracker {
            override fun isLocationAvailable() = true
            override fun hasLocationPermission() = true
            override fun locationUpdates(intervalMillis: Long) = emptyFlow<LocationSample>()
        }
        val courses = com.windowhyun.health.data.tracking.InMemoryCourses(
            listOf(sampleCourse, sampleCourse.copy(id = 2, name = "공원 한 바퀴", points = sampleCourse.points.take(8))),
        )
        val viewModel = RunSetupViewModel(
            location, noSteps, RunServiceController(context),
            RunTracker(fixture.runs, fixture.settings, noSteps), fixture.settings,
            courses, com.windowhyun.health.data.tracking.ActiveCourse(),
        )
        viewModel.selectCourse(1)
        show(dark) { RunSetupScreen(onRunStarted = {}, viewModel = viewModel) }
        waitFor { viewModel.uiState.value.selectedCourseId == 1L }
        compose.saveScreenshot(name)
    }

    /** 코스를 따라 달리는 중([offNorth] 가 크면 코스에서 벗어난 상태)의 진행 화면. */
    private fun activeWithCourse(name: String, offNorthMeters: Double) {
        val tracker = RunTracker(fixture.runs, fixture.settings, noSteps) { now }
        runBlocking {
            tracker.start(RunGoal(), null, sampleCourse)
            tracker.onLocation(LocationSample(37.5, 127.0, accuracyMeters = 6f, timestamp = 0))
            repeat(12) { i ->
                now += 33_000
                val off = if (i >= 10) offNorthMeters else 0.0
                tracker.onLocation(LocationSample(37.5 + (i + 1) * 0.0009 + off / 111_195.0 * 0, 127.0 + off / 90_000.0, accuracyMeters = 6f, timestamp = now))
            }
        }
        val viewModel = RunActiveViewModel(tracker, fixture.settings, RunServiceController(context))
        show(false) { RunActiveScreen(onFinished = {}, viewModel = viewModel) }
        waitFor { viewModel.uiState.value.tracking.courseGuidance != null }
        compose.saveScreenshot(name)
    }

    private fun result(name: String, dark: Boolean) {
        val run = runBlocking { fixture.runs.getRun(1)!! }.copy(
            laps = listOf(
                RunLap(id = 1, lapNumber = 1, distanceMeters = 1000.0, durationSeconds = 322, paceSecPerKm = 322.0),
                RunLap(id = 2, lapNumber = 2, distanceMeters = 1000.0, durationSeconds = 311, paceSecPerKm = 311.0),
                RunLap(id = 3, lapNumber = 3, distanceMeters = 1000.0, durationSeconds = 338, paceSecPerKm = 338.0),
                RunLap(id = 4, lapNumber = 4, distanceMeters = 1000.0, durationSeconds = 329, paceSecPerKm = 329.0),
            ),
            steps = 9_400,
        )
        show(dark) {
            LazyColumn(
                modifier = Modifier.padding(0.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item { RunHeadline("2026년 10월 1일 목요일 07:12", run.distanceMeters, DistanceUnit.KM) }
                item { RunStatGrid(run = run, distanceUnit = DistanceUnit.KM) }
                runLapItems(laps = run.laps, distanceUnit = DistanceUnit.KM)
            }
        }
        compose.saveScreenshot(name)
    }

    /** 공유용 러닝 카드. 360x450dp 로 그대로 그려 본다. */
    private fun shareCard(name: String, withRoute: Boolean, best: Boolean) {
        val run = runBlocking { fixture.runs.getRun(1)!! }.let { if (withRoute) it else it.copy(route = emptyList()) }
        val data = RunCardData.from(run, DistanceUnit.KM, isPersonalBest = best)
        show(false) {
            Box(modifier = Modifier.size(360.dp, 450.dp)) { RunShareCard(data) }
        }
        compose.saveScreenshot(name)
    }

    @Test fun `share card with route`() = shareCard("run_card_route", withRoute = true, best = true)
    @Test fun `share card without route`() = shareCard("run_card_noroute", withRoute = false, best = false)
    @Test fun `active light`() = active("run_active_light", dark = false)
    @Test fun `active dark`() = active("run_active_dark", dark = true)
    @Test fun `setup light`() = setup("run_setup_light", dark = false, gpsOn = true)
    @Test fun `setup gps off dark`() = setup("run_setup_off_dark", dark = true, gpsOn = false)
    @Test fun `setup with courses light`() = setupWithCourses("run_setup_course_light", dark = false)
    @Test fun `active on course`() = activeWithCourse("run_active_course", offNorthMeters = 0.0)
    @Test fun `active off course`() = activeWithCourse("run_active_offcourse", offNorthMeters = 120.0)
    @Test fun `result light`() = result("run_result_light", dark = false)
    @Test fun `result dark`() = result("run_result_dark", dark = true)
}
