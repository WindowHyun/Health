package com.windowhyun.health.ui.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.core.model.BodyPart
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
import com.windowhyun.health.domain.model.ThemeMode
import com.windowhyun.health.ui.HealthBottomBar
import com.windowhyun.health.ui.home.HomeScreen
import com.windowhyun.health.ui.home.HomeViewModel
import com.windowhyun.health.ui.navigation.TopLevelDestination
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
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * 홈 화면을 실제 데이터로 그려 PNG 로 남긴다. 눈으로 보는 용도이고 검증은 하지 않는다.
 * build/screenshots 에 저장한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class HomeScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: HealthDatabase
    private lateinit var viewModel: HomeViewModel
    private lateinit var workouts: WorkoutRepositoryImpl
    private lateinit var tracker: RunTracker
    private var todayRoutineId = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workouts = WorkoutRepositoryImpl(db.workoutDao(), db.routineDao(), db.exerciseDao(), db.personalRecordDao())
        val routines = RoutineRepositoryImpl(db.routineDao())
        val exercises = ExerciseRepositoryImpl(db.exerciseDao())
        val runs = RunRepositoryImpl(db.runDao())
        val file = File(context.cacheDir, "home-shot-${System.nanoTime()}.preferences_pb")
        val settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { file },
        )
        runBlocking { seed(workouts, routines, exercises, runs) }
        tracker = RunTracker(runs, settings, SensorStepCounter(context))
        viewModel = HomeViewModel(workouts, routines, runs, settings, tracker)
    }

    @After
    fun tearDown() = db.close()

    /** 일주일쯤 쓴 앱처럼: 오늘 예정 루틴 하나, 최근 운동 셋, 최근 러닝 셋. */
    private suspend fun seed(
        workouts: WorkoutRepositoryImpl,
        routines: RoutineRepositoryImpl,
        exercises: ExerciseRepositoryImpl,
        runs: RunRepositoryImpl,
    ) {
        suspend fun exercise(name: String, part: BodyPart) = exercises.getExercise(
            exercises.addExercise(Exercise(id = 0, name = name, category = ExerciseCategory.BARBELL, bodyPart = part)),
        )!!
        val bench = exercise("벤치프레스", BodyPart.CHEST)
        val squat = exercise("스쿼트", BodyPart.LEG)
        val row = exercise("바벨 로우", BodyPart.BACK)
        suspend fun routine(name: String, vararg items: Exercise, today: Boolean = false) = routines.saveRoutine(
            Routine(
                name = name,
                scheduledDays = if (today) setOf(LocalDate.now().dayOfWeek) else emptySet(),
                items = items.mapIndexed { i, e -> RoutineItem(exercise = e, orderIndex = i, defaultSets = 4) },
            ),
        )
        val chest = routine("가슴 · 삼두", bench, row, today = true)
        val leg = routine("하체 데이", squat, row)
        val back = routine("등 · 이두", row, bench)

        suspend fun finishedWorkout(routineId: Long, daysAgo: Long, kg: Double, minutes: Long) {
            val id = workouts.startWorkout(routineId)
            db.workoutDao().getWorkoutDetail(id)!!.exercises.flatMap { it.sets }.forEach {
                workouts.setCompleted(it.id, kg, 8, true)
            }
            workouts.finishWorkout(id)
            val stored = db.workoutDao().getWorkout(id)!!
            val shift = daysAgo * 86_400_000L
            db.workoutDao().updateWorkout(
                stored.copy(
                    date = stored.date - daysAgo,
                    startTime = stored.startTime - shift,
                    endTime = stored.endTime?.minus(shift),
                    durationSeconds = minutes * 60,
                ),
            )
        }
        todayRoutineId = chest
        finishedWorkout(leg, daysAgo = 1, kg = 100.0, minutes = 68)
        finishedWorkout(back, daysAgo = 3, kg = 70.0, minutes = 55)
        finishedWorkout(chest, daysAgo = 9, kg = 80.0, minutes = 62)

        suspend fun finishedRun(daysAgo: Long, meters: Double, seconds: Long) {
            val id = runs.startRun(RunGoalType.FREE, 0.0)
            val pace = seconds / (meters / 1000)
            runs.finishRun(id, System.currentTimeMillis(), meters, seconds, pace, pace - 12, (meters * 0.06).toInt(), 0)
            val stored = db.runDao().getRun(id)!!
            val shift = daysAgo * 86_400_000L
            db.runDao().updateRun(stored.copy(date = stored.date - daysAgo, startTime = stored.startTime - shift, endTime = stored.endTime?.minus(shift)))
        }
        finishedRun(daysAgo = 2, meters = 8_200.0, seconds = 2_580)
        finishedRun(daysAgo = 8, meters = 5_000.0, seconds = 1_590)
        finishedRun(daysAgo = 12, meters = 10_400.0, seconds = 3_420)
    }

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
        // 실제 시간으로 기다린다(DB 는 다른 스레드에서 읽는다).
        val deadline = System.currentTimeMillis() + 5_000
        while (viewModel.uiState.value.recentRuns.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 운동 · 러닝이 진행 중인 홈. 이어하기 띠가 보이고 오늘 루틴의 시작은 막힌다. */
    private fun startInProgress() = runBlocking {
        workouts.startWorkout(todayRoutineId)
        tracker.start(com.windowhyun.health.domain.model.RunGoal(RunGoalType.FREE, 0.0))
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
