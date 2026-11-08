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
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.core.notification.RestTimerNotifier
import com.windowhyun.health.domain.model.ThemeMode
import com.windowhyun.health.ui.HealthBottomBar
import com.windowhyun.health.ui.home.HomeScreen
import com.windowhyun.health.ui.home.HomeViewModel
import com.windowhyun.health.ui.navigation.Routes
import com.windowhyun.health.ui.navigation.TopLevelDestination
import com.windowhyun.health.ui.session.WorkoutSessionScreen
import com.windowhyun.health.ui.session.WorkoutSessionViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 글자 크기를 1.8배로 키운 사용자(접근성 설정)에게 화면이 깨지지 않는지 눈으로 본다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h860dp-xxhdpi", fontScale = 1.8f)
class LargeFontScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fixture: ScreenshotFixture
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fixture = ScreenshotFixture(context)
        fixture.seed()
    }

    @After
    fun tearDown() = fixture.close()

    @Test
    fun `home at large font`() {
        val vm = HomeViewModel(fixture.workouts, fixture.routines, fixture.runs, fixture.settings, fixture.tracker)
        compose.setContent {
            HealthTheme(themeMode = ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(1f).fillMaxWidth()) {
                            HomeScreen({}, {}, {}, {}, {}, {}, {}, viewModel = vm)
                        }
                        HealthBottomBar(isSelected = { it == TopLevelDestination.HOME }, onSelect = {})
                    }
                }
            }
        }
        waitFor { vm.uiState.value.recentRuns.isNotEmpty() }
        compose.saveScreenshot("large_home")
    }

    @Test
    fun `session at large font`() {
        val workoutId = runBlocking {
            val id = fixture.workouts.startWorkout(fixture.todayRoutineId)
            val sets = fixture.db.workoutDao().getWorkoutDetail(id)!!.exercises.first().sets
            fixture.workouts.setCompleted(sets[0].id, 80.0, 8, true)
            id
        }
        val vm = WorkoutSessionViewModel(
            fixture.workouts, fixture.exercises, fixture.settings, RestTimerNotifier(context),
            SavedStateHandle(mapOf(Routes.ARG_WORKOUT_ID to workoutId)),
            com.windowhyun.health.wear.WatchLink(),
        )
        vm.startRestTimer(90)
        compose.setContent {
            HealthTheme(themeMode = ThemeMode.LIGHT) { WorkoutSessionScreen(onFinished = {}, onDiscarded = {}, viewModel = vm) }
        }
        waitFor { vm.uiState.value.workout != null && vm.uiState.value.lastPerformance.isNotEmpty() }
        compose.saveScreenshot("large_session")
    }
}
