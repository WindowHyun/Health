package com.windowhyun.health.ui

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.windowhyun.health.domain.model.RunLap
import com.windowhyun.health.domain.model.RunPoint
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.RunRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.repository.RunRepository
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.domain.repository.WorkoutRepository
import com.windowhyun.health.ui.history.HistoryEntry
import com.windowhyun.health.ui.history.HistoryFilter
import com.windowhyun.health.ui.history.HistoryViewModel
import com.windowhyun.health.ui.navigation.Routes
import com.windowhyun.health.ui.running.RunDetailViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate

/**
 * 기록 탭(헬스 + 러닝 합친 목록)과 러닝 상세 화면 검증.
 *
 * 러닝 기록은 저장은 되는데 열어 볼 방법이 없었다. 다시 그렇게 되지 않도록
 * 목록에 섞여 나오는지, 상세에서 메모/삭제가 되는지 여기서 잡는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryAndRunDetailTest {

    private val dispatcher = StandardTestDispatcher()

    /** 앱 수명 스코프 대역. 테스트에서 이 안의 작업이 끝나길 기다릴 수 있게 Job 을 따로 둔다. */
    private val appJob = SupervisorJob()
    private val appScope = CoroutineScope(dispatcher + appJob)

    private lateinit var db: HealthDatabase
    private lateinit var workouts: WorkoutRepository
    private lateinit var runs: RunRepository
    private lateinit var settings: SettingsRepository

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
        runs = RunRepositoryImpl(db.runDao())
        val file = File(context.cacheDir, "history-test-${System.nanoTime()}.preferences_pb")
        settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher)) { file },
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** 러닝 1건을 만들고 끝낸다. [minutesAgo] 만큼 이전에 시작한 것으로 둔다. */
    private suspend fun finishRun(distanceMeters: Double, minutesAgo: Long): Long {
        val runId = runs.startRun(RunGoalType.FREE, 0.0)
        val stored = runs.getRun(runId)!!
        runs.saveRun(stored.copy(startTime = stored.startTime - minutesAgo * 60_000))
        runs.finishRun(
            runId = runId,
            endTime = System.currentTimeMillis(),
            distanceMeters = distanceMeters,
            durationSeconds = 1_800,
            averagePaceSecPerKm = 360.0,
            bestPaceSecPerKm = 330.0,
            calories = 300,
            steps = 5_000,
        )
        return runId
    }

    private fun historyViewModel() = HistoryViewModel(workouts, runs, settings)

    /** 러닝과 헬스가 한 목록에 같이 나와야 한다. */
    @Test
    fun `lists runs together with workouts`() = runTest(dispatcher) {
        val workoutId = workouts.startWorkout(null)
        workouts.finishWorkout(workoutId)
        val runId = finishRun(distanceMeters = 5_000.0, minutesAgo = 60)

        val state = historyViewModel().uiState.first { !it.loading }

        assertThat(state.entries).hasSize(2)
        assertThat(state.gymCount).isEqualTo(1)
        assertThat(state.runCount).isEqualTo(1)
        assertThat(state.entries.filterIsInstance<HistoryEntry.Running>().single().run.id)
            .isEqualTo(runId)
        assertThat(state.entries.filterIsInstance<HistoryEntry.Gym>().single().workout.id)
            .isEqualTo(workoutId)
    }

    /** 같은 날이면 늦게 시작한 기록이 위로 온다. */
    @Test
    fun `sorts the newest record first`() = runTest(dispatcher) {
        finishRun(distanceMeters = 3_000.0, minutesAgo = 120)
        val workoutId = workouts.startWorkout(null)
        workouts.finishWorkout(workoutId)

        val state = historyViewModel().uiState.first { !it.loading }

        // 방금 끝낸 헬스가 2시간 전 러닝보다 먼저 나와야 한다.
        assertThat(state.entries.first()).isInstanceOf(HistoryEntry.Gym::class.java)
    }

    /** 필터를 고르면 그 종류만 남는다. */
    @Test
    fun `filters the list by record type`() = runTest(dispatcher) {
        val workoutId = workouts.startWorkout(null)
        workouts.finishWorkout(workoutId)
        finishRun(distanceMeters = 5_000.0, minutesAgo = 30)

        val viewModel = historyViewModel()
        viewModel.uiState.first { !it.loading }

        viewModel.setFilter(HistoryFilter.RUNNING)
        val onlyRuns = viewModel.uiState.first { it.filter == HistoryFilter.RUNNING }
        assertThat(onlyRuns.visibleEntries).hasSize(1)
        assertThat(onlyRuns.visibleEntries.single()).isInstanceOf(HistoryEntry.Running::class.java)
        // 전체 개수는 그대로여서 칩의 숫자가 흔들리지 않는다.
        assertThat(onlyRuns.entries).hasSize(2)

        viewModel.setFilter(HistoryFilter.GYM)
        val onlyGym = viewModel.uiState.first { it.filter == HistoryFilter.GYM }
        assertThat(onlyGym.visibleEntries.single()).isInstanceOf(HistoryEntry.Gym::class.java)
    }

    private fun runDetailViewModel(runId: Long) = RunDetailViewModel(
        runRepository = runs,
        settingsRepository = settings,
        savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_RUN_ID to runId)),
        appScope = appScope,
    )

    /** 저장된 러닝을 id 로 열 수 있어야 한다. */
    @Test
    fun `loads a saved run by id`() = runTest(dispatcher) {
        val runId = finishRun(distanceMeters = 7_500.0, minutesAgo = 10)

        val state = runDetailViewModel(runId).uiState.first { !it.loading }

        assertThat(state.run?.id).isEqualTo(runId)
        assertThat(state.run?.distanceMeters).isWithin(0.001).of(7_500.0)
        assertThat(state.deleted).isFalse()
    }

    /** 메모를 고치면 DB 에 남아야 한다. */
    @Test
    fun `saves an edited memo`() = runTest(dispatcher) {
        val runId = finishRun(distanceMeters = 5_000.0, minutesAgo = 10)
        val viewModel = runDetailViewModel(runId)
        viewModel.uiState.first { !it.loading }

        viewModel.setMemo("바람이 셌다")
        viewModel.saveMemo()

        // 저장이 끝나 상태에 반영될 때까지 기다린다(Room 은 자기 스레드에서 쓴다).
        val state = viewModel.uiState.first { it.run?.memo == "바람이 셌다" }
        assertThat(state.memoDirty).isFalse()
        assertThat(state.editingMemo).isFalse()
        assertThat(runs.getRun(runId)?.memo).isEqualTo("바람이 셌다")
    }

    /** 화면을 벗어날 때 쓰다 만 메모도 저장된다. */
    @Test
    fun `saves a pending memo when leaving`() = runTest(dispatcher) {
        val runId = finishRun(distanceMeters = 5_000.0, minutesAgo = 10)
        val viewModel = runDetailViewModel(runId)
        viewModel.uiState.first { !it.loading }

        viewModel.setMemo("저장 안 누름")
        viewModel.saveMemoIfNeeded()

        // Room 은 자기 스레드에서 쓰므로, 저장이 끝난 뒤 상태가 바뀌는 것을 기다린다.
        viewModel.uiState.first { !it.memoDirty && it.run?.memo == "저장 안 누름" }
        assertThat(runs.getRun(runId)?.memo).isEqualTo("저장 안 누름")
    }

    /**
     * 뒤로 가기로 화면이 사라져도 쓰던 메모는 저장된다.
     *
     * 뒤로 가면 ViewModel 이 정리되며 viewModelScope 가 취소된다. 저장을 그 스코프에서
     * 하면 DB 에 쓰기 전에 끊겨 메모가 사라진다.
     */
    @Test
    fun `saves a pending memo when the screen is closed`() = runTest(dispatcher) {
        val runId = finishRun(distanceMeters = 5_000.0, minutesAgo = 10)
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { runDetailViewModel(runId) } },
        )[RunDetailViewModel::class]
        viewModel.uiState.first { !it.loading }

        viewModel.setMemo("뒤로 가기 전에 씀")
        // 뒤로 가기와 같다: ViewModel 정리 -> viewModelScope 취소 -> onCleared
        store.clear()
        appJob.children.toList().joinAll()

        assertThat(runs.getRun(runId)?.memo).isEqualTo("뒤로 가기 전에 씀")
    }

    /** 빈 메모는 null 로 지운다. 목록에 빈 따옴표가 뜨지 않게 한다. */
    @Test
    fun `clears the memo when emptied`() = runTest(dispatcher) {
        val runId = finishRun(distanceMeters = 5_000.0, minutesAgo = 10)
        runs.updateMemo(runId, "지울 메모")
        val viewModel = runDetailViewModel(runId)
        viewModel.uiState.first { !it.loading }

        viewModel.setMemo("   ")
        viewModel.saveMemo()

        viewModel.uiState.first { !it.memoDirty && !it.editingMemo && it.run?.memo == null }
        assertThat(runs.getRun(runId)?.memo).isNull()
    }

    /** 삭제하면 기록이 없어지고 화면이 닫힌다. */
    @Test
    fun `deletes the run`() = runTest(dispatcher) {
        val runId = finishRun(distanceMeters = 5_000.0, minutesAgo = 10)
        val viewModel = runDetailViewModel(runId)
        viewModel.uiState.first { !it.loading }

        viewModel.delete()

        assertThat(viewModel.uiState.first { it.deleted }.deleted).isTrue()
        assertThat(runs.getRun(runId)).isNull()
        // 목록에서도 사라져야 한다.
        assertThat(historyViewModel().uiState.first { !it.loading }.entries).isEmpty()
    }

    /** 이미 지워진 기록을 열면 바로 닫는다. */
    @Test
    fun `closes when the run is gone`() = runTest(dispatcher) {
        val state = runDetailViewModel(999).uiState.first { !it.loading }

        assertThat(state.run).isNull()
        assertThat(state.deleted).isTrue()
    }

    /** 러닝을 통째로 저장하면 같은 기록이 갱신된다(복사본이 생기지 않는다). */
    @Test
    fun `saving a run replaces it instead of duplicating`() = runTest(dispatcher) {
        val runId = finishRun(distanceMeters = 5_000.0, minutesAgo = 10)

        val stored = runs.getRun(runId)!!
        val sameId = runs.saveRun(stored.copy(distanceMeters = 6_000.0))

        assertThat(sameId).isEqualTo(runId)
        assertThat(runs.getRun(runId)?.distanceMeters).isWithin(0.001).of(6_000.0)
        assertThat(historyViewModel().uiState.first { !it.loading }.entries).hasSize(1)
    }

    /**
     * Lap·경로 없이 받은 러닝(목록 조회 결과)을 저장해도 경로가 지워지면 안 된다.
     * 행을 갈아 끼우는 방식이면 CASCADE 로 Lap 과 GPS 경로가 함께 사라진다.
     */
    @Test
    fun `saving a run keeps its laps and route`() = runTest(dispatcher) {
        val runId = finishRun(distanceMeters = 5_000.0, minutesAgo = 10)
        runs.appendLap(runId, RunLap(lapNumber = 1, distanceMeters = 1_000.0, durationSeconds = 300, paceSecPerKm = 300.0))
        runs.appendRoutePoints(runId, listOf(RunPoint(latitude = 37.5, longitude = 127.0, timestamp = 1)))

        val fromList = runs.observeRecentRuns(10).first().single()
        assertThat(fromList.laps).isEmpty() // 목록 조회는 Lap 을 싣지 않는다
        runs.saveRun(fromList.copy(memo = "목록에서 고침"))

        val stored = runs.getRun(runId)!!
        assertThat(stored.memo).isEqualTo("목록에서 고침")
        assertThat(stored.laps).hasSize(1)
        assertThat(stored.route).hasSize(1)
    }

    /** 1년이 넘은 기록은 목록 범위 밖이다. */
    @Test
    fun `keeps the list to the last year`() = runTest(dispatcher) {
        val runId = finishRun(distanceMeters = 5_000.0, minutesAgo = 10)
        val old = runs.getRun(runId)!!
        runs.saveRun(old.copy(date = LocalDate.now().minusYears(2)))

        val state = historyViewModel().uiState.first { !it.loading }

        assertThat(state.entries).isEmpty()
    }
}
