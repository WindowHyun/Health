package com.windowhyun.health.ui.gym

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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

    /** 템플릿 id 는 서로 달라야 한다(목록의 key 이기도 하다). */
    @Test
    fun `template ids are unique`() {
        val ids = RoutineTemplates.all.map { it.id }
        assertThat(ids).containsNoDuplicates()
    }

    /**
     * 날마다 만들어지는 루틴 이름이 템플릿끼리 겹치면 루틴 목록에 같은 이름이 두 개 생겨
     * 어느 것이 어느 프로그램인지 알 수 없다.
     */
    @Test
    fun `routine names are unique across all templates`() {
        val names = RoutineTemplates.all.flatMap { it.days }.map { it.routineName }
        assertThat(names).containsNoDuplicates()
    }

    /** 한 날에 같은 종목이 두 줄 들어 있으면 오타이거나 의도하지 않은 중복이다. */
    @Test
    fun `no exercise repeats inside a day`() {
        RoutineTemplates.all.flatMap { it.days }.forEach { day ->
            val names = day.exercises.map { it.first }
            assertWithMessage(day.routineName).that(names).containsNoDuplicates()
        }
    }

    /** 세트 수가 0 이거나 터무니없이 크면 입력 실수다. 편집 화면의 한도(1~20)를 넘지 않는다. */
    @Test
    fun `default sets stay in a sensible range`() {
        RoutineTemplates.all.flatMap { it.days }.forEach { day ->
            day.exercises.forEach { (name, sets) ->
                assertWithMessage("${day.routineName} / $name").that(sets).isIn(1..10)
            }
        }
    }

    /** 비어 있는 날이나 설명 없는 템플릿이 목록에 올라가면 안 된다. */
    @Test
    fun `every template has a title description and non empty days`() {
        RoutineTemplates.all.forEach { template ->
            assertWithMessage(template.id).that(template.title).isNotEmpty()
            assertWithMessage(template.id).that(template.description).isNotEmpty()
            assertWithMessage(template.id).that(template.days).isNotEmpty()
            template.days.forEach { assertWithMessage("${template.id}/${it.routineName}").that(it.exercises).isNotEmpty() }
        }
    }

    /** 목록은 갈래별로 묶여 보이고, 모든 템플릿이 정확히 한 갈래에 들어간다. */
    @Test
    fun `grouped list contains every template exactly once in category order`() {
        val grouped = RoutineTemplates.byCategory
        assertThat(grouped.flatMap { it.second }.map { it.id })
            .containsExactlyElementsIn(RoutineTemplates.all.map { it.id })
        assertThat(grouped.map { it.first }).isInStrictOrder(compareBy<RoutineTemplates.Category> { it.ordinal })
        grouped.forEach { (category, templates) ->
            assertThat(templates.all { it.category == category }).isTrue()
        }
    }

    /** 새로 넣은 프로그램은 실제로 적용되어 날마다 루틴이 만들어진다. */
    @Test
    fun `every template can be applied and creates a routine per day`() = runTest(dispatcher) {
        RoutineTemplates.all.forEach { template ->
            viewModel.applyTemplate(template)
            val result = viewModel.applied.first()
            assertWithMessage(template.id).that(result.missingExerciseNames).isEmpty()
            assertWithMessage(template.id).that(result.createdRoutineNames)
                .containsExactlyElementsIn(template.days.map { it.routineName }).inOrder()
        }

        val expected = RoutineTemplates.all.sumOf { it.days.size }
        assertThat(routines.observeRoutines().first()).hasSize(expected)
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
