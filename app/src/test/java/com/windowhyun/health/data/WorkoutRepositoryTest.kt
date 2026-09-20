package com.windowhyun.health.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.PersonalRecordType
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RoutineRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.model.RoutineItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek

/**
 * 실제 Room DB(인메모리)로 "운동 시작 -> 세트 기록 -> 종료" 흐름을 검증한다.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryTest {

    private lateinit var db: HealthDatabase
    private lateinit var workoutRepository: WorkoutRepositoryImpl
    private lateinit var routineRepository: RoutineRepositoryImpl
    private lateinit var exerciseRepository: ExerciseRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exerciseRepository = ExerciseRepositoryImpl(db.exerciseDao())
        routineRepository = RoutineRepositoryImpl(db.routineDao())
        workoutRepository = WorkoutRepositoryImpl(
            workoutDao = db.workoutDao(),
            routineDao = db.routineDao(),
            exerciseDao = db.exerciseDao(),
            personalRecordDao = db.personalRecordDao(),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun createBenchRoutine(sets: Int = 3): Pair<Long, Long> {
        val exerciseId = exerciseRepository.addExercise(
            Exercise(
                id = 0,
                name = "벤치프레스",
                category = ExerciseCategory.BARBELL,
                bodyPart = BodyPart.CHEST,
            ),
        )
        val exercise = exerciseRepository.getExercise(exerciseId)!!
        val routineId = routineRepository.saveRoutine(
            Routine(
                name = "상체",
                scheduledDays = setOf(DayOfWeek.MONDAY),
                items = listOf(RoutineItem(exercise = exercise, orderIndex = 0, defaultSets = sets)),
            ),
        )
        return routineId to exerciseId
    }

    @Test
    /** 루틴으로 시작하면 기본 세트 수만큼 세트 생성 */
    fun `starting from a routine creates the planned sets`() = runTest {
        val (routineId, _) = createBenchRoutine(sets = 3)

        val workoutId = workoutRepository.startWorkout(routineId)
        val workout = workoutRepository.getWorkout(workoutId)!!

        assertThat(workout.routineName).isEqualTo("상체")
        assertThat(workout.isActive).isTrue()
        assertThat(workout.exercises).hasSize(1)
        assertThat(workout.exercises.first().sets).hasSize(3)
        assertThat(workout.exercises.first().sets.map { it.setNumber }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    /** 지난 기록이 다음 운동의 기본값이 된다 */
    fun `prefills the next workout from the last performance`() = runTest {
        val (routineId, _) = createBenchRoutine(sets = 3)

        // 1회차: 60kg x 10, 10, 8
        val first = workoutRepository.startWorkout(routineId)
        val firstSets = workoutRepository.getWorkout(first)!!.exercises.first().sets
        workoutRepository.setCompleted(firstSets[0].id, 60.0, 10, true)
        workoutRepository.setCompleted(firstSets[1].id, 60.0, 10, true)
        workoutRepository.setCompleted(firstSets[2].id, 60.0, 8, true)
        workoutRepository.finishWorkout(first)

        // 2회차: 시작하자마자 지난 값이 들어 있어야 한다.
        val second = workoutRepository.startWorkout(routineId)
        val secondSets = workoutRepository.getWorkout(second)!!.exercises.first().sets

        assertThat(secondSets.map { it.weightKg }).containsExactly(60.0, 60.0, 60.0).inOrder()
        assertThat(secondSets.map { it.reps }).containsExactly(10, 10, 8).inOrder()
        assertThat(secondSets.none { it.completed }).isTrue()
    }

    @Test
    /** 종료 시 미완료 세트 정리 + 요약 생성 */
    fun `finishing drops incomplete sets and builds a summary`() = runTest {
        val (routineId, _) = createBenchRoutine(sets = 3)
        val workoutId = workoutRepository.startWorkout(routineId)
        val sets = workoutRepository.getWorkout(workoutId)!!.exercises.first().sets

        workoutRepository.setCompleted(sets[0].id, 60.0, 10, true)
        workoutRepository.setCompleted(sets[1].id, 60.0, 8, true)
        // 3세트는 완료하지 않는다.

        val summary = workoutRepository.finishWorkout(workoutId)

        assertThat(summary.totalSets).isEqualTo(2)
        assertThat(summary.totalReps).isEqualTo(18)
        assertThat(summary.totalVolumeKg).isWithin(0.001).of(60.0 * 10 + 60.0 * 8)
        assertThat(summary.exerciseCount).isEqualTo(1)

        val stored = workoutRepository.getWorkout(workoutId)!!
        assertThat(stored.isActive).isFalse()
        assertThat(stored.exercises.first().sets).hasSize(2)
    }

    @Test
    /** 첫 기록은 PR 로 저장되어 다시 읽을 수 있다 */
    fun `persists personal records so the summary can reread them`() = runTest {
        val (routineId, _) = createBenchRoutine(sets = 1)
        val workoutId = workoutRepository.startWorkout(routineId)
        val set = workoutRepository.getWorkout(workoutId)!!.exercises.first().sets.first()
        workoutRepository.setCompleted(set.id, 60.0, 10, true)

        val summary = workoutRepository.finishWorkout(workoutId)

        assertThat(summary.personalRecords.map { it.type }).containsExactly(
            PersonalRecordType.MAX_WEIGHT,
            PersonalRecordType.MAX_VOLUME,
            PersonalRecordType.MAX_ESTIMATED_ONE_RM,
        )
        // DB 에 저장되어 화면이 다시 만들어져도 읽을 수 있다.
        val persisted = workoutRepository.observeWorkoutRecords(workoutId).first()
        assertThat(persisted).hasSize(3)
        assertThat(persisted.first().exerciseName).isEqualTo("벤치프레스")
    }

    @Test
    /** 기록을 넘지 못하면 PR 없음 */
    fun `reports no record when the session is weaker`() = runTest {
        val (routineId, _) = createBenchRoutine(sets = 1)

        val first = workoutRepository.startWorkout(routineId)
        val firstSet = workoutRepository.getWorkout(first)!!.exercises.first().sets.first()
        workoutRepository.setCompleted(firstSet.id, 60.0, 10, true)
        workoutRepository.finishWorkout(first)

        val second = workoutRepository.startWorkout(routineId)
        val secondSet = workoutRepository.getWorkout(second)!!.exercises.first().sets.first()
        workoutRepository.setCompleted(secondSet.id, 55.0, 8, true)
        val summary = workoutRepository.finishWorkout(second)

        assertThat(summary.personalRecords).isEmpty()
    }

    @Test
    /** 세트 삭제 후 번호 재정렬 */
    fun `renumbers sets after a deletion`() = runTest {
        val (routineId, _) = createBenchRoutine(sets = 3)
        val workoutId = workoutRepository.startWorkout(routineId)
        val sets = workoutRepository.getWorkout(workoutId)!!.exercises.first().sets

        workoutRepository.removeSet(sets[0].id)

        val remaining = workoutRepository.getWorkout(workoutId)!!.exercises.first().sets
        assertThat(remaining).hasSize(2)
        assertThat(remaining.map { it.setNumber }).containsExactly(1, 2).inOrder()
    }

    @Test
    /** 진행 중인 세션 조회(이어하기) */
    fun `exposes the active session for resuming`() = runTest {
        val (routineId, _) = createBenchRoutine()
        val workoutId = workoutRepository.startWorkout(routineId)

        assertThat(workoutRepository.observeActiveWorkout().first()?.id).isEqualTo(workoutId)

        workoutRepository.finishWorkout(workoutId)
        assertThat(workoutRepository.observeActiveWorkout().first()).isNull()
    }

    @Test
    /** 루틴을 지워도 기록과 루틴 이름은 남는다 */
    fun `keeps the workout and its routine name after the routine is deleted`() = runTest {
        val (routineId, _) = createBenchRoutine(sets = 1)
        val workoutId = workoutRepository.startWorkout(routineId)
        val set = workoutRepository.getWorkout(workoutId)!!.exercises.first().sets.first()
        workoutRepository.setCompleted(set.id, 50.0, 5, true)
        workoutRepository.finishWorkout(workoutId)

        routineRepository.deleteRoutine(routineId)

        val stored = workoutRepository.getWorkout(workoutId)
        assertThat(stored).isNotNull()
        assertThat(stored!!.routineName).isEqualTo("상체")
        assertThat(stored.routineId).isNull()
    }
}
