package com.windowhyun.health.ui.gym

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RoutineRepositoryImpl
import com.windowhyun.health.data.seed.ExerciseSeedCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 루틴 템플릿(5x5, PPL 등) 검증.
 *
 * 종목을 이름으로 찾아 연결하는 구조라, 시드 이름이 하나만 바뀌어도 그 종목만
 * 조용히 빠진다. 템플릿이 실제 시드와 맞물려 전부 만들어지는지를 확인한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoutineTemplateTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var db: HealthDatabase
    private lateinit var routines: RoutineRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var viewModel: RoutineTemplateViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        // 실제 시드 콜백을 태워서, 앱이 처음 실행됐을 때와 같은 DB 상태로 검증한다.
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .addCallback(ExerciseSeedCallback)
            .allowMainThreadQueries()
            .build()
        routines = RoutineRepositoryImpl(db.routineDao())
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        viewModel = RoutineTemplateViewModel(routines, exercises)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** 시드에 있는 모든 템플릿의 모든 종목이 실제로 존재해야 한다. 하나라도 빠지면 조용히 건너뛴다. */
    @Test
    fun `every template exercise name exists in the seed`() = runTest(dispatcher) {
        val seeded = exercises.observeExercises().first().map { it.name }.toSet()

        val missing = RoutineTemplates.all.flatMap { it.days }
            .flatMap { it.exercises }
            .map { it.first }
            .filterNot { it in seeded }

        assertThat(missing).isEmpty()
    }

    /** 템플릿을 고르면 그 프로그램의 모든 날짜가 루틴으로 만들어진다. */
    @Test
    fun `applying a template creates one routine per day`() = runTest(dispatcher) {
        val template = RoutineTemplates.all.first { it.id == "ppl" }

        viewModel.applyTemplate(template)
        val result = viewModel.applied.first()

        assertThat(result.createdRoutineNames).containsExactly("푸시 (PPL)", "풀 (PPL)", "레그 (PPL)").inOrder()
        assertThat(result.missingExerciseNames).isEmpty()

        val saved = routines.observeRoutines().first()
        assertThat(saved.map { it.name }).containsExactly("푸시 (PPL)", "풀 (PPL)", "레그 (PPL)")
    }

    /** 만들어진 루틴은 종목 순서와 기본 세트 수를 그대로 담고 있어야 한다. */
    @Test
    fun `keeps exercise order and default sets from the template`() = runTest(dispatcher) {
        val template = RoutineTemplates.all.first { it.id == "5x5" }

        viewModel.applyTemplate(template)
        viewModel.applied.first()

        val routineA = routines.observeRoutines().first().first { it.name == "5x5 A" }
        assertThat(routineA.items.map { it.exercise.name }).containsExactly(
            "스쿼트", "벤치프레스", "바벨로우",
        ).inOrder()
        assertThat(routineA.items.map { it.defaultSets }).containsExactly(5, 5, 5).inOrder()
    }

    /** 곧바로 시작할 수 있어야 하므로, 만든 루틴은 비어 있으면 안 된다. */
    @Test
    fun `created routines are never empty`() = runTest(dispatcher) {
        RoutineTemplates.all.forEach { template ->
            viewModel.applyTemplate(template)
            viewModel.applied.first()
        }

        val saved = routines.observeRoutines().first()
        assertThat(saved).isNotEmpty()
        saved.forEach { routine ->
            assertThat(routine.items).isNotEmpty()
        }
    }

    /** 종목을 지운 뒤 적용해도, 남은 종목으로는 루틴이 만들어지고 지운 것은 빠졌다고 알려 준다. */
    @Test
    fun `skips a deleted exercise but keeps the rest of the day`() = runTest(dispatcher) {
        val squat = exercises.observeExercises().first().first { it.name == "스쿼트" }
        exercises.deleteExercise(squat)
        val template = RoutineTemplates.all.first { it.id == "5x5" }

        viewModel.applyTemplate(template)
        val result = viewModel.applied.first()

        assertThat(result.missingExerciseNames).contains("스쿼트")
        val routineA = routines.observeRoutines().first().first { it.name == "5x5 A" }
        assertThat(routineA.items.map { it.exercise.name }).doesNotContain("스쿼트")
        assertThat(routineA.items).isNotEmpty()
    }

    /** 하루치 종목이 전부 사라지면 그 루틴은 아예 만들지 않는다. 빈 루틴은 시작이 안 된다. */
    @Test
    fun `does not create a routine whose exercises are all missing`() = runTest(dispatcher) {
        val all = exercises.observeExercises().first()
        val template = RoutineTemplates.all.first { it.id == "5x5" }
        val dayBNames = template.days.first { it.routineName == "5x5 B" }.exercises.map { it.first }
        all.filter { it.name in dayBNames }.forEach { exercises.deleteExercise(it) }

        viewModel.applyTemplate(template)
        val result = viewModel.applied.first()

        assertThat(result.createdRoutineNames).containsExactly("5x5 A")
        val saved = routines.observeRoutines().first()
        assertThat(saved.map { it.name }).doesNotContain("5x5 B")
    }

    /** 다섯 템플릿 전부 이름이 겹치지 않는 종류이며, id 도 서로 달라야 한다. */
    @Test
    fun `template ids are unique`() {
        val ids = RoutineTemplates.all.map { it.id }
        assertThat(ids).containsNoDuplicates()
    }

    /** 카드를 빠르게 두 번 눌러도 루틴은 한 벌만 생긴다. */
    @Test
    fun `a double tap creates the routines only once`() = runTest(dispatcher) {
        val template = RoutineTemplates.all.first { it.id == "5x5" }

        viewModel.applyTemplate(template)
        viewModel.applyTemplate(template)
        viewModel.applied.first()

        val names = routines.observeRoutines().first().map { it.name }
        assertThat(names).containsExactly("5x5 A", "5x5 B")
    }

    /** 적용이 끝나면 다른 템플릿을 다시 고를 수 있어야 한다(가드가 풀린다). */
    @Test
    fun `can apply another template after the first finishes`() = runTest(dispatcher) {
        viewModel.applyTemplate(RoutineTemplates.all.first { it.id == "5x5" })
        viewModel.applied.first()
        viewModel.applyTemplate(RoutineTemplates.all.first { it.id == "upper_lower" })
        viewModel.applied.first()

        assertThat(routines.observeRoutines().first()).hasSize(4)
    }
}
