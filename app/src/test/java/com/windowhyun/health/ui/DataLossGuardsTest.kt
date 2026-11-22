package com.windowhyun.health.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.data.FakeSettingsRepository
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RoutineRepositoryImpl
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.model.WorkoutExerciseRecord
import com.windowhyun.health.domain.model.WorkoutSet
import com.windowhyun.health.ui.gym.RoutineEditViewModel
import com.windowhyun.health.ui.navigation.Routes
import com.windowhyun.health.ui.session.SetRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 기록을 잘못 눌러 잃지 않게 하는 확인 · 경고의 회귀 테스트.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DataLossGuardsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: HealthDatabase
    private lateinit var routines: RoutineRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        routines = RoutineRepositoryImpl(db.routineDao())
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun exercise(name: String = "벤치") = Exercise(
        id = 1,
        name = name,
        category = ExerciseCategory.BARBELL,
        bodyPart = BodyPart.CHEST,
    )

    private fun set(number: Int, completed: Boolean) =
        WorkoutSet(id = number.toLong(), setNumber = number, weightKg = 60.0, reps = 8, completed = completed)

    private fun workout(vararg sets: WorkoutSet) = Workout(
        date = LocalDate.of(2026, 11, 22),
        startTime = 0,
        exercises = listOf(WorkoutExerciseRecord(id = 1, exercise = exercise(), orderIndex = 0, sets = sets.toList())),
    )

    // ----- 세트 삭제 확인 -----

    private fun showSetRow(set: WorkoutSet, onRemove: () -> Unit) {
        compose.setContent {
            HealthTheme {
                SetRow(
                    set = set,
                    weightUnit = WeightUnit.KG,
                    onValuesChange = { _, _, _ -> },
                    onToggleCompleted = { _, _, _ -> },
                    onRemove = onRemove,
                )
            }
        }
    }

    @Test
    fun `removing a completed set asks first and removes only after confirming`() {
        var removed = 0
        showSetRow(set(1, completed = true)) { removed++ }

        compose.onNodeWithContentDescription("세트 삭제").performClick()

        compose.onNodeWithText("세트를 삭제할까요?").assertIsDisplayed()
        assertThat(removed).isEqualTo(0)

        compose.onNodeWithText("삭제").performClick()
        assertThat(removed).isEqualTo(1)
    }

    @Test
    fun `cancelling the confirmation keeps the completed set`() {
        var removed = 0
        showSetRow(set(1, completed = true)) { removed++ }

        compose.onNodeWithContentDescription("세트 삭제").performClick()
        compose.onNodeWithText("취소").performClick()

        assertThat(removed).isEqualTo(0)
    }

    @Test
    fun `removing a set that is not done yet needs no confirmation`() {
        var removed = 0
        showSetRow(set(1, completed = false)) { removed++ }

        compose.onNodeWithContentDescription("세트 삭제").performClick()

        assertThat(removed).isEqualTo(1)
    }

    // ----- 루틴 편집: 저장하지 않은 변경 -----

    private fun editor(routineId: Long = 0) = RoutineEditViewModel(
        routineRepository = routines,
        exerciseRepository = exercises,
        settingsRepository = FakeSettingsRepository(),
        savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_ROUTINE_ID to routineId)),
    )

    @Test
    fun `a fresh routine editor has nothing to lose`() = runTest(dispatcher) {
        assertThat(editor().hasUnsavedChanges()).isFalse()
    }

    @Test
    fun `typing a name or adding an exercise counts as unsaved`() = runTest(dispatcher) {
        val byName = editor()
        byName.setName("상체")
        assertThat(byName.hasUnsavedChanges()).isTrue()

        val byExercise = editor()
        byExercise.addExercise(exercise())
        assertThat(byExercise.hasUnsavedChanges()).isTrue()
    }

    @Test
    fun `undoing the edit by hand is not unsaved`() = runTest(dispatcher) {
        val viewModel = editor()
        viewModel.setName("상체")
        viewModel.setName("")

        assertThat(viewModel.hasUnsavedChanges()).isFalse()
    }

    @Test
    fun `an opened routine is clean until it is edited and clean again after saving`() = runTest(dispatcher) {
        val id = exercises.addExercise(exercise("스쿼트").copy(id = 0))
        val saved = routines.saveRoutine(
            com.windowhyun.health.domain.model.Routine(
                name = "하체",
                items = listOf(
                    com.windowhyun.health.domain.model.RoutineItem(
                        exercise = exercises.getExercise(id)!!,
                        orderIndex = 0,
                        defaultSets = 3,
                    ),
                ),
            ),
        )

        val viewModel = editor(saved)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.name).isEqualTo("하체")
        assertThat(viewModel.hasUnsavedChanges()).isFalse()

        viewModel.changeSets(0, +1)
        assertThat(viewModel.hasUnsavedChanges()).isTrue()

        viewModel.save()
        advanceUntilIdle()
        assertThat(viewModel.hasUnsavedChanges()).isFalse()
    }
}
