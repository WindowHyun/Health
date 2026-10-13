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
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.usecase.SupersetGroups
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
import java.time.DayOfWeek

/** "주4일 4분할(하체 화 / 가슴·어깨·팔 A 목 / 등 토 / 가슴·어깨·팔 B 일)" 템플릿이 적은 대로 만들어지는지. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class Split4DayTemplateTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var db: HealthDatabase
    private lateinit var routines: RoutineRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var viewModel: RoutineTemplateViewModel

    private val template get() = RoutineTemplates.all.first { it.id == "split_4day_lower_upper_back" }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .addCallback(ExerciseSeedCallback)
            .allowMainThreadQueries()
            .build().also { it.openHelper.writableDatabase } // 먼저 열어 둔다: 닫을 때 여는 중이면 서로 기다려 멈춘다
        routines = RoutineRepositoryImpl(db.routineDao())
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        viewModel = RoutineTemplateViewModel(routines, exercises)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun applied(): Map<String, Routine> {
        viewModel.applyTemplate(template)
        val result = viewModel.applied.first()
        assertThat(result.missingExerciseNames).isEmpty()
        return routines.observeRoutines().first().associateBy { it.name }
    }

    private fun Routine.plan() = items.map { it.exercise.name to it.defaultSets }

    private fun Routine.rest(exercise: String) = items.first { it.exercise.name == exercise }.restSeconds

    @Test
    fun `makes the four routines in the weekly order`() = runTest(dispatcher) {
        viewModel.applyTemplate(template)

        assertThat(viewModel.applied.first().createdRoutineNames).containsExactly(
            "하체 (화)", "가슴·어깨·팔 A (목)", "등 (토)", "가슴·어깨·팔 B (일)",
        ).inOrder()
    }

    @Test
    fun `lower day`() = runTest(dispatcher) {
        assertThat(applied().getValue("하체 (화)").plan()).containsExactly(
            "스쿼트" to 4,
            "루마니안 데드리프트" to 3,
            "레그프레스" to 3,
            "레그 컬" to 3,
            "카프레이즈" to 3,
        ).inOrder()
    }

    @Test
    fun `chest shoulder arm A pairs pushdown with curl`() = runTest(dispatcher) {
        val a = applied().getValue("가슴·어깨·팔 A (목)")

        assertThat(a.plan()).containsExactly(
            "벤치프레스" to 4,
            "인클라인 덤벨 프레스" to 3,
            "덤벨 숄더프레스" to 3,
            "사이드 레터럴 레이즈" to 3,
            "케이블 푸시다운" to 3,
            "바벨 컬" to 3,
        ).inOrder()
        val group = a.items.associate { it.exercise.name to it.supersetGroup }
        assertThat(group.getValue("케이블 푸시다운")).isNotEqualTo(SupersetGroups.NONE)
        assertThat(group.getValue("케이블 푸시다운")).isEqualTo(group.getValue("바벨 컬"))
        listOf("벤치프레스", "인클라인 덤벨 프레스", "덤벨 숄더프레스", "사이드 레터럴 레이즈").forEach {
            assertThat(group.getValue(it)).isEqualTo(SupersetGroups.NONE)
        }
    }

    @Test
    fun `back day`() = runTest(dispatcher) {
        assertThat(applied().getValue("등 (토)").plan()).containsExactly(
            "데드리프트" to 3,
            "랫풀다운" to 4,
            "시티드 케이블로우" to 3,
            "덤벨로우" to 3,
            "페이스풀" to 3,
        ).inOrder()
    }

    @Test
    fun `chest shoulder arm B pairs dips with hammer curl`() = runTest(dispatcher) {
        val b = applied().getValue("가슴·어깨·팔 B (일)")

        assertThat(b.plan()).containsExactly(
            "오버헤드 프레스" to 4,
            "덤벨 벤치프레스" to 3,
            "케이블 크로스오버" to 3,
            "리어 델트 플라이 머신" to 3,
            "딥스" to 3,
            "해머 컬" to 3,
        ).inOrder()
        val group = b.items.associate { it.exercise.name to it.supersetGroup }
        assertThat(group.getValue("딥스")).isNotEqualTo(SupersetGroups.NONE)
        assertThat(group.getValue("딥스")).isEqualTo(group.getValue("해머 컬"))
        listOf("오버헤드 프레스", "덤벨 벤치프레스", "케이블 크로스오버", "리어 델트 플라이 머신").forEach {
            assertThat(group.getValue(it)).isEqualTo(SupersetGroups.NONE)
        }
    }

    /** 운영 원칙: 복합 운동 휴식 2분, 보조 1~1.5분. */
    @Test
    fun `compound lifts rest two minutes and accessories one to ninety seconds`() = runTest(dispatcher) {
        val all = applied()
        val lower = all.getValue("하체 (화)")
        val a = all.getValue("가슴·어깨·팔 A (목)")
        val back = all.getValue("등 (토)")
        val b = all.getValue("가슴·어깨·팔 B (일)")

        listOf(
            lower to "스쿼트", lower to "루마니안 데드리프트",
            a to "벤치프레스", a to "인클라인 덤벨 프레스", a to "덤벨 숄더프레스",
            back to "데드리프트",
            b to "오버헤드 프레스", b to "덤벨 벤치프레스",
        ).forEach { (routine, exercise) ->
            assertWithMessage("${routine.name} / $exercise").that(routine.rest(exercise)).isEqualTo(120)
        }
        // 보조 운동은 60~90초(슈퍼셋은 묶음의 마지막 종목 값).
        listOf(
            lower to "레그프레스", lower to "레그 컬", lower to "카프레이즈",
            a to "사이드 레터럴 레이즈", a to "바벨 컬",
            back to "랫풀다운", back to "시티드 케이블로우", back to "덤벨로우", back to "페이스풀",
            b to "케이블 크로스오버", b to "리어 델트 플라이 머신", b to "해머 컬",
        ).forEach { (routine, exercise) ->
            assertWithMessage("${routine.name} / $exercise").that(routine.rest(exercise)).isIn(60..90)
        }
    }

    @Test
    fun `scheduled on tuesday thursday saturday and sunday`() = runTest(dispatcher) {
        val all = applied()

        assertThat(all.getValue("하체 (화)").scheduledDays).containsExactly(DayOfWeek.TUESDAY)
        assertThat(all.getValue("가슴·어깨·팔 A (목)").scheduledDays).containsExactly(DayOfWeek.THURSDAY)
        assertThat(all.getValue("등 (토)").scheduledDays).containsExactly(DayOfWeek.SATURDAY)
        assertThat(all.getValue("가슴·어깨·팔 B (일)").scheduledDays).containsExactly(DayOfWeek.SUNDAY)
    }

    /** 이 템플릿과 체지방 유지 템플릿을 둘 다 적용해도 루틴 이름이 겹치지 않는다. */
    @Test
    fun `can be applied together with the other four day template`() = runTest(dispatcher) {
        viewModel.applyTemplate(RoutineTemplates.all.first { it.id == "fat_maintenance_4day" })
        viewModel.applied.first()
        viewModel.applyTemplate(template)
        viewModel.applied.first()

        val names = routines.observeRoutines().first().map { it.name }
        assertThat(names).hasSize(8)
        assertThat(names).containsNoDuplicates()
    }

    /** 설명에 적은 대체 운동 안내가 실제 종목 이름과 맞아야 한다(바벨로우 · 풀업은 시드에 있다). */
    @Test
    fun `the alternatives named in the description exist`() = runTest(dispatcher) {
        val seeded = exercises.observeExercises().first().map { it.name }.toSet()

        listOf("바벨로우", "풀업").forEach { assertThat(seeded).contains(it) }
        assertThat(template.description).contains("바벨로우")
        assertThat(template.description).contains("풀업")
    }
}
