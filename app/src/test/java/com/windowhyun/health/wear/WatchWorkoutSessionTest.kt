package com.windowhyun.health.wear

import android.Manifest
import android.app.NotificationManager
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
import com.windowhyun.health.domain.model.LocationSample
import com.windowhyun.health.domain.repository.LocationTracker
import com.windowhyun.health.service.RunServiceController
import com.windowhyun.health.service.RunTrackingService
import com.windowhyun.health.ui.navigation.Routes
import com.windowhyun.health.ui.session.WorkoutSessionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

/** 시계에서 세트를 완료하면 폰에서 누른 것과 똑같이 되는지, 시계에서 러닝을 시작할 때 폰이 어떻게 하는지. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WatchWorkoutSessionTest {

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
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).allowMainThreadQueries().build().also { it.openHelper.writableDatabase } // 먼저 열어 둔다: 닫을 때 여는 중이면 서로 기다려 멈춘다
        workouts = WorkoutRepositoryImpl(db.workoutDao(), db.routineDao(), db.exerciseDao(), db.personalRecordDao())
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher)) {
                File(context.cacheDir, "watch-workout-${System.nanoTime()}.preferences_pb")
            },
        )
        AppForeground.setStartedActivitiesForTest(0)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
        AppForeground.setStartedActivitiesForTest(0)
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

    /** 벤치 3세트(62.5kg x 8)로 운동을 시작해 두고 세트 번호들을 돌려준다. */
    private fun startBench(reps: Int = 8): List<Long> = runBlocking {
        val exerciseId = exercises.addExercise(Exercise(0, "벤치", ExerciseCategory.BARBELL, BodyPart.CHEST))
        val workoutId = workouts.startWorkout(null)
        val workoutExerciseId = workouts.addExerciseToWorkout(workoutId, exerciseId)
        repeat(2) { workouts.addSet(workoutExerciseId) }
        val sets = workouts.getWorkout(workoutId)!!.exercises.single().sets
        sets.forEach { workouts.setCompleted(it.id, 62.5, reps, false, 0) }
        sets.map { it.id }
    }

    private fun completedSets(): List<Boolean> = runBlocking {
        workouts.getWorkout(1L)!!.exercises.single().sets.map { it.completed }
    }

    /** Room 은 다른 스레드에서 끝나므로 실제 시간으로 잠깐 기다리며 테스트 시계도 함께 돌린다. */
    private fun await(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) {
            dispatcher.scheduler.advanceTimeBy(100)
            dispatcher.scheduler.runCurrent()
            Thread.sleep(10)
        }
        assertThat(condition()).isTrue()
    }

    /**
     * 아무 일도 안 일어나야 하는 테스트용. 저장 한 번에 코루틴이 여러 번 갈아타므로, 일이 일어날 시간을 충분히 준다.
     * (짧게 기다리면 잘못 동작해도 아직 안 일어난 것으로 보여 통과해 버린다.)
     */
    private fun settle(millis: Long = 1_000) {
        val end = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < end) {
            dispatcher.scheduler.runCurrent()
            Thread.sleep(25)
        }
    }

    private fun withSession(block: (WorkoutSessionViewModel) -> Unit) {
        val viewModel = viewModel()
        dispatcher.scheduler.runCurrent()
        try {
            block(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `opening the session shows the current set on the watch`() {
        val ids = startBench()
        withSession {
            await { link.workout.value.active }

            val snapshot = link.workout.value
            assertThat(snapshot.exerciseName).isEqualTo("벤치")
            assertThat(snapshot.setId).isEqualTo(ids[0])
            assertThat(snapshot.setNumber).isEqualTo(1)
            assertThat(snapshot.setCount).isEqualTo(3)
            assertThat(snapshot.weightKg).isEqualTo(62.5)
            assertThat(snapshot.reps).isEqualTo(8)
        }
    }

    @Test
    fun `completing from the watch finishes the set and starts the rest`() {
        val ids = startBench()
        withSession { viewModel ->
            await { link.workout.value.active }

            assertThat(link.emitSetCompletion(ids[0])).isTrue()
            await { completedSets() == listOf(true, false, false) }

            // 폰에서 완료 버튼을 누른 것과 같다: 쉬는 시간이 시작된다.
            await { viewModel.restTimer.value.visible }
            // 그리고 시계에는 다음 세트가 뜬다.
            await { link.workout.value.setId == ids[1] }
            assertThat(link.workout.value.setNumber).isEqualTo(2)
        }
    }

    /** 같은 완료를 시계가 두 번 보내도(눌림이 겹쳤다) 다음 세트까지 끝나지 않는다. */
    @Test
    fun `a repeated completion does not finish the next set`() {
        val ids = startBench()
        withSession {
            await { link.workout.value.active }
            link.emitSetCompletion(ids[0])
            await { completedSets() == listOf(true, false, false) }
            await { link.workout.value.setId == ids[1] }

            // 시계 화면이 아직 첫 세트를 보고 있었다.
            assertThat(link.emitSetCompletion(ids[0])).isFalse()
            settle()
            assertThat(completedSets()).isEqualTo(listOf(true, false, false))
        }
    }

    /**
     * 폰에서 먼저 끝냈는데 시계로 가는 세트 정보가 아직 새로 안 갔다(값이 멈춘 뒤에야 보낸다).
     * 그 사이 시계가 같은 세트의 완료를 보내도 다음 세트가 끝나지 않는다.
     */
    @Test
    fun `a set already finished on the phone is not followed by the next one`() {
        val ids = startBench()
        withSession {
            await { link.workout.value.active }

            runBlocking { workouts.setCompleted(ids[0], 62.5, 8, true, 0) }
            // 시계로 가는 정보는 아직 첫 세트다.
            assertThat(link.emitSetCompletion(ids[0])).isTrue()
            settle()

            assertThat(completedSets()).isEqualTo(listOf(true, false, false))
        }
    }

    /** 폰에서 횟수를 지웠는데 시계는 아직 예전 값을 보고 있다. 빈 세트는 완료하지 않는다. */
    @Test
    fun `a set emptied on the phone is not completed from the watch`() {
        val ids = startBench()
        withSession {
            await { link.workout.value.active }

            runBlocking { workouts.setCompleted(ids[0], 62.5, 0, false, 0) }
            assertThat(link.emitSetCompletion(ids[0])).isTrue()
            settle()

            assertThat(completedSets()).isEqualTo(listOf(false, false, false))
        }
    }

    @Test
    fun `a set with no reps is not completed by the watch`() {
        val ids = startBench(reps = 0)
        withSession {
            await { link.workout.value.active }

            assertThat(link.emitSetCompletion(ids[0])).isFalse()
        }
    }

    @Test
    fun `leaving the session clears the watch`() {
        startBench()
        val viewModel = viewModel()
        dispatcher.scheduler.runCurrent()
        await { link.workout.value.active }

        // 화면이 사라질 때 ViewModel 이 정리된다.
        val onCleared = viewModel.javaClass.getDeclaredMethod("onCleared").apply { isAccessible = true }
        onCleared.invoke(viewModel)

        assertThat(link.workout.value.active).isFalse()
        viewModel.viewModelScope.cancel()
    }

    // ----- 시계에서 러닝 시작 -----

    private class FakeLocation(private val permitted: Boolean) : LocationTracker {
        override fun locationUpdates(intervalMillis: Long): Flow<LocationSample> = emptyFlow()
        override fun isLocationAvailable() = true
        override fun hasLocationPermission() = permitted
    }

    private fun starter(permitted: Boolean = true) =
        WatchRunStarter(context, FakeLocation(permitted), RunServiceController(context))

    private fun postedNotification() = shadowOf(context.getSystemService(NotificationManager::class.java))
        .getNotification(WatchRunStarter.NOTIFICATION_ID)

    @Test
    fun `with the app on screen the run starts right away`() {
        AppForeground.setStartedActivitiesForTest(1)

        assertThat(starter().requestStart()).isTrue()

        val started = shadowOf(context as android.app.Application).nextStartedService
        assertThat(started.action).isEqualTo(RunTrackingService.ACTION_START)
        assertThat(postedNotification()).isNull()
    }

    /** 앱이 화면에 없으면 위치 서비스를 몰래 시작할 수 없다. 사용자가 누를 알림을 띄운다. */
    @Test
    fun `with the app in the pocket it asks you to tap a button instead`() {
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertThat(starter().requestStart()).isTrue()

        assertThat(shadowOf(context as android.app.Application).nextStartedService).isNull()
        val notification = postedNotification()
        assertThat(notification).isNotNull()
        assertThat(notification.actions.single().title.toString()).isEqualTo("러닝 시작")
    }

    @Test
    fun `without location permission the run cannot start`() {
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        AppForeground.setStartedActivitiesForTest(1)

        assertThat(starter(permitted = false).requestStart()).isFalse()

        assertThat(shadowOf(context as android.app.Application).nextStartedService).isNull()
        // 알림은 뜨지만 시작 버튼은 없다(눌러도 시작할 수 없으니).
        val notification = postedNotification()
        assertThat(notification).isNotNull()
        assertThat(notification.actions).isNull()
    }
}
