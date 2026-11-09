package com.windowhyun.health.wear

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.domain.model.Exercise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 폰의 운동 화면이 없어도 시계가 세트를 보고 끝낼 수 있는지. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackgroundWorkoutBridgeTest {

    private val dispatcher = StandardTestDispatcher()
    private val link = WatchLink { 1_700_000_000_000L }
    private val scope = CoroutineScope(dispatcher)

    private lateinit var db: HealthDatabase
    private lateinit var workouts: WorkoutRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var bridge: BackgroundWorkoutBridge

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).allowMainThreadQueries().build()
            .also { it.openHelper.writableDatabase }
        workouts = WorkoutRepositoryImpl(db.workoutDao(), db.routineDao(), db.exerciseDao(), db.personalRecordDao())
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        bridge = BackgroundWorkoutBridge(workouts, flowOf(false), link, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private fun startBench(): Long = runBlocking {
        val exerciseId = exercises.addExercise(Exercise(0, "bench", ExerciseCategory.BARBELL, BodyPart.CHEST))
        val workoutId = workouts.startWorkout(null)
        val workoutExerciseId = workouts.addExerciseToWorkout(workoutId, exerciseId)
        repeat(2) { workouts.addSet(workoutExerciseId) }
        workouts.getWorkout(workoutId)!!.exercises.single().sets.forEach { workouts.setCompleted(it.id, 60.0, 8, false, 0) }
        workoutId
    }

    private fun completed(workoutId: Long) =
        runBlocking { workouts.getWorkout(workoutId)!!.exercises.single().sets.map { it.completed } }

    private fun await(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) {
            dispatcher.scheduler.advanceTimeBy(100)
            dispatcher.scheduler.runCurrent()
            Thread.sleep(10)
        }
        assertThat(condition()).isTrue()
    }

    private fun settle(millis: Long = 800) {
        val end = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < end) {
            dispatcher.scheduler.advanceTimeBy(50)
            dispatcher.scheduler.runCurrent()
            Thread.sleep(20)
        }
    }

    @Test
    fun `without a session screen the watch sees the current set and can finish it`() {
        val workoutId = startBench()
        bridge.start()
        await { link.workout.value.active }
        val first = link.workout.value.setId

        assertThat(link.emitSetCompletion(first)).isTrue()
        await { completed(workoutId).first() }

        assertThat(completed(workoutId)).containsExactly(true, false, false).inOrder()
        // 끝낸 다음 세트가 시계에 올라온다.
        await { link.workout.value.setId != first }
    }

    @Test
    fun `a stale set id from the watch finishes nothing`() {
        val workoutId = startBench()
        bridge.start()
        await { link.workout.value.active }

        link.emitSetCompletion(link.workout.value.setId + 99)
        settle()

        assertThat(completed(workoutId)).containsExactly(false, false, false)
    }

    @Test
    fun `while a session screen is attached the bridge leaves the watch alone`() {
        startBench()
        link.attachSession()
        bridge.start()
        settle()

        assertThat(link.workout.value.active).isFalse()

        link.detachSession()
        await { link.workout.value.active }
    }

    @Test
    fun `the workout disappears from the watch once it is finished`() {
        val workoutId = startBench()
        bridge.start()
        await { link.workout.value.active }

        runBlocking { workouts.discardWorkout(workoutId) }
        await { !link.workout.value.active }
    }
}
