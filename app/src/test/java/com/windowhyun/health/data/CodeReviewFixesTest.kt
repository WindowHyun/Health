package com.windowhyun.health.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RoutineRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.model.RoutineItem
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * 코드 리뷰에서 나온 결함들의 재현 테스트.
 *
 * 각 테스트는 고치기 전 코드에서 실패해야 의미가 있다.
 */
@RunWith(RobolectricTestRunner::class)
class CodeReviewFixesTest {

    private lateinit var db: HealthDatabase
    private lateinit var workouts: WorkoutRepositoryImpl
    private lateinit var routines: RoutineRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        routines = RoutineRepositoryImpl(db.routineDao())
        workouts = WorkoutRepositoryImpl(
            workoutDao = db.workoutDao(),
            routineDao = db.routineDao(),
            exerciseDao = db.exerciseDao(),
            personalRecordDao = db.personalRecordDao(),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun squatId(): Long = exercises.addExercise(
        Exercise(id = 0, name = "스쿼트", category = ExerciseCategory.BARBELL, bodyPart = BodyPart.LEG),
    )

    // ----- P0: 같은 종목이 두 번 들어가도 PR 이 중복되지 않는다 -----

    /**
     * 같은 종목을 한 세션에 두 번 넣어도 PR 은 종목당 한 번만 나와야 한다.
     * 중복되면 결과 화면의 LazyColumn 키가 겹쳐 앱이 죽는다.
     */
    @Test
    fun `does not produce duplicate records for a repeated exercise`() = runTest {
        val exerciseId = squatId()
        val workoutId = workouts.startWorkout(null)

        // 스쿼트를 두 블록으로 나눠 수행(슈퍼세트처럼).
        repeat(2) {
            val weId = workouts.addExerciseToWorkout(workoutId, exerciseId)
            val set = db.workoutDao().getSets(weId).first()
            workouts.setCompleted(set.id, 100.0, 5, true)
        }

        val summary = workouts.finishWorkout(workoutId)

        val keys = summary.personalRecords.map { it.exerciseId to it.type }
        assertThat(keys).containsNoDuplicates()

        val persisted = workouts.observeWorkoutRecords(workoutId).first()
        assertThat(persisted.map { it.exerciseId to it.type }).containsNoDuplicates()
    }

    /** 나눠서 한 볼륨은 합산되어야 한다(세션 단위 기준). */
    @Test
    fun `sums session volume across repeated blocks of the same exercise`() = runTest {
        val exerciseId = squatId()
        val workoutId = workouts.startWorkout(null)
        repeat(2) {
            val weId = workouts.addExerciseToWorkout(workoutId, exerciseId)
            val set = db.workoutDao().getSets(weId).first()
            workouts.setCompleted(set.id, 100.0, 5, true)
        }

        val summary = workouts.finishWorkout(workoutId)

        // 100kg x 5 를 두 번 = 1,000kg
        val volumeRecord = summary.personalRecords
            .first { it.type == com.windowhyun.health.core.model.PersonalRecordType.MAX_VOLUME }
        assertThat(volumeRecord.value).isWithin(0.001).of(1_000.0)
    }

    // ----- P2: 횟수가 0 으로 지워진 세트가 기준선을 오염시키지 않는다 -----

    /**
     * 완료 표시만 남고 횟수가 0 이 된 세트는 기준선에서 빠져야 한다.
     * 포함되면 그 중량이 영원한 최고 기록이 되어 이후 PR 이 막힌다.
     */
    @Test
    fun `ignores completed sets whose reps were cleared`() = runTest {
        val exerciseId = squatId()

        // 1회차: 실수로 200kg 를 완료 처리했다가 횟수를 0 으로 지움
        val first = workouts.startWorkout(null)
        val firstWe = workouts.addExerciseToWorkout(first, exerciseId)
        val firstSet = db.workoutDao().getSets(firstWe).first()
        workouts.setCompleted(firstSet.id, 200.0, 5, true)
        workouts.setCompleted(firstSet.id, 200.0, 0, true)
        workouts.finishWorkout(first)

        // 2회차: 정상적으로 100kg x 5 → 첫 제대로 된 기록이므로 PR 이어야 한다
        val second = workouts.startWorkout(null)
        val secondWe = workouts.addExerciseToWorkout(second, exerciseId)
        val secondSet = db.workoutDao().getSets(secondWe).first()
        workouts.setCompleted(secondSet.id, 100.0, 5, true)
        val summary = workouts.finishWorkout(second)

        // 최고 중량 PR 이 나와야 한다. 200kg/0회가 기준선에 남아 있으면
        // 100kg 는 그것을 넘지 못해 최고 중량 PR 이 사라진다.
        // (볼륨 PR 은 0회 세트의 볼륨이 0 이라 어느 쪽이든 나오므로 구분이 안 된다.)
        assertThat(summary.personalRecords.map { it.type })
            .contains(com.windowhyun.health.core.model.PersonalRecordType.MAX_WEIGHT)
    }

    // ----- P4: 이미 완료된 세트를 고쳐도 최초 완료 시각은 유지된다 -----

    @Test
    fun `keeps the original completion time when a set is edited`() = runTest {
        val exerciseId = squatId()
        val workoutId = workouts.startWorkout(null)
        val weId = workouts.addExerciseToWorkout(workoutId, exerciseId)
        val set = db.workoutDao().getSets(weId).first()

        workouts.setCompleted(set.id, 100.0, 5, true)
        val firstCompletedAt = db.workoutDao().getSet(set.id)!!.completedAt

        workouts.setCompleted(set.id, 102.5, 5, true)
        val afterEdit = db.workoutDao().getSet(set.id)!!

        assertThat(firstCompletedAt).isNotNull()
        assertThat(afterEdit.completedAt).isEqualTo(firstCompletedAt)
        assertThat(afterEdit.weightKg).isWithin(0.001).of(102.5)
    }

    /** 완료를 취소하면 시각도 지워지고, 다시 완료하면 새로 찍힌다. */
    @Test
    fun `clears the completion time when a set is uncompleted`() = runTest {
        val exerciseId = squatId()
        val workoutId = workouts.startWorkout(null)
        val weId = workouts.addExerciseToWorkout(workoutId, exerciseId)
        val set = db.workoutDao().getSets(weId).first()

        workouts.setCompleted(set.id, 100.0, 5, true)
        workouts.setCompleted(set.id, 100.0, 5, false)

        assertThat(db.workoutDao().getSet(set.id)!!.completedAt).isNull()
    }

    // ----- P2: 루틴 저장이 중복 삽입되지 않는다 -----

    /** 기존 루틴을 다시 저장하면 새로 만들어지지 않고 갱신되어야 한다. */
    @Test
    fun `saving an existing routine updates instead of inserting`() = runTest {
        val exercise = exercises.getExercise(squatId())!!
        val id = routines.saveRoutine(
            Routine(name = "하체", items = listOf(RoutineItem(exercise = exercise, orderIndex = 0, defaultSets = 3))),
        )
        routines.saveRoutine(
            Routine(id = id, name = "하체 A", items = listOf(RoutineItem(exercise = exercise, orderIndex = 0, defaultSets = 4))),
        )

        val all = routines.observeRoutines().first()
        assertThat(all).hasSize(1)
        assertThat(all.first().name).isEqualTo("하체 A")
    }

    // ----- P1: 설정이 동시에 바뀌어도 유실되지 않는다 -----

    /**
     * 두 설정을 동시에 바꿔도 둘 다 남아야 한다.
     * 읽기를 edit 블록 밖에서 하면 뒤엣것이 먼저 것을 덮어쓴다.
     */
    @Test
    fun `concurrent setting changes do not overwrite each other`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "settings-test-${System.nanoTime()}.preferences_pb")
        val settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { file },
        )

        val a = async { settings.update { it.copy(weightUnit = WeightUnit.LB) } }
        val b = async { settings.update { it.copy(distanceUnit = DistanceUnit.MILE) } }
        a.await()
        b.await()

        val result = settings.settings.first()
        assertThat(result.weightUnit).isEqualTo(WeightUnit.LB)
        assertThat(result.distanceUnit).isEqualTo(DistanceUnit.MILE)
    }
}
