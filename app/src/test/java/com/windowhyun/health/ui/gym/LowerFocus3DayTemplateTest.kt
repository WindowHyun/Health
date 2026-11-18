package com.windowhyun.health.ui.gym

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RoutineRepositoryImpl
import com.windowhyun.health.data.seed.DefaultExercises
import com.windowhyun.health.data.seed.ExerciseSeedCallback
import com.windowhyun.health.domain.model.Routine
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

/** "하체 집중 주3일(화 · 목 · 토)" 템플릿: 하체가 중심이고, 요일이 화 · 목 · 토로 붙고, 상체가 줄지 않을 만큼만 곁들인다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LowerFocus3DayTemplateTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: HealthDatabase
    private lateinit var routines: RoutineRepositoryImpl
    private lateinit var viewModel: RoutineTemplateViewModel

    private val template get() = RoutineTemplates.all.first { it.id == "lower_focus_3day" }
    private val byName = DefaultExercises.all.associateBy { it.name }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .addCallback(ExerciseSeedCallback)
            .allowMainThreadQueries()
            .build().also { it.openHelper.writableDatabase }
        routines = RoutineRepositoryImpl(db.routineDao())
        viewModel = RoutineTemplateViewModel(routines, ExerciseRepositoryImpl(db.exerciseDao()))
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun applied(): List<Routine> {
        viewModel.applyTemplate(template)
        val result = viewModel.applied.first()
        assertThat(result.missingExerciseNames).isEmpty()
        return routines.observeRoutines().first()
    }

    @Test
    fun `makes three routines on tuesday thursday and saturday`() = runTest(dispatcher) {
        val made = applied()

        assertThat(made).hasSize(3)
        val byDay = made.associate { r -> r.scheduledDays.single() to r.name }
        assertThat(byDay.keys).containsExactly(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
        assertThat(byDay.getValue(DayOfWeek.TUESDAY)).contains("A")
        assertThat(byDay.getValue(DayOfWeek.THURSDAY)).contains("B")
        assertThat(byDay.getValue(DayOfWeek.SATURDAY)).contains("C")
    }

    /** "하체 위주": 하체 세트가 전체의 3분의 2 이상이고, 매일 하체가 먼저 나온다. */
    @Test
    fun `lower body is most of the work and always comes first`() {
        val sets = template.days.flatMap { it.exercises }
        val lower = sets.filter { byName.getValue(it.first).bodyPart == BodyPart.LEG }.sumOf { it.second }
        val total = sets.sumOf { it.second }
        assertThat(lower.toDouble() / total).isAtLeast(0.6)

        template.days.forEach { day ->
            assertThat(byName.getValue(day.exercises.first().first).bodyPart).isEqualTo(BodyPart.LEG)
            assertThat(day.exercises.count { byName.getValue(it.first).bodyPart == BodyPart.LEG }).isAtLeast(4)
        }
    }

    /** 세 날이 같지 않다: 스쿼트(앞) · 루마니안/힙 쓰러스트(뒤) · 한 다리(볼륨). */
    @Test
    fun `each day has its own emphasis`() {
        val (a, b, c) = template.days.map { d -> d.exercises.map { it.first } }
        assertThat(a.first()).isEqualTo("스쿼트")
        assertThat(b.take(2)).containsExactly("루마니안 데드리프트", "힙 쓰러스트").inOrder()
        assertThat(c).containsAtLeast("불가리안 스플릿 스쿼트", "스텝업")
        // 같은 종목이 세 날 모두에 나오지 않는다(같은 곳을 세 번 두드리지 않는다).
        val allThree = a.intersect(b.toSet()).intersect(c.toSet())
        assertThat(allThree).isEmpty()
    }

    /** 상체는 곁들이는 정도: 매일 당기기 1 + 밀기 1, 세트 수가 같다. */
    @Test
    fun `upper body is a light pull and push pair each day`() {
        template.days.forEach { day ->
            val upper = day.exercises.filter { byName.getValue(it.first).bodyPart in setOf(BodyPart.BACK, BodyPart.CHEST, BodyPart.SHOULDER) }
            assertThat(upper).hasSize(2)
            assertThat(upper.sumOf { it.second }).isAtMost(6)
        }
    }

    @Test
    fun `heavy lifts rest longer and every exercise has its own rest`() = runTest(dispatcher) {
        val made = applied().associateBy { it.scheduledDays.single() }
        fun Routine.rest(name: String) = items.first { it.exercise.name == name }.restSeconds
        assertThat(made.getValue(DayOfWeek.TUESDAY).rest("스쿼트")).isEqualTo(150)
        assertThat(made.getValue(DayOfWeek.THURSDAY).rest("루마니안 데드리프트")).isAtLeast(120)
        assertThat(made.getValue(DayOfWeek.SATURDAY).rest("플랭크")).isEqualTo(45)
        template.days.forEach { day -> assertThat(day.restSeconds.keys).containsExactlyElementsIn(day.exercises.map { it.first }) }
    }

    /** 하체 날 사이에 쉬는 날이 있다(이틀 연속 하체 없음). */
    @Test
    fun `lower days are never back to back`() {
        val days = template.days.flatMap { it.scheduledDays }.map { it.value }.sorted()
        (days + (days.first() + 7)).zipWithNext().forEach { (x, y) -> assertThat(y - x).isAtLeast(2) }
    }

    @Test
    fun `a session fits in about an hour`() {
        template.days.forEach { day ->
            // 세트당 약 40초 + 휴식으로 어림한다.
            val minutes = day.exercises.sumOf { (name, sets) -> sets * (40 + day.restSeconds.getValue(name)) } / 60.0
            assertThat(minutes).isAtMost(70.0)
        }
    }
}
