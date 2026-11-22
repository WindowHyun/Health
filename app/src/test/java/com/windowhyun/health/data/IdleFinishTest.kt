package com.windowhyun.health.data

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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 종료를 잊고 한참 뒤에 눌러도 운동 시간이 부풀지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
class IdleFinishTest {

    private lateinit var db: HealthDatabase
    private lateinit var workouts: WorkoutRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workouts = WorkoutRepositoryImpl(db.workoutDao(), db.routineDao(), db.exerciseDao(), db.personalRecordDao())
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val hour = 60 * 60 * 1000L

    /** 세트 하나를 끝낸 진행 중 운동. 시작 · 완료 시각은 지금으로부터 얼마 전으로 옮겨 둔다. */
    private suspend fun workoutStartedAgo(startAgo: Long, lastSetDoneAgo: Long): Long {
        val exerciseId = exercises.addExercise(
            Exercise(id = 0, name = "벤치", category = ExerciseCategory.BARBELL, bodyPart = BodyPart.CHEST),
        )
        val workoutId = workouts.startWorkout(null)
        workouts.addExerciseToWorkout(workoutId, exerciseId)
        val set = workouts.getWorkout(workoutId)!!.exercises.single().sets.single()
        workouts.setCompleted(set.id, 60.0, 8, true, 0)

        val now = System.currentTimeMillis()
        db.workoutDao().updateWorkout(db.workoutDao().getWorkout(workoutId)!!.copy(startTime = now - startAgo))
        val stored = db.workoutDao().getWorkoutDetail(workoutId)!!.exercises.single().sets.single()
        db.workoutDao().updateSet(stored.copy(completedAt = now - lastSetDoneAgo))
        return workoutId
    }

    @Test
    fun `forgetting to finish does not count the idle hours`() = runTest {
        // 6시간 전에 시작해 5시간 전에 마지막 세트를 끝내고, 지금에야 종료를 눌렀다.
        val workoutId = workoutStartedAgo(startAgo = 6 * hour, lastSetDoneAgo = 5 * hour)

        val summary = workouts.finishWorkout(workoutId)

        assertThat(summary.durationSeconds).isAtLeast(3_600L - 5)
        assertThat(summary.durationSeconds).isAtMost(3_600L + 5)
        assertThat(workouts.getWorkout(workoutId)!!.durationSeconds).isEqualTo(summary.durationSeconds)
    }

    @Test
    fun `finishing soon after the last set counts up to now`() = runTest {
        // 1시간 전에 시작해 10분 전에 마지막 세트를 끝냈다. 쉬는 중이었으니 지금까지가 운동 시간이다.
        val workoutId = workoutStartedAgo(startAgo = 1 * hour, lastSetDoneAgo = 10 * 60 * 1000L)

        val summary = workouts.finishWorkout(workoutId)

        assertThat(summary.durationSeconds).isAtLeast(3_600L - 5)
        assertThat(summary.durationSeconds).isAtMost(3_600L + 5)
    }
}
