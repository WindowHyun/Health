package com.windowhyun.health.ui.running

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.model.LocationSample
import com.windowhyun.health.domain.model.RunGoal
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.ThemeMode
import com.windowhyun.health.domain.repository.StepCounter
import com.windowhyun.health.service.RunServiceController
import com.windowhyun.health.ui.screenshot.ScreenshotFixture
import com.windowhyun.health.ui.screenshot.waitFor
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 러닝 진행 화면에서 지도로 갔다가 기록으로 돌아와도 아래 버튼이 그대로 있어야 한다. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class RunActiveMapToggleTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fixture: ScreenshotFixture
    private lateinit var context: Context
    private var now = 0L

    private val steps = object : StepCounter {
        override fun isAvailable() = true
        override fun hasPermission() = true
        override fun cumulativeSteps() = emptyFlow<Long>()
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fixture = ScreenshotFixture(context)
    }

    @After
    fun tearDown() = fixture.close()

    private fun showActiveRun(): RunActiveViewModel {
        val tracker = RunTracker(fixture.runs, fixture.settings, steps) { now }
        runBlocking {
            tracker.start(RunGoal(RunGoalType.DISTANCE, 5_000.0))
            tracker.onLocation(LocationSample(37.5, 127.0, accuracyMeters = 6f, timestamp = 0))
            repeat(10) { i ->
                now += 30_000
                tracker.onLocation(LocationSample(37.5 + (i + 1) * 0.0009, 127.0, accuracyMeters = 6f, timestamp = now))
            }
        }
        val viewModel = RunActiveViewModel(tracker, fixture.settings, RunServiceController(context))
        compose.setContent {
            HealthTheme(themeMode = ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RunActiveScreen(onFinished = {}, viewModel = viewModel)
                }
            }
        }
        waitFor { viewModel.uiState.value.tracking.distanceMeters > 0 }
        compose.waitForIdle()
        return viewModel
    }

    @Test
    fun `the pause button survives a trip to the map and back`() {
        showActiveRun()
        compose.onNodeWithText("일시정지").assertIsDisplayed()
        val before = compose.onNodeWithText("일시정지").getUnclippedBoundsInRoot()

        compose.onNodeWithText("지도").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("일시정지").assertIsDisplayed()

        compose.onNodeWithText("기록").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("일시정지").assertIsDisplayed()
        val after = compose.onNodeWithText("일시정지").getUnclippedBoundsInRoot()

        assertThat(after.height).isEqualTo(before.height)
        assertThat(after.top).isEqualTo(before.top)
    }
}
