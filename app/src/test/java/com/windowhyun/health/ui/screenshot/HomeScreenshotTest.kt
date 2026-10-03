package com.windowhyun.health.ui.screenshot

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.domain.model.RunGoal
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.ThemeMode
import com.windowhyun.health.ui.HealthBottomBar
import com.windowhyun.health.ui.home.HomeScreen
import com.windowhyun.health.ui.home.HomeViewModel
import com.windowhyun.health.ui.navigation.TopLevelDestination
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 홈 화면을 가짜 사용 기록으로 그려 build/screenshots 에 남긴다. 눈으로 보는 용도다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class HomeScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fixture: ScreenshotFixture
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        fixture = ScreenshotFixture(ApplicationProvider.getApplicationContext<Context>())
        fixture.seed()
        viewModel = HomeViewModel(
            fixture.workouts, fixture.routines, fixture.runs, fixture.settings, fixture.tracker,
        )
    }

    @After
    fun tearDown() = fixture.close()

    private fun shoot(name: String, dark: Boolean) {
        compose.setContent {
            HealthTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(1f).fillMaxWidth()) {
                            HomeScreen(
                                onOpenSettings = {},
                                onStartWorkout = {},
                                onOpenGym = {},
                                onOpenRunning = {},
                                onOpenRunResult = {},
                                onOpenWorkout = {},
                                onOpenRun = {},
                                viewModel = viewModel,
                            )
                        }
                        HealthBottomBar(isSelected = { it == TopLevelDestination.HOME }, onSelect = {})
                    }
                }
            }
        }
        waitFor { viewModel.uiState.value.recentRuns.isNotEmpty() }
        compose.saveScreenshot(name)
    }

    /** 운동 · 러닝이 진행 중인 홈. 이어하기 띠가 보이고 오늘 루틴의 시작은 막힌다. */
    private fun startInProgress() = runBlocking {
        fixture.workouts.startWorkout(fixture.todayRoutineId)
        fixture.tracker.start(RunGoal(RunGoalType.FREE, 0.0))
    }

    @Test
    fun `home light`() = shoot("home_light", dark = false)

    @Test
    fun `home dark`() = shoot("home_dark", dark = true)

    @Test
    fun `home in progress light`() {
        startInProgress()
        shoot("home_active_light", dark = false)
    }

    @Test
    fun `home in progress dark`() {
        startInProgress()
        shoot("home_active_dark", dark = true)
    }
}
