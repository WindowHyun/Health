package com.windowhyun.health.ui.running

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.model.LocationSample
import com.windowhyun.health.domain.model.RunGoal
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.RunStatus
import com.windowhyun.health.domain.model.ThemeMode
import com.windowhyun.health.domain.repository.StepCounter
import com.windowhyun.health.service.RunServiceController
import com.windowhyun.health.service.RunTrackingService
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 러닝 종료는 되돌릴 수 없다(끝난 러닝은 이어 달릴 수 없다). 일시정지 옆의 큰 빨간 버튼이라 한 번 묻는다. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class RunStopConfirmTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fixture: ScreenshotFixture
    private lateinit var context: Context
    private lateinit var tracker: RunTracker
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
        tracker = RunTracker(fixture.runs, fixture.settings, steps) { now }
        runBlocking {
            tracker.start(RunGoal(RunGoalType.FREE, 0.0))
            tracker.onLocation(LocationSample(37.5, 127.0, accuracyMeters = 6f, timestamp = 0))
            repeat(5) { i ->
                now += 5_000
                tracker.onLocation(LocationSample(37.5 + (i + 1) * 0.0001, 127.0, accuracyMeters = 6f, timestamp = now))
            }
            tracker.pause()
        }
        val viewModel = RunActiveViewModel(tracker, fixture.settings, RunServiceController(context))
        compose.setContent {
            HealthTheme(themeMode = ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) { RunActiveScreen(onFinished = {}, viewModel = viewModel) }
            }
        }
        waitFor { viewModel.uiState.value.tracking.status == RunStatus.PAUSED }
        compose.waitForIdle()
        drainStartedServices()
    }

    @After
    fun tearDown() {
        runBlocking { tracker.discard() }
        fixture.close()
    }

    private fun drainStartedServices() {
        val app = context as Application
        while (shadowOf(app).nextStartedService != null) Unit
    }

    private fun stopRequested(): Boolean {
        val app = context as Application
        var found = false
        while (true) {
            val intent = shadowOf(app).nextStartedService ?: break
            if (intent.action == RunTrackingService.ACTION_STOP) found = true
        }
        return found
    }

    @Test
    fun `pressing stop asks first and nothing is stopped yet`() {
        compose.onNodeWithText("종료").performClick()

        compose.onNodeWithText("러닝을 종료할까요?").assertIsDisplayed()
        assertThat(stopRequested()).isFalse()
    }

    @Test
    fun `choosing to keep running closes the question and stops nothing`() {
        compose.onNodeWithText("종료").performClick()
        compose.onNodeWithText("계속 달리기").performClick()
        compose.waitForIdle()

        assertThat(compose.onAllDialogs()).isEmpty()
        assertThat(stopRequested()).isFalse()
    }

    @Test
    fun `confirming stops the run`() {
        compose.onNodeWithText("종료").performClick()
        compose.onNode(hasText("종료") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()

        assertThat(stopRequested()).isTrue()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllDialogs() =
        onAllNodes(isDialog()).fetchSemanticsNodes()
}
