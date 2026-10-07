package com.windowhyun.health.data

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.local.entity.ExerciseEntity
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RoutineRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.ExerciseDeleteResult
import com.windowhyun.health.domain.model.ExerciseEditResult
import com.windowhyun.health.domain.model.ExerciseUsage
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.model.RoutineItem
import com.windowhyun.health.ui.gym.RoutineEditViewModel
import com.windowhyun.health.ui.gym.deleteConfirmText
import com.windowhyun.health.ui.gym.editErrorMessage
import com.windowhyun.health.ui.navigation.Routes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 직접 만든 종목을 고치고 지우는 규칙. 종목을 지우면 그 종목의 운동 기록이 연쇄로 지워지는
 * 구조라서, 기록이 있는 종목을 지우지 못하게 막는 것이 가장 중요하다.
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseManagementTest {

    private lateinit var db: HealthDatabase
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var routines: RoutineRepositoryImpl
    private lateinit var workouts: WorkoutRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).allowMainThreadQueries().build().also { it.openHelper.writableDatabase } // 먼저 열어 둔다: 닫을 때 여는 중이면 서로 기다려 멈춘다
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        routines = RoutineRepositoryImpl(db.routineDao())
        workouts = WorkoutRepositoryImpl(db.workoutDao(), db.routineDao(), db.exerciseDao(), db.personalRecordDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun custom(name: String, type: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_REPS): Long =
        exercises.addExercise(
            Exercise(id = 0, name = name, category = ExerciseCategory.OTHER, bodyPart = BodyPart.CORE, trackingType = type),
        )

    /** 이 종목으로 운동 한 번을 기록해 둔다. */
    private suspend fun trainWith(exerciseId: Long): Long {
        val workoutId = workouts.startWorkout(null)
        val weId = workouts.addExerciseToWorkout(workoutId, exerciseId)
        workouts.setCompleted(db.workoutDao().getSets(weId).first().id, 20.0, 10, true)
        workouts.finishWorkout(workoutId)
        return workoutId
    }

    @Test
    fun `renames a custom exercise and keeps its history`() = runTest {
        val id = custom("케틀벨 스윙")
        val workoutId = trainWith(id)

        val result = exercises.editExercise(id, "  케틀벨 스윙 (러시안)  ", ExerciseCategory.KETTLEBELL, BodyPart.FULL_BODY, ExerciseTrackingType.WEIGHT_REPS)

        assertThat(result).isEqualTo(ExerciseEditResult.Saved)
        val saved = exercises.getExercise(id)!!
        assertThat(saved.name).isEqualTo("케틀벨 스윙 (러시안)")
        assertThat(saved.bodyPart).isEqualTo(BodyPart.FULL_BODY)
        assertThat(workouts.getWorkout(workoutId)!!.exercises.single().exercise.name).isEqualTo("케틀벨 스윙 (러시안)")
    }

    @Test
    fun `built in exercises can be neither edited nor deleted`() = runTest {
        // 테스트 DB 에는 기본 종목이 들어 있지 않다. 기본 종목 하나를 직접 넣는다.
        val builtInId = db.exerciseDao().insert(
            ExerciseEntity(name = "벤치프레스", category = ExerciseCategory.BARBELL, bodyPart = BodyPart.CHEST, isBuiltIn = true),
        )
        val builtIn = exercises.getExercise(builtInId)!!

        assertThat(exercises.editExercise(builtIn.id, "다른 이름", builtIn.category, builtIn.bodyPart, builtIn.trackingType))
            .isEqualTo(ExerciseEditResult.NotEditable)
        assertThat(exercises.deleteCustomExercise(builtIn.id)).isEqualTo(ExerciseDeleteResult.NotDeletable)
        assertThat(exercises.getExercise(builtIn.id)).isNotNull()
    }

    @Test
    fun `refuses blank and duplicate names`() = runTest {
        val first = custom("A 운동")
        val second = custom("B 운동")

        assertThat(exercises.editExercise(second, "   ", ExerciseCategory.OTHER, BodyPart.CORE, ExerciseTrackingType.WEIGHT_REPS))
            .isEqualTo(ExerciseEditResult.NameBlank)
        assertThat(exercises.editExercise(second, "A 운동", ExerciseCategory.OTHER, BodyPart.CORE, ExerciseTrackingType.WEIGHT_REPS))
            .isEqualTo(ExerciseEditResult.NameTaken)
        // 자기 이름을 그대로 두고 다른 것만 바꾸는 것은 된다.
        assertThat(exercises.editExercise(first, "A 운동", ExerciseCategory.CABLE, BodyPart.ARM, ExerciseTrackingType.WEIGHT_REPS))
            .isEqualTo(ExerciseEditResult.Saved)
        assertThat(exercises.getExercise(second)!!.name).isEqualTo("B 운동")
    }

    @Test
    fun `tracking type is locked once there is history`() = runTest {
        val free = custom("자유 종목")
        val used = custom("쓴 종목")
        trainWith(used)

        assertThat(exercises.editExercise(free, "자유 종목", ExerciseCategory.OTHER, BodyPart.CORE, ExerciseTrackingType.TIME))
            .isEqualTo(ExerciseEditResult.Saved)
        assertThat(exercises.editExercise(used, "쓴 종목", ExerciseCategory.OTHER, BodyPart.CORE, ExerciseTrackingType.TIME))
            .isEqualTo(ExerciseEditResult.TrackingTypeLocked)
        assertThat(exercises.getExercise(used)!!.trackingType).isEqualTo(ExerciseTrackingType.WEIGHT_REPS)
    }

    /** 가장 중요한 안전장치: 기록이 있는 종목을 지우면 그 기록이 사라지므로 지우지 않는다. */
    @Test
    fun `refuses to delete an exercise that has workout history`() = runTest {
        val id = custom("기록 있는 종목")
        val workoutId = trainWith(id)

        val result = exercises.deleteCustomExercise(id)

        assertThat(result).isEqualTo(ExerciseDeleteResult.InUse(1))
        assertThat(exercises.getExercise(id)).isNotNull()
        assertThat(workouts.getWorkout(workoutId)!!.exercises).hasSize(1)
    }

    /** 진행 중인 운동에 들어 있는 종목도 마찬가지다. */
    @Test
    fun `refuses to delete an exercise in a workout in progress`() = runTest {
        val id = custom("진행 중 종목")
        val workoutId = workouts.startWorkout(null)
        workouts.addExerciseToWorkout(workoutId, id)

        assertThat(exercises.deleteCustomExercise(id)).isEqualTo(ExerciseDeleteResult.InUse(1))
    }

    @Test
    fun `deletes an unused exercise and drops it from routines`() = runTest {
        val keep = custom("남길 종목")
        val gone = custom("지울 종목")
        val routineId = routines.saveRoutine(
            Routine(
                name = "루틴",
                items = listOf(
                    RoutineItem(exercise = exercises.getExercise(keep)!!, orderIndex = 0, defaultSets = 3),
                    RoutineItem(exercise = exercises.getExercise(gone)!!, orderIndex = 1, defaultSets = 3),
                ),
            ),
        )
        assertThat(exercises.getUsage(gone)).isEqualTo(ExerciseUsage(workoutCount = 0, routineCount = 1))

        assertThat(exercises.deleteCustomExercise(gone)).isEqualTo(ExerciseDeleteResult.Deleted)

        assertThat(exercises.getExercise(gone)).isNull()
        assertThat(routines.getRoutine(routineId)!!.items.map { it.exercise.name }).containsExactly("남길 종목")
    }

    @Test
    fun `messages explain why`() {
        assertThat(editErrorMessage(ExerciseEditResult.Saved)).isNull()
        assertThat(editErrorMessage(ExerciseEditResult.NameTaken)).contains("같은 이름")
        assertThat(deleteConfirmText(ExerciseUsage(3, 0))).contains("지울 수 없습니다")
        assertThat(deleteConfirmText(ExerciseUsage(0, 2))).contains("루틴 2개")
    }

    // ----- 루틴 편집 중에 종목을 지우거나 고치면 목록에도 반영 -----

    private fun routineEditViewModel() = RoutineEditViewModel(
        routines, exercises, SavedStateHandle(mapOf(Routes.ARG_ROUTINE_ID to 0L)),
    )

    /** 지운 종목이 편집 중인 목록에 남으면 저장할 때 앱이 죽는다. */
    @Test
    fun `deleting an exercise removes it from the routine being edited`() = runTest {
        val a = custom("가 종목")
        val b = custom("나 종목")
        val viewModel = routineEditViewModel()
        viewModel.addExercise(exercises.getExercise(a)!!)
        viewModel.addExercise(exercises.getExercise(b)!!)

        viewModel.exerciseManager.delete(a)

        val items = viewModel.uiState.value.items
        assertThat(items.map { it.exercise.name }).containsExactly("나 종목")
        assertThat(items.single().orderIndex).isEqualTo(0)
    }

    @Test
    fun `renaming shows the new name in the routine being edited`() = runTest {
        val id = custom("옛 이름")
        val viewModel = routineEditViewModel()
        viewModel.addExercise(exercises.getExercise(id)!!)

        viewModel.exerciseManager.edit(id, "새 이름", ExerciseCategory.OTHER, BodyPart.CORE, ExerciseTrackingType.WEIGHT_REPS)

        assertThat(viewModel.uiState.value.items.single().exercise.name).isEqualTo("새 이름")
    }

    /** 지우지 못한 종목은 목록에서도 그대로 둔다. */
    @Test
    fun `a refused delete leaves the routine being edited alone`() = runTest {
        val id = custom("쓴 종목")
        trainWith(id)
        val viewModel = routineEditViewModel()
        viewModel.addExercise(exercises.getExercise(id)!!)

        viewModel.exerciseManager.delete(id)

        assertThat(viewModel.uiState.value.items).hasSize(1)
    }
}
