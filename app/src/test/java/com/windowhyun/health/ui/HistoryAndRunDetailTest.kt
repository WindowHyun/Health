package com.windowhyun.health.ui

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
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
        advanceUntilIdle()

        // uiState 는 구독이 있을 때만 흐르므로 다시 구독해 최신 값을 받는다.
        val state = viewModel.uiState.first { !it.memoDirty && !it.editingMemo }
        assertThat(state.run?.memo).isEqualTo("바람이 셌다")
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
        advanceUntilIdle()

        assertThat(runs.getRun(runId)?.memo).isEqualTo("저장 안 누름")
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
        advanceUntilIdle()

        assertThat(runs.getRun(runId)?.memo).isNull()
    }

    /** 삭제하면 기록이 없어지고 화면이 닫힌다. */
    @Test
    fun `deletes the run`() = runTest(dispatcher) {
        val runId = finishRun(distanceMeters = 5_000.0, minutesAgo = 10)
        val viewModel = runDetailViewModel(runId)
        viewModel.uiState.first { !it.loading }

        viewModel.delete()
        advanceUntilIdle()

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
