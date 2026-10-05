package com.windowhyun.health.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RoutineRepositoryImpl
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

@RunWith(RobolectricTestRunner::class)
class RoutineRepositoryTest {

    private lateinit var db: HealthDatabase
    private lateinit var routineRepository: RoutineRepositoryImpl
    private lateinit var exerciseRepository: ExerciseRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        routineRepository = RoutineRepositoryImpl(db.routineDao())
        exerciseRepository = ExerciseRepositoryImpl(db.exerciseDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun exercise(name: String): Exercise {
        val id = exerciseRepository.addExercise(
            Exercise(id = 0, name = name, category = ExerciseCategory.BARBELL, bodyPart = BodyPart.CHEST),
        )
        return exerciseRepository.getExercise(id)!!
    }

    private suspend fun saveRoutine(name: String, exerciseNames: List<String>, days: Set<DayOfWeek>): Long =
        routineRepository.saveRoutine(
            Routine(
                name = name,
                scheduledDays = days,
                items = exerciseNames.mapIndexed { index, exerciseName ->
                    RoutineItem(exercise = exercise(exerciseName), orderIndex = index, defaultSets = 3)
                },
            ),
        )

    /** 저장한 순서대로 운동이 돌아와야 한다. */
    @Test
    fun `keeps the exercise order`() = runTest {
        val id = saveRoutine("상체", listOf("벤치프레스", "랫풀다운", "숄더프레스"), emptySet())

        val routine = routineRepository.getRoutine(id)!!

        assertThat(routine.items.map { it.exercise.name })
            .containsExactly("벤치프레스", "랫풀다운", "숄더프레스").inOrder()
        assertThat(routine.items.map { it.orderIndex }).containsExactly(0, 1, 2).inOrder()
    }

    /** 순서를 바꿔 다시 저장하면 바뀐 순서가 유지된다. */
    @Test
    fun `saves a reordered routine`() = runTest {
        val id = saveRoutine("상체", listOf("벤치프레스", "랫풀다운"), emptySet())
        val original = routineRepository.getRoutine(id)!!

        routineRepository.saveRoutine(
            original.copy(items = original.items.reversed().mapIndexed { i, it -> it.copy(orderIndex = i) }),
        )

        val reordered = routineRepository.getRoutine(id)!!
        assertThat(reordered.items.map { it.exercise.name })
            .containsExactly("랫풀다운", "벤치프레스").inOrder()
    }

    /** 지정한 요일에만 홈 화면 루틴으로 올라와야 한다. */
    @Test
    fun `returns routines scheduled for a given day`() = runTest {
        saveRoutine("월요일 상체", listOf("벤치프레스"), setOf(DayOfWeek.MONDAY))
        saveRoutine("화요일 하체", listOf("스쿼트"), setOf(DayOfWeek.TUESDAY))

        val monday = routineRepository.observeRoutinesForDay(DayOfWeek.MONDAY).first()

        assertThat(monday.map { it.name }).containsExactly("월요일 상체")
    }

    /** 루틴을 수정해도 목록 정렬 기준(생성 순서)이 뒤집히지 않아야 한다. */
    @Test
    fun `preserves list order when a routine is edited`() = runTest {
        val firstId = saveRoutine("A", listOf("벤치프레스"), emptySet())
        saveRoutine("B", listOf("스쿼트"), emptySet())

        val first = routineRepository.getRoutine(firstId)!!
        routineRepository.saveRoutine(first.copy(name = "A 수정"))

        val names = routineRepository.observeRoutines().first().map { it.name }
        assertThat(names).containsExactly("A 수정", "B").inOrder()
    }
}
