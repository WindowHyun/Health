package com.windowhyun.health.ui.home

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.RoutineRepositoryImpl
import com.windowhyun.health.data.repository.RunRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.data.sensor.SensorStepCounter
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.usecase.GoalProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** 주간 목표: 진행 계산, 저장, 그리고 홈에 보이는 모습. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2000dp")
class WeeklyGoalTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var db: HealthDatabase
    private lateinit var workouts: WorkoutRepositoryImpl
    private lateinit var runs: RunRepositoryImpl
    private lateinit var routines: RoutineRepositoryImpl
    private lateinit var settings: SettingsRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).allowMainThreadQueries().build().also { it.openHelper.writableDatabase } // 먼저 열어 둔다: 닫을 때 여는 중이면 서로 기다려 멈춘다
        workouts = WorkoutRepositoryImpl(db.workoutDao(), db.routineDao(), db.exerciseDao(), db.personalRecordDao())
        runs = RunRepositoryImpl(db.runDao())
        routines = RoutineRepositoryImpl(db.routineDao())
        settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
                File(context.cacheDir, "weekly-goal-${System.nanoTime()}.preferences_pb")
            },
        )
    }

    @After
    fun tearDown() = db.close()

    // ----- 계산 -----

    @Test
    fun `no goal means no progress`() {
        assertThat(GoalProgress.of(3.0, 0.0)).isNull()
        assertThat(GoalProgress.of(3.0, -1.0)).isNull()
    }

    @Test
    fun `progress is clamped and reports achievement`() {
        val half = GoalProgress.of(2.0, 4.0)!!
        assertThat(half.fraction).isWithin(0.001f).of(0.5f)
        assertThat(half.achieved).isFalse()
        assertThat(half.remaining).isEqualTo(2.0)

        val done = GoalProgress.of(4.0, 4.0)!!
        assertThat(done.achieved).isTrue()
        assertThat(done.remaining).isEqualTo(0.0)

        val over = GoalProgress.of(9.0, 4.0)!!
        assertThat(over.fraction).isEqualTo(1f)
        assertThat(over.achieved).isTrue()
    }

    // ----- 저장 -----

    @Test
    fun `goals are stored and default to off`() = runBlocking {
        assertThat(settings.current().weeklyWorkoutGoal).isEqualTo(0)
        assertThat(settings.current().weeklyRunGoalMeters).isEqualTo(0)

        settings.update { it.copy(weeklyWorkoutGoal = 3, weeklyRunGoalMeters = 20_000) }

        val stored = settings.settings.first()
        assertThat(stored.weeklyWorkoutGoal).isEqualTo(3)
        assertThat(stored.weeklyRunGoalMeters).isEqualTo(20_000)
    }

    // ----- 홈 -----

    private fun home(): HomeViewModel = HomeViewModel(
        workoutRepository = workouts,
        routineRepository = routines,
        runRepository = runs,
        settingsRepository = settings,
        runTracker = RunTracker(runs, settings, SensorStepCounter(context)),
    )

    private fun show(viewModel: HomeViewModel) {
        compose.setContent {
            HealthTheme {
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
        }
    }

    @Test
    fun `home invites you to set a goal when there is none`() {
        show(home())
        compose.waitForIdle()

        compose.onNodeWithText("이번 주 목표 정하기").assertIsDisplayed()
    }

    @Test
    fun `home shows this weeks progress toward each goal`() {
        runBlocking {
            settings.update { it.copy(weeklyWorkoutGoal = 3, weeklyRunGoalMeters = 20_000) }
            val workoutId = workouts.startWorkout(null)
            workouts.finishWorkout(workoutId)
            val runId = runs.startRun(RunGoalType.FREE, 0.0)
            runs.finishRun(runId, System.currentTimeMillis(), 8_400.0, 2_800, 333.0, 320.0, 500, 8_000)
        }
        val viewModel = home()
        show(viewModel)
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.weekly.workoutCount == 1 }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("헬스 1 / 3회").assertIsDisplayed()
        compose.onNodeWithContentDescription("러닝 8.4 / 20km").assertIsDisplayed()
        compose.onNodeWithText("이번 주 목표 정하기").assertDoesNotExist()
    }

    @Test
    fun `home says achieved once the goal is met`() {
        runBlocking {
            settings.update { it.copy(weeklyWorkoutGoal = 1) }
            workouts.finishWorkout(workouts.startWorkout(null))
        }
        val viewModel = home()
        show(viewModel)
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.weekly.workoutCount == 1 }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("헬스 목표 달성, 1 / 1회").assertIsDisplayed()
    }

    @Test
    fun `home shows the run goal in miles when the user uses miles`() {
        runBlocking {
            settings.update { it.copy(distanceUnit = DistanceUnit.MILE, weeklyRunGoalMeters = 16_093) }
        }
        val viewModel = home()
        show(viewModel)
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.settings.weeklyRunGoalMeters == 16_093 }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("러닝 0.0 / 10mile").assertIsDisplayed()
    }
}
