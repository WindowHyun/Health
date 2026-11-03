package com.windowhyun.health.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.SetType
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RoutineRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.model.RoutineItem
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 세트를 잘못 지웠을 때 되돌리기. (한 세트도 안 한 운동 버리기는 세션 화면 쪽 테스트에서 본다.) */
@RunWith(RobolectricTestRunner::class)
class SessionSafetyRepositoryTest {

    private lateinit var db: HealthDatabase
    private lateinit var workouts: WorkoutRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var routines: RoutineRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).allowMainThreadQueries().build()
            .also { it.openHelper.writableDatabase } // 먼저 열어 둔다: 닫을 때 여는 중이면 서로 기다려 멈춘다
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        routines = RoutineRepositoryImpl(db.routineDao())
        workouts = WorkoutRepositoryImpl(db.workoutDao(), db.routineDao(), db.exerciseDao(), db.personalRecordDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun startBench(sets: Int = 4): Long {
        val exerciseId = exercises.addExercise(Exercise(0, "벤치프레스", ExerciseCategory.BARBELL, BodyPart.CHEST))
        val exercise = exercises.getExercise(exerciseId)!!
        val routineId = routines.saveRoutine(
            Routine(name = "상체", items = listOf(RoutineItem(exercise = exercise, orderIndex = 0, defaultSets = sets))),
        )
        return workouts.startWorkout(routineId)
    }

    private suspend fun setsOf(workoutId: Long) = workouts.getWorkout(workoutId)!!.exercises.first().sets

    // ----- 세트 되살리기 -----

    @Test
    fun `a removed set comes back at its place with its values`() = runTest {
        val workoutId = startBench(sets = 4)
        val original = setsOf(workoutId)
        original.forEach { workouts.setCompleted(it.id, 60.0 + it.setNumber * 2.5, 8, false, 0) }
        val victim = setsOf(workoutId)[1] // 두 번째 세트
        val exerciseId = workouts.getWorkout(workoutId)!!.exercises.first().id

        workouts.removeSet(victim.id)
        assertThat(setsOf(workoutId)).hasSize(3)

        workouts.restoreSet(exerciseId, victim)

        val restored = setsOf(workoutId)
        assertThat(restored).hasSize(4)
        assertThat(restored.map { it.setNumber }).containsExactly(1, 2, 3, 4).inOrder()
        assertThat(restored.map { it.weightKg }).containsExactlyElementsIn(original.map { 60.0 + it.setNumber * 2.5 }).inOrder()
    }

    @Test
    fun `a removed set keeps its type and completed state when restored`() = runTest {
        val workoutId = startBench(sets = 3)
        val first = setsOf(workoutId).first()
        workouts.setSetType(first.id, SetType.WARMUP)
        workouts.setCompleted(first.id, 40.0, 10, true, 0)
        val exerciseId = workouts.getWorkout(workoutId)!!.exercises.first().id
        val victim = setsOf(workoutId).first()

        workouts.removeSet(victim.id)
        workouts.restoreSet(exerciseId, victim)

        val back = setsOf(workoutId).first()
        assertThat(back.setType).isEqualTo(SetType.WARMUP)
        assertThat(back.completed).isTrue()
        assertThat(back.weightKg).isEqualTo(40.0)
        assertThat(back.reps).isEqualTo(10)
    }

    /** 맨 뒤 세트를 지웠다 되살려도 번호가 어긋나지 않는다. */
    @Test
    fun `the last set can be restored too`() = runTest {
        val workoutId = startBench(sets = 3)
        val victim = setsOf(workoutId).last()
        val exerciseId = workouts.getWorkout(workoutId)!!.exercises.first().id

        workouts.removeSet(victim.id)
        workouts.restoreSet(exerciseId, victim)

        assertThat(setsOf(workoutId).map { it.setNumber }).containsExactly(1, 2, 3).inOrder()
    }
}
