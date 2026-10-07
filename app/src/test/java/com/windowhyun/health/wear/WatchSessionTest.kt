package com.windowhyun.health.wear

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.notification.RestTimerNotifier
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.shared.WatchCommand
import com.windowhyun.health.ui.navigation.Routes
import com.windowhyun.health.ui.session.WorkoutSessionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
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

/** 세션의 휴식 타이머가 시계와 어떻게 이어지는지. 시계에서 누른 버튼은 폰 화면에서 누른 것과 같아야 한다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WatchSessionTest {

    private val dispatcher = StandardTestDispatcher()
    private var wallNow = 1_700_000_000_000L
    private val link = WatchLink { wallNow }

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
        val file = File(context.cacheDir, "watch-session-${System.nanoTime()}.preferences_pb")
        settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher)) { file },
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel() = WorkoutSessionViewModel(
        workoutRepository = workouts,
        exerciseRepository = exercises,
        settingsRepository = settings,
        restTimerNotifier = RestTimerNotifier(context),
        savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_WORKOUT_ID to 1L)),
        clock = { dispatcher.scheduler.currentTime },
        watchLink = link,
    )

    /** 화면의 경과 시간 타이머는 끝나지 않는다. 단언이 실패해도 정리해야 테스트가 멈추지 않는다. */
    private suspend fun withSession(block: suspend (WorkoutSessionViewModel) -> Unit) {
        val viewModel = viewModel()
        // 시계 명령을 듣기 시작하도록 한 번 돌려 준다(실제로는 화면이 열리는 사이 이미 끝나 있다).
        dispatcher.scheduler.runCurrent()
        try {
            block(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `starting a rest tells the watch how long is left`() = runTest(dispatcher) {
        withSession { viewModel ->
            viewModel.startRestTimer(90)

            val rest = link.rest.value
            assertThat(rest.active).isTrue()
            assertThat(rest.totalSeconds).isEqualTo(90)
            assertThat(rest.paused).isFalse()
            assertThat(rest.endsAtMillis).isEqualTo(wallNow + 90_000)
        }
    }

    @Test
    fun `adjusting the rest moves the end time the watch sees`() = runTest(dispatcher) {
        withSession { viewModel ->
            viewModel.startRestTimer(60)
            viewModel.adjustRestTimer(15)

            assertThat(link.rest.value.endsAtMillis).isEqualTo(wallNow + 75_000)
            assertThat(link.rest.value.totalSeconds).isEqualTo(75)
        }
    }

    @Test
    fun `pausing freezes the remaining time and resuming sets a new end`() = runTest(dispatcher) {
        withSession { viewModel ->
            viewModel.startRestTimer(60)
            advanceTimeBy(10_000)
            viewModel.toggleRestPause()

            assertThat(link.rest.value.paused).isTrue()
            assertThat(link.rest.value.pausedRemainingMillis).isEqualTo(50_000)

            viewModel.toggleRestPause()
            assertThat(link.rest.value.paused).isFalse()
            assertThat(link.rest.value.endsAtMillis).isEqualTo(wallNow + 50_000)
        }
    }

    @Test
    fun `stopping the rest clears the watch`() = runTest(dispatcher) {
        withSession { viewModel ->
            viewModel.startRestTimer(60)
            viewModel.stopRestTimer()

            assertThat(link.rest.value.active).isFalse()
        }
    }

    /** 끝난 뒤 잠깐 0 을 보여 주다 사라지면 시계의 휴식 화면도 같이 사라진다. */
    @Test
    fun `the watch rest goes away after the afterglow`() = runTest(dispatcher) {
        withSession { viewModel ->
            viewModel.startRestTimer(2)
            advanceTimeBy(2_300)
            assertThat(link.rest.value.active).isTrue() // 끝난 직후엔 아직 0 을 보여 준다

            advanceTimeBy(2_500)

            assertThat(viewModel.restTimer.value.visible).isFalse()
            assertThat(link.rest.value.active).isFalse()
        }
    }

    // ----- 시계에서 온 명령 -----

    @Test
    fun `plus and minus fifteen from the watch change the rest like the phone buttons`() = runTest(dispatcher) {
        withSession { viewModel ->
            viewModel.startRestTimer(60)

            link.emitRestCommand(WatchCommand.REST_ADD)
            advanceTimeBy(1)
            assertThat(viewModel.restTimer.value.remainingSeconds).isEqualTo(75)

            link.emitRestCommand(WatchCommand.REST_SUB)
            link.emitRestCommand(WatchCommand.REST_SUB)
            advanceTimeBy(1)
            assertThat(viewModel.restTimer.value.remainingSeconds).isEqualTo(45)
            // 명령을 처리하느라 흐른 1ms 안팎의 차이는 허용한다(테스트 시계와 보내는 시각의 기준이 다르다).
            assertThat(link.rest.value.endsAtMillis).isIn(
                com.google.common.collect.Range.closed(wallNow + 44_990, wallNow + 45_000),
            )
        }
    }

    @Test
    fun `skip from the watch ends the rest`() = runTest(dispatcher) {
        withSession { viewModel ->
            viewModel.startRestTimer(60)

            link.emitRestCommand(WatchCommand.REST_SKIP)
            advanceTimeBy(1)

            assertThat(viewModel.restTimer.value.visible).isFalse()
            assertThat(link.rest.value.active).isFalse()
        }
    }

    @Test
    fun `pause from the watch pauses and a second press resumes`() = runTest(dispatcher) {
        withSession { viewModel ->
            viewModel.startRestTimer(60)

            link.emitRestCommand(WatchCommand.REST_TOGGLE_PAUSE)
            advanceTimeBy(1)
            assertThat(viewModel.restTimer.value.paused).isTrue()

            link.emitRestCommand(WatchCommand.REST_TOGGLE_PAUSE)
            advanceTimeBy(1)
            assertThat(viewModel.restTimer.value.paused).isFalse()
        }
    }

    /** 운동 화면을 나가면 타이머가 멈추므로, 시계에 남은 휴식 화면도 치운다. */
    @Test
    fun `leaving the session clears the watch`() = runTest(dispatcher) {
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel() as T
            },
        )[WorkoutSessionViewModel::class.java]
        viewModel.startRestTimer(60)
        assertThat(link.rest.value.active).isTrue()

        store.clear()

        assertThat(link.rest.value.active).isFalse()
    }

    // ----- 폰이 시계 메시지를 받는 서비스가 등록돼 있는가 -----

    /** 선언이 빠지면 시계에서 누른 버튼이 폰에 아무 일도 일으키지 않는다. 눈에 띄지 않는 고장이라 확인해 둔다. */
    @Test
    fun `the watch command service is registered for wearable messages`() {
        val intent = Intent("com.google.android.gms.wearable.MESSAGE_RECEIVED")
            .setData(Uri.parse("wear://some-node${WatchCommand.RUN_PAUSE.path}"))
            .setPackage(context.packageName)

        val services = context.packageManager.queryIntentServices(intent, 0)

        assertThat(services.map { it.serviceInfo.name }).contains(WatchCommandService::class.java.name)
    }

    @Test
    fun `messages outside the command folder do not wake the service`() {
        val intent = Intent("com.google.android.gms.wearable.MESSAGE_RECEIVED")
            .setData(Uri.parse("wear://some-node/something/else"))
            .setPackage(context.packageName)

        assertThat(context.packageManager.queryIntentServices(intent, 0)).isEmpty()
    }
}
