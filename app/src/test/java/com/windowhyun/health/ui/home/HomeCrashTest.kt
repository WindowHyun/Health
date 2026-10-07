package com.windowhyun.health.ui.home

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RoutineRepositoryImpl
import com.windowhyun.health.data.repository.RunRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.data.sensor.SensorStepCounter
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.model.RoutineItem
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.RunLap
import com.windowhyun.health.ui.running.runLapItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * "루틴으로 운동하고 저장하면 앱이 튕기고, 다시 켜도 안 들어가진다" 재현.
 *
 * 저장하면 홈으로 돌아가고, 앱을 켜도 홈이 먼저 뜬다. 홈 목록이 헬스 기록과 러닝 기록을
 * 각자의 DB id 로 구분하고 있어서, 러닝 1번과 헬스 1번이 함께 있으면 목록 키가 겹쳐
 * 화면을 그리는 순간 죽는다. 기록은 남아 있으므로 켤 때마다 다시 죽는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2000dp")
class HomeCrashTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: HealthDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .allowMainThreadQueries()
            .build().also { it.openHelper.writableDatabase } // 먼저 열어 둔다: 닫을 때 여는 중이면 서로 기다려 멈춘다
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `home opens after saving a routine workout when a run exists`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workouts = WorkoutRepositoryImpl(
            workoutDao = db.workoutDao(),
            routineDao = db.routineDao(),
            exerciseDao = db.exerciseDao(),
            personalRecordDao = db.personalRecordDao(),
        )
        val routines = RoutineRepositoryImpl(db.routineDao())
        val exercises = ExerciseRepositoryImpl(db.exerciseDao())
        val runs = RunRepositoryImpl(db.runDao())
        val file = File(context.cacheDir, "home-crash-${System.nanoTime()}.preferences_pb")
        val settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { file },
        )

        runBlocking {
            // 예전에 러닝을 한 번 했다 -> run id 1
            val runId = runs.startRun(RunGoalType.FREE, 0.0)
            runs.finishRun(runId, System.currentTimeMillis(), 3_000.0, 1_200, 400.0, 380.0, 200, 3_000)

            // 루틴을 만들고 그 루틴으로 첫 운동을 해서 저장했다 -> workout id 1
            val squatId = exercises.addExercise(
                Exercise(id = 0, name = "스쿼트", category = ExerciseCategory.BARBELL, bodyPart = BodyPart.LEG),
            )
            val squat = exercises.getExercise(squatId)!!
            val routineId = routines.saveRoutine(
                Routine(name = "하체", items = listOf(RoutineItem(exercise = squat, orderIndex = 0, defaultSets = 3))),
            )
            val workoutId = workouts.startWorkout(routineId)
            val firstSet = db.workoutDao().getWorkoutDetail(workoutId)!!.exercises.single().sets.first()
            workouts.setCompleted(firstSet.id, 100.0, 5, true)
            workouts.finishWorkout(workoutId)

            check(workoutId == runId) { "재현 조건: 헬스와 러닝의 id 가 같아야 한다 ($workoutId, $runId)" }
        }

        val viewModel = HomeViewModel(
            workoutRepository = workouts,
            routineRepository = routines,
            runRepository = runs,
            settingsRepository = settings,
            runTracker = RunTracker(runs, settings, SensorStepCounter(context)),
        )

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
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.recentRuns.isNotEmpty() }
        compose.waitForIdle()

        // 헬스 기록과 러닝 기록이 둘 다 화면에 나와야 한다.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("하체"))
        compose.onNodeWithText("하체").assertIsDisplayed()
        // 거리는 주간 요약에도 나오므로, 러닝 카드에만 있는 평균 페이스로 확인한다.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("평균 6'40", substring = true))
        compose.onNodeWithText("평균 6'40", substring = true).assertIsDisplayed()
    }

    /** 기록이 복구되며 Lap 번호가 겹쳐도 러닝 결과 · 상세 화면이 죽지 않는다. */
    @Test
    fun `run laps with the same number do not crash`() {
        val laps = listOf(
            RunLap(id = 1, lapNumber = 1, distanceMeters = 1_000.0, durationSeconds = 300, paceSecPerKm = 300.0),
            RunLap(id = 2, lapNumber = 1, distanceMeters = 1_000.0, durationSeconds = 310, paceSecPerKm = 310.0),
        )

        compose.setContent {
            HealthTheme {
                LazyColumn { runLapItems(laps, DistanceUnit.KM) }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("5'10\"", substring = true).assertIsDisplayed()
    }
}
