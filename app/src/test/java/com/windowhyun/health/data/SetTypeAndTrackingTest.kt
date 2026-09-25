package com.windowhyun.health.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.model.PersonalRecordType
import com.windowhyun.health.core.model.SetType
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
 * 워밍업 제외와 시간·횟수 기록 방식 검증.
 *
 * 워밍업이 집계에 섞이면 총 볼륨과 개인 기록이 실제보다 부풀려지고,
 * 한 번 저장된 기록은 나중에 고쳐도 되돌릴 수 없다.
 */
@RunWith(RobolectricTestRunner::class)
class SetTypeAndTrackingTest {

    private lateinit var db: HealthDatabase
    private lateinit var workouts: WorkoutRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        workouts = WorkoutRepositoryImpl(
            workoutDao = db.workoutDao(),
            routineDao = db.routineDao(),
            exerciseDao = db.exerciseDao(),
            personalRecordDao = db.personalRecordDao(),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addExercise(name: String, tracking: ExerciseTrackingType): Long =
        exercises.addExercise(
            Exercise(
                id = 0,
                name = name,
                category = ExerciseCategory.BARBELL,
                bodyPart = BodyPart.LEG,
                trackingType = tracking,
            ),
        )

    /** 워밍업 세트는 총 볼륨·총 세트·총 반복에서 빠져야 한다. */
    @Test
    fun `excludes warmup sets from the workout totals`() = runTest {
        val exerciseId = addExercise("스쿼트", ExerciseTrackingType.WEIGHT_REPS)
        val workoutId = workouts.startWorkout(null)
        val weId = workouts.addExerciseToWorkout(workoutId, exerciseId)

        workouts.addSet(weId)
        val sets = db.workoutDao().getSets(weId)

        // 1세트는 워밍업 40kg x 10, 2세트는 본세트 100kg x 5
        workouts.setSetType(sets[0].id, SetType.WARMUP)
        workouts.setCompleted(sets[0].id, 40.0, 10, true)
        workouts.setCompleted(sets[1].id, 100.0, 5, true)

        val summary = workouts.finishWorkout(workoutId)

        assertThat(summary.totalVolumeKg).isWithin(0.001).of(500.0) // 워밍업 400kg 제외
        assertThat(summary.totalSets).isEqualTo(1)
        assertThat(summary.totalReps).isEqualTo(5)
    }

    /** 워밍업 중량이 최고 중량 PR 로 잡히면 안 된다. */
    @Test
    fun `does not treat a heavy warmup as a personal record`() = runTest {
        val exerciseId = addExercise("스쿼트", ExerciseTrackingType.WEIGHT_REPS)
        val workoutId = workouts.startWorkout(null)
        val weId = workouts.addExerciseToWorkout(workoutId, exerciseId)
        workouts.addSet(weId)
        val sets = db.workoutDao().getSets(weId)

        // 워밍업에 잘못 입력한 999kg 는 기록이 되면 안 된다.
        workouts.setSetType(sets[0].id, SetType.WARMUP)
        workouts.setCompleted(sets[0].id, 999.0, 1, true)
        workouts.setCompleted(sets[1].id, 100.0, 5, true)

        val summary = workouts.finishWorkout(workoutId)

        val maxWeight = summary.personalRecords.first { it.type == PersonalRecordType.MAX_WEIGHT }
        assertThat(maxWeight.value).isWithin(0.001).of(100.0)
    }

    /** 워밍업은 다음 운동의 기본값(지난 기록)으로도 쓰이면 안 된다. */
    @Test
    fun `does not prefill the next workout from warmup sets`() = runTest {
        val exerciseId = addExercise("스쿼트", ExerciseTrackingType.WEIGHT_REPS)
        val first = workouts.startWorkout(null)
        val weId = workouts.addExerciseToWorkout(first, exerciseId)
        workouts.addSet(weId)
        val sets = db.workoutDao().getSets(weId)
        workouts.setSetType(sets[0].id, SetType.WARMUP)
        workouts.setCompleted(sets[0].id, 40.0, 10, true)
        workouts.setCompleted(sets[1].id, 100.0, 5, true)
        workouts.finishWorkout(first)

        val last = workouts.getLastPerformance(exerciseId, excludeWorkoutId = 0)

        assertThat(last).hasSize(1)
        assertThat(last.first().weightKg).isWithin(0.001).of(100.0)
    }

    /** 시간으로 재는 운동(플랭크)은 시간이 저장되고 최고 시간이 PR 이 된다. */
    @Test
    fun `records a time based exercise`() = runTest {
        val exerciseId = addExercise("플랭크", ExerciseTrackingType.TIME)
        val workoutId = workouts.startWorkout(null)
        val weId = workouts.addExerciseToWorkout(workoutId, exerciseId)
        val set = db.workoutDao().getSets(weId).first()

        workouts.setCompleted(set.id, 0.0, 0, true, durationSeconds = 90)

        val summary = workouts.finishWorkout(workoutId)

        assertThat(db.workoutDao().getSet(set.id)!!.durationSeconds).isEqualTo(90)
        val record = summary.personalRecords.single()
        assertThat(record.type).isEqualTo(PersonalRecordType.MAX_DURATION)
        assertThat(record.value).isWithin(0.001).of(90.0)
        // 중량이 없으므로 볼륨은 0 이어야 한다.
        assertThat(summary.totalVolumeKg).isEqualTo(0.0)
    }

    /** 더 오래 버티면 PR 이 갱신된다. */
    @Test
    fun `beats the previous best duration`() = runTest {
        val exerciseId = addExercise("플랭크", ExerciseTrackingType.TIME)

        val first = workouts.startWorkout(null)
        val firstWe = workouts.addExerciseToWorkout(first, exerciseId)
        workouts.setCompleted(db.workoutDao().getSets(firstWe).first().id, 0.0, 0, true, 90)
        workouts.finishWorkout(first)

        val second = workouts.startWorkout(null)
        val secondWe = workouts.addExerciseToWorkout(second, exerciseId)
        workouts.setCompleted(db.workoutDao().getSets(secondWe).first().id, 0.0, 0, true, 120)
        val summary = workouts.finishWorkout(second)

        val record = summary.personalRecords.single()
        assertThat(record.type).isEqualTo(PersonalRecordType.MAX_DURATION)
        assertThat(record.previousValue).isWithin(0.001).of(90.0)
    }

    /** 횟수로 재는 운동(풀업)은 최고 횟수가 PR 이 된다. 1RM 은 의미가 없다. */
    @Test
    fun `records a reps only exercise`() = runTest {
        val exerciseId = addExercise("풀업", ExerciseTrackingType.REPS_ONLY)
        val workoutId = workouts.startWorkout(null)
        val weId = workouts.addExerciseToWorkout(workoutId, exerciseId)
        workouts.setCompleted(db.workoutDao().getSets(weId).first().id, 0.0, 12, true)

        val summary = workouts.finishWorkout(workoutId)

        val record = summary.personalRecords.single()
        assertThat(record.type).isEqualTo(PersonalRecordType.MAX_REPS)
        assertThat(record.value).isWithin(0.001).of(12.0)
    }

    /** 기본 제공 종목의 기록 방식이 시드에 반영되어야 한다. */
    @Test
    fun `seeds tracking types for built in exercises`() = runTest {
        val seeded = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HealthDatabase::class.java,
        ).addCallback(com.windowhyun.health.data.seed.ExerciseSeedCallback)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = seeded.exerciseDao()
            assertThat(dao.getByName("플랭크")!!.trackingType).isEqualTo(ExerciseTrackingType.TIME)
            assertThat(dao.getByName("풀업")!!.trackingType).isEqualTo(ExerciseTrackingType.REPS_ONLY)
            assertThat(dao.getByName("스쿼트")!!.trackingType)
                .isEqualTo(ExerciseTrackingType.WEIGHT_REPS)
        } finally {
            seeded.close()
        }
    }
}
