package com.windowhyun.health.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.notification.RestTimerNotifier
import com.windowhyun.health.data.backup.BackupRepositoryImpl
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RoutineRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.model.RoutineItem
import com.windowhyun.health.ui.gym.RoutineEditViewModel
import com.windowhyun.health.ui.navigation.Routes
import com.windowhyun.health.ui.session.WorkoutSessionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 슈퍼셋: 저장, 루틴 → 세션 복사, 묶음 정리, 쉬는 시간 규칙, 백업.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SupersetTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var db: HealthDatabase
    private lateinit var workouts: WorkoutRepositoryImpl
    private lateinit var routines: RoutineRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var settings: SettingsRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .allowMainThreadQueries()
            .build().also { it.openHelper.writableDatabase } // 먼저 열어 둔다: 닫을 때 여는 중이면 서로 기다려 멈춘다
        workouts = WorkoutRepositoryImpl(db.workoutDao(), db.routineDao(), db.exerciseDao(), db.personalRecordDao())
        routines = RoutineRepositoryImpl(db.routineDao())
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        val file = File(context.cacheDir, "superset-${System.nanoTime()}.preferences_pb")
        settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher)) { file },
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun exercise(name: String): Exercise {
        val id = exercises.addExercise(
            Exercise(id = 0, name = name, category = ExerciseCategory.BARBELL, bodyPart = BodyPart.CHEST),
        )
        return exercises.getExercise(id)!!
    }

    private fun item(exercise: Exercise, group: Int = 0) = RoutineItem(
        exercise = exercise,
        orderIndex = 0,
        defaultSets = 2,
        supersetGroup = group,
    )

    /** 운동 4개: 벤치 - (로우, 스쿼트) 슈퍼셋 - 데드. */
    private suspend fun routineWithSuperset(): Long {
        val names = listOf("벤치", "로우", "스쿼트", "데드")
        val list = names.map { exercise(it) }
        return routines.saveRoutine(
            Routine(
                name = "테스트",
                items = listOf(item(list[0]), item(list[1], 7), item(list[2], 7), item(list[3])),
            ),
        )
    }

    private suspend fun groupsOf(workoutId: Long) =
        workouts.getWorkout(workoutId)!!.exercises.map { it.supersetGroup }

    /** 루틴에 저장한 묶음이 그대로 저장된다. 번호는 1부터 다시 매겨진다. */
    @Test
    fun `routine keeps its superset and renumbers it`() = runTest(dispatcher) {
        val routineId = routineWithSuperset()

        val saved = routines.getRoutine(routineId)!!

        assertThat(saved.items.map { it.supersetGroup }).containsExactly(0, 1, 1, 0).inOrder()
    }

    /** 혼자 남은 묶음 번호는 저장할 때 풀린다. */
    @Test
    fun `routine save drops a lone superset member`() = runTest(dispatcher) {
        val a = exercise("가")
        val b = exercise("나")
        val id = routines.saveRoutine(Routine(name = "x", items = listOf(item(a, 3), item(b))))

        assertThat(routines.getRoutine(id)!!.items.map { it.supersetGroup }).containsExactly(0, 0).inOrder()
    }

    /** 루틴으로 운동을 시작하면 묶음도 같이 따라온다. */
    @Test
    fun `starting from a routine copies the superset`() = runTest(dispatcher) {
        val routineId = routineWithSuperset()

        val workoutId = workouts.startWorkout(routineId)

        assertThat(groupsOf(workoutId)).containsExactly(0, 1, 1, 0).inOrder()
    }

    @Test
    fun `link and unlink change only the targeted exercise`() = runTest(dispatcher) {
        val workoutId = workouts.startWorkout(null)
        val ids = listOf("가", "나", "다").map { workouts.addExerciseToWorkout(workoutId, exercise(it).id) }

        workouts.linkSupersetWithPrevious(ids[1])
        assertThat(groupsOf(workoutId)).containsExactly(1, 1, 0).inOrder()

        workouts.linkSupersetWithPrevious(ids[2])
        assertThat(groupsOf(workoutId)).containsExactly(1, 1, 1).inOrder()

        workouts.unlinkSuperset(ids[2])
        assertThat(groupsOf(workoutId)).containsExactly(1, 1, 0).inOrder()

        workouts.unlinkSuperset(ids[0])
        assertThat(groupsOf(workoutId)).containsExactly(0, 0, 0).inOrder()
    }

    /** 첫 운동은 앞이 없어 묶을 수 없다. */
    @Test
    fun `the first exercise cannot be linked`() = runTest(dispatcher) {
        val workoutId = workouts.startWorkout(null)
        val first = workouts.addExerciseToWorkout(workoutId, exercise("가").id)
        workouts.addExerciseToWorkout(workoutId, exercise("나").id)

        workouts.linkSupersetWithPrevious(first)

        assertThat(groupsOf(workoutId)).containsExactly(0, 0).inOrder()
    }

    /** 묶음 한쪽을 지우면 남은 운동이 혼자 묶인 채로 남지 않는다. */
    @Test
    fun `removing one member dissolves a two exercise superset`() = runTest(dispatcher) {
        val routineId = routineWithSuperset()
        val workoutId = workouts.startWorkout(routineId)
        val second = workouts.getWorkout(workoutId)!!.exercises[2].id

        workouts.removeWorkoutExercise(second)

        assertThat(groupsOf(workoutId)).containsExactly(0, 0, 0).inOrder()
    }

    @Test
    fun `removing a non member leaves the superset alone`() = runTest(dispatcher) {
        val routineId = routineWithSuperset()
        val workoutId = workouts.startWorkout(routineId)
        val last = workouts.getWorkout(workoutId)!!.exercises[3].id

        workouts.removeWorkoutExercise(last)

        assertThat(groupsOf(workoutId)).containsExactly(0, 1, 1).inOrder()
    }

    // ----- 백업 -----

    @Test
    fun `backup round trip keeps supersets in routines and workouts`() = runTest(dispatcher) {
        val routineId = routineWithSuperset()
        val workoutId = workouts.startWorkout(routineId)
        val backup = BackupRepositoryImpl(db, db.backupDao(), settings, dispatcher)
        val out = ByteArrayOutputStream().also { backup.exportBackup(it) }

        db.backupDao().deleteAllWorkouts()
        backup.restoreBackup(ByteArrayInputStream(out.toByteArray()))

        assertThat(groupsOf(workoutId)).containsExactly(0, 1, 1, 0).inOrder()
        assertThat(routines.getRoutine(routineId)!!.items.map { it.supersetGroup })
            .containsExactly(0, 1, 1, 0).inOrder()
    }

    // ----- 쉬는 시간 -----

    private fun sessionViewModel(workoutId: Long) = WorkoutSessionViewModel(
        workoutRepository = workouts,
        exerciseRepository = exercises,
        settingsRepository = settings,
        restTimerNotifier = RestTimerNotifier(context),
        savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_WORKOUT_ID to workoutId)),
        clock = { dispatcher.scheduler.currentTime },
    )

    private suspend fun withSession(workoutId: Long, block: suspend (WorkoutSessionViewModel) -> Unit) {
        val viewModel = sessionViewModel(workoutId)
        try {
            block(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    /** DB 는 다른 스레드에서 돈다. 기다리며 테스트 시계를 조금씩 돌린다. */
    private fun TestScope.settle() {
        repeat(30) {
            advanceTimeBy(10)
            Thread.sleep(10)
        }
    }

    /** [index] 번째 운동의 첫 세트를 완료 처리하고 휴식 타이머가 보이는지 돌려준다. */
    private suspend fun TestScope.completeFirstSetShowsRest(
        viewModel: WorkoutSessionViewModel,
        workoutId: Long,
        index: Int,
    ): Boolean {
        viewModel.stopRestTimer()
        val record = workouts.getWorkout(workoutId)!!.exercises[index]
        viewModel.toggleSetCompleted(record.sets.first(), 50.0, 8, 0, record.restSeconds)
        settle()
        return viewModel.restTimer.value.visible
    }

    @Test
    fun `rest starts only after the last exercise of a superset`() = runTest(dispatcher) {
        val workoutId = workouts.startWorkout(routineWithSuperset())

        withSession(workoutId) { viewModel ->
            // 0: 묶음 아님 -> 쉰다
            assertThat(completeFirstSetShowsRest(viewModel, workoutId, 0)).isTrue()
            // 1: 묶음의 첫 운동 -> 쉬지 않고 바로 다음 운동으로
            assertThat(completeFirstSetShowsRest(viewModel, workoutId, 1)).isFalse()
            // 2: 묶음의 마지막 운동 -> 쉰다
            assertThat(completeFirstSetShowsRest(viewModel, workoutId, 2)).isTrue()
            // 3: 묶음 아님 -> 쉰다
            assertThat(completeFirstSetShowsRest(viewModel, workoutId, 3)).isTrue()
        }
    }

    /** 묶음을 풀면 그 운동도 다시 쉰다. */
    @Test
    fun `unlinking restores the rest after that exercise`() = runTest(dispatcher) {
        val workoutId = workouts.startWorkout(routineWithSuperset())
        val second = workouts.getWorkout(workoutId)!!.exercises[2].id
        workouts.unlinkSuperset(second)

        withSession(workoutId) { viewModel ->
            assertThat(completeFirstSetShowsRest(viewModel, workoutId, 1)).isTrue()
        }
    }

    // ----- 루틴 편집 화면 -----

    private fun routineEditor() = RoutineEditViewModel(
        routineRepository = routines,
        exerciseRepository = exercises,
        savedStateHandle = SavedStateHandle(),
    )

    @Test
    fun `routine editor links unlinks and cleans up on removal and move`() = runTest(dispatcher) {
        val viewModel = routineEditor()
        listOf("가", "나", "다", "라").forEach { viewModel.addExercise(exercise(it)) }
        fun groups() = viewModel.uiState.value.items.map { it.supersetGroup }

        viewModel.linkSuperset(1)
        assertThat(groups()).containsExactly(1, 1, 0, 0).inOrder()
        viewModel.linkSuperset(3)
        assertThat(groups()).containsExactly(1, 1, 2, 2).inOrder()

        // 묶음 사이로 옮겨 서로 떨어지면 묶음이 풀린다
        viewModel.moveItem(1, 2)
        assertThat(groups()).containsExactly(0, 0, 0, 0).inOrder()

        viewModel.linkSuperset(1)
        viewModel.linkSuperset(3)
        // 묶음 한쪽을 지우면 남은 운동도 풀린다
        viewModel.removeItem(1)
        assertThat(groups()).containsExactly(0, 1, 1).inOrder()

        viewModel.unlinkSuperset(2)
        assertThat(groups()).containsExactly(0, 0, 0).inOrder()
    }
}
