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
import kotlinx.coroutines.launch
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
        // 세트 2 는 화면에서 직접 고쳐 넣은 값이다.
        vm.updateSetValues(workouts.getWorkout(workoutId)!!.exercises.single().sets[1], 62.5, 6, 0)
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
        vm.updateSetValues(workouts.getWorkout(workoutId)!!.exercises.single().sets[0], 60.0, 8, 0)
        settle()

        vm.finishWorkout(completeUnchecked = true)
        settle()

        assertThat(workouts.getWorkout(workoutId)).isNotNull()
        assertThat(workouts.getWorkout(workoutId)!!.exercises.single().sets.single().completed).isTrue()
        vm.viewModelScope.cancel()
    }

    // ----- 코드 리뷰(v0.17.6) -----

    /** 무게만 따라 채워진 세트(횟수 0)는 한 적 없는 세트다. "체크 안 한 세트도 저장"이 60kg x 0회를 완료로 남기지 않는다. */
    @Test
    fun `saving unchecked sets skips sets that only have a weight`() = runVmTest {
        val (workoutId, ids) = workoutWithSets()
        workouts.setCompleted(ids[0], 60.0, 8, true)
        workouts.setCompleted(ids[1], 60.0, 0, false) // 앞 세트의 무게만 따라 채워졌다
        workouts.setCompleted(ids[2], 60.0, 5, false) // 하고 나서 체크만 깜빡했다
        val vm = viewModel(handleFor(workoutId))
        settle()
        vm.updateSetValues(workouts.getWorkout(workoutId)!!.exercises.single().sets[2], 60.0, 5, 0)
        settle()

        vm.finishWorkout(completeUnchecked = true)
        settle()

        val sets = workouts.getWorkout(workoutId)!!.exercises.single().sets
        assertThat(sets.map { it.weightKg to it.reps }).containsExactly(60.0 to 8, 60.0 to 5).inOrder()
        vm.viewModelScope.cancel()
    }

    /**
     * 지난 운동 값이 미리 채워진 채 손대지 않은 세트(대개 안 한 마지막 세트)는 "체크 깜빡한 세트" 가 아니다.
     * 종료 창에서 한 번 잘못 눌러 하지 않은 세트가 기록(볼륨 · 개인 기록)이 되지 않게 한다.
     */
    @Test
    fun `untouched prefilled sets are not offered for saving`() = runVmTest {
        val (workoutId, ids) = workoutWithSets()
        workouts.setCompleted(ids[0], 60.0, 8, true)
        workouts.setCompleted(ids[1], 60.0, 8, false) // 지난번 값이 미리 채워짐, 손대지 않음
        workouts.setCompleted(ids[2], 60.0, 8, false)

        val record = workouts.getWorkout(workoutId)!!.exercises.single()

        assertThat(record.rescuableSets(emptySet())).isEmpty()
    }

    @Test
    fun `sets the user edited or skipped past are offered for saving`() = runVmTest {
        val (workoutId, ids) = workoutWithSets()
        workouts.setCompleted(ids[0], 60.0, 8, false) // 건너뛴 세트: 뒤의 세트가 끝났다
        workouts.setCompleted(ids[1], 60.0, 8, true)
        workouts.setCompleted(ids[2], 62.5, 6, false) // 직접 고친 세트
        val record = workouts.getWorkout(workoutId)!!.exercises.single()

        assertThat(record.rescuableSets(setOf(ids[2])).map { it.id }).containsExactly(ids[0], ids[2]).inOrder()
        // 직접 고쳤어도 횟수가 없으면(무게만) 완료로 저장할 수 없다.
        workouts.setCompleted(ids[2], 62.5, 0, false)
        assertThat(workouts.getWorkout(workoutId)!!.exercises.single().rescuableSets(setOf(ids[2])).map { it.id }).containsExactly(ids[0])
    }

    /** 세트 값을 고친 기록은 화면이 다시 만들어져도(앱이 정리됐다 돌아와도) 남는다. */
    @Test
    fun `edited sets are remembered across a recreated screen`() = runVmTest {
        val (workoutId, ids) = workoutWithSets()
        val handle = handleFor(workoutId)
        val first = viewModel(handle)
        settle()
        first.updateSetValues(workouts.getWorkout(workoutId)!!.exercises.single().sets[1], 62.5, 6, 0)
        settle()
        first.viewModelScope.cancel()

        val second = viewModel(handle)
        backgroundScope.launch(dispatcher) { second.uiState.collect { } } // 화면이 보고 있는 것처럼
        settle()
        assertThat(second.uiState.value.editedSetIds).containsExactly(ids[1])
        second.viewModelScope.cancel()
    }

    /** 앞 세트를 고쳐 뒤 세트의 무게가 따라 바뀐 것은 "직접 고친 것" 이 아니다. */
    @Test
    fun `carried over weights do not count as edits`() = runVmTest {
        val (workoutId, ids) = workoutWithSets()
        val vm = viewModel(handleFor(workoutId))
        backgroundScope.launch(dispatcher) { vm.uiState.collect { } } // 화면이 보고 있는 것처럼
        settle()

        vm.updateSetValues(workouts.getWorkout(workoutId)!!.exercises.single().sets[0], 70.0, 8, 0)
        settle()

        assertThat(vm.uiState.value.editedSetIds).containsExactly(ids[0])
        vm.viewModelScope.cancel()
    }

    @Test
    fun `the screen follows the exercise only when the active one changes`() {
        assertThat(shouldScrollToActive(previous = null, current = 1L)).isFalse() // 처음 그릴 때
        assertThat(shouldScrollToActive(previous = 1L, current = 1L)).isFalse()
        assertThat(shouldScrollToActive(previous = 1L, current = 2L)).isTrue() // 종목을 끝냈다
        assertThat(shouldScrollToActive(previous = 2L, current = null)).isFalse() // 모두 끝났다
    }

    /** 세트를 지우고 그 종목을 통째로 뺀 뒤 "실행 취소"를 눌러도 앱이 죽지 않는다(없는 종목에 세트를 넣으면 DB 가 거절한다). */
    @Test
    fun `undoing a removed set after its exercise was removed does nothing`() = runVmTest {
        val (workoutId, ids) = workoutWithSets()
        val vm = viewModel(handleFor(workoutId))
        settle()
        val workoutExerciseId = workouts.getWorkout(workoutId)!!.exercises.single().id

        vm.removeSet(ids[0])
        settle()
        vm.removeExercise(workoutExerciseId)
        settle()
        vm.undoRemoveSet()
        settle()

        assertThat(workouts.getWorkout(workoutId)!!.exercises).isEmpty()
        assertThat(db.backupDao().allWorkoutSets()).isEmpty()
        vm.viewModelScope.cancel()
    }

    /** 저장소도 스스로 막는다: 없는 종목에 세트를 되살리라는 요청은 던지지 않고 무시한다. */
    @Test
    fun `restoring a set into a missing exercise is ignored`() = runVmTest {
        val (workoutId, _) = workoutWithSets()
        val record = workouts.getWorkout(workoutId)!!.exercises.single()
        workouts.removeWorkoutExercise(record.id)

        workouts.restoreSet(record.id, record.sets.first())

        assertThat(db.backupDao().allWorkoutSets()).isEmpty()
    }

    /** 다른 종목은 그대로라면 실행 취소가 여전히 된다(막는 것은 없어진 종목뿐). */
    @Test
    fun `undo still works when a different exercise was removed`() = runVmTest {
        val (workoutId, ids) = workoutWithSets()
        val row = exercises.addExercise(Exercise(id = 0, name = "바벨 로우", category = ExerciseCategory.BARBELL, bodyPart = BodyPart.BACK))
        val otherId = workouts.addExerciseToWorkout(workoutId, row)
        val vm = viewModel(handleFor(workoutId))
        settle()

        vm.removeSet(ids[0])
        settle()
        vm.removeExercise(otherId)
        settle()
        vm.undoRemoveSet()
        settle()

        assertThat(workouts.getWorkout(workoutId)!!.exercises.single().sets).hasSize(3)
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
