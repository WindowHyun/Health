package com.windowhyun.health.ui.gym

/**
 * 이름난 헬스 루틴 목록. 자주 쓰는 국내외 프로그램을 고른 것이라 종목 구성을
 * 임의로 바꾸지 않았다 — 원래 알려진 구성 그대로 넣어야 "그 루틴"이 된다.
 *
 * 종목 이름은 [com.windowhyun.health.data.seed.DefaultExercises] 에 있는 이름과
 * 정확히 같아야 한다. 적용할 때 이름으로 종목을 찾기 때문이다.
 */
object RoutineTemplates {

    /** 템플릿 하루치. 여러 날이면 요일별로 별도 루틴이 만들어진다. */
    data class Day(
        val routineName: String,
        /** 종목 이름 -> 기본 세트 수. */
        val exercises: List<Pair<String, Int>>,
    )

    data class Template(
        val id: String,
        val title: String,
        val description: String,
        val days: List<Day>,
    )

    val all: List<Template> = listOf(
        Template(
            id = "5x5",
            title = "5x5 (초보자 전신)",
            description = "스쿼트 중심 2분할, A·B 를 번갈아 주 3회. 처음 시작하는 사람에게 가장 많이 추천되는 루틴입니다.",
            days = listOf(
                Day(
                    routineName = "5x5 A",
                    exercises = listOf(
                        "스쿼트" to 5,
                        "벤치프레스" to 5,
                        "바벨로우" to 5,
                    ),
                ),
                Day(
                    routineName = "5x5 B",
                    exercises = listOf(
                        "스쿼트" to 5,
                        "오버헤드 프레스" to 5,
                        "데드리프트" to 1,
                    ),
                ),
            ),
        ),
        Template(
            id = "ppl",
            title = "PPL 3분할 (푸시 · 풀 · 레그)",
            description = "미는 근육 / 당기는 근육 / 하체로 나눠 주 3~6회. 세계적으로 가장 널리 쓰이는 분할입니다.",
            days = listOf(
                Day(
                    routineName = "푸시 (PPL)",
                    exercises = listOf(
                        "벤치프레스" to 4,
                        "인클라인 벤치프레스" to 3,
                        "오버헤드 프레스" to 3,
                        "사이드 레터럴 레이즈" to 3,
                        "케이블 푸시다운" to 3,
                    ),
                ),
                Day(
                    routineName = "풀 (PPL)",
                    exercises = listOf(
                        "데드리프트" to 3,
                        "바벨로우" to 4,
                        "랫풀다운" to 3,
                        "페이스풀" to 3,
                        "바벨 컬" to 3,
                    ),
                ),
                Day(
                    routineName = "레그 (PPL)",
                    exercises = listOf(
                        "스쿼트" to 4,
                        "레그프레스" to 3,
                        "루마니안 데드리프트" to 3,
                        "레그 컬" to 3,
                        "카프레이즈" to 3,
                    ),
                ),
            ),
        ),
        Template(
            id = "upper_lower",
            title = "상하체 2분할",
            description = "상체 / 하체로 나눠 주 4회. 3분할보다 빈도가 높아 회복과 성장의 균형이 좋습니다.",
            days = listOf(
                Day(
                    routineName = "상체 (2분할)",
                    exercises = listOf(
                        "벤치프레스" to 4,
                        "바벨로우" to 4,
                        "오버헤드 프레스" to 3,
                        "랫풀다운" to 3,
                        "바벨 컬" to 3,
                        "케이블 푸시다운" to 3,
                    ),
                ),
                Day(
                    routineName = "하체 (2분할)",
                    exercises = listOf(
                        "스쿼트" to 4,
                        "루마니안 데드리프트" to 3,
                        "레그프레스" to 3,
                        "레그 컬" to 3,
                        "카프레이즈" to 3,
                    ),
                ),
            ),
        ),
        Template(
            id = "3way",
            title = "3분할 (가슴·등·하체)",
            description = "국내 헬스장에서 가장 흔한 3분할 구성. 가슴+삼두 / 등+이두 / 하체+어깨로 나눕니다.",
            days = listOf(
                Day(
                    routineName = "가슴 · 삼두 (3분할)",
                    exercises = listOf(
                        "벤치프레스" to 4,
                        "인클라인 벤치프레스" to 3,
                        "덤벨 플라이" to 3,
                        "케이블 크로스오버" to 3,
                        "라잉 트라이셉스 익스텐션" to 3,
                    ),
                ),
                Day(
                    routineName = "등 · 이두 (3분할)",
                    exercises = listOf(
                        "데드리프트" to 3,
                        "바벨로우" to 4,
                        "랫풀다운" to 3,
                        "시티드 케이블로우" to 3,
                        "덤벨 컬" to 3,
                    ),
                ),
                Day(
                    routineName = "하체 · 어깨 (3분할)",
                    exercises = listOf(
                        "스쿼트" to 4,
                        "레그프레스" to 3,
                        "레그 컬" to 3,
                        "오버헤드 프레스" to 3,
                        "사이드 레터럴 레이즈" to 3,
                    ),
                ),
            ),
        ),
        Template(
            id = "5way",
            title = "5분할 (브로 스플릿)",
            description = "부위마다 하루씩, 주 5회. 부위별로 종목 수를 많이 넣을 수 있어 숙련자가 많이 씁니다.",
            days = listOf(
                Day(
                    routineName = "가슴 (5분할)",
                    exercises = listOf(
                        "벤치프레스" to 4,
                        "인클라인 벤치프레스" to 3,
                        "덤벨 플라이" to 3,
                        "케이블 크로스오버" to 3,
                        "딥스" to 3,
                    ),
                ),
                Day(
                    routineName = "등 (5분할)",
                    exercises = listOf(
                        "데드리프트" to 4,
                        "바벨로우" to 3,
                        "랫풀다운" to 3,
                        "시티드 케이블로우" to 3,
                        "티바로우" to 3,
                    ),
                ),
                Day(
                    routineName = "어깨 (5분할)",
                    exercises = listOf(
                        "오버헤드 프레스" to 4,
                        "덤벨 숄더프레스" to 3,
                        "사이드 레터럴 레이즈" to 3,
                        "벤트오버 레터럴 레이즈" to 3,
                        "페이스풀" to 3,
                    ),
                ),
                Day(
                    routineName = "팔 (5분할)",
                    exercises = listOf(
                        "바벨 컬" to 3,
                        "해머 컬" to 3,
                        "케이블 푸시다운" to 3,
                        "라잉 트라이셉스 익스텐션" to 3,
                        "클로즈그립 벤치프레스" to 3,
                    ),
                ),
                Day(
                    routineName = "하체 (5분할)",
                    exercises = listOf(
                        "스쿼트" to 4,
                        "프론트 스쿼트" to 3,
                        "레그프레스" to 3,
                        "레그 익스텐션" to 3,
                        "레그 컬" to 3,
                        "카프레이즈" to 3,
                    ),
                ),
            ),
        ),
    )
}
