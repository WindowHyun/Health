package com.windowhyun.health.data.healthconnect

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.data.backup.BackupRepositoryImpl
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RunRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.HealthConnectAvailability
import com.windowhyun.health.domain.model.HealthConnectGateway
import com.windowhyun.health.domain.model.HealthConnectLedger
import com.windowhyun.health.domain.model.HealthPermissionState
import com.windowhyun.health.domain.model.HealthSession
import com.windowhyun.health.domain.model.HealthSessionKind
import com.windowhyun.health.domain.model.HeartRateSummary
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.ui.navigation.Routes
import com.windowhyun.health.ui.running.RunDetailViewModel
import com.windowhyun.health.ui.settings.HealthConnectUiState
import com.windowhyun.health.ui.settings.HealthConnectViewModel
import com.windowhyun.health.ui.settings.healthConnectStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import java.io.IOException
import java.time.LocalDate

/** Health Connect 로 내보내기 · 지우기 · 체중 가져오기 · 심박 읽기. SDK 는 가짜 게이트웨이로 바꿔 규칙만 검증한다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HealthConnectTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeGateway : HealthConnectGateway {
        var availability = HealthConnectAvailability.AVAILABLE
        var permissions = HealthPermissionState(canWrite = true, canReadWeight = true, canReadHeartRate = true)
        var weightKg: Double? = null
        var heartRate: HeartRateSummary? = null
        var failUpsert: Exception? = null
        var failHeartRate: Exception? = null
        val upserts = mutableListOf<List<HealthSession>>()
        val deletes = mutableListOf<List<String>>()
        var heartRateRequests = 0

        override fun availability() = availability
        override suspend fun permissionState() = permissions
        override suspend fun upsert(sessions: List<HealthSession>) {
            failUpsert?.let { throw it }
            upserts += sessions
        }
        override suspend fun delete(clientIds: List<String>) {
            deletes += clientIds
        }
        override suspend fun latestWeightKg(withinDays: Int) = weightKg
        override suspend fun heartRate(startMillis: Long, endMillis: Long): HeartRateSummary? {
            heartRateRequests++
            failHeartRate?.let { throw it }
            return heartRate
        }

        fun written() = upserts.flatten()
    }

    private class FakeLedger : HealthConnectLedger {
        var entries: Map<String, String> = emptyMap()
        override suspend fun read() = entries
        override suspend fun write(entries: Map<String, String>) {
            this.entries = entries
        }
    }

    private lateinit var context: Context
    private lateinit var db: HealthDatabase
    private lateinit var runs: RunRepositoryImpl
    private lateinit var workouts: WorkoutRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var settings: SettingsRepositoryImpl
    private val gateway = FakeGateway()
    private val ledger = FakeLedger()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).allowMainThreadQueries().build().also { it.openHelper.writableDatabase } // 먼저 열어 둔다: 닫을 때 여는 중이면 서로 기다려 멈춘다
        runs = RunRepositoryImpl(db.runDao())
        workouts = WorkoutRepositoryImpl(db.workoutDao(), db.routineDao(), db.exerciseDao(), db.personalRecordDao())
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) {
                File(context.cacheDir, "hc-${System.nanoTime()}.preferences_pb")
            },
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun syncer(limit: Int = HealthConnectSyncer.HISTORY_LIMIT, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) =
        HealthConnectSyncer(gateway, ledger, settings, runs, workouts, scope, limit)

    private fun enable(importWeight: Boolean = false) = runBlocking {
        settings.update { it.copy(healthConnectEnabled = true, healthConnectImportWeight = importWeight) }
    }

    private suspend fun finishedRun(km: Double = 5.0, seconds: Long = 1_500): Long {
        val id = runs.startRun(RunGoalType.FREE, 0.0)
        runs.finishRun(id, System.currentTimeMillis() + 5_000, km * 1000, seconds, 300.0, 280.0, 320, 4_000)
        return id
    }

    private suspend fun finishedWorkout(sets: Int = 2): Long {
        val exerciseId = exercises.addExercise(Exercise(0, "벤치", ExerciseCategory.BARBELL, BodyPart.CHEST))
        val workoutId = workouts.startWorkout(null)
        val weId = workouts.addExerciseToWorkout(workoutId, exerciseId)
        repeat(sets - 1) { workouts.addSet(weId) }
        workouts.getWorkout(workoutId)!!.exercises.single().sets.forEach {
            workouts.setCompleted(it.id, 60.0, 8, true, 0)
        }
        workouts.finishWorkout(workoutId)
        return workoutId
    }

    // ----- 내보낼 모양 -----

    private fun run(end: Long? = 2_000_000, start: Long = 1_000_000, id: Long = 7, km: Double = 5.0) = Run(
        id = id, date = LocalDate.of(2026, 10, 7), startTime = start, endTime = end,
        durationSeconds = 1_000, distanceMeters = km * 1000, calories = 300, steps = 4_000,
    )

    @Test
    fun `a finished run becomes a running session`() {
        val session = run().toHealthSession()!!

        assertThat(session.kind).isEqualTo(HealthSessionKind.RUN)
        assertThat(session.clientId).isEqualTo("health-run-7")
        assertThat(session.startMillis).isEqualTo(1_000_000)
        assertThat(session.endMillis).isEqualTo(2_000_000)
        assertThat(session.distanceMeters).isEqualTo(5_000.0)
        assertThat(session.calories).isEqualTo(300)
        assertThat(session.steps).isEqualTo(4_000)
    }

    /** 기록하다 만 러닝이 다른 앱에 새어 나가면 안 된다. */
    @Test
    fun `an unfinished or empty run is not exported`() {
        assertThat(run(end = null).toHealthSession()).isNull()
        assertThat(run(end = 1_000_000, start = 1_000_000).toHealthSession()).isNull()
        assertThat(run(end = 900_000, start = 1_000_000).toHealthSession()).isNull()
    }

    @Test
    fun `the same record keeps its name and a changed one changes its fingerprint`() {
        val a = run(km = 5.0).toHealthSession()!!
        val b = run(km = 5.0).toHealthSession()!!
        val changed = run(km = 6.0).toHealthSession()!!

        assertThat(a.clientId).isEqualTo(b.clientId)
        assertThat(a.fingerprint).isEqualTo(b.fingerprint)
        assertThat(changed.clientId).isEqualTo(a.clientId)
        assertThat(changed.fingerprint).isNotEqualTo(a.fingerprint)
    }

    @Test
    fun `runs and workouts never share a name even with the same id`() {
        assertThat(runClientId(1)).isNotEqualTo(workoutClientId(1))
    }

    @Test
    fun `a workout without a finished set is not exported`() {
        val empty = Workout(id = 3, date = LocalDate.of(2026, 10, 7), startTime = 1_000, endTime = 5_000)

        assertThat(empty.toHealthSession()).isNull()
    }

    // ----- 동기화 -----

    @Test
    fun `nothing happens until the user turns it on`() = runBlocking<Unit> {
        finishedRun()

        assertThat(syncer().sync()).isEqualTo(HealthSyncResult.Disabled)
        assertThat(gateway.upserts).isEmpty()
    }

    @Test
    fun `an unsupported device or missing permission sends nothing`() = runBlocking<Unit> {
        enable()
        finishedRun()

        gateway.availability = HealthConnectAvailability.UNAVAILABLE
        assertThat(syncer().sync()).isEqualTo(HealthSyncResult.Unavailable)

        gateway.availability = HealthConnectAvailability.AVAILABLE
        gateway.permissions = HealthPermissionState(canWrite = false, canReadWeight = true)
        assertThat(syncer().sync()).isEqualTo(HealthSyncResult.NoPermission)

        assertThat(gateway.upserts).isEmpty()
    }

    @Test
    fun `finished records are sent and unfinished ones are not`() = runBlocking<Unit> {
        enable()
        val runId = finishedRun()
        val workoutId = finishedWorkout()
        runs.startRun(RunGoalType.FREE, 0.0) // 아직 달리는 중

        val result = syncer().sync()

        assertThat(result).isEqualTo(HealthSyncResult.Done(written = 2, deleted = 0))
        assertThat(gateway.written().map { it.clientId })
            .containsExactly(runClientId(runId), workoutClientId(workoutId))
    }

    @Test
    fun `syncing again with nothing new sends nothing`() = runBlocking<Unit> {
        enable()
        finishedRun()
        val syncer = syncer()
        syncer.sync()
        gateway.upserts.clear()

        val second = syncer.sync()

        assertThat(second).isEqualTo(HealthSyncResult.Done(written = 0, deleted = 0))
        assertThat(gateway.upserts).isEmpty()
    }

    @Test
    fun `only a changed record is sent again`() = runBlocking<Unit> {
        enable()
        val first = finishedRun(km = 5.0)
        val second = finishedRun(km = 8.0)
        val syncer = syncer()
        syncer.sync()
        gateway.upserts.clear()

        // 첫 러닝의 거리를 고친 것처럼 기록을 바꾼다.
        runs.finishRun(first, System.currentTimeMillis() + 9_000, 5_500.0, 1_600, 300.0, 280.0, 330, 4_100)
        val result = syncer.sync()

        assertThat(result).isEqualTo(HealthSyncResult.Done(written = 1, deleted = 0))
        assertThat(gateway.written().single().clientId).isEqualTo(runClientId(first))
        assertThat(gateway.written().map { it.clientId }).doesNotContain(runClientId(second))
    }

    /** 앱에서 지운 기록은 Health Connect 에서도 지운다. */
    @Test
    fun `a deleted record is removed from health connect`() = runBlocking<Unit> {
        enable()
        val keep = finishedRun()
        val gone = finishedRun(km = 3.0)
        val syncer = syncer()
        syncer.sync()

        runs.deleteRun(gone)
        val result = syncer.sync()

        assertThat(result).isEqualTo(HealthSyncResult.Done(written = 0, deleted = 1))
        assertThat(gateway.deletes.flatten()).containsExactly(runClientId(gone))
        assertThat(ledger.entries.keys).containsExactly(runClientId(keep))
    }

    /** 기록이 많아 목록이 잘렸다면 "안 보인다"가 "지웠다"가 아니다. 멀쩡한 기록을 지우면 안 된다. */
    @Test
    fun `a truncated list never deletes anything`() = runBlocking<Unit> {
        enable()
        finishedRun()
        finishedRun(km = 3.0)
        val syncer = syncer(limit = 2)
        ledger.entries = mapOf("health-run-999" to "old")

        val result = syncer.sync()

        assertThat(result).isEqualTo(HealthSyncResult.Done(written = 2, deleted = 0))
        assertThat(gateway.deletes).isEmpty()
        assertThat(ledger.entries).containsKey("health-run-999")
    }

    /** 보내다 실패하면 장부를 고치지 않는다. 그래야 다음에 같은 기록을 다시 시도한다. */
    @Test
    fun `a failed send is remembered and retried`() = runBlocking<Unit> {
        enable()
        val runId = finishedRun()
        val syncer = syncer()
        gateway.failUpsert = IOException("Health Connect 가 응답하지 않아요")

        val failed = syncer.sync()

        assertThat(failed).isEqualTo(HealthSyncResult.Failed("Health Connect 가 응답하지 않아요"))
        assertThat(ledger.entries).isEmpty()
        assertThat(settings.current().healthConnectLastError).isEqualTo("Health Connect 가 응답하지 않아요")

        gateway.failUpsert = null
        val retried = syncer.sync()

        assertThat(retried).isEqualTo(HealthSyncResult.Done(written = 1, deleted = 0))
        assertThat(gateway.written().single().clientId).isEqualTo(runClientId(runId))
        assertThat(settings.current().healthConnectLastError).isNull()
        assertThat(settings.current().healthConnectLastSyncAt).isGreaterThan(0)
    }

    // ----- 체중 -----

    @Test
    fun `the latest weight replaces the weight used for calories`() = runBlocking<Unit> {
        enable(importWeight = true)
        settings.update { it.copy(bodyWeightKg = 70.0) }
        gateway.weightKg = 68.4

        syncer().sync()

        assertThat(settings.current().bodyWeightKg).isWithin(0.001).of(68.4)
    }

    @Test
    fun `the weight is left alone unless importing is on and allowed`() = runBlocking<Unit> {
        settings.update { it.copy(bodyWeightKg = 70.0) }
        gateway.weightKg = 60.0

        enable(importWeight = false)
        syncer().sync()
        assertThat(settings.current().bodyWeightKg).isEqualTo(70.0)

        enable(importWeight = true)
        gateway.permissions = HealthPermissionState(canWrite = true, canReadWeight = false)
        syncer().sync()
        assertThat(settings.current().bodyWeightKg).isEqualTo(70.0)
    }

    @Test
    fun `a nonsense weight is clamped and a tiny change is ignored`() = runBlocking<Unit> {
        enable(importWeight = true)
        settings.update { it.copy(bodyWeightKg = 70.0) }

        gateway.weightKg = 900.0
        syncer().sync()
        assertThat(settings.current().bodyWeightKg).isEqualTo(250.0)

        gateway.weightKg = null
        syncer().sync()
        assertThat(settings.current().bodyWeightKg).isEqualTo(250.0)
    }

    /** 0.05kg 도 안 되는 차이로 설정을 자꾸 고쳐 쓰지 않는다. */
    @Test
    fun `a tiny weight difference does not rewrite the setting`() = runBlocking<Unit> {
        enable(importWeight = true)
        settings.update { it.copy(bodyWeightKg = 70.0) }

        gateway.weightKg = 70.02
        syncer().sync()
        assertThat(settings.current().bodyWeightKg).isEqualTo(70.0)

        gateway.weightKg = 70.2
        syncer().sync()
        assertThat(settings.current().bodyWeightKg).isWithin(0.001).of(70.2)
    }

    // ----- 이 기기에만 있는 값 -----

    @Test
    fun `health connect settings are not carried by a backup`() = runBlocking<Unit> {
        val backup = BackupRepositoryImpl(db, db.backupDao(), settings, Dispatchers.IO)
        finishedRun() // 기록이 하나도 없는 백업은 복원하지 않는다
        settings.update { it.copy(healthConnectEnabled = true, healthConnectImportWeight = true, weeklyWorkoutGoal = 3) }
        val bytes = ByteArrayOutputStream().also { backup.exportBackup(it) }.toByteArray()

        // 다른 기기에서 복원한다고 치고, 그 기기에서는 아직 연동을 켜지 않았다.
        settings.update { it.copy(healthConnectEnabled = false, healthConnectImportWeight = false, weeklyWorkoutGoal = 0) }
        backup.restoreBackup(ByteArrayInputStream(bytes))

        val restored = settings.current()
        assertThat(restored.weeklyWorkoutGoal).isEqualTo(3)
        assertThat(restored.healthConnectEnabled).isFalse()
        assertThat(restored.healthConnectImportWeight).isFalse()
    }

    @Test
    fun `the ledger remembers entries`() = runBlocking<Unit> {
        val store = DataStoreHealthConnectLedger(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) {
                File(context.cacheDir, "ledger-${System.nanoTime()}.preferences_pb")
            },
        )
        assertThat(store.read()).isEmpty()

        store.write(mapOf("health-run-1" to "100:5000:1500:300:4000", "health-workout-2" to "200:2:960:벤치"))

        assertThat(store.read()).containsExactly(
            "health-run-1", "100:5000:1500:300:4000",
            "health-workout-2", "200:2:960:벤치",
        )
    }

    // ----- 설정 화면 -----

    /** 화면이 구독하는 것처럼 상태를 계속 받아 둔다(구독자가 없으면 상태가 갱신되지 않는다). */
    private fun viewModel(scope: TestScope): HealthConnectViewModel {
        val viewModel = HealthConnectViewModel(gateway, settings, syncer(scope = scope.backgroundScope))
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { viewModel.uiState.collect { } }
        return viewModel
    }

    /** 설정 저장은 다른 스레드에서 끝나므로 실제 시간으로 잠깐 기다린다. */
    private fun await(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) {
            dispatcher.scheduler.runCurrent()
            Thread.sleep(10)
        }
        assertThat(condition()).isTrue()
    }

    @Test
    fun `allowing everything turns the export on and syncs`() = runTest(dispatcher) {
        val viewModel = viewModel(this)
        runBlocking { finishedRun() }
        val all = HealthConnectPermissions.all

        viewModel.onPermissionResult(all)
        await { gateway.written().isNotEmpty() }

        assertThat(runBlocking { settings.current().healthConnectEnabled }).isTrue()
        assertThat(viewModel.uiState.value.permissionDenied).isFalse()
    }

    @Test
    fun `denying the permission turns the export off and says why`() = runTest(dispatcher) {
        // 전에 켜 둔 연동인데 이번에 권한이 거절됐다.
        enable()
        gateway.permissions = HealthPermissionState()
        val viewModel = viewModel(this)

        viewModel.onPermissionResult(emptySet())
        await { viewModel.uiState.value.permissionDenied }
        await { !runBlocking { settings.current().healthConnectEnabled } }

        assertThat(runBlocking { settings.current().healthConnectEnabled }).isFalse()
        assertThat(gateway.upserts).isEmpty()
    }

    @Test
    fun `turning the export off keeps what was sent`() = runTest(dispatcher) {
        enable()
        val viewModel = viewModel(this)

        viewModel.setExportEnabled(false)
        await { !runBlocking { settings.current().healthConnectEnabled } }

        assertThat(gateway.deletes).isEmpty()
    }

    @Test
    fun `the settings screen reports what is allowed`() {
        val granted = HealthConnectPermissions.stateFrom(HealthConnectPermissions.exportPermissions)
        assertThat(granted.canWrite).isTrue()
        assertThat(granted.canReadWeight).isFalse()

        val partial = HealthConnectPermissions.stateFrom(
            HealthConnectPermissions.exportPermissions.drop(1).toSet() + HealthConnectPermissions.readWeight,
        )
        assertThat(partial.canWrite).isFalse()
        assertThat(partial.canReadWeight).isTrue()

        assertThat(HealthConnectPermissions.stateFrom(emptySet())).isEqualTo(HealthPermissionState())
    }

    @Test
    fun `the status line tells what to do`() {
        val on = HealthConnectUiState(
            availability = HealthConnectAvailability.AVAILABLE,
            permissions = HealthPermissionState(canWrite = true),
            enabled = true,
        )

        assertThat(healthConnectStatus(HealthConnectUiState())).contains("켜면 허용 화면이 열립니다")
        assertThat(healthConnectStatus(on)).contains("보냅니다")
        assertThat(healthConnectStatus(on.copy(lastSyncAt = 1_700_000_000_000))).contains("마지막 동기화")
        assertThat(healthConnectStatus(on.copy(lastError = "끊김"))).contains("끊김")
        assertThat(healthConnectStatus(on.copy(permissions = HealthPermissionState()))).contains("권한이 꺼져 있습니다")
        assertThat(healthConnectStatus(HealthConnectUiState(permissionDenied = true))).contains("권한이 꺼져 있습니다")
    }

    // ----- 러닝 상세의 심박 -----

    private fun detail(runId: Long) = RunDetailViewModel(
        runRepository = runs,
        settingsRepository = settings,
        savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_RUN_ID to runId)),
        appScope = CoroutineScope(Dispatchers.IO),
        healthConnect = gateway,
    )

    @Test
    fun `a run shows the heart rate from health connect`() = runTest(dispatcher) {
        val runId = runBlocking { finishedRun() }
        gateway.heartRate = HeartRateSummary(averageBpm = 152, maxBpm = 178)

        val state = detail(runId).uiState.first { !it.loading }

        assertThat(state.heartRate).isEqualTo(HeartRateSummary(152, 178))
    }

    @Test
    fun `no permission means no heart rate lookup at all`() = runTest(dispatcher) {
        val runId = runBlocking { finishedRun() }
        gateway.heartRate = HeartRateSummary(152, 178)
        gateway.permissions = HealthPermissionState(canWrite = true, canReadHeartRate = false)

        val state = detail(runId).uiState.first { !it.loading }

        assertThat(state.heartRate).isNull()
        assertThat(gateway.heartRateRequests).isEqualTo(0)
    }

    @Test
    fun `an unavailable health connect shows no heart rate and the run still opens`() = runTest(dispatcher) {
        val runId = runBlocking { finishedRun() }
        gateway.heartRate = HeartRateSummary(152, 178)
        gateway.availability = HealthConnectAvailability.UNAVAILABLE

        val state = detail(runId).uiState.first { !it.loading }

        assertThat(state.heartRate).isNull()
        assertThat(state.run).isNotNull()
    }

    /** 심박을 못 읽는다고 러닝 상세가 안 열리면 안 된다. */
    @Test
    fun `a failing heart rate lookup does not break the run detail`() = runTest(dispatcher) {
        val runId = runBlocking { finishedRun() }
        gateway.failHeartRate = SecurityException("권한 없음")

        val state = detail(runId).uiState.first { !it.loading }

        assertThat(state.heartRate).isNull()
        assertThat(state.run).isNotNull()
    }
}
