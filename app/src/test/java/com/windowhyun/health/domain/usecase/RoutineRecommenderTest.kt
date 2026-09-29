package com.windowhyun.health.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.data.seed.DefaultExercises
import com.windowhyun.health.domain.usecase.RoutineRecommender.Level
import com.windowhyun.health.domain.usecase.RoutineRecommender.Preferences
import org.junit.Test

/**
 * 맞춤 루틴 추천 규칙 검증.
 *
 * 입력 조합이 많아서 대표 몇 개만 보면 놓치기 쉽다. 부위 하나씩 빼거나 집중하는 모든 경우와
 * 주당 횟수·경력의 모든 조합을 돌려 "빼라는 부위가 들어가지 않는다" 같은 약속을 확인한다.
 */
class RoutineRecommenderTest {

    private val allNames = DefaultExercises.all.map { it.name }.toSet()
    private val parts = RoutineRecommender.selectableParts

    private fun recommend(
        focus: Set<BodyPart> = emptySet(),
        excluded: Set<BodyPart> = emptySet(),
        days: Int = 3,
        level: Level = Level.BEGINNER,
        available: Set<String> = allNames,
    ) = RoutineRecommender.recommend(Preferences(focus, excluded, days, level), available)

    /** 모든 입력 조합. 부위는 하나씩 집중 / 하나씩 제외. */
    private fun allCombinations() = sequence {
        val singles = listOf(emptySet<BodyPart>()) + parts.map { setOf(it) }
        for (days in RoutineRecommender.dayRange) for (level in Level.entries) {
            for (focus in singles) for (excluded in singles) {
                if (focus.isNotEmpty() && focus == excluded) continue
                yield(Preferences(focus, excluded, days, level))
            }
        }
    }

    /** 추천 종목 이름은 기본 제공 종목과 같아야 한다. 틀리면 그 종목만 조용히 빠진다. */
    @Test
    fun `every catalog exercise exists in the seed`() {
        val missing = RoutineRecommender.catalog.map { it.name }.filterNot { it in allNames }
        assertThat(missing).isEmpty()
    }

    /** 사전의 부위가 시드와 같아야 한다. 다르면 "빼기"가 엉뚱한 종목을 뺀다. */
    @Test
    fun `catalog body parts match the seed`() {
        val seedPart = DefaultExercises.all.associate { it.name to it.bodyPart }
        RoutineRecommender.catalog.forEach { entry ->
            assertWithMessage(entry.name).that(entry.part).isEqualTo(seedPart[entry.name])
        }
    }

    /** 가장 중요한 약속: 빼라고 한 부위의 종목은 어떤 조합에서도 나오지 않는다. */
    @Test
    fun `never recommends an excluded body part`() {
        allCombinations().forEach { prefs ->
            val plan = RoutineRecommender.recommend(prefs, allNames)
            val parts = plan.days.flatMap { day -> day.exercises.map { it.bodyPart } }
            assertWithMessage(prefs.toString()).that(parts).containsNoneIn(prefs.excluded)
        }
    }

    /** 고른 주당 횟수만큼 루틴이 나오고, 날마다 종목이 있다. */
    @Test
    fun `makes one routine per training day`() {
        allCombinations().forEach { prefs ->
            val plan = RoutineRecommender.recommend(prefs, allNames)
            assertWithMessage(prefs.toString()).that(plan.days).hasSize(prefs.daysPerWeek)
            plan.days.forEach { day ->
                assertWithMessage("$prefs ${day.name}").that(day.exercises).isNotEmpty()
            }
        }
    }

    /** 하루에 같은 종목이 두 번 나오지 않고, 루틴 이름도 겹치지 않는다. */
    @Test
    fun `no duplicates within a day or across routine names`() {
        allCombinations().forEach { prefs ->
            val plan = RoutineRecommender.recommend(prefs, allNames)
            plan.days.forEach { day ->
                assertWithMessage("$prefs ${day.name}").that(day.exercises.map { it.name }).containsNoDuplicates()
            }
            assertWithMessage(prefs.toString()).that(plan.days.map { it.name }).containsNoDuplicates()
        }
    }

    /** 초보는 하루 5종목 이하, 중급은 6종목 이하(코어를 끝에 붙인 날은 +1). */
    @Test
    fun `keeps the daily volume to the level`() {
        allCombinations().forEach { prefs ->
            val limit = if (prefs.level == Level.BEGINNER) 5 else 6
            RoutineRecommender.recommend(prefs, allNames).days.forEach { day ->
                assertWithMessage("$prefs ${day.name}").that(day.exercises.size).isAtMost(limit)
            }
        }
    }

    /** 집중 부위는 종목이 늘고, 그날 가장 앞에 오고, 한 세트 더 받는다. */
    @Test
    fun `gives the focus part more exercises, first place and an extra set`() {
        val plain = recommend(days = 3, level = Level.INTERMEDIATE)
        val focused = recommend(focus = setOf(BodyPart.SHOULDER), days = 3, level = Level.INTERMEDIATE)

        fun shoulderCount(plan: RoutineRecommender.Plan) =
            plan.days.sumOf { day -> day.exercises.count { it.bodyPart == BodyPart.SHOULDER } }
        assertThat(shoulderCount(focused)).isGreaterThan(shoulderCount(plain))

        val push = focused.days.first { it.name.contains("푸시") }
        assertThat(push.exercises.first().bodyPart).isEqualTo(BodyPart.SHOULDER)

        val ohpPlain = plain.days.flatMap { it.exercises }.first { it.name == "오버헤드 프레스" }
        val ohpFocused = focused.days.flatMap { it.exercises }.first { it.name == "오버헤드 프레스" }
        assertThat(ohpFocused.sets).isEqualTo(ohpPlain.sets + 1)
        assertThat(ohpFocused.isFocus).isTrue()
    }

    /** 하체를 빼면 하체 날이 사라지는 대신 다른 날로 채워 주 3회를 맞추고, 코어는 남는다. */
    @Test
    fun `fills the week when a whole day is excluded`() {
        val plan = recommend(excluded = setOf(BodyPart.LEG), days = 3)

        assertThat(plan.days.map { it.name }).containsExactly("맞춤 푸시 A", "맞춤 풀", "맞춤 푸시 B").inOrder()
        val allParts = plan.days.flatMap { day -> day.exercises.map { it.bodyPart } }
        assertThat(allParts).contains(BodyPart.CORE)
    }

    /** 같은 분할이 두 번 나오면 두 번째 날은 다른 종목부터 고른다. */
    @Test
    fun `repeated days use different lead exercises`() {
        val plan = recommend(days = 6, level = Level.INTERMEDIATE)
        val pushA = plan.days.first { it.name == "맞춤 푸시 A" }.exercises.map { it.name }
        val pushB = plan.days.first { it.name == "맞춤 푸시 B" }.exercises.map { it.name }

        assertThat(pushA).isNotEqualTo(pushB)
        assertThat(pushA.first()).isNotEqualTo(pushB.first())
    }

    /** 사용자가 지운 종목은 추천하지 않는다. */
    @Test
    fun `skips exercises that were deleted`() {
        val plan = recommend(days = 2, available = allNames - "스쿼트" - "벤치프레스")

        val names = plan.days.flatMap { day -> day.exercises.map { it.name } }
        assertThat(names).containsNoneOf("스쿼트", "벤치프레스")
        assertThat(names).isNotEmpty()
    }

    /** 집중과 제외에 같은 부위가 들어오면 제외를 따른다(안전한 쪽). */
    @Test
    fun `exclusion wins over focus`() {
        val plan = recommend(focus = setOf(BodyPart.CHEST), excluded = setOf(BodyPart.CHEST))

        val parts = plan.days.flatMap { day -> day.exercises.map { it.bodyPart } }
        assertThat(parts).doesNotContain(BodyPart.CHEST)
    }

    /** 다 빼면 만들 수 없다고 알려 준다(빈 루틴을 만들지 않는다). */
    @Test
    fun `reports when nothing is left`() {
        val plan = recommend(excluded = parts.toSet())

        assertThat(plan.isEmpty).isTrue()
        assertThat(plan.notes.single()).contains("만들 수 없습니다")
    }

    /** 같은 입력이면 항상 같은 결과. 미리보기와 실제로 만들어지는 루틴이 달라지면 안 된다. */
    @Test
    fun `is deterministic`() {
        val prefs = Preferences(setOf(BodyPart.ARM), setOf(BodyPart.CORE), 5, Level.INTERMEDIATE)
        assertThat(RoutineRecommender.recommend(prefs, allNames))
            .isEqualTo(RoutineRecommender.recommend(prefs, allNames))
    }

    /** 사람이 읽어 보는 용도: 대표 조합의 결과를 출력한다(검증은 위 테스트들이 한다). */
    @Test
    fun `print sample plans`() {
        listOf(
            Preferences(daysPerWeek = 2),
            Preferences(focus = setOf(BodyPart.CHEST), daysPerWeek = 3),
            Preferences(focus = setOf(BodyPart.LEG), excluded = setOf(BodyPart.SHOULDER), daysPerWeek = 4, level = Level.INTERMEDIATE),
            Preferences(focus = setOf(BodyPart.ARM), excluded = setOf(BodyPart.LEG), daysPerWeek = 3, level = Level.INTERMEDIATE),
        ).forEach { prefs ->
            val plan = RoutineRecommender.recommend(prefs, allNames)
            println("=== focus=${prefs.focus.map { it.label }} excluded=${prefs.excluded.map { it.label }} days=${prefs.daysPerWeek} ${prefs.level.label}")
            plan.notes.forEach { println("  · $it") }
            plan.days.forEach { day ->
                println("  [${day.name}] " + day.exercises.joinToString(", ") { "${it.name} ${it.sets}" + if (it.isFocus) "*" else "" })
            }
        }
    }
}
