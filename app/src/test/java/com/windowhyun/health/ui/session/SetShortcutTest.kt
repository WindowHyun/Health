package com.windowhyun.health.ui.session

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.model.SetType
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.core.notification.RestTimerNotifier
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.ThemeMode
import com.windowhyun.health.domain.model.WorkoutSet
import com.windowhyun.health.domain.usecase.SetCarryOver
import com.windowhyun.health.ui.navigation.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** 헬스 세트를 숫자판 없이 맞추는 두 가지 지름길: 뒤 세트가 따라가는 것과 ± 버튼. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SetShortcutTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HealthDatabase
    private lateinit var workouts: WorkoutRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var settings: SettingsRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).allowMainThreadQueries().build()
        workouts = WorkoutRepositoryImpl(db.workoutDao(), db.routineDao(), db.exerciseDao(), db.personalRecordDao())
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher)) {
                File(context.cacheDir, "set-shortcut-${System.nanoTime()}.preferences_pb")
            },
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    // ----- 따라가기 규칙(화면 없이) -----

    private fun set(id: Long, number: Int, kg: Double, completed: Boolean = false, type: SetType = SetType.NORMAL) =
        WorkoutSet(id = id, setNumber = number, weightKg = kg, reps = 5, completed = completed, setType = type)

    @Test
    fun `later sets with the same weight follow the edit`() {
        val sets = listOf(set(1, 1, 60.0), set(2, 2, 60.0), set(3, 3, 60.0))

        val followers = SetCarryOver.followers(sets, sets[0], 62.5)

        assertThat(followers.map { it.id }).containsExactly(2L, 3L)
    }

    @Test
    fun `sets that were set apart on purpose are left alone`() {
        val sets = listOf(
            set(1, 1, 60.0),
            set(2, 2, 50.0), // 일부러 가볍게 잡아 둔 세트
            set(3, 3, 60.0, completed = true), // 이미 끝낸 세트
            set(4, 4, 60.0, type = SetType.WARMUP), // 워밍업
            set(5, 5, 60.0),
        )

        val followers = SetCarryOver.followers(sets, sets[0], 65.0)

        assertThat(followers.map { it.id }).containsExactly(5L)
    }

    @Test
    fun `earlier sets never change and only an unfinished normal set can lead`() {
        val sets = listOf(set(1, 1, 60.0), set(2, 2, 60.0), set(3, 3, 60.0))

        assertThat(SetCarryOver.followers(sets, sets[1], 70.0).map { it.id }).containsExactly(3L)
        assertThat(SetCarryOver.followers(sets, sets[0].copy(completed = true), 70.0)).isEmpty()
        assertThat(SetCarryOver.followers(sets, sets[0].copy(setType = SetType.WARMUP), 70.0)).isEmpty()
        assertThat(SetCarryOver.followers(sets, sets[0], 60.0)).isEmpty()
    }

    // ----- 세션에서 -----

    private suspend fun newWorkoutWith(weights: List<Double>): List<Long> {
        val exerciseId = exercises.addExercise(
            Exercise(0, "벤치", ExerciseCategory.BARBELL, BodyPart.CHEST),
        )
        val workoutId = workouts.startWorkout(null)
        val workoutExerciseId = workouts.addExerciseToWorkout(workoutId, exerciseId)
        // 새 운동에는 세트가 1개 있다. 모자란 만큼 더한다.
        val missing = weights.size - workouts.getWorkout(workoutId)!!.exercises.single().sets.size
        repeat(missing) { workouts.addSet(workoutExerciseId) }
        val sets = workouts.getWorkout(workoutId)!!.exercises.single().sets
        sets.zip(weights).forEach { (set, kg) -> workouts.setCompleted(set.id, kg, 5, false, 0) }
        return sets.map { it.id }
    }

    private fun viewModel() = WorkoutSessionViewModel(
        workoutRepository = workouts,
        exerciseRepository = exercises,
        settingsRepository = settings,
        restTimerNotifier = RestTimerNotifier(context),
        savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_WORKOUT_ID to 1L)),
        clock = { dispatcher.scheduler.currentTime },
    )

    private fun weightsNow(): List<Double> = runBlocking {
        workouts.getWorkout(1L)!!.exercises.single().sets.map { it.weightKg }
    }

    /**
     * 저장은 Room 의 다른 스레드에서 끝나므로, 기대한 값이 될 때까지 실제 시간으로 잠깐 기다린다.
     * (경과 시간 타이머가 끝나지 않는 코루틴이라 테스트 시계를 끝까지 돌리면 멈춘다.)
     */
    private fun awaitWeights(vararg expected: Double) {
        val deadline = System.currentTimeMillis() + 5_000
        while (weightsNow() != expected.toList() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertThat(weightsNow()).containsExactlyElementsIn(expected.toList()).inOrder()
    }

    @Test
    fun `editing the first set moves the sets that matched it`() = runBlocking {
        newWorkoutWith(listOf(60.0, 60.0, 50.0))
        val viewModel = viewModel()
        val first = workouts.getWorkout(1L)!!.exercises.single().sets.first()

        viewModel.updateSetValues(first, 62.5, 5, 0)
        awaitWeights(62.5, 62.5, 50.0)
        viewModel.viewModelScope.cancel()
    }

    /** 글자를 칠 때마다 값이 들어오고 화면의 세트는 한 글자 전 값이다. 그래도 끝값으로 모인다. */
    @Test
    fun `typing digit by digit ends with every matching set on the typed weight`() = runBlocking {
        newWorkoutWith(listOf(60.0, 60.0, 60.0))
        val viewModel = viewModel()
        val staleFirst = workouts.getWorkout(1L)!!.exercises.single().sets.first()

        // 60 을 지우고 62.5 를 친다: 6, 62, 62., 62.5 ... 화면은 늘 처음 세트(60)를 들고 있다.
        listOf(6.0, 62.0, 62.0, 62.5).forEach { kg -> viewModel.updateSetValues(staleFirst, kg, 5, 0) }
        awaitWeights(62.5, 62.5, 62.5)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `a finished set keeps its record when an earlier set is edited`() = runBlocking {
        val ids = newWorkoutWith(listOf(60.0, 60.0, 60.0))
        workouts.setCompleted(ids[1], 60.0, 5, true, 0)
        val viewModel = viewModel()
        val first = workouts.getWorkout(1L)!!.exercises.single().sets.first()

        viewModel.updateSetValues(first, 70.0, 5, 0)
        awaitWeights(70.0, 60.0, 70.0)
        viewModel.viewModelScope.cancel()
    }

    // ----- ± 버튼 -----

    private fun showRow(
        quickAdjust: Boolean,
        completed: Boolean = false,
        unit: WeightUnit = WeightUnit.KG,
        type: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_REPS,
        onValues: (Double, Int, Int) -> Unit = { _, _, _ -> },
        weightKg: Double = 60.0,
        reps: Int = 8,
        seconds: Int = 0,
    ) {
        compose.setContent {
            HealthTheme(themeMode = ThemeMode.LIGHT) {
                MaterialTheme {
                    SetRow(
                        set = WorkoutSet(
                            id = 1, setNumber = 1, weightKg = weightKg, reps = reps, completed = completed,
                            durationSeconds = seconds,
                        ),
                        weightUnit = unit,
                        trackingType = type,
                        quickAdjust = quickAdjust,
                        onValuesChange = onValues,
                        onToggleCompleted = { _, _, _ -> },
                        onRemove = {},
                    )
                }
            }
        }
    }

    @Test
    fun `plus and minus change the weight and reps without the keyboard`() {
        val seen = mutableListOf<Triple<Double, Int, Int>>()
        showRow(quickAdjust = true, onValues = { w, r, d -> seen += Triple(w, r, d) })

        compose.onNodeWithContentDescription("무게 2.5kg 늘리기").performClick()
        compose.onNodeWithContentDescription("무게 2.5kg 늘리기").performClick()
        compose.onNodeWithContentDescription("무게 2.5kg 줄이기").performClick()
        compose.onNodeWithContentDescription("횟수 1회 늘리기").performClick()

        assertThat(seen.map { it.first }).containsExactly(62.5, 65.0, 62.5, 62.5).inOrder()
        assertThat(seen.last().second).isEqualTo(9)
    }

    @Test
    fun `pounds step by five`() {
        val seen = mutableListOf<Double>()
        // 135lb = 61.23kg. 한 번 누르면 140lb 가 된다.
        showRow(quickAdjust = true, unit = WeightUnit.LB, weightKg = WeightUnit.LB.toKg(135.0), onValues = { w, _, _ -> seen += w })

        compose.onNodeWithContentDescription("무게 5lb 늘리기").performClick()

        assertThat(WeightUnit.LB.fromKg(seen.single())).isWithin(0.05).of(140.0)
    }

    @Test
    fun `weight and reps never go below zero`() {
        val seen = mutableListOf<Triple<Double, Int, Int>>()
        showRow(quickAdjust = true, weightKg = 1.0, reps = 0, onValues = { w, r, d -> seen += Triple(w, r, d) })

        compose.onNodeWithContentDescription("무게 2.5kg 줄이기").performClick()
        compose.onNodeWithContentDescription("횟수 1회 줄이기").performClick()

        assertThat(seen.first().first).isEqualTo(0.0)
        assertThat(seen.last().second).isEqualTo(0)
    }

    @Test
    fun `timed sets step by seconds`() {
        val seen = mutableListOf<Int>()
        showRow(quickAdjust = true, type = ExerciseTrackingType.TIME, seconds = 58, onValues = { _, _, d -> seen += d })

        compose.onNodeWithContentDescription("시간 5초 늘리기").performClick()

        assertThat(seen.single()).isEqualTo(63)
        compose.onNodeWithContentDescription("무게 2.5kg 늘리기").assertDoesNotExist()
    }

    @Test
    fun `buttons only show on the set you are doing`() {
        showRow(quickAdjust = false)
        compose.onNodeWithContentDescription("무게 2.5kg 늘리기").assertDoesNotExist()
    }

    @Test
    fun `a finished set has no plus and minus`() {
        showRow(quickAdjust = true, completed = true)
        compose.onNodeWithContentDescription("무게 2.5kg 늘리기").assertDoesNotExist()
    }

    @Test
    fun `the buttons appear on the set in progress`() {
        showRow(quickAdjust = true)
        compose.onNodeWithContentDescription("무게 2.5kg 늘리기").assertExists()
    }
}
