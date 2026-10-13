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

/**
 * "체지방 유지 고강도 주4일" 템플릿과, 그것을 위해 템플릿에 생긴 기능(슈퍼셋 · 종목별 휴식 · 요일).
 * 짠 대로(가슴 / 등 / 하체+어깨 평일 45분, 토요일 러닝 뒤 팔+복근) 루틴이 만들어지는지 확인한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FatMaintenanceTemplateTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var db: HealthDatabase
    private lateinit var routines: RoutineRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var viewModel: RoutineTemplateViewModel

    private val template get() = RoutineTemplates.all.first { it.id == "fat_maintenance_4day" }

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

    // ----- 구성 -----

    @Test
    fun `makes four routines in the weekly order`() = runTest(dispatcher) {
        viewModel.applyTemplate(template)
        val result = viewModel.applied.first()

        assertThat(result.createdRoutineNames).containsExactly(
            "체지방 유지 · 가슴",
            "체지방 유지 · 등",
            "체지방 유지 · 하체 + 어깨",
            "체지방 유지 · 팔 + 복근 (토)",
        ).inOrder()
    }

    @Test
    fun `chest day is built as planned`() = runTest(dispatcher) {
        val chest = applied().getValue("체지방 유지 · 가슴")

        assertThat(chest.items.map { it.exercise.name to it.defaultSets }).containsExactly(
            "벤치프레스" to 4,
            "인클라인 덤벨 프레스" to 3,
            "케이블 크로스오버" to 3,
            "어시스티드 딥스" to 3,
            "와이드 푸시업" to 1,
        ).inOrder()
    }

    @Test
    fun `back day is built as planned`() = runTest(dispatcher) {
        val back = applied().getValue("체지방 유지 · 등")

        assertThat(back.items.map { it.exercise.name to it.defaultSets }).containsExactly(
            "풀업" to 4,
            "바벨로우" to 3,
            "시티드 케이블로우" to 3,
            "와이드그립 랫풀다운" to 3,
            "페이스풀" to 3,
        ).inOrder()
    }

    @Test
    fun `leg and shoulder day pairs the romanian deadlift with the lateral raise`() = runTest(dispatcher) {
        val legs = applied().getValue("체지방 유지 · 하체 + 어깨")

        assertThat(legs.items.map { it.exercise.name }).containsExactly(
            "스쿼트", "오버헤드 프레스", "루마니안 데드리프트", "사이드 레터럴 레이즈", "레그프레스", "카프레이즈",
        ).inOrder()
        val group = legs.items.associate { it.exercise.name to it.supersetGroup }
        assertThat(group.getValue("루마니안 데드리프트")).isNotEqualTo(SupersetGroups.NONE)
        assertThat(group.getValue("루마니안 데드리프트")).isEqualTo(group.getValue("사이드 레터럴 레이즈"))
        listOf("스쿼트", "오버헤드 프레스", "레그프레스", "카프레이즈").forEach {
            assertThat(group.getValue(it)).isEqualTo(SupersetGroups.NONE)
        }
    }

    @Test
    fun `saturday has two arm supersets then abs`() = runTest(dispatcher) {
        val arms = applied().getValue("체지방 유지 · 팔 + 복근 (토)")

        assertThat(arms.items.map { it.exercise.name }).containsExactly(
            "바벨 컬", "케이블 푸시다운", "해머 컬", "벤치 딥스", "행잉 레그레이즈", "플랭크",
        ).inOrder()
        val group = arms.items.associate { it.exercise.name to it.supersetGroup }
        assertThat(group.getValue("바벨 컬")).isNotEqualTo(SupersetGroups.NONE)
        assertThat(group.getValue("바벨 컬")).isEqualTo(group.getValue("케이블 푸시다운"))
        assertThat(group.getValue("해머 컬")).isEqualTo(group.getValue("벤치 딥스"))
        // 두 묶음은 서로 다른 묶음이다.
        assertThat(group.getValue("바벨 컬")).isNotEqualTo(group.getValue("해머 컬"))
        assertThat(group.getValue("행잉 레그레이즈")).isEqualTo(SupersetGroups.NONE)
        assertThat(group.getValue("플랭크")).isEqualTo(SupersetGroups.NONE)
    }

    // ----- 휴식 · 요일 -----

    @Test
    fun `heavy lifts rest longer than accessories`() = runTest(dispatcher) {
        val all = applied()
        fun rest(routine: String, exercise: String) =
            all.getValue(routine).items.first { it.exercise.name == exercise }.restSeconds

        assertThat(rest("체지방 유지 · 가슴", "벤치프레스")).isEqualTo(150)
        assertThat(rest("체지방 유지 · 가슴", "케이블 크로스오버")).isEqualTo(75)
        assertThat(rest("체지방 유지 · 등", "바벨로우")).isEqualTo(150)
        assertThat(rest("체지방 유지 · 등", "페이스풀")).isEqualTo(60)
        assertThat(rest("체지방 유지 · 하체 + 어깨", "스쿼트")).isEqualTo(150)
        assertThat(rest("체지방 유지 · 하체 + 어깨", "카프레이즈")).isEqualTo(45)
        // 슈퍼셋은 묶음의 마지막 종목을 마친 뒤에 쉬므로 그 값이 쓰인다.
        assertThat(rest("체지방 유지 · 하체 + 어깨", "사이드 레터럴 레이즈")).isEqualTo(90)
        assertThat(rest("체지방 유지 · 팔 + 복근 (토)", "케이블 푸시다운")).isEqualTo(60)
    }

    /** 정하지 않은 종목은 그 종목의 기본 휴식을 그대로 쓴다. */
    @Test
    fun `an exercise without an override keeps its default rest`() = runTest(dispatcher) {
        val all = applied()
        val seeded = exercises.observeExercises().first().associateBy { it.name }

        // 바벨 컬은 이 템플릿에서 휴식을 따로 정하지 않았다.
        val curl = all.getValue("체지방 유지 · 팔 + 복근 (토)").items.first { it.exercise.name == "바벨 컬" }
        assertThat(curl.restSeconds).isEqualTo(seeded.getValue("바벨 컬").defaultRestSeconds)
    }

    @Test
    fun `routines are scheduled monday tuesday thursday and saturday`() = runTest(dispatcher) {
        val all = applied()

        assertThat(all.getValue("체지방 유지 · 가슴").scheduledDays).containsExactly(DayOfWeek.MONDAY)
        assertThat(all.getValue("체지방 유지 · 등").scheduledDays).containsExactly(DayOfWeek.TUESDAY)
        assertThat(all.getValue("체지방 유지 · 하체 + 어깨").scheduledDays).containsExactly(DayOfWeek.THURSDAY)
        assertThat(all.getValue("체지방 유지 · 팔 + 복근 (토)").scheduledDays).containsExactly(DayOfWeek.SATURDAY)
    }

    /** 다른 템플릿은 요일을 정하지 않는다(홈의 오늘의 루틴을 마음대로 채우지 않는다). */
    @Test
    fun `other templates do not schedule days`() = runTest(dispatcher) {
        viewModel.applyTemplate(RoutineTemplates.all.first { it.id == "ppl" })
        viewModel.applied.first()

        routines.observeRoutines().first().forEach { assertThat(it.scheduledDays).isEmpty() }
    }

    // ----- 계획한 시간 -----

    /**
     * 평일 3일은 45분이 목표다. 워밍업에 5분을 남기려면 본 세트 시간이 40분 이하여야 한다.
     * 한 세트 45초 + 휴식으로 어림한다(슈퍼셋은 묶음 안에서 쉬지 않고 이어 한다).
     */
    @Test
    fun `each weekday session fits in forty minutes of working time`() {
        val weekdays = template.days.filter { day -> day.scheduledDays.none { it == DayOfWeek.SATURDAY } }
        assertThat(weekdays).hasSize(3)

        weekdays.forEach { day ->
            val grouped = day.supersets.flatten().toSet()
            val seconds = day.exercises.sumOf { (name, sets) ->
                val rest = day.restSeconds[name] ?: 90
                // 묶음 중간 종목은 쉬지 않는다. 묶음의 마지막 종목만 쉰다.
                val isInnerOfSuperset = name in grouped && day.supersets.none { it.last() == name }
                sets * (WORK_SECONDS_PER_SET + if (isInnerOfSuperset) 0 else rest)
            }
            assertWithMessage("${day.routineName}: ${seconds / 60}분").that(seconds).isAtMost(40 * 60)
        }
    }

    // ----- 템플릿 자료 검증(모든 템플릿) -----

    /** 슈퍼셋으로 적은 종목은 그 날 종목 안에 있어야 하고, 한 묶음은 목록에서 이어져 있어야 한다. */
    @Test
    fun `superset members exist and sit next to each other`() {
        RoutineTemplates.all.flatMap { it.days }.forEach { day ->
            val names = day.exercises.map { it.first }
            day.supersets.forEach { group ->
                assertWithMessage("${day.routineName} $group").that(group.size).isAtLeast(2)
                assertWithMessage("${day.routineName} $group").that(names).containsAtLeastElementsIn(group)
                val positions = group.map { names.indexOf(it) }
                assertWithMessage("${day.routineName} $group 은 이어져 있어야 한다")
                    .that(positions).containsExactlyElementsIn((positions.first()..positions.last()).toList()).inOrder()
            }
            // 한 종목이 두 묶음에 들어가면 어느 쪽인지 알 수 없다.
            assertWithMessage(day.routineName).that(day.supersets.flatten()).containsNoDuplicates()
        }
    }

    /** 휴식 시간 이름표는 그 날의 종목이어야 하고, 앱이 받는 범위(5~600초) 안이어야 한다. */
    @Test
    fun `rest overrides name real exercises and stay in range`() {
        RoutineTemplates.all.flatMap { it.days }.forEach { day ->
            val names = day.exercises.map { it.first }.toSet()
            day.restSeconds.forEach { (name, seconds) ->
                assertWithMessage("${day.routineName} / $name").that(names).contains(name)
                assertWithMessage("${day.routineName} / $name").that(seconds).isIn(5..600)
            }
        }
    }

    /** 이름난 프로그램 템플릿이 슈퍼셋이나 요일을 몰래 얻지 않았는지(이번에 바꾼 것은 새 템플릿뿐). */
    @Test
    fun `only the two newest templates use supersets rest overrides and schedules`() {
        val withExtras = setOf("fat_maintenance_4day", "split_4day_lower_upper_back")
        RoutineTemplates.all.filter { it.id !in withExtras }.flatMap { it.days }.forEach { day ->
            assertWithMessage(day.routineName).that(day.supersets).isEmpty()
            assertWithMessage(day.routineName).that(day.restSeconds).isEmpty()
            assertWithMessage(day.routineName).that(day.scheduledDays).isEmpty()
        }
    }

    private companion object {
        /** 한 세트에 드는 시간(초) 어림값. */
        const val WORK_SECONDS_PER_SET = 45
    }
}
