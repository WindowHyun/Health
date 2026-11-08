package com.windowhyun.health.ui.session

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
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.ui.navigation.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
import java.io.File

/** 전문가 사용성 점검에서 나온 세션 화면 수정: 휴식 타이머 복원, 종료할 때 체크 안 한 세트, 세트 삭제 취소. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionUsabilityFixesTest {

    private val dispatcher = StandardTestDispatcher()
    private var clockSkew = 0L
    private val clock: () -> Long = { dispatcher.scheduler.currentTime + clockSkew + 1_000_000L }

    private lateinit var context: Context
    private lateinit var db: HealthDatabase
    private lateinit var workouts: WorkoutRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var settings: SettingsRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).allowMainThreadQueries()
            .build().also { it.openHelper.writableDatabase }
        workouts = WorkoutRepositoryImpl(db.workoutDao(), db.routineDao(), db.exerciseDao(), db.personalRecordDao())
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher)) { File(context.cacheDir, "usab-${System.nanoTime()}.preferences_pb") },
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.settle() = repeat(30) { advanceTimeBy(10); Thread.sleep(10) }

    private val created = mutableListOf<WorkoutSessionViewModel>()

    /** 화면의 경과 시간 타이머는 끝나지 않는다. 단언이 실패해도 모두 정리해야 테스트가 멈추지 않는다. */
    private fun runVmTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            created.forEach { it.viewModelScope.cancel() }
        }
    }

    private fun viewModel(handle: SavedStateHandle) = WorkoutSessionViewModel(
        workoutRepository = workouts,
        exerciseRepository = exercises,
        settingsRepository = settings,
        restTimerNotifier = RestTimerNotifier(context),
        savedStateHandle = handle,
        clock = clock,
    ).also { created += it }

    private fun handleFor(workoutId: Long) = SavedStateHandle(mapOf(Routes.ARG_WORKOUT_ID to workoutId))

    // ----- 휴식 타이머 복원 -----

    @Test
    fun `a running rest timer comes back after the screen is recreated`() = runVmTest {
        val handle = handleFor(1)
        val first = viewModel(handle)
        first.startRestTimer(90)
        advanceTimeBy(10_000)
        first.viewModelScope.cancel() // 앱이 정리됐다

        val second = viewModel(handle)
        assertThat(second.restTimer.value.visible).isTrue()
        assertThat(second.restTimer.value.totalSeconds).isEqualTo(90)
        assertThat(second.restTimer.value.remainingSeconds).isIn(79..81)
        advanceTimeBy(5_000)
        assertThat(second.restTimer.value.remainingSeconds).isIn(74..76)
        second.viewModelScope.cancel()
    }

    @Test
    fun `a paused rest timer comes back paused with the same time left`() = runVmTest {
        val handle = handleFor(1)
        val first = viewModel(handle)
        first.startRestTimer(60)
        advanceTimeBy(20_000)
        first.toggleRestPause()
        val left = first.restTimer.value.remainingSeconds
        first.viewModelScope.cancel()

        clockSkew += 120_000 // 멈춰 둔 채로 시간이 흘렀다
        val second = viewModel(handle)
        assertThat(second.restTimer.value.paused).isTrue()
        assertThat(second.restTimer.value.remainingSeconds).isIn((left - 1)..left) // 틱 사이의 올림 차이
        second.viewModelScope.cancel()
    }

    @Test
    fun `a rest timer that ended long ago is not brought back`() = runVmTest {
        val handle = handleFor(1)
        val first = viewModel(handle)
        first.startRestTimer(30)
        first.viewModelScope.cancel()

        clockSkew += 10 * 60_000L
        val second = viewModel(handle)
        assertThat(second.restTimer.value.visible).isFalse()
        second.viewModelScope.cancel()
    }

    @Test
    fun `a stopped rest timer stays gone`() = runVmTest {
        val handle = handleFor(1)
        val first = viewModel(handle)
        first.startRestTimer(60)
        first.stopRestTimer()
        first.viewModelScope.cancel()

        val second = viewModel(handle)
        assertThat(second.restTimer.value.visible).isFalse()
        second.viewModelScope.cancel()
    }

    @Test
    fun `a time that makes no sense after a reboot is dropped`() = runVmTest {
        val handle = handleFor(1)
        handle["rest_total_seconds"] = 60
        handle["rest_ends_at"] = clock() + 3_600_000L // 총 시간보다 훨씬 뒤: 시계가 되돌아간 값
        handle["rest_paused"] = false
        val vm = viewModel(handle)
        assertThat(vm.restTimer.value.visible).isFalse()
        vm.viewModelScope.cancel()
    }

    // ----- 종료할 때 체크 안 한 세트 -----

    private suspend fun workoutWithSets(): Pair<Long, List<Long>> {
        val bench = exercises.addExercise(Exercise(id = 0, name = "벤치프레스", category = ExerciseCategory.BARBELL, bodyPart = BodyPart.CHEST))
        val workoutId = workouts.startWorkout(null)
        val weId = workouts.addExerciseToWorkout(workoutId, bench)
        workouts.addSet(weId)
        workouts.addSet(weId)
        val ids = db.workoutDao().getSets(weId).map { it.id }
        return workoutId to ids
    }

    @Test
    fun `finishing can also save unchecked sets that have values and drops empty ones`() = runVmTest {
        val (workoutId, ids) = workoutWithSets()
        // 세트 1: 체크함. 세트 2: 값만 넣고 체크 안 함. 세트 3: 값 없음.
        workouts.setCompleted(ids[0], 60.0, 8, true)
        workouts.setCompleted(ids[1], 62.5, 6, false)
        workouts.setCompleted(ids[2], 0.0, 0, false)
        val vm = viewModel(handleFor(workoutId))
        settle()

        vm.finishWorkout(completeUnchecked = true)
        settle()

        val sets = workouts.getWorkout(workoutId)!!.exercises.single().sets
        assertThat(sets.map { it.weightKg to it.reps }).containsExactly(60.0 to 8, 62.5 to 6).inOrder()
        assertThat(sets.all { it.completed }).isTrue()
        vm.viewModelScope.cancel()
    }

    @Test
    fun `a plain finish still drops unchecked sets`() = runVmTest {
        val (workoutId, ids) = workoutWithSets()
        workouts.setCompleted(ids[0], 60.0, 8, true)
        workouts.setCompleted(ids[1], 62.5, 6, false)
        val vm = viewModel(handleFor(workoutId))
        settle()

        vm.finishWorkout()
        settle()

        assertThat(workouts.getWorkout(workoutId)!!.exercises.single().sets).hasSize(1)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `saving unchecked sets rescues a workout where nothing was checked`() = runVmTest {
        val (workoutId, ids) = workoutWithSets()
        workouts.setCompleted(ids[0], 60.0, 8, false)
        val vm = viewModel(handleFor(workoutId))
        settle()

        vm.finishWorkout(completeUnchecked = true)
        settle()

        assertThat(workouts.getWorkout(workoutId)).isNotNull()
        assertThat(workouts.getWorkout(workoutId)!!.exercises.single().sets.single().completed).isTrue()
        vm.viewModelScope.cancel()
    }

    @Test
    fun `the notification prompt is remembered`() = runVmTest {
        val vm = viewModel(handleFor(1))
        assertThat(settings.settings.first().notificationPromptShown).isFalse()
        vm.markNotificationPrompted()
        settle()
        assertThat(settings.current().notificationPromptShown).isTrue()
        vm.viewModelScope.cancel()
    }
}
