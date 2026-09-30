package com.windowhyun.health.ui

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RECOVERED_MEMO
import com.windowhyun.health.data.repository.RunRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.data.sensor.SensorStepCounter
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.RunGoal
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.RunPoint
import com.windowhyun.health.ui.history.WorkoutDetailViewModel
import com.windowhyun.health.ui.navigation.Routes
import com.windowhyun.health.ui.session.WorkoutSummaryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * 전체 코드리뷰에서 나온 데이터 손실 · 중복 기록 문제의 회귀 테스트.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReviewFixesTest {

    private val dispatcher = StandardTestDispatcher()
    private val appJob = SupervisorJob()
    private val appScope = CoroutineScope(appJob + dispatcher)

    private lateinit var db: HealthDatabase
    private lateinit var workouts: WorkoutRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var runs: RunRepositoryImpl
    private lateinit var settings: SettingsRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workouts = WorkoutRepositoryImpl(
            workoutDao = db.workoutDao(),
            routineDao = db.routineDao(),
            exerciseDao = db.exerciseDao(),
            personalRecordDao = db.personalRecordDao(),
        )
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        runs = RunRepositoryImpl(db.runDao())
        val file = File(context.cacheDir, "review-fixes-${System.nanoTime()}.preferences_pb")
        settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher)) { file },
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** 세트 하나를 끝낸 운동을 저장하고 id 를 돌려준다. */
    private suspend fun finishedWorkout(memo: String? = null): Long {
        val squat = exercises.addExercise(
            Exercise(id = 0, name = "스쿼트", category = ExerciseCategory.BARBELL, bodyPart = BodyPart.LEG),
        )
        val workoutId = workouts.startWorkout(null)
        val weId = workouts.addExerciseToWorkout(workoutId, squat)
        workouts.setCompleted(db.workoutDao().getSets(weId).first().id, 100.0, 5, true)
        workouts.finishWorkout(workoutId)
        if (memo != null) workouts.updateMemo(workoutId, memo)
        return workoutId
    }

    /** 화면을 떠날 때처럼 ViewModel 을 정리하고, 앱 스코프에 맡긴 저장이 끝날 때까지 기다린다. */
    private suspend fun leave(viewModel: ViewModel) {
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
        }
        ViewModelProvider(store, factory)[viewModel::class.java]
        store.clear()
        appJob.children.toList().forEach { it.join() }
    }

    /**
     * 화면이 맡긴 일(불러오기 · 저장)을 돌린다. DB 는 다른 스레드에서 돌고 viewModelScope 에는
     * 끝나지 않는 상태 공유 작업도 있어서, 잠깐씩 기다리며 여러 번 돌린다.
     */
    private fun TestScope.settle() {
        repeat(30) {
            advanceUntilIdle()
            Thread.sleep(10)
        }
        advanceUntilIdle()
    }

    private fun handle(workoutId: Long) = SavedStateHandle(mapOf(Routes.ARG_WORKOUT_ID to workoutId))

    // ----- 메모 -----

    /** 메모를 불러오기 전에 나가도 원래 메모가 지워지지 않는다. */
    @Test
    fun `leaving detail before the memo loads keeps the memo`() = runTest(dispatcher) {
        val workoutId = finishedWorkout(memo = "허리 조심")
        val viewModel = WorkoutDetailViewModel(workouts, settings, handle(workoutId), appScope)

        viewModel.saveMemo() // 불러오기 전에 뒤로 가기
        settle()
        assertThat(workouts.getWorkout(workoutId)!!.memo).isEqualTo("허리 조심")
        leave(viewModel)

        assertThat(workouts.getWorkout(workoutId)!!.memo).isEqualTo("허리 조심")
    }

    /** 메모를 고치고 바로 뒤로 가도 저장된다(뒤로 가기에 저장이 취소되지 않는다). */
    @Test
    fun `memo edited in detail is saved when leaving`() = runTest(dispatcher) {
        val workoutId = finishedWorkout(memo = "허리 조심")
        val viewModel = WorkoutDetailViewModel(workouts, settings, handle(workoutId), appScope)
        viewModel.uiState.first { it.memo == "허리 조심" }

        viewModel.setMemo("무릎도 조심")
        leave(viewModel)

        assertThat(workouts.getWorkout(workoutId)!!.memo).isEqualTo("무릎도 조심")
    }

    @Test
    fun `leaving summary before the memo loads keeps the memo`() = runTest(dispatcher) {
        val workoutId = finishedWorkout(memo = "컨디션 좋음")
        val viewModel = WorkoutSummaryViewModel(workouts, settings, handle(workoutId), appScope)

        viewModel.saveMemo() // 불러오기 전에 뒤로 가기
        settle()
        assertThat(workouts.getWorkout(workoutId)!!.memo).isEqualTo("컨디션 좋음")
        leave(viewModel)

        assertThat(workouts.getWorkout(workoutId)!!.memo).isEqualTo("컨디션 좋음")
    }

    @Test
    fun `memo edited in summary is saved when leaving`() = runTest(dispatcher) {
        val workoutId = finishedWorkout()
        val viewModel = WorkoutSummaryViewModel(workouts, settings, handle(workoutId), appScope)
        viewModel.uiState.first { it.workout != null }
        viewModel.uiState.first { it.memo == "" }

        viewModel.setMemo("컨디션 좋음")
        leave(viewModel)

        assertThat(workouts.getWorkout(workoutId)!!.memo).isEqualTo("컨디션 좋음")
    }

    // ----- 운동 시작 · 종료 중복 -----

    /** 시작을 두 번(동시에) 눌러도 진행 중인 운동은 하나다. */
    @Test
    fun `starting twice resumes the same workout`() = runTest(dispatcher) {
        val ids = List(3) { async(Dispatchers.IO) { workouts.startWorkout(null) } }.awaitAll()

        assertThat(ids.toSet()).hasSize(1)
        assertThat(workouts.startWorkout(null)).isEqualTo(ids.first())
    }

    /** 종료가 두 번 들어와도 기록 · 종료 시각이 한 번만 남는다. */
    @Test
    fun `finishing twice keeps a single record set`() = runTest(dispatcher) {
        val workoutId = finishedWorkout()
        val endTime = workouts.getWorkout(workoutId)!!.endTime
        val records = workouts.observeWorkoutRecords(workoutId).first()

        val again = workouts.finishWorkout(workoutId)

        assertThat(workouts.getWorkout(workoutId)!!.endTime).isEqualTo(endTime)
        assertThat(workouts.observeWorkoutRecords(workoutId).first()).hasSize(records.size)
        assertThat(again.personalRecords).hasSize(records.size)
        assertThat(records).isNotEmpty()
    }

    @Test
    fun `concurrent finishes keep a single record set`() = runTest(dispatcher) {
        val squat = exercises.addExercise(
            Exercise(id = 0, name = "데드리프트", category = ExerciseCategory.BARBELL, bodyPart = BodyPart.BACK),
        )
        val workoutId = workouts.startWorkout(null)
        val weId = workouts.addExerciseToWorkout(workoutId, squat)
        workouts.setCompleted(db.workoutDao().getSets(weId).first().id, 140.0, 3, true)

        List(3) { async(Dispatchers.IO) { workouts.finishWorkout(workoutId) } }.awaitAll()

        val types = workouts.observeWorkoutRecords(workoutId).first().map { it.type }
        assertThat(types).containsNoDuplicates()
        assertThat(types).isNotEmpty()
    }

    // ----- 앱이 죽어 끝나지 않은 러닝 -----

    private suspend fun abandonedRun(pointsAt: List<Long>): Long {
        val runId = runs.startRun(RunGoalType.FREE, 0.0)
        if (pointsAt.isNotEmpty()) {
            runs.appendRoutePoints(runId, pointsAt.map { RunPoint(latitude = 37.5, longitude = 127.0, timestamp = it) })
            runs.updateProgress(runId, 1_200.0, 420, 350.0, 330.0, 80, 0)
        }
        return runId
    }

    /** 경로가 남은 러닝은 마지막 지점 시각으로 마감하고, 아무것도 없는 러닝은 지운다. */
    @Test
    fun `closes runs left open by a killed app`() = runTest(dispatcher) {
        val start = runs.getRun(abandonedRun(emptyList()))!!.startTime
        val withRoute = abandonedRun(listOf(start + 60_000, start + 420_000))
        val empty = abandonedRun(emptyList())
        runs.deleteRun(1) // 위에서 시각만 얻으려고 만든 러닝

        val closed = runs.closeUnfinishedRuns(excludeRunId = 0)

        assertThat(closed).isEqualTo(1)
        val recovered = runs.getRun(withRoute)!!
        assertThat(recovered.endTime).isEqualTo(start + 420_000)
        assertThat(recovered.memo).isEqualTo(RECOVERED_MEMO)
        assertThat(recovered.distanceMeters).isWithin(0.001).of(1_200.0)
        assertThat(runs.getRun(empty)).isNull()
        assertThat(runs.getActiveRun()).isNull()
    }

    /** 지금 기록 중인 러닝은 건드리지 않는다. */
    @Test
    fun `recovery leaves the run being recorded alone`() = runTest(dispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tracker = RunTracker(runs, settings, SensorStepCounter(context))
        val old = abandonedRun(listOf(System.currentTimeMillis()))
        tracker.start(RunGoal(RunGoalType.FREE, 0.0))
        val current = tracker.state.value.runId

        tracker.recoverUnfinishedRuns()

        assertThat(runs.getRun(current)!!.endTime).isNull()
        assertThat(runs.getRun(old)!!.endTime).isNotNull()
        tracker.discard()
    }
}
