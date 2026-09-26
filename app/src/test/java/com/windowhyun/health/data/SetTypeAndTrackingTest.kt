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

    // ----- 코드 리뷰 수정 -----

    /** 세트 번호를 한 번 누르면 워밍업이 되어야 한다. 가장 흔한 이유가 워밍업 표시다. */
    @Test
    fun `one tap turns a normal set into a warmup`() {
        assertThat(SetType.NORMAL.next()).isEqualTo(SetType.WARMUP)
        // 네 번 누르면 제자리로 돌아온다.
        var type = SetType.NORMAL
        val seen = mutableListOf<SetType>()
        repeat(4) {
            type = type.next()
            seen += type
        }
        assertThat(seen).containsExactly(SetType.WARMUP, SetType.DROP, SetType.FAILURE, SetType.NORMAL).inOrder()
    }

    /**
     * 횟수 운동에는 중량 칸이 없다. 예전에 +10kg 로 기록한 풀업이 "횟수" 운동이 된 뒤에도
     * 10kg 가 복사되면, 보이지도 지워지지도 않는 값이 볼륨에 들어간다.
     */
    @Test
    fun `never carries a hidden weight into a reps only exercise`() = runTest {
        val pullUpId = addExercise("풀업", ExerciseTrackingType.WEIGHT_REPS)
        val first = workouts.startWorkout(null)
        val firstWe = workouts.addExerciseToWorkout(first, pullUpId)
        workouts.setCompleted(db.workoutDao().getSets(firstWe).first().id, 10.0, 8, true)
        workouts.finishWorkout(first)

        // 풀업이 횟수 운동으로 바뀐다(v3 마이그레이션과 같은 상황).
        val pullUp = exercises.getExercise(pullUpId)!!
        exercises.updateExercise(pullUp.copy(trackingType = ExerciseTrackingType.REPS_ONLY))

        val second = workouts.startWorkout(null)
        val secondWe = workouts.addExerciseToWorkout(second, pullUpId)
        val planned = db.workoutDao().getSets(secondWe).first()
        assertThat(planned.weightKg).isEqualTo(0.0)
        assertThat(planned.reps).isEqualTo(8) // 횟수는 그대로 이어받는다

        // 화면이 숨은 값을 넘겨도 저장되지 않는다.
        workouts.setCompleted(planned.id, 10.0, 8, true)
        workouts.addSet(secondWe)
        val sets = db.workoutDao().getSets(secondWe)
        assertThat(sets.map { it.weightKg }).containsExactly(0.0, 0.0)

        assertThat(workouts.finishWorkout(second).totalVolumeKg).isEqualTo(0.0)
    }

    /**
     * 끝난 기록에서 세트를 워밍업으로 바꾸면 그 세트로 세운 PR 이 사라지고,
     * 그 잘못된 값 때문에 막혔던 뒤 기록의 PR 이 살아나야 한다.
     */
    @Test
    fun `recomputes records after a finished set becomes a warmup`() = runTest {
        val squatId = addExercise("스쿼트", ExerciseTrackingType.WEIGHT_REPS)
        suspend fun session(vararg sets: Pair<Double, Int>): Pair<Long, List<Long>> {
            val workoutId = workouts.startWorkout(null)
            val weId = workouts.addExerciseToWorkout(workoutId, squatId)
            repeat(sets.size - 1) { workouts.addSet(weId) }
            val ids = db.workoutDao().getSets(weId).map { it.id }
            sets.forEachIndexed { i, (kg, reps) -> workouts.setCompleted(ids[i], kg, reps, true) }
            workouts.finishWorkout(workoutId)
            return workoutId to ids
        }

        session(100.0 to 5)
        // 워밍업인데 200kg 로 잘못 입력했다 -> 가짜 최고 중량 PR
        val (mistake, mistakeSets) = session(200.0 to 1, 100.0 to 5)
        // 그 뒤 진짜로 150kg 를 들었지만 기준이 200kg 라 PR 이 안 잡혔다
        val (later, _) = session(150.0 to 3)
        val records = db.personalRecordDao()
        assertThat(records.getByWorkout(mistake).map { it.type }).contains(PersonalRecordType.MAX_WEIGHT.name)
        assertThat(records.getByWorkout(later).map { it.type }).doesNotContain(PersonalRecordType.MAX_WEIGHT.name)

        workouts.setSetType(mistakeSets[0], SetType.WARMUP)

        assertThat(records.getByWorkout(mistake).map { it.type })
            .doesNotContain(PersonalRecordType.MAX_WEIGHT.name)
        val laterWeight = records.getByWorkout(later).single { it.type == PersonalRecordType.MAX_WEIGHT.name }
        assertThat(laterWeight.value).isWithin(0.001).of(150.0)
        assertThat(laterWeight.previousValue).isWithin(0.001).of(100.0)
    }

    /** 끝난 기록을 지워도 뒤 기록의 PR 기준이 다시 맞춰진다. */
    @Test
    fun `recomputes records after a finished workout is deleted`() = runTest {
        val squatId = addExercise("스쿼트", ExerciseTrackingType.WEIGHT_REPS)
        suspend fun session(kg: Double, reps: Int): Long {
            val workoutId = workouts.startWorkout(null)
            val weId = workouts.addExerciseToWorkout(workoutId, squatId)
            workouts.setCompleted(db.workoutDao().getSets(weId).first().id, kg, reps, true)
            workouts.finishWorkout(workoutId)
            return workoutId
        }
        session(100.0, 5)
        val mistake = session(200.0, 1)
        val later = session(150.0, 3)

        workouts.deleteWorkout(mistake)

        val laterWeight = db.personalRecordDao().getByWorkout(later)
            .single { it.type == PersonalRecordType.MAX_WEIGHT.name }
        assertThat(laterWeight.value).isWithin(0.001).of(150.0)
    }

    /** 진행 중인 세션을 고칠 때는 PR 을 건드리지 않는다. 끝낼 때 계산한다. */
    @Test
    fun `does not write records while a workout is in progress`() = runTest {
        val squatId = addExercise("스쿼트", ExerciseTrackingType.WEIGHT_REPS)
        val workoutId = workouts.startWorkout(null)
        val weId = workouts.addExerciseToWorkout(workoutId, squatId)
        workouts.setCompleted(db.workoutDao().getSets(weId).first().id, 100.0, 5, true)

        assertThat(db.personalRecordDao().getByWorkout(workoutId)).isEmpty()
    }

    /** 시간·횟수 운동의 현재 기록은 그 방식의 지표로 나와야 한다(0kg 세 줄이 아니라). */
    @Test
    fun `current records follow the tracking type`() = runTest {
        val plankId = addExercise("플랭크", ExerciseTrackingType.TIME)
        val workoutId = workouts.startWorkout(null)
        val weId = workouts.addExerciseToWorkout(workoutId, plankId)
        workouts.setCompleted(db.workoutDao().getSets(weId).first().id, 0.0, 0, true, durationSeconds = 120)
        workouts.finishWorkout(workoutId)

        val current = workouts.getPersonalRecords(plankId)

        assertThat(current.map { it.type }).containsExactly(PersonalRecordType.MAX_DURATION)
        assertThat(current.single().value).isWithin(0.001).of(120.0)
    }
}
