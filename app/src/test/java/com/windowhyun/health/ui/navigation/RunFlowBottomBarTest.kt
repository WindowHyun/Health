package com.windowhyun.health.ui.navigation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.model.LocationSample
import com.windowhyun.health.domain.model.RunGoal
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.ThemeMode
import com.windowhyun.health.domain.repository.LocationTracker
import com.windowhyun.health.domain.repository.StepCounter
import com.windowhyun.health.service.RunServiceController
import com.windowhyun.health.ui.HealthBottomBar
import com.windowhyun.health.ui.running.RunActiveScreen
import com.windowhyun.health.ui.running.RunActiveViewModel
import com.windowhyun.health.ui.running.RunSetupScreen
import com.windowhyun.health.ui.running.RunSetupViewModel
import com.windowhyun.health.ui.running.RunSummaryScreen
import com.windowhyun.health.ui.running.RunSummaryViewModel
import com.windowhyun.health.ui.screenshot.ScreenshotFixture
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 러닝 탭에서 달리고(지도를 보고) 끝낸 뒤 러닝 탭으로 돌아왔을 때 하단 탭이 다시 보이는지.
 *
 * 실제 화면과 같은 Scaffold · 하단 탭 · 경로 연결(HealthApp / HealthNavHost 와 같은 호출)로 구성했다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class RunFlowBottomBarTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fixture: ScreenshotFixture
    private lateinit var context: Context
    private lateinit var tracker: RunTracker
    private lateinit var nav: NavHostController
    private var now = 0L
    private var summaryVm: RunSummaryViewModel? = null

    private val steps = object : StepCounter {
        override fun isAvailable() = true
        override fun hasPermission() = true
        override fun cumulativeSteps() = emptyFlow<Long>()
    }
    private val location = object : LocationTracker {
        override fun isLocationAvailable() = true
        override fun hasLocationPermission() = true
        override fun locationUpdates(intervalMillis: Long) = emptyFlow<LocationSample>()
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fixture = ScreenshotFixture(context)
        tracker = RunTracker(fixture.runs, fixture.settings, steps) { now }
    }

    @After
    fun tearDown() = fixture.close()

    @Composable
    private fun TestApp() {
        nav = rememberNavController()
        val backStackEntry by nav.currentBackStackEntryAsState()
        val destination = backStackEntry?.destination
        val topLevel = remember { TopLevelDestination.entries.map { it.route }.toSet() }
        val serviceController = remember { RunServiceController(context) }

        Scaffold(
            bottomBar = {
                if (destination?.route in topLevel) {
                    HealthBottomBar(
                        isSelected = { d -> destination?.hierarchy?.any { it.route == d.route } == true },
                        onSelect = { d ->
                            nav.navigate(d.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                NavHost(nav, startDestination = Routes.HOME) {
                    composable(Routes.HOME) { androidx.compose.material3.Text("home-screen") }
                    composable(Routes.HISTORY) { androidx.compose.material3.Text("history-screen") }
                    composable(Routes.GYM) { androidx.compose.material3.Text("gym-screen") }
                    // HealthNavHost 의 러닝 흐름과 같은 연결.
                    composable(Routes.RUNNING) {
                        RunSetupScreen(
                            onRunStarted = { nav.navigate(Routes.RUN_ACTIVE) { launchSingleTop = true } },
                            viewModel = remember { RunSetupViewModel(location, steps, serviceController, tracker, fixture.settings) },
                        )
                    }
                    composable(Routes.RUN_ACTIVE) {
                        RunActiveScreen(
                            onFinished = {
                                nav.navigate(Routes.RUN_SUMMARY) { popUpTo(Routes.RUN_ACTIVE) { inclusive = true } }
                            },
                            viewModel = remember { RunActiveViewModel(tracker, fixture.settings, serviceController) },
                        )
                    }
                    composable(Routes.RUN_SUMMARY) {
                        RunSummaryScreen(
                            onClose = {
                                if (!nav.popBackStack(Routes.RUNNING, inclusive = false)) nav.popBackStack()
                            },
                            viewModel = remember { RunSummaryViewModel(fixture.runs, tracker, fixture.settings).also { summaryVm = it } },
                        )
                    }
                }
            }
        }
    }

    /** DB 는 다른 스레드에서 읽는다. 화면을 계속 돌려 주며 조건이 될 때까지 실제 시간으로 기다린다. */
    private fun settleUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            compose.waitForIdle()
            if (condition()) return
            Thread.sleep(20)
        }
        compose.waitForIdle()
    }

    private fun route(): String? = nav.currentBackStackEntry?.destination?.route

    private fun launch() {
        compose.setContent {
            HealthTheme(themeMode = ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) { TestApp() }
            }
        }
        compose.waitForIdle()
    }

    /** 하단 탭 하나. 화면 제목에도 같은 글자가 있어서(예: "러닝") 선택 가능한 칸만 고른다. */
    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun selectRunningTab() {
        tab("러닝").performClick()
        compose.waitForIdle()
    }

    private fun runSomeMeters() = runBlocking {
        tracker.start(RunGoal(RunGoalType.FREE, 0.0))
        tracker.onLocation(LocationSample(37.5, 127.0, accuracyMeters = 6f, timestamp = 0))
        repeat(8) { i ->
            now += 30_000
            tracker.onLocation(LocationSample(37.5 + (i + 1) * 0.0009, 127.0, accuracyMeters = 6f, timestamp = now))
        }
    }

    private fun assertTabsVisible() {
        listOf("홈", "헬스", "러닝", "기록").forEach { tab(it).assertIsDisplayed() }
    }

    private fun runFlow(openMap: Boolean, closeMapBeforeFinish: Boolean) {
        launch()
        selectRunningTab()
        assertThat(route()).isEqualTo(Routes.RUNNING)
        assertTabsVisible()

        runSomeMeters()
        settleUntil { route() == Routes.RUN_ACTIVE }
        compose.waitForIdle()
        assertThat(route()).isEqualTo(Routes.RUN_ACTIVE)

        if (openMap) {
            compose.onNodeWithText("지도").performClick()
            compose.waitForIdle()
            if (closeMapBeforeFinish) {
                compose.onNodeWithText("기록").performClick()
                compose.waitForIdle()
            }
        }

        runBlocking { tracker.finish() }
        settleUntil { route() == Routes.RUN_SUMMARY }
        compose.waitForIdle()
        assertThat(route()).isEqualTo(Routes.RUN_SUMMARY)

        // 기록을 다 불러온 뒤 "저장" 버튼과 같은 동작(저장하고 닫기)을 한다.
        settleUntil { summaryVm?.uiState?.value?.run != null }
        compose.runOnUiThread { summaryVm!!.save() }
        settleUntil { route() == Routes.RUNNING }
        compose.waitForIdle()

        assertThat(route()).isEqualTo(Routes.RUNNING)
        assertTabsVisible()
    }

    @Test
    fun `tabs are back after a run with no map`() = runFlow(openMap = false, closeMapBeforeFinish = false)

    @Test
    fun `tabs are back after opening the map and coming back`() = runFlow(openMap = true, closeMapBeforeFinish = true)

    @Test
    fun `tabs are back after finishing with the map still open`() = runFlow(openMap = true, closeMapBeforeFinish = false)
}
