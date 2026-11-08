package com.windowhyun.health.ui.gym

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.data.seed.DefaultExercises
import org.junit.Test

/**
 * 기본 루틴 템플릿의 "운동 프로그램으로서의 품질". 이름이 맞고 형식이 맞는 것(RoutineTemplateTest)과 별개로,
 * 전문가 점검에서 나온 문제(휴식 누락 · 밀기/당기기 쏠림 · 장비 불일치 · 하루 과다 · 안내 부재)가 다시 생기지 않게 한다.
 */
class RoutineProgramQualityTest {

    private val byName = DefaultExercises.all.associateBy { it.name }
    private val templates get() = RoutineTemplates.all

    /** 큰 운동(고중량 복합). 휴식을 60초로 두면 프로그램이 성립하지 않는다. */
    private val mainLifts = setOf("스쿼트", "데드리프트", "벤치프레스", "오버헤드 프레스", "프론트 스쿼트")

    @Test
    fun `every exercise in every template has its own rest`() {
        templates.flatMap { t -> t.days.map { t.id to it } }.forEach { (id, day) ->
            day.exercises.forEach { (name, _) ->
                assertWithMessage("$id / ${day.routineName} / $name 의 휴식").that(day.restSeconds[name]).isNotNull()
            }
        }
    }

    @Test
    fun `main barbell lifts rest at least two minutes and heavy strength programs three`() {
        val strength = setOf("5x5", "starting_strength", "wendler_531")
        templates.forEach { t ->
            t.days.forEach { day ->
                day.exercises.map { it.first }.filter { it in mainLifts }.forEach { name ->
                    val rest = day.restSeconds.getValue(name)
                    assertWithMessage("${t.id} / ${day.routineName} / $name").that(rest).isAtLeast(120)
                    if (t.id in strength) assertWithMessage("${t.id} / $name").that(rest).isAtLeast(180)
                }
            }
        }
    }

    @Test
    fun `accessory work never rests longer than the main lifts of the same day`() {
        templates.flatMap { it.days }.forEach { day ->
            val mainRests = day.exercises.map { it.first }.filter { it in mainLifts }.map { day.restSeconds.getValue(it) }
            if (mainRests.isEmpty()) return@forEach
            val longestAccessory = day.exercises.map { it.first }.filter { it !in mainLifts }.maxOf { day.restSeconds.getValue(it) }
            assertWithMessage(day.routineName).that(longestAccessory).isAtMost(mainRests.max())
        }
    }

    private val pullShoulder = setOf("페이스풀", "벤트오버 레터럴 레이즈", "리어 델트 플라이 머신")

    private fun kind(name: String): String? {
        val x = byName.getValue(name)
        return when (x.bodyPart) {
            BodyPart.BACK -> "pull"
            BodyPart.CHEST -> "push"
            BodyPart.SHOULDER -> if (name in pullShoulder) "pull" else "push"
            BodyPart.ARM -> if ("컬" in name && "트라이" !in name) "pull" else "push"
            else -> null
        }
    }

    /** 밀기만 많으면 어깨가 말리고 다친다. 한 템플릿 안에서 밀기:당기기가 0.6~1.7 배 안이어야 한다. */
    @Test
    fun `push and pull volume stay balanced`() {
        templates.forEach { t ->
            val sets = t.days.flatMap { it.exercises }.groupBy({ kind(it.first) }, { it.second }).mapValues { it.value.sum() }
            val push = sets["push"] ?: 0
            val pull = sets["pull"] ?: 0
            if (push == 0 && pull == 0) return@forEach // 코어 · 하체 전용
            assertWithMessage("${t.id} push=$push pull=$pull").that(push.toDouble() / pull.coerceAtLeast(1)).isIn(com.google.common.collect.Range.closed(0.6, 1.7))
        }
    }

    @Test
    fun `no day is longer than twenty six sets`() {
        templates.flatMap { t -> t.days.map { t.id to it } }.forEach { (id, day) ->
            assertWithMessage("$id / ${day.routineName}").that(day.exercises.sumOf { it.second }).isAtMost(26)
        }
    }

    /** "기구 없이" 라고 한 템플릿에 덤벨 · 바벨 · 머신 종목이 섞이면 안 된다. */
    @Test
    fun `the bodyweight template only uses bodyweight exercises`() {
        val home = templates.first { it.id == "bodyweight_home" }
        home.days.flatMap { it.exercises }.forEach { (name, _) ->
            assertWithMessage(name).that(byName.getValue(name).category).isEqualTo(ExerciseCategory.BODYWEIGHT)
        }
    }

    @Test
    fun `the dumbbell template only needs dumbbells and a bench`() {
        val allowed = setOf(ExerciseCategory.DUMBBELL)
        templates.first { it.id == "dumbbell_only" }.days.flatMap { it.exercises }.forEach { (name, _) ->
            assertWithMessage(name).that(byName.getValue(name).category).isIn(allowed)
        }
    }

    @Test
    fun `two routines of one template never share a weekday`() {
        templates.forEach { t ->
            val days = t.days.flatMap { it.scheduledDays }
            assertWithMessage(t.id).that(days).containsNoDuplicates()
        }
    }

    /** 같은 날 몸의 같은 부위를 이틀 연속 훈련하는 배치가 없어야 한다(하체 · 등 데드리프트 포함). */
    @Test
    fun `no template trains the same main body part on consecutive days`() {
        templates.forEach { t ->
            val scheduled = t.days.flatMap { day -> day.scheduledDays.map { it to day } }.sortedBy { it.first.value }
            if (scheduled.size < 2) return@forEach
            fun legHeavy(d: RoutineTemplates.Day) = d.exercises.map { it.first }.count { byName.getValue(it).bodyPart == BodyPart.LEG } >= 3
            scheduled.zipWithNext().forEach { (a, b) ->
                if (b.first.value - a.first.value == 1) {
                    assertWithMessage("${t.id}: ${a.second.routineName} → ${b.second.routineName}")
                        .that(legHeavy(a.second) && legHeavy(b.second)).isFalse()
                }
            }
        }
    }

    /** 설명에 수준 · 횟수 · 휴식(또는 진행)이 들어 있어야 한다. 앱은 세트 수만 담기 때문에 나머지는 글로 안내한다. */
    @Test
    fun `every template description tells level reps and progression`() {
        templates.forEach { t ->
            assertWithMessage("${t.id} 수준").that(t.description).contains("수준")
            assertWithMessage("${t.id} 횟수").that(t.description).containsMatch("횟수|회")
            assertWithMessage("${t.id} 휴식 · 진행").that(t.description).containsMatch("휴식|진행")
        }
    }

    @Test
    fun `five three one explains its percentages cycle and training max`() {
        val d = templates.first { it.id == "wendler_531" }.description
        listOf("훈련 맥스", "65%", "75%", "85%", "95%", "디로드", "+2.5kg", "+5kg").forEach { assertThat(d).contains(it) }
    }

    @Test
    fun `the five three one assistance is short and the lifts are alone as the first exercise`() {
        val t = templates.first { it.id == "wendler_531" }
        t.days.forEach { day ->
            assertThat(day.exercises.first().first).isIn(mainLifts)
            assertThat(day.exercises.first().second).isEqualTo(3)
            day.exercises.drop(1).forEach { (name, sets) -> assertWithMessage("${day.routineName} / $name").that(sets).isAtMost(4) }
        }
        // 허리에 부담이 큰 굿모닝 · 어깨 앞쪽에 부담이 큰 딥스는 5/3/1 보조로 쓰지 않는다.
        assertThat(t.days.flatMap { it.exercises }.map { it.first }).containsNoneOf("굿모닝", "딥스")
    }

    @Test
    fun `new bodyweight exercises exist and every default rest is in range`() {
        listOf("맨몸 런지", "인클라인 푸시업").forEach { assertThat(byName).containsKey(it) }
        DefaultExercises.all.mapNotNull { it.defaultRestSeconds }.forEach { assertThat(it).isIn(5..600) }
        assertWithMessage("휴식이 없는 종목(유산소 제외)")
            .that(DefaultExercises.all.filter { it.defaultRestSeconds == null && it.bodyPart != BodyPart.CARDIO }.map { it.name }).isEmpty()
    }
}
