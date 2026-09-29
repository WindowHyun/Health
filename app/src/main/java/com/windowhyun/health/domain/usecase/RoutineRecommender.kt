package com.windowhyun.health.domain.usecase

import com.windowhyun.health.core.model.BodyPart

/**
 * 원하는 부위 / 빼고 싶은 부위에 맞춰 주간 루틴을 짠다.
 *
 * 서버나 외부 AI 없이 기기 안에서 규칙으로 계산한다. 같은 입력이면 항상 같은 결과가
 * 나오고, 인터넷 없이도 된다. 규칙은 흔히 쓰는 운동 원칙을 옮긴 것이다.
 *
 * 1. **분할** — 주당 횟수로 정한다(2회 전신, 3회 푸시/풀/하체, 4회 상하체 …).
 * 2. **종목 수 배분** — 큰 근육(가슴·등·하체)에 더 많이, 원하는 부위는 두 배 비중으로.
 *    하루 종목 수 안에서 비례 배분한다(D'Hondt 방식).
 * 3. **순서** — 주 운동(다관절)을 먼저, 그 안에서 원하는 부위를 먼저. 보조 운동으로
 *    먼저 지치게 하면 주 운동이 무너지므로, 집중 부위라도 보조 운동은 뒤에 둔다.
 * 4. **세트** — 그날 가장 먼저 하는 집중 부위 종목만 한 세트 더. 종목 수까지 늘리므로
 *    모든 종목에 더하면 하루 세트가 너무 많아진다.
 * 5. **같은 분할이 두 번 나오면**(주 6회 등) 두 번째 날은 다른 종목부터 고른다.
 *
 * 빼고 싶은 부위는 그 부위가 **주동근인 종목**만 뺀다. 벤치프레스처럼 어깨를 함께
 * 쓰는 운동은 남으므로, 부상 때문이라면 결과를 보고 직접 빼야 한다(화면에서 안내).
 */
object RoutineRecommender {

    enum class Level(val label: String) {
        BEGINNER("초보"),
        INTERMEDIATE("중급 이상"),
    }

    data class Preferences(
        val focus: Set<BodyPart> = emptySet(),
        val excluded: Set<BodyPart> = emptySet(),
        val daysPerWeek: Int = 3,
        val level: Level = Level.BEGINNER,
    )

    data class RecommendedExercise(
        val name: String,
        val bodyPart: BodyPart,
        val sets: Int,
        val isFocus: Boolean,
    )

    data class RecommendedDay(
        val name: String,
        val exercises: List<RecommendedExercise>,
    )

    data class Plan(
        val days: List<RecommendedDay>,
        /** 왜 이렇게 짰는지. 미리보기 화면에 그대로 보여 준다. */
        val notes: List<String>,
    ) {
        val isEmpty: Boolean get() = days.isEmpty()
    }

    /** 추천에 쓰는 부위. 전신·유산소 종목은 루틴 뼈대로 쓰지 않는다. */
    val selectableParts: List<BodyPart> = listOf(
        BodyPart.CHEST,
        BodyPart.BACK,
        BodyPart.SHOULDER,
        BodyPart.ARM,
        BodyPart.LEG,
        BodyPart.CORE,
    )

    val dayRange: IntRange = 2..6

    // ----- 종목 사전 -----

    /**
     * 미는 운동인지 당기는 운동인지. 팔·어깨는 둘 다 있어서 날짜에 맞게 골라 넣는다.
     * [HINGE] 는 데드리프트처럼 엉덩이를 접는 운동이다. 등으로 분류되지만 하체를 크게 써서
     * 상체 날에는 넣지 않는다.
     */
    enum class Pattern { PUSH, PULL, HINGE, LEGS, CORE }

    data class CatalogEntry(
        val name: String,
        val part: BodyPart,
        val pattern: Pattern,
        /** 그날의 주 운동(무거운 다관절 운동). 먼저 하고 세트도 더 준다. */
        val compound: Boolean,
    )

    /**
     * 추천에 쓰는 종목. 부위 안에서 **먼저 고를 순서**로 적었다.
     * 이름은 기본 제공 종목과 같아야 한다(없는 종목은 추천에서 빠진다).
     */
    val catalog: List<CatalogEntry> = listOf(
        // 가슴: 프레스 두 개 다음에 플라이. 프레스만 셋 넣지 않는다.
        CatalogEntry("벤치프레스", BodyPart.CHEST, Pattern.PUSH, compound = true),
        CatalogEntry("인클라인 벤치프레스", BodyPart.CHEST, Pattern.PUSH, compound = true),
        CatalogEntry("덤벨 플라이", BodyPart.CHEST, Pattern.PUSH, compound = false),
        CatalogEntry("딥스", BodyPart.CHEST, Pattern.PUSH, compound = true),
        CatalogEntry("덤벨 벤치프레스", BodyPart.CHEST, Pattern.PUSH, compound = true),
        CatalogEntry("케이블 크로스오버", BodyPart.CHEST, Pattern.PUSH, compound = false),
        CatalogEntry("체스트 프레스 머신", BodyPart.CHEST, Pattern.PUSH, compound = true),
        CatalogEntry("푸시업", BodyPart.CHEST, Pattern.PUSH, compound = false),
        // 등: 수평으로 당기기와 수직으로 당기기를 번갈아
        CatalogEntry("데드리프트", BodyPart.BACK, Pattern.HINGE, compound = true),
        CatalogEntry("바벨로우", BodyPart.BACK, Pattern.PULL, compound = true),
        CatalogEntry("랫풀다운", BodyPart.BACK, Pattern.PULL, compound = true),
        CatalogEntry("시티드 케이블로우", BodyPart.BACK, Pattern.PULL, compound = true),
        CatalogEntry("풀업", BodyPart.BACK, Pattern.PULL, compound = true),
        CatalogEntry("덤벨로우", BodyPart.BACK, Pattern.PULL, compound = true),
        CatalogEntry("티바로우", BodyPart.BACK, Pattern.PULL, compound = true),
        // 어깨: 앞·옆은 미는 날, 뒤는 당기는 날
        CatalogEntry("오버헤드 프레스", BodyPart.SHOULDER, Pattern.PUSH, compound = true),
        CatalogEntry("사이드 레터럴 레이즈", BodyPart.SHOULDER, Pattern.PUSH, compound = false),
        CatalogEntry("덤벨 숄더프레스", BodyPart.SHOULDER, Pattern.PUSH, compound = true),
        CatalogEntry("페이스풀", BodyPart.SHOULDER, Pattern.PULL, compound = false),
        CatalogEntry("벤트오버 레터럴 레이즈", BodyPart.SHOULDER, Pattern.PULL, compound = false),
        CatalogEntry("슈러그", BodyPart.SHOULDER, Pattern.PULL, compound = false),
        // 팔: 이두·삼두를 번갈아. 클로즈그립 벤치는 벤치프레스와 겹치므로 맨 뒤, 보조로 둔다.
        CatalogEntry("바벨 컬", BodyPart.ARM, Pattern.PULL, compound = false),
        CatalogEntry("케이블 푸시다운", BodyPart.ARM, Pattern.PUSH, compound = false),
        CatalogEntry("덤벨 컬", BodyPart.ARM, Pattern.PULL, compound = false),
        CatalogEntry("라잉 트라이셉스 익스텐션", BodyPart.ARM, Pattern.PUSH, compound = false),
        CatalogEntry("해머 컬", BodyPart.ARM, Pattern.PULL, compound = false),
        CatalogEntry("클로즈그립 벤치프레스", BodyPart.ARM, Pattern.PUSH, compound = false),
        // 하체: 큰 운동 셋 다음에 보조. 무거운 다관절만 대여섯 개 넣지 않는다.
        CatalogEntry("스쿼트", BodyPart.LEG, Pattern.LEGS, compound = true),
        CatalogEntry("루마니안 데드리프트", BodyPart.LEG, Pattern.LEGS, compound = true),
        CatalogEntry("레그프레스", BodyPart.LEG, Pattern.LEGS, compound = true),
        CatalogEntry("레그 컬", BodyPart.LEG, Pattern.LEGS, compound = false),
        CatalogEntry("레그 익스텐션", BodyPart.LEG, Pattern.LEGS, compound = false),
        CatalogEntry("런지", BodyPart.LEG, Pattern.LEGS, compound = true),
        CatalogEntry("프론트 스쿼트", BodyPart.LEG, Pattern.LEGS, compound = true),
        CatalogEntry("카프레이즈", BodyPart.LEG, Pattern.LEGS, compound = false),
        // 코어
        CatalogEntry("행잉 레그레이즈", BodyPart.CORE, Pattern.CORE, compound = false),
        CatalogEntry("플랭크", BodyPart.CORE, Pattern.CORE, compound = false),
        CatalogEntry("케이블 크런치", BodyPart.CORE, Pattern.CORE, compound = false),
        CatalogEntry("크런치", BodyPart.CORE, Pattern.CORE, compound = false),
    )

    // ----- 분할 -----

    /**
     * 하루에 들어가는 부위. [patterns] 가 비어 있으면 그 부위의 모든 종목을 쓴다.
     * [finisher] 는 비례 배분과 상관없이 마지막에 한 종목만 넣는 자리다.
     */
    private data class Slot(
        val part: BodyPart,
        val patterns: Set<Pattern> = emptySet(),
        val finisher: Boolean = false,
    )

    private enum class DayType(val label: String, val slots: List<Slot>) {
        FULL(
            "전신",
            listOf(
                Slot(BodyPart.LEG),
                Slot(BodyPart.BACK),
                Slot(BodyPart.CHEST),
                Slot(BodyPart.SHOULDER, setOf(Pattern.PUSH)),
                Slot(BodyPart.ARM),
                Slot(BodyPart.CORE),
            ),
        ),
        PUSH(
            "푸시",
            listOf(
                Slot(BodyPart.CHEST),
                Slot(BodyPart.SHOULDER, setOf(Pattern.PUSH)),
                Slot(BodyPart.ARM, setOf(Pattern.PUSH)),
            ),
        ),
        PULL(
            "풀",
            listOf(
                Slot(BodyPart.BACK, setOf(Pattern.PULL, Pattern.HINGE)),
                Slot(BodyPart.SHOULDER, setOf(Pattern.PULL)),
                Slot(BodyPart.ARM, setOf(Pattern.PULL)),
            ),
        ),
        LEGS("하체", listOf(Slot(BodyPart.LEG), Slot(BodyPart.CORE))),
        UPPER(
            "상체",
            // 당기기를 미는 것보다 앞에 둔다. 비중이 같으면 앞 부위가 한 자리 더 받는다.
            // 데드리프트는 하체를 크게 쓰므로 상체 날에 넣지 않는다.
            listOf(
                Slot(BodyPart.BACK, setOf(Pattern.PULL)),
                Slot(BodyPart.CHEST),
                Slot(BodyPart.SHOULDER),
                Slot(BodyPart.ARM),
            ),
        ),
        LOWER("하체", listOf(Slot(BodyPart.LEG), Slot(BodyPart.CORE))),
    }

    private fun splitFor(days: Int): List<DayType> = when (days) {
        2 -> listOf(DayType.FULL, DayType.FULL)
        3 -> listOf(DayType.PUSH, DayType.PULL, DayType.LEGS)
        4 -> listOf(DayType.UPPER, DayType.LOWER, DayType.UPPER, DayType.LOWER)
        5 -> listOf(DayType.PUSH, DayType.PULL, DayType.LEGS, DayType.UPPER, DayType.LOWER)
        else -> listOf(DayType.PUSH, DayType.PULL, DayType.LEGS, DayType.PUSH, DayType.PULL, DayType.LEGS)
    }

    private fun splitDescription(days: Int): String = when (days) {
        2 -> "주 2회 → 전신 A/B"
        3 -> "주 3회 → 푸시 · 풀 · 하체 3분할"
        4 -> "주 4회 → 상체 / 하체 2분할을 두 번"
        5 -> "주 5회 → 푸시 · 풀 · 하체 + 상체 · 하체"
        else -> "주 6회 → 푸시 · 풀 · 하체를 두 번"
    }

    /** 부위별 기본 비중. 큰 근육일수록 종목을 더 받는다. */
    private fun baseWeight(part: BodyPart): Int = when (part) {
        BodyPart.CHEST, BodyPart.BACK, BodyPart.LEG -> 3
        BodyPart.SHOULDER, BodyPart.ARM -> 2
        else -> 1
    }

    // ----- 추천 -----

    /**
     * @param availableExerciseNames 지금 DB 에 있는 종목 이름. 사용자가 지운 종목은 고르지 않는다.
     */
    fun recommend(preferences: Preferences, availableExerciseNames: Set<String>): Plan {
        val days = preferences.daysPerWeek.coerceIn(dayRange)
        // 원하는 부위와 빼는 부위가 겹치면 빼는 쪽을 따른다(안전한 쪽).
        val excluded = preferences.excluded
        val focus = preferences.focus - excluded
        val pool = catalog.filter { it.name in availableExerciseNames && it.part !in excluded }

        fun candidates(slot: Slot) = pool.filter {
            it.part == slot.part && (slot.patterns.isEmpty() || it.pattern in slot.patterns)
        }

        // 1. 분할에서 빼는 부위를 지우고, 남은 부위가 코어뿐인 날은 없앤다.
        var dayTypes = splitFor(days)
            .map { type -> type to type.slots.filter { candidates(it).isNotEmpty() } }
            .filter { (_, slots) -> slots.any { it.part != BodyPart.CORE } }
        if (dayTypes.isEmpty()) {
            return Plan(emptyList(), listOf("뺀 부위가 너무 많아 루틴을 만들 수 없습니다."))
        }
        // 날이 빠졌으면 남은 날을 돌려 가며 채워 주당 횟수를 맞춘다.
        if (dayTypes.size < days) {
            val base = dayTypes
            dayTypes = List(days) { base[it % base.size] }
        }
        // 코어를 빼지 않았는데 코어가 들어간 날이 없어졌으면 다른 날 끝에 붙인다.
        val coreSlot = Slot(BodyPart.CORE, finisher = true)
        if (BodyPart.CORE !in excluded && candidates(coreSlot).isNotEmpty() &&
            dayTypes.none { (_, slots) -> slots.any { it.part == BodyPart.CORE } }
        ) {
            dayTypes = dayTypes.map { (type, slots) -> type to (slots + coreSlot) }
        }

        // 2. 날마다 종목을 고른다.
        val occurrences = dayTypes.groupingBy { it.first.label }.eachCount()
        val seen = mutableMapOf<String, Int>()
        val planDays = dayTypes.map { (type, slots) ->
            val variant = seen.getOrDefault(type.label, 0)
            seen[type.label] = variant + 1
            val budget = when {
                preferences.level == Level.BEGINNER && type == DayType.FULL -> 5
                preferences.level == Level.BEGINNER -> 4
                else -> 6
            }
            val exercises = buildDay(slots, budget, variant, focus, preferences.level, ::candidates)
            val suffix = if ((occurrences[type.label] ?: 0) > 1) " ${'A' + variant}" else ""
            RecommendedDay(name = "맞춤 ${type.label}$suffix", exercises = exercises)
        }.filter { it.exercises.isNotEmpty() }

        return Plan(planDays, buildNotes(days, focus, excluded, preferences.level))
    }

    private fun buildDay(
        slots: List<Slot>,
        budget: Int,
        variant: Int,
        focus: Set<BodyPart>,
        level: Level,
        candidates: (Slot) -> List<CatalogEntry>,
    ): List<RecommendedExercise> {
        // 같은 분할의 두 번째 날은 목록을 한 칸 돌려 다른 종목부터 고른다.
        val options = slots.associateWith { slot ->
            val list = candidates(slot)
            if (list.isEmpty()) list else list.drop(variant % list.size) + list.take(variant % list.size)
        }
        val weight = slots.associateWith { baseWeight(it.part) * if (it.part in focus) 2 else 1 }

        // 마무리 자리(코어)는 먼저 한 종목을 떼어 두고, 나머지를 비례 배분한다.
        val allocated = slots.associateWith { if (it.finisher && options.getValue(it).isNotEmpty()) 1 else 0 }
            .toMutableMap()
        val shared = slots.filterNot { it.finisher }
        // D'Hondt 비례 배분: 매번 "비중 / (이미 받은 수 + 1)" 이 가장 큰 부위에 한 자리.
        repeat(budget - allocated.values.sum()) {
            val next = shared
                .filter { allocated.getValue(it) < options.getValue(it).size }
                .maxByOrNull { weight.getValue(it).toDouble() / (allocated.getValue(it) + 1) }
                ?: return@repeat
            allocated[next] = allocated.getValue(next) + 1
        }

        val picked = mutableListOf<CatalogEntry>()
        slots.forEach { slot ->
            options.getValue(slot)
                .filter { entry -> picked.none { it.name == entry.name } }
                .take(allocated.getValue(slot))
                .let(picked::addAll)
        }

        val baseCompoundSets = if (level == Level.BEGINNER) 3 else 4
        val ordered = picked
            // 주 운동 먼저, 그 안에서 원하는 부위 먼저, 코어는 맨 끝. 같으면 원래 순서.
            .sortedWith(
                compareBy<CatalogEntry>({ it.part == BodyPart.CORE }, { !it.compound }, { it.part !in focus }),
            )
        val leadFocus = ordered.firstOrNull { it.part in focus }
        return ordered.map { entry ->
            val base = if (entry.compound) baseCompoundSets else 3
            RecommendedExercise(
                name = entry.name,
                bodyPart = entry.part,
                sets = if (entry === leadFocus) base + 1 else base,
                isFocus = entry.part in focus,
            )
        }
    }

    private fun buildNotes(
        days: Int,
        focus: Set<BodyPart>,
        excluded: Set<BodyPart>,
        level: Level,
    ): List<String> = buildList {
        add(splitDescription(days))
        add(
            if (level == Level.BEGINNER) {
                "${level.label}: 하루 4~5종목, 큰 운동 3세트"
            } else {
                "${level.label}: 하루 6종목, 큰 운동 4세트"
            },
        )
        if (focus.isNotEmpty()) {
            val names = focus.sortedBy { it.ordinal }.joinToString(", ") { it.label }
            add("$names 집중: 종목을 더 넣고 앞쪽에 배치, 첫 종목은 한 세트 더")
        }
        if (excluded.isNotEmpty()) {
            val names = excluded.sortedBy { it.ordinal }.joinToString(", ") { it.label }
            add("$names 제외: 그 부위가 주로 쓰이는 종목을 뺐습니다. 함께 쓰이는 운동(예: 벤치프레스의 어깨)은 남아 있으니 부상 때문이라면 확인해 주세요")
        }
    }
}
