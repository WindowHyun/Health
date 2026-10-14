package com.windowhyun.health.ui.gym

import java.time.DayOfWeek

/**
 * 헬스 루틴 템플릿의 모델. 내용은 코드가 아니라 `app/src/main/resources/health/routine_templates.json` 에 있다
 * (형식과 규칙은 [RoutineTemplateCatalog], 추가 방법은 docs/DATA.md).
 *
 * 이름난 프로그램(5x5 · PPL · 5/3/1 · PHUL · 아놀드 스플릿 등)은 알려진 종목 구성을
 * 임의로 바꾸지 않았다 — 원래 구성 그대로 넣어야 "그 루틴"이 된다. 맨몸 · 덤벨 · 머신 · 코어 같은
 * 장비별, 목적별 구성은 이 앱에서 흔히 쓰는 조합으로 짠 것이다.
 *
 * 종목 이름은 기본 종목 파일(`exercises.json`)에 있는 이름과 정확히 같아야 한다.
 * 적용할 때 이름으로 종목을 찾기 때문이다.
 */
object RoutineTemplates {

    /** 템플릿 하루치. 여러 날이면 요일별로 별도 루틴이 만들어진다. */
    data class Day(
        val routineName: String,
        /** 종목 이름 -> 기본 세트 수. */
        val exercises: List<Pair<String, Int>>,
        /**
         * 슈퍼셋으로 묶을 종목 이름들. 묶음마다 [exercises] 안에서 **이어서** 나와야 한다(앱의 슈퍼셋 규칙).
         * 묶음 중간 종목은 쉬지 않고 다음 종목으로 가고, 묶음의 마지막 종목을 마친 뒤에만 쉰다.
         */
        val supersets: List<List<String>> = emptyList(),
        /** 종목별 휴식 시간(초). 없는 종목은 그 종목의 기본 휴식을 쓴다. 슈퍼셋은 묶음의 마지막 종목 값이 쓰인다. */
        val restSeconds: Map<String, Int> = emptyMap(),
        /** 이 루틴을 하는 요일. 비어 있으면 요일을 정하지 않는다(홈의 "오늘의 루틴"에 쓰인다). */
        val scheduledDays: Set<DayOfWeek> = emptySet(),
    )

    /** 목록에서 묶어 보여 주는 갈래. 선언 순서가 화면 순서다. */
    enum class Category(val label: String) {
        BEGINNER("처음 시작하는 사람"),
        SPLIT("분할 프로그램"),
        STRENGTH("근력 향상"),
        EQUIPMENT("집 · 장비별"),
        FOCUS("부위 · 목적별"),
    }

    data class Template(
        val id: String,
        val category: Category,
        val title: String,
        val description: String,
        val days: List<Day>,
    )

    /** 앱에 포함된 템플릿(`resources/health/routine_templates.json`). 처음 쓸 때 한 번 읽는다. */
    val all: List<Template> by lazy { RoutineTemplateCatalog.loadBundled() }

    /** 갈래별로 묶은 목록(화면 순서). 템플릿이 없는 갈래는 뺀다. */
    val byCategory: List<Pair<Category, List<Template>>> by lazy {
        Category.entries.map { category -> category to all.filter { it.category == category } }
            .filter { it.second.isNotEmpty() }
    }
}
