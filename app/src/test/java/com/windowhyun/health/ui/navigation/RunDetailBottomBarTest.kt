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
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.domain.model.ThemeMode
import com.windowhyun.health.ui.HealthBottomBar
import com.windowhyun.health.ui.history.HistoryScreen
import com.windowhyun.health.ui.history.HistoryViewModel
import com.windowhyun.health.ui.running.RunDetailScreen
import com.windowhyun.health.ui.running.RunDetailViewModel
import com.windowhyun.health.ui.screenshot.ScreenshotFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 기록 탭에서 지난 러닝(지도가 있는 화면)을 열었다가 뒤로 나오면 하단 탭이 다시 보여야 한다. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class RunDetailBottomBarTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fixture: ScreenshotFixture
    private lateinit var context: Context
    private lateinit var nav: NavHostController
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fixture = ScreenshotFixture(context)
        fixture.seed()
    }

    @After
    fun tearDown() = fixture.close()

    @Composable
    private fun TestApp() {
        nav = rememberNavController()
        val backStackEntry by nav.currentBackStackEntryAsState()
        val destination = backStackEntry?.destination
        val topLevel = remember { TopLevelDestination.entries.map { it.route }.toSet() }

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
                    composable(Routes.HISTORY) {
                        HistoryScreen(
                            onOpenWorkout = {},
                            onOpenRun = { runId -> nav.navigate(Routes.runDetail(runId)) },
                            onOpenExercise = {},
                            viewModel = remember { HistoryViewModel(fixture.workouts, fixture.runs, fixture.settings) },
                        )
                    }
                    composable(
                        route = "${Routes.RUN_DETAIL}/{${Routes.ARG_RUN_ID}}",
                        arguments = listOf(navArgument(Routes.ARG_RUN_ID) { type = NavType.LongType }),
                    ) { entry ->
                        val runId = entry.arguments!!.getLong(Routes.ARG_RUN_ID)
                        RunDetailScreen(
                            onBack = { nav.popBackStack() },
                            viewModel = remember {
                                RunDetailViewModel(
                                    runRepository = fixture.runs,
                                    settingsRepository = fixture.settings,
                                    savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_RUN_ID to runId)),
                                    appScope = appScope,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    private fun route(): String? = nav.currentBackStackEntry?.destination?.route

    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun settleUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            compose.waitForIdle()
            if (condition()) return
            Thread.sleep(20)
        }
        compose.waitForIdle()
    }

    private fun assertTabsVisible() {
        listOf("홈", "헬스", "러닝", "기록").forEach { tab(it).assertIsDisplayed() }
    }

    private fun openHistoryAndPastRun() {
        compose.setContent {
            HealthTheme(themeMode = ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) { TestApp() }
            }
        }
        compose.waitForIdle()
        tab("기록").performClick()
        settleUntil { route() == Routes.HISTORY }
        assertTabsVisible()

        // 목록에서 러닝 한 줄을 연다.
        settleUntil { compose.onAllNodes(hasText("러닝") and !isSelectable()).fetchSemanticsNodes().isNotEmpty() }
        // 러닝 한 줄은 "러닝" 글자가 든, 하단 탭이 아닌 칸이다.
        compose.onAllNodes(hasText("러닝") and !isSelectable())[0].performClick()
        settleUntil { route()?.startsWith(Routes.RUN_DETAIL) == true }
        assertThat(route()).startsWith(Routes.RUN_DETAIL)
    }

    @Test
    fun `tabs are back after leaving a past run with the system back`() {
        openHistoryAndPastRun()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settleUntil { route() == Routes.HISTORY }

        assertThat(route()).isEqualTo(Routes.HISTORY)
        assertTabsVisible()
    }

    @Test
    fun `tabs are back after leaving a past run with the back arrow`() {
        openHistoryAndPastRun()

        compose.onNode(androidx.compose.ui.test.hasContentDescription("뒤로")).performClick()
        settleUntil { route() == Routes.HISTORY }

        assertThat(route()).isEqualTo(Routes.HISTORY)
        assertTabsVisible()
    }
}
