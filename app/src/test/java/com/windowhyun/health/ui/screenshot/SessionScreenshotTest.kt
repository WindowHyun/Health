package com.windowhyun.health.ui.screenshot

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.core.notification.RestTimerNotifier
import com.windowhyun.health.domain.model.ThemeMode
import com.windowhyun.health.ui.navigation.Routes
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

/** 운동 진행 화면(세트 두 개 완료, 휴식 타이머 동작 중)을 build/screenshots 에 남긴다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1000dp-xxhdpi")
class SessionScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fixture: ScreenshotFixture
    private lateinit var viewModel: WorkoutSessionViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fixture = ScreenshotFixture(context)
        fixture.seed()
        val workoutId = runBlocking {
            val id = fixture.workouts.startWorkout(fixture.todayRoutineId)
            val sets = fixture.db.workoutDao().getWorkoutDetail(id)!!.exercises.first().sets
            fixture.workouts.setCompleted(sets[0].id, 80.0, 8, true)
            fixture.workouts.setCompleted(sets[1].id, 80.0, 8, true)
            fixture.workouts.setSetType(sets[3].id, com.windowhyun.health.core.model.SetType.WARMUP)
            id
        }
        viewModel = WorkoutSessionViewModel(
            fixture.workouts, fixture.exercises, fixture.settings, RestTimerNotifier(context),
            SavedStateHandle(mapOf(Routes.ARG_WORKOUT_ID to workoutId)),
            com.windowhyun.health.wear.WatchLink(),
        )
        viewModel.startRestTimer(90)
    }

    @After
    fun tearDown() = fixture.close()

    private fun shoot(name: String, dark: Boolean) {
        compose.setContent {
            HealthTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                WorkoutSessionScreen(onFinished = {}, onDiscarded = {}, viewModel = viewModel)
            }
        }
        waitFor { viewModel.uiState.value.workout != null && viewModel.uiState.value.lastPerformance.isNotEmpty() }
        compose.saveScreenshot(name)
    }

    @Test
    fun `session light`() = shoot("session_light", dark = false)

    @Test
    fun `session dark`() = shoot("session_dark", dark = true)
}
