package com.windowhyun.health.ui.gym

import java.time.DayOfWeek

/**
 * 헬스 루틴 템플릿. 이름난 프로그램(5x5 · PPL · 5/3/1 · PHUL · 아놀드 스플릿 등)은 알려진 종목 구성을
 * 임의로 바꾸지 않았다 — 원래 구성 그대로 넣어야 "그 루틴"이 된다. 맨몸 · 덤벨 · 머신 · 코어 같은
 * 장비별, 목적별 구성은 이 앱에서 흔히 쓰는 조합으로 짠 것이다.
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

    val all: List<Template> = listOf(
        Template(
            id = "5x5",
            category = Category.BEGINNER,
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
            category = Category.SPLIT,
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
            category = Category.SPLIT,
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
            category = Category.SPLIT,
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
            category = Category.SPLIT,
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
        Template(
            id = "starting_strength",
            category = Category.BEGINNER,
            title = "스타팅 스트렝스",
            description = "스쿼트 · 벤치 · 데드리프트 · 프레스, 네 가지 큰 운동만. A·B 를 번갈아 주 3회, 세트 수가 적어 회복이 빠릅니다.",
            days = listOf(
                Day(
                    routineName = "스타팅 스트렝스 A",
                    exercises = listOf(
                        "스쿼트" to 3,
                        "벤치프레스" to 3,
                        "데드리프트" to 1,
                    ),
                ),
                Day(
                    routineName = "스타팅 스트렝스 B",
                    exercises = listOf(
                        "스쿼트" to 3,
                        "오버헤드 프레스" to 3,
                        "데드리프트" to 1,
                    ),
                ),
            ),
        ),
        Template(
            id = "full_body_3",
            category = Category.BEGINNER,
            title = "전신 3일 (주 3회)",
            description = "하루에 온몸을 한 번씩, 월·수·금처럼 하루 걸러. 종목을 날마다 바꿔 한 가지 동작에 질리지 않게 했습니다.",
            days = listOf(
                Day(
                    routineName = "전신 A",
                    exercises = listOf(
                        "스쿼트" to 3,
                        "벤치프레스" to 3,
                        "바벨로우" to 3,
                        "사이드 레터럴 레이즈" to 3,
                        "플랭크" to 3,
                    ),
                ),
                Day(
                    routineName = "전신 B",
                    exercises = listOf(
                        "루마니안 데드리프트" to 3,
                        "오버헤드 프레스" to 3,
                        "랫풀다운" to 3,
                        "레그프레스" to 3,
                        "바벨 컬" to 2,
                        "케이블 푸시다운" to 2,
                    ),
                ),
                Day(
                    routineName = "전신 C",
                    exercises = listOf(
                        "프론트 스쿼트" to 3,
                        "인클라인 덤벨 프레스" to 3,
                        "시티드 케이블로우" to 3,
                        "레그 컬" to 3,
                        "행잉 레그레이즈" to 3,
                    ),
                ),
            ),
        ),
        Template(
            id = "machine_start",
            category = Category.BEGINNER,
            title = "머신 입문 (전신 2일)",
            description = "자세를 잡기 쉬운 머신과 케이블 위주. 헬스장이 처음이라 바벨이 부담스러울 때 시작하기 좋습니다.",
            days = listOf(
                Day(
                    routineName = "머신 입문 A",
                    exercises = listOf(
                        "레그프레스" to 3,
                        "체스트 프레스 머신" to 3,
                        "랫풀다운" to 3,
                        "머신 숄더프레스" to 3,
                        "레그 컬" to 3,
                        "케이블 크런치" to 3,
                    ),
                ),
                Day(
                    routineName = "머신 입문 B",
                    exercises = listOf(
                        "레그 익스텐션" to 3,
                        "시티드 로우 머신" to 3,
                        "펙덱 플라이" to 3,
                        "머신 레터럴 레이즈" to 3,
                        "바이셉스 컬 머신" to 2,
                        "트라이셉스 익스텐션 머신" to 2,
                        "카프레이즈" to 3,
                    ),
                ),
            ),
        ),
        Template(
            id = "phul",
            category = Category.SPLIT,
            title = "PHUL (상하체 파워 + 근비대)",
            description = "상체 · 하체를 각각 무겁게(파워) 한 번, 가볍게 많이(근비대) 한 번 — 주 4회. 힘과 근육 크기를 같이 키우는 프로그램입니다.",
            days = listOf(
                Day(
                    routineName = "PHUL 상체 파워",
                    exercises = listOf(
                        "벤치프레스" to 4,
                        "인클라인 덤벨 프레스" to 3,
                        "바벨로우" to 4,
                        "랫풀다운" to 3,
                        "오버헤드 프레스" to 3,
                        "바벨 컬" to 3,
                        "라잉 트라이셉스 익스텐션" to 3,
                    ),
                ),
                Day(
                    routineName = "PHUL 하체 파워",
                    exercises = listOf(
                        "스쿼트" to 4,
                        "데드리프트" to 3,
                        "레그프레스" to 5,
                        "레그 컬" to 3,
                        "카프레이즈" to 4,
                    ),
                ),
                Day(
                    routineName = "PHUL 상체 근비대",
                    exercises = listOf(
                        "인클라인 벤치프레스" to 4,
                        "덤벨 플라이" to 3,
                        "시티드 케이블로우" to 4,
                        "랫풀다운" to 3,
                        "덤벨 숄더프레스" to 3,
                        "사이드 레터럴 레이즈" to 3,
                        "인클라인 덤벨 컬" to 3,
                        "케이블 푸시다운" to 3,
                    ),
                ),
                Day(
                    routineName = "PHUL 하체 근비대",
                    exercises = listOf(
                        "프론트 스쿼트" to 4,
                        "런지" to 3,
                        "레그 익스텐션" to 3,
                        "시티드 레그 컬" to 3,
                        "카프레이즈" to 4,
                        "시티드 카프 레이즈" to 3,
                    ),
                ),
            ),
        ),
        Template(
            id = "arnold",
            category = Category.SPLIT,
            title = "아놀드 스플릿 (가슴·등 / 어깨·팔 / 하체)",
            description = "아놀드 슈워제네거가 쓴 것으로 알려진 3분할. 같은 순서를 두 번 돌려 주 6회가 원래 방식이고, 하루 쉬며 주 3~4회로 줄여도 됩니다.",
            days = listOf(
                Day(
                    routineName = "아놀드 가슴 · 등",
                    exercises = listOf(
                        "벤치프레스" to 4,
                        "인클라인 벤치프레스" to 4,
                        "덤벨 풀오버" to 4,
                        "풀업" to 4,
                        "티바로우" to 4,
                        "데드리프트" to 3,
                    ),
                ),
                Day(
                    routineName = "아놀드 어깨 · 팔",
                    exercises = listOf(
                        "오버헤드 프레스" to 4,
                        "사이드 레터럴 레이즈" to 4,
                        "벤트오버 레터럴 레이즈" to 4,
                        "바벨 컬" to 4,
                        "컨센트레이션 컬" to 3,
                        "라잉 트라이셉스 익스텐션" to 4,
                        "케이블 푸시다운" to 3,
                    ),
                ),
                Day(
                    routineName = "아놀드 하체",
                    exercises = listOf(
                        "스쿼트" to 5,
                        "레그프레스" to 4,
                        "레그 익스텐션" to 4,
                        "레그 컬" to 4,
                        "카프레이즈" to 5,
                    ),
                ),
            ),
        ),
        Template(
            id = "wendler_531",
            category = Category.STRENGTH,
            title = "5/3/1 (웬들러)",
            description = "하루 한 가지 큰 운동(스쿼트 · 벤치 · 데드리프트 · 프레스)에 보조 운동을 붙인 주 4회. 4주 주기로 무게를 조금씩 올립니다 — 주차별 비율은 직접 계산해 무게를 정하세요.",
            days = listOf(
                Day(
                    routineName = "5/3/1 스쿼트 데이",
                    exercises = listOf(
                        "스쿼트" to 3,
                        "레그프레스" to 5,
                        "레그 컬" to 5,
                        "행잉 레그레이즈" to 5,
                    ),
                ),
                Day(
                    routineName = "5/3/1 벤치 데이",
                    exercises = listOf(
                        "벤치프레스" to 3,
                        "덤벨 벤치프레스" to 5,
                        "덤벨로우" to 5,
                        "케이블 푸시다운" to 5,
                    ),
                ),
                Day(
                    routineName = "5/3/1 데드리프트 데이",
                    exercises = listOf(
                        "데드리프트" to 3,
                        "굿모닝" to 5,
                        "랫풀다운" to 5,
                        "행잉 레그레이즈" to 5,
                    ),
                ),
                Day(
                    routineName = "5/3/1 프레스 데이",
                    exercises = listOf(
                        "오버헤드 프레스" to 3,
                        "딥스" to 5,
                        "시티드 케이블로우" to 5,
                        "페이스풀" to 5,
                    ),
                ),
            ),
        ),
        Template(
            id = "bodyweight_home",
            category = Category.EQUIPMENT,
            title = "맨몸 · 홈트 (2일)",
            description = "기구 없이 철봉이나 낮은 테이블 정도만 있으면 됩니다. 밀기 · 당기기 · 하체 · 코어를 이틀에 나눠 돌립니다.",
            days = listOf(
                Day(
                    routineName = "홈트 A",
                    exercises = listOf(
                        "푸시업" to 4,
                        "인버티드 로우" to 4,
                        "맨몸 스쿼트" to 4,
                        "글루트 브릿지" to 3,
                        "플랭크" to 3,
                        "마운틴 클라이머" to 3,
                    ),
                ),
                Day(
                    routineName = "홈트 B",
                    exercises = listOf(
                        "파이크 푸시업" to 3,
                        "친업" to 4,
                        "런지" to 3,
                        "점프 스쿼트" to 3,
                        "레그레이즈" to 3,
                        "버피" to 3,
                    ),
                ),
            ),
        ),
        Template(
            id = "dumbbell_only",
            category = Category.EQUIPMENT,
            title = "덤벨 전용 (2일)",
            description = "덤벨과 벤치만 있으면 되는 상체 · 하체 구성. 집이나 작은 헬스장에서도 할 수 있습니다.",
            days = listOf(
                Day(
                    routineName = "덤벨 A",
                    exercises = listOf(
                        "고블릿 스쿼트" to 4,
                        "덤벨 벤치프레스" to 4,
                        "덤벨로우" to 4,
                        "덤벨 숄더프레스" to 3,
                        "덤벨 컬" to 3,
                        "오버헤드 트라이셉스 익스텐션" to 3,
                    ),
                ),
                Day(
                    routineName = "덤벨 B",
                    exercises = listOf(
                        "덤벨 루마니안 데드리프트" to 4,
                        "불가리안 스플릿 스쿼트" to 3,
                        "인클라인 덤벨 프레스" to 3,
                        "덤벨 풀오버" to 3,
                        "사이드 레터럴 레이즈" to 3,
                        "해머 컬" to 3,
                        "트라이셉스 킥백" to 3,
                    ),
                ),
            ),
        ),
        Template(
            id = "core_focus",
            category = Category.FOCUS,
            title = "코어 · 복근 집중",
            description = "버티기, 다리 들기, 비틀기, 안정화를 한 번에. 다른 운동 끝에 붙이거나 따로 20분 정도로 합니다.",
            days = listOf(
                Day(
                    routineName = "코어 집중",
                    exercises = listOf(
                        "플랭크" to 3,
                        "행잉 레그레이즈" to 3,
                        "케이블 크런치" to 3,
                        "러시안 트위스트" to 3,
                        "사이드 플랭크" to 3,
                        "데드버그" to 3,
                    ),
                ),
            ),
        ),
        Template(
            id = "glute_leg",
            category = Category.FOCUS,
            title = "힙 · 하체 집중 (2일)",
            description = "엉덩이와 허벅지 뒤쪽을 중심으로. 힙 쓰러스트와 한 다리 운동으로 둔근을 자극합니다.",
            days = listOf(
                Day(
                    routineName = "힙 · 하체 A",
                    exercises = listOf(
                        "힙 쓰러스트" to 4,
                        "불가리안 스플릿 스쿼트" to 3,
                        "루마니안 데드리프트" to 3,
                        "힙 어브덕션 머신" to 3,
                        "케이블 풀스루" to 3,
                    ),
                ),
                Day(
                    routineName = "힙 · 하체 B",
                    exercises = listOf(
                        "스쿼트" to 4,
                        "레그프레스" to 3,
                        "워킹 런지" to 3,
                        "시티드 레그 컬" to 3,
                        "글루트 브릿지" to 3,
                    ),
                ),
            ),
        ),
        Template(
            id = "runner_strength",
            category = Category.FOCUS,
            title = "러너 보강 (주 2회)",
            description = "달리기를 하는 사람을 위한 하체 · 코어 보강. 부상이 잦은 둔근 · 햄스트링 · 종아리와 몸통 안정에 맞췄고, 근육통이 크지 않게 세트를 적게 잡았습니다.",
            days = listOf(
                Day(
                    routineName = "러너 보강 A",
                    exercises = listOf(
                        "고블릿 스쿼트" to 3,
                        "덤벨 루마니안 데드리프트" to 3,
                        "스텝업" to 3,
                        "카프레이즈" to 3,
                        "플랭크" to 3,
                    ),
                ),
                Day(
                    routineName = "러너 보강 B",
                    exercises = listOf(
                        "리버스 런지" to 3,
                        "힙 쓰러스트" to 3,
                        "시티드 카프 레이즈" to 3,
                        "사이드 플랭크" to 3,
                        "데드버그" to 3,
                    ),
                ),
            ),
        ),
        Template(
            id = "fat_maintenance_4day",
            category = Category.SPLIT,
            title = "체지방 유지 고강도 주4일 (웨이트 3 + 러닝 1)",
            description = "평일 45분 웨이트 3일(가슴 / 등 / 하체·어깨)과 토요일 러닝 + 팔·복근. 큰 복합 운동은 RPE 8~9의 고중량으로, " +
                "마지막 세트는 드롭세트로 마무리합니다. 월·화·목·토로 요일이 정해져 홈의 오늘의 루틴에 나옵니다(루틴 편집에서 바꿀 수 있습니다). " +
                "토요일 러닝은 러닝 탭에서 인터벌 켜기: 달리기 3:00 / 걷기(조깅) 2:00 × 5회, 먼저 뛰고 팔·복근은 그 뒤에 하세요.",
            days = listOf(
                Day(
                    routineName = "체지방 유지 · 가슴",
                    exercises = listOf(
                        "벤치프레스" to 4,
                        "인클라인 덤벨 프레스" to 3,
                        "케이블 크로스오버" to 3,
                        "어시스티드 딥스" to 3,
                        "와이드 푸시업" to 1,
                    ),
                    restSeconds = mapOf(
                        "벤치프레스" to 150,
                        "인클라인 덤벨 프레스" to 120,
                        "케이블 크로스오버" to 75,
                        "어시스티드 딥스" to 90,
                        "와이드 푸시업" to 60,
                    ),
                    scheduledDays = setOf(DayOfWeek.MONDAY),
                ),
                Day(
                    routineName = "체지방 유지 · 등",
                    exercises = listOf(
                        "풀업" to 4,
                        "바벨로우" to 3,
                        "시티드 케이블로우" to 3,
                        "와이드그립 랫풀다운" to 3,
                        "페이스풀" to 3,
                    ),
                    restSeconds = mapOf(
                        "풀업" to 120,
                        "바벨로우" to 150,
                        "시티드 케이블로우" to 90,
                        "와이드그립 랫풀다운" to 75,
                        "페이스풀" to 60,
                    ),
                    scheduledDays = setOf(DayOfWeek.TUESDAY),
                ),
                Day(
                    routineName = "체지방 유지 · 하체 + 어깨",
                    exercises = listOf(
                        "스쿼트" to 4,
                        "오버헤드 프레스" to 3,
                        "루마니안 데드리프트" to 3,
                        "사이드 레터럴 레이즈" to 3,
                        "레그프레스" to 2,
                        "카프레이즈" to 3,
                    ),
                    // 45분 안에 끝내려고 하체 뒤쪽과 어깨 옆쪽을 번갈아 한다.
                    supersets = listOf(listOf("루마니안 데드리프트", "사이드 레터럴 레이즈")),
                    restSeconds = mapOf(
                        "스쿼트" to 150,
                        "오버헤드 프레스" to 120,
                        "사이드 레터럴 레이즈" to 90,
                        "레그프레스" to 90,
                        "카프레이즈" to 45,
                    ),
                    scheduledDays = setOf(DayOfWeek.THURSDAY),
                ),
                Day(
                    routineName = "체지방 유지 · 팔 + 복근 (토)",
                    exercises = listOf(
                        "바벨 컬" to 3,
                        "케이블 푸시다운" to 3,
                        "해머 컬" to 2,
                        "벤치 딥스" to 2,
                        "행잉 레그레이즈" to 3,
                        "플랭크" to 3,
                    ),
                    supersets = listOf(
                        listOf("바벨 컬", "케이블 푸시다운"),
                        listOf("해머 컬", "벤치 딥스"),
                    ),
                    restSeconds = mapOf(
                        "케이블 푸시다운" to 60,
                        "벤치 딥스" to 60,
                        "행잉 레그레이즈" to 60,
                        "플랭크" to 45,
                    ),
                    scheduledDays = setOf(DayOfWeek.SATURDAY),
                ),
            ),
        ),
        Template(
            id = "split_4day_lower_upper_back",
            category = Category.SPLIT,
            title = "주4일 4분할 (하체 / 가슴·어깨·팔 A / 등 / 가슴·어깨·팔 B)",
            description = "화 하체, 목 가슴·어깨·팔 A, 토 등, 일 가슴·어깨·팔 B. 복합 운동은 휴식 2분, 보조 운동은 1~1.5분으로 맞췄습니다. " +
                "횟수 목표: 하체 스쿼트 6~8 · 루마니안 8 · 레그프레스 10~12 · 레그 컬 10~12 · 카프 12~15 / A 벤치 6~8 · 인클라인 8~10 · 숄더프레스 8~10 · " +
                "사이드 레이즈 12~15 · 푸시다운+컬 10~12 / 등 데드리프트 5 · 랫풀다운 8~10 · 로우 10 · 페이스풀 15 / " +
                "B 오버헤드 6~8 · 덤벨 벤치 8~10 · 크로스오버 12~15 · 리어 델트 12~15. " +
                "등 날 데드리프트가 부담되면 빼고 바벨로우 4세트(6~8회)를 넣으세요. 랫풀다운은 풀업으로 바꿔도 됩니다. " +
                "요일이 화·목·토·일로 정해져 홈의 오늘의 루틴에 나옵니다(루틴 편집에서 바꿀 수 있습니다).",
            days = listOf(
                Day(
                    routineName = "하체 (화)",
                    exercises = listOf(
                        "스쿼트" to 4,
                        "루마니안 데드리프트" to 3,
                        "레그프레스" to 3,
                        "레그 컬" to 3,
                        "카프레이즈" to 3,
                    ),
                    restSeconds = mapOf(
                        "스쿼트" to 120,
                        "루마니안 데드리프트" to 120,
                        "레그프레스" to 90,
                        "레그 컬" to 75,
                        "카프레이즈" to 60,
                    ),
                    scheduledDays = setOf(DayOfWeek.TUESDAY),
                ),
                Day(
                    routineName = "가슴·어깨·팔 A (목)",
                    exercises = listOf(
                        "벤치프레스" to 4,
                        "인클라인 덤벨 프레스" to 3,
                        "덤벨 숄더프레스" to 3,
                        "사이드 레터럴 레이즈" to 3,
                        "케이블 푸시다운" to 3,
                        "바벨 컬" to 3,
                    ),
                    supersets = listOf(listOf("케이블 푸시다운", "바벨 컬")),
                    restSeconds = mapOf(
                        "벤치프레스" to 120,
                        "인클라인 덤벨 프레스" to 120,
                        "덤벨 숄더프레스" to 120,
                        "사이드 레터럴 레이즈" to 60,
                        "바벨 컬" to 75,
                    ),
                    scheduledDays = setOf(DayOfWeek.THURSDAY),
                ),
                Day(
                    routineName = "등 (토)",
                    exercises = listOf(
                        "데드리프트" to 3,
                        "랫풀다운" to 4,
                        "시티드 케이블로우" to 3,
                        "덤벨로우" to 3,
                        "페이스풀" to 3,
                    ),
                    restSeconds = mapOf(
                        "데드리프트" to 120,
                        "랫풀다운" to 90,
                        "시티드 케이블로우" to 90,
                        "덤벨로우" to 90,
                        "페이스풀" to 60,
                    ),
                    scheduledDays = setOf(DayOfWeek.SATURDAY),
                ),
                Day(
                    routineName = "가슴·어깨·팔 B (일)",
                    exercises = listOf(
                        "오버헤드 프레스" to 4,
                        "덤벨 벤치프레스" to 3,
                        "케이블 크로스오버" to 3,
                        "리어 델트 플라이 머신" to 3,
                        "딥스" to 3,
                        "해머 컬" to 3,
                    ),
                    supersets = listOf(listOf("딥스", "해머 컬")),
                    restSeconds = mapOf(
                        "오버헤드 프레스" to 120,
                        "덤벨 벤치프레스" to 120,
                        "케이블 크로스오버" to 75,
                        "리어 델트 플라이 머신" to 60,
                        "해머 컬" to 75,
                    ),
                    scheduledDays = setOf(DayOfWeek.SUNDAY),
                ),
            ),
        ),
    )

    /** 갈래별로 묶은 목록(화면 순서). 템플릿이 없는 갈래는 뺀다. */
    val byCategory: List<Pair<Category, List<Template>>> =
        Category.entries.map { category -> category to all.filter { it.category == category } }
            .filter { it.second.isNotEmpty() }
}
