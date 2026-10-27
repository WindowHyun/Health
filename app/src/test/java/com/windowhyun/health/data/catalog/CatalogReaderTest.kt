package com.windowhyun.health.data.catalog

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.ui.gym.RoutineTemplates
import org.junit.Test
import java.time.DayOfWeek

/**
 * 종목 · 템플릿 파일 검증기. 앱에 포함된 파일과 사용자가 가져오는 외부 파일이 같은 규칙을 쓴다.
 * 손으로 쓴 파일을 한 번에 고칠 수 있게, 첫 문제에서 멈추지 않고 모든 문제를 경로와 함께 알려 줘야 한다.
 */
class CatalogReaderTest {

    // ----- 파일 만들기 -----

    private fun ex(name: String = "벤치프레스", category: String = "BARBELL", bodyPart: String = "CHEST", extra: String = "") =
        """{"name":"$name","category":"$category","bodyPart":"$bodyPart"$extra}"""

    private fun exercisesFile(vararg items: String) = """{"exercises":[${items.joinToString(",")}]}"""

    private fun line(name: String = "스쿼트", sets: String = "3", extra: String = "") =
        """{"name":"$name","sets":$sets$extra}"""

    private fun day(name: String = "하루", lines: List<String> = listOf(line()), extra: String = "") =
        """{"name":"$name"$extra,"exercises":[${lines.joinToString(",")}]}"""

    private fun tpl(
        id: String = "t",
        category: String = "SPLIT",
        title: String = "제목",
        description: String = "설명",
        days: List<String> = listOf(day()),
    ) = """{"id":"$id","category":"$category","title":"$title","description":"$description","days":[${days.joinToString(",")}]}"""

    private fun templatesFile(vararg items: String) = """{"templates":[${items.joinToString(",")}]}"""

    private fun read(json: String, context: ImportContext = ImportContext()) = CatalogReader.read(json, context)

    private fun CatalogReport.errorTexts(): List<String> = errors.map { it.toString() }

    /** 오류가 정확히 하나이고, 그 설명에 [fragments] 가 모두 들어 있다. */
    private fun assertOneError(report: CatalogReport, vararg fragments: String) {
        assertThat(report.errorTexts()).hasSize(1)
        val text = report.errors.single().toString()
        fragments.forEach { assertThat(text).contains(it) }
    }

    private fun assertContainsAll(text: String, vararg fragments: String) {
        fragments.forEach { assertThat(text).contains(it) }
    }

    private fun templateErrors(templateJson: String, context: ImportContext = ImportContext()) =
        read(templatesFile(templateJson), context)

    private val strict = ImportContext(
        limits = ImportLimits.EXTERNAL,
        checkExerciseReferences = true,
        markExercisesBuiltIn = false,
        knownExerciseNames = setOf("스쿼트", "벤치프레스", "레그 컬", "바벨 컬", "케이블 푸시다운", "딥스", "해머 컬"),
    )

    // ----- 파일 전체 -----

    @Test
    fun `reads a valid exercises file`() {
        val report = read(exercisesFile(ex("벤치프레스"), ex("플랭크", "BODYWEIGHT", "CORE", ""","trackingType":"TIME","defaultRestSeconds":45""")))

        assertThat(report.isValid).isTrue()
        assertThat(report.hasExercisesSection).isTrue()
        assertThat(report.hasTemplatesSection).isFalse()
        assertThat(report.exercises.map { it.name }).containsExactly("벤치프레스", "플랭크").inOrder()
        val plank = report.exercises.last()
        assertThat(plank.category).isEqualTo(ExerciseCategory.BODYWEIGHT)
        assertThat(plank.bodyPart).isEqualTo(BodyPart.CORE)
        assertThat(plank.trackingType).isEqualTo(ExerciseTrackingType.TIME)
        assertThat(plank.defaultRestSeconds).isEqualTo(45)
    }

    @Test
    fun `reads a valid templates file`() {
        val json = templatesFile(
            tpl(
                id = "mine", category = "BEGINNER", title = "내 분할", description = "설명입니다",
                days = listOf(
                    day(
                        "하체 (화)",
                        lines = listOf(
                            line("스쿼트", "4", ""","rest":120"""),
                            line("케이블 푸시다운", "3", ""","rest":60,"superset":"Z""""),
                            line("바벨 컬", "3", ""","rest":75,"superset":"Z""""),
                            line("딥스", "2", ""","superset":"A""""),
                            line("해머 컬", "2", ""","superset":"A""""),
                        ),
                        extra = ""","scheduledDays":["TUESDAY","SATURDAY"]""",
                    ),
                ),
            ),
        )

        val report = read(json)

        assertThat(report.isValid).isTrue()
        assertThat(report.hasTemplatesSection).isTrue()
        val t = report.templates.single()
        assertThat(t.id).isEqualTo("mine")
        assertThat(t.category).isEqualTo(RoutineTemplates.Category.BEGINNER)
        assertThat(t.title).isEqualTo("내 분할")
        val d = t.days.single()
        assertThat(d.routineName).isEqualTo("하체 (화)")
        assertThat(d.exercises).containsExactly("스쿼트" to 4, "케이블 푸시다운" to 3, "바벨 컬" to 3, "딥스" to 2, "해머 컬" to 2).inOrder()
        assertThat(d.restSeconds).containsExactly("스쿼트", 120, "케이블 푸시다운", 60, "바벨 컬", 75)
        // 묶음 번호는 라벨의 알파벳순이 아니라 처음 나온 순서다.
        assertThat(d.supersets).containsExactly(listOf("케이블 푸시다운", "바벨 컬"), listOf("딥스", "해머 컬")).inOrder()
        assertThat(d.scheduledDays).containsExactly(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY)
    }

    @Test
    fun `a file may hold exercises and templates together`() {
        val json = """{"version":1,"exercises":[${ex("새 운동")}],"templates":[${tpl(days = listOf(day(lines = listOf(line("새 운동")))))}]}"""

        val report = read(json, strict)

        assertThat(report.isValid).isTrue()
        assertThat(report.exercises.map { it.name }).containsExactly("새 운동")
        assertThat(report.templates).hasSize(1)
    }

    @Test
    fun `an empty object has nothing to import`() {
        assertOneError(read("{}"), "가져올 내용이 없습니다")
    }

    @Test
    fun `the root must be an object`() {
        assertOneError(read("[]"), "객체")
        assertOneError(read("\"text\""), "객체")
        assertOneError(read("12"), "객체")
    }

    @Test
    fun `unknown top level key is rejected with a suggestion`() {
        val report = read("""{"templats":[]}""")

        assertThat(report.errorTexts().joinToString("\n")).contains("templats")
        assertThat(report.errorTexts().joinToString("\n")).contains("'templates' 를 쓰려던 건가요")
    }

    @Test
    fun `format version one or none is accepted and others are not`() {
        assertThat(read("""{"version":1,"exercises":[]}""").isValid).isTrue()
        assertThat(read("""{"exercises":[]}""").isValid).isTrue()

        assertOneError(read("""{"version":2,"exercises":[]}"""), "version", "형식 버전", "2")
        assertOneError(read("""{"version":"1","exercises":[]}"""), "version", "숫자")
        assertOneError(read("""{"version":0,"exercises":[]}"""), "version", "형식 버전")
    }

    @Test
    fun `null sections count as missing`() {
        assertOneError(read("""{"exercises":null}"""), "exercises", "필수")
    }

    // ----- JSON 문법 · 크기 -----

    @Test
    fun `a syntax error tells the line and column`() {
        val report = read("{\n  \"exercises\": [\n    {,}\n  ]\n}")

        assertOneError(report, "JSON 문법", "3행")
    }

    @Test
    fun `garbage is a syntax error and never a crash`() {
        listOf("", "   ", "{", "}", "{\"a\":", "not json", "{'a':1}", "{\"exercises\":[1,]}", "\u0000").forEach { text ->
            val report = read(text)
            assertThat(report.isValid).isFalse()
            assertThat(report.errors).isNotEmpty()
        }
    }

    @Test
    fun `nesting deeper than the limit is refused before parsing`() {
        val report = read("[".repeat(9) + "]".repeat(9))

        assertOneError(report, "중첩", "8단계")
    }

    @Test
    fun `nesting exactly at the limit passes the depth check`() {
        val report = read("""{"a":[[[[[[[1]]]]]]]}""")

        assertThat(report.errorTexts().joinToString("\n")).doesNotContain("중첩")
    }

    /** 글 속의 괄호와 따옴표 이스케이프는 깊이로 세지 않는다. */
    @Test
    fun `brackets inside strings do not count as nesting`() {
        val description = """a \" ${"[".repeat(30)} \\ ${"{".repeat(30)}"""
        val report = read(templatesFile(tpl(description = description)))

        assertThat(report.isValid).isTrue()
        assertThat(report.templates.single().description).startsWith("a \" [[[")
    }

    @Test
    fun `an extremely deep file does not overflow the stack`() {
        val report = read("[".repeat(200_000))

        assertThat(report.isValid).isFalse()
    }

    // ----- 글 -----

    @Test
    fun `blank and padded names are rejected`() {
        assertOneError(read(exercisesFile(ex(name = "  "))), "exercises[0]", "name", "비어")
        assertOneError(read(exercisesFile(ex(name = " 벤치프레스"))), "name", "앞이나 뒤에 공백")
        assertOneError(read(exercisesFile(ex(name = "벤치프레스 "))), "name", "앞이나 뒤에 공백")
    }

    @Test
    fun `an inner space is part of the name and is kept`() {
        assertThat(read(exercisesFile(ex(name = "레그 컬"))).exercises.single().name).isEqualTo("레그 컬")
    }

    @Test
    fun `name length is limited`() {
        assertThat(read(exercisesFile(ex(name = "가".repeat(40)))).isValid).isTrue()
        assertOneError(read(exercisesFile(ex(name = "가".repeat(41)))), "41자", "40자까지")
    }

    @Test
    fun `title description and id limits`() {
        assertThat(read(templatesFile(tpl(title = "가".repeat(60)))).isValid).isTrue()
        assertOneError(templateErrors(tpl(title = "가".repeat(61))), "title", "61자")
        assertThat(read(templatesFile(tpl(description = "가".repeat(2000)))).isValid).isTrue()
        assertOneError(templateErrors(tpl(description = "가".repeat(2001))), "description", "2001자")
        assertThat(read(templatesFile(tpl(id = "a".repeat(40)))).isValid).isTrue()
        assertOneError(templateErrors(tpl(id = "a".repeat(41))), "id", "41자")
    }

    @Test
    fun `template id accepts only ascii letters digits underscore and hyphen`() {
        listOf("my_split-2", "5x5", "PPL").forEach { assertThat(read(templatesFile(tpl(id = it))).isValid).isTrue() }
        listOf("내 템플릿", "a b", "a.b", "a/b", "한글").forEach {
            assertOneError(templateErrors(tpl(id = it)), "id", "영문자")
        }
    }

    @Test
    fun `control characters are refused`() {
        assertOneError(read(exercisesFile(ex(name = """바\u0007벨"""))), "name", "쓸 수 없는 문자", "U+0007")
        assertOneError(read(exercisesFile(ex(name = """바\u0000벨"""))), "U+0000")
        // 이름에는 줄바꿈 · 탭도 못 쓴다.
        assertOneError(read(exercisesFile(ex(name = """바\n벨"""))), "U+000A")
        assertOneError(read(exercisesFile(ex(name = """바\tベル"""))), "U+0009")
    }

    @Test
    fun `characters that reverse or hide text are refused`() {
        listOf("061C", "200E", "200F", "202A", "202B", "202C", "202D", "202E", "2066", "2067", "2068", "2069", "2028", "2029", "FEFF").forEach { code ->
            val report = read(exercisesFile(ex(name = "바\\u$code" + "벨")))
            assertOneError(report, "쓸 수 없는 문자", "U+$code")
        }
    }

    /** 위험한 문자를 메시지로 되돌려 보여 주면 화면의 글을 속일 수 있다. */
    @Test
    fun `dangerous characters are never echoed back in messages`() {
        val report = read(exercisesFile(ex(name = """바\u202E벨""", category = "BARBEL\\u0007")))

        val everything = report.issues.joinToString("\n") { it.toString() }
        assertThat(everything).doesNotContain("\u202E")
        assertThat(everything).doesNotContain("\u0007")
    }

    @Test
    fun `unpaired surrogates are refused but emoji pairs are fine`() {
        assertOneError(read(exercisesFile(ex(name = """바\ud800벨"""))), "쓸 수 없는 문자")
        assertOneError(read(exercisesFile(ex(name = """바\udc00벨"""))), "쓸 수 없는 문자")
        assertThat(read(exercisesFile(ex(name = """바😀벨"""))).isValid).isTrue()
    }

    @Test
    fun `text is normalized so a decomposed hangul name still matches`() {
        // "한" 을 자모로 풀어 쓴 형태(맥에서 만든 글에서 나올 수 있다).
        val report = read(exercisesFile(ex(name = """한""")))

        assertThat(report.exercises.single().name).isEqualTo("한")
    }

    @Test
    fun `description keeps line breaks but not other control characters`() {
        val ok = read(templatesFile(tpl(description = """첫 줄\r\n둘째 줄\r셋째\t줄""")))
        assertThat(ok.isValid).isTrue()
        assertThat(ok.templates.single().description).isEqualTo("첫 줄\n둘째 줄\n셋째\t줄")

        assertOneError(templateErrors(tpl(description = """a\u0007b""")), "description", "U+0007")
    }

    @Test
    fun `text fields must be strings`() {
        assertOneError(read("""{"exercises":[{"name":5,"category":"BARBELL","bodyPart":"CHEST"}]}"""), "name", "글")
        assertOneError(read("""{"exercises":[{"name":["a"],"category":"BARBELL","bodyPart":"CHEST"}]}"""), "name", "글")
        assertOneError(read("""{"exercises":[{"name":true,"category":"BARBELL","bodyPart":"CHEST"}]}"""), "name", "글")
    }

    @Test
    fun `a syntax error tells the column too`() {
        // 3행: 공백 네 칸 뒤에 "{,}" — 쉼표는 6열이다.
        val report = read("{\n  \"exercises\": [\n    {,}\n  ]\n}")

        assertOneError(report, "3행 6열")
    }

    @Test
    fun `a description may start or end with white space and keeps it`() {
        val report = read(templatesFile(tpl(description = """\n 앞뒤에 공백과 줄바꿈 \n""")))

        assertThat(report.isValid).isTrue()
        assertThat(report.templates.single().description).isEqualTo("\n 앞뒤에 공백과 줄바꿈 \n")
    }

    @Test
    fun `a template that replaces an imported one does not clash with its old routine names`() {
        val context = ImportContext(
            replaceableTemplateIds = setOf("mine"),
            otherRoutineNames = mapOf("mine" to setOf("하루"), "other" to setOf("남의 루틴")),
        )

        val same = templateErrors(tpl(id = "mine", days = listOf(day("하루"))), context)
        val clash = templateErrors(tpl(id = "mine", days = listOf(day("남의 루틴"))), context)

        assertThat(same.warnings.map { it.toString() }.none { "다른 템플릿" in it }).isTrue()
        assertThat(clash.warnings.map { it.toString() }.any { "다른 템플릿" in it }).isTrue()
    }

    /** 비슷한 이름 찾기는 비싸서 처음 몇 개 이름에만 한다. 오류 자체는 모두 알린다. */
    @Test
    fun `suggestions are looked up only for the first twenty unknown names`() {
        val names = (1..25).map { "스쿼트$it" }
        val lines = names.map { line(it) }
        val report = templateErrors(tpl(days = listOf(day(lines = lines))), strict)

        assertThat(report.errors).hasSize(25)
        assertThat(report.errors.count { "를 쓰려던 건가요" in it.message }).isEqualTo(20)
    }

    @Test
    fun `a long name is shortened where it is echoed`() {
        val report = read(exercisesFile(ex(name = "가".repeat(100))))

        val path = report.errors.single().path
        assertThat(path).contains("…")
        assertThat(path).doesNotContain("가".repeat(41))
    }

    // ----- 숫자 · 정해진 값 -----

    @Test
    fun `sets must be an integer between 1 and 20`() {
        assertThat(read(templatesFile(tpl(days = listOf(day(lines = listOf(line(sets = "1"))))))).isValid).isTrue()
        assertThat(read(templatesFile(tpl(days = listOf(day(lines = listOf(line(sets = "20"))))))).isValid).isTrue()

        fun bad(value: String, vararg fragments: String) =
            assertOneError(templateErrors(tpl(days = listOf(day(lines = listOf(line(sets = value)))))), "sets", *fragments)
        bad("0", "1~20", "지금 0")
        bad("21", "1~20", "지금 21")
        bad("-1", "1~20")
        bad("3.5", "정수")
        bad("\"3\"", "숫자")
        bad("true", "정수")
        bad("99999999999", "정수")
        bad("null", "필수")
    }

    @Test
    fun `rest must be between 0 and 600 seconds and may be left out`() {
        fun rest(value: String) = read(templatesFile(tpl(days = listOf(day(lines = listOf(line(extra = ""","rest":$value""")))))))
        assertThat(rest("0").isValid).isTrue() // 슈퍼셋의 앞 종목: 쉬지 않음
        assertThat(rest("5").isValid).isTrue()
        assertThat(rest("600").isValid).isTrue()
        assertThat(rest("null").isValid).isTrue()
        assertOneError(rest("-1"), "rest", "0~600")
        assertOneError(rest("601"), "rest", "0~600")
        assertOneError(rest("\"60\""), "rest", "숫자")
    }

    @Test
    fun `default rest of an exercise is limited in the same way`() {
        assertThat(read(exercisesFile(ex(extra = ""","defaultRestSeconds":5"""))).isValid).isTrue()
        assertThat(read(exercisesFile(ex(extra = ""","defaultRestSeconds":600"""))).isValid).isTrue()
        assertOneError(read(exercisesFile(ex(extra = ""","defaultRestSeconds":4"""))), "defaultRestSeconds", "5~600")
        assertOneError(read(exercisesFile(ex(extra = ""","defaultRestSeconds":601"""))), "defaultRestSeconds", "5~600")
    }

    @Test
    fun `a wrong enum value lists the valid ones and suggests the nearest`() {
        val category = read(exercisesFile(ex(category = "BARBEL")))
        assertOneError(category, "category", "'BARBEL'", "'BARBELL' 를 쓰려던 건가요", "DUMBBELL")

        val bodyPart = read(exercisesFile(ex(bodyPart = "CHST")))
        assertOneError(bodyPart, "bodyPart", "'CHEST' 를 쓰려던 건가요")

        val tracking = read(exercisesFile(ex(extra = ""","trackingType":"WEIGHT"""")))
        assertOneError(tracking, "trackingType", "WEIGHT_REPS")
    }

    @Test
    fun `an enum value in the wrong case is not accepted`() {
        assertOneError(read(exercisesFile(ex(category = "barbell"))), "category", "'BARBELL' 를 쓰려던 건가요")
    }

    @Test
    fun `a missing required field is named`() {
        assertOneError(read("""{"exercises":[{"name":"벤치프레스","category":"BARBELL"}]}"""), "bodyPart", "필수")
        assertOneError(read("""{"exercises":[{"category":"BARBELL","bodyPart":"CHEST"}]}"""), "exercises[0]", "name", "필수")
    }

    // ----- 키 · 모양 -----

    @Test
    fun `a misspelled key is an error with a suggestion instead of being ignored`() {
        val report = read("""{"exercises":[{"name":"벤치프레스","categroy":"BARBELL","category":"BARBELL","bodyPart":"CHEST"}]}""")

        assertOneError(report, "exercises[0] '벤치프레스'", "'categroy'", "'category' 를 쓰려던 건가요")
    }

    @Test
    fun `a typo in rest does not silently drop the rest`() {
        val report = read(templatesFile(tpl(days = listOf(day(lines = listOf(line(extra = ""","reset":120""")))))))

        assertOneError(report, "'reset'", "'rest' 를 쓰려던 건가요")
    }

    @Test
    fun `sections must be arrays and entries must be objects`() {
        assertOneError(read("""{"exercises":{}}"""), "exercises", "배열")
        assertOneError(read("""{"templates":"x"}"""), "templates", "배열")
        assertOneError(read("""{"exercises":[5]}"""), "exercises[0]", "객체")
        assertOneError(read(templatesFile("[]")), "templates[0]", "객체")
        assertOneError(read(templatesFile(tpl(days = listOf("1")))), "days[0]", "객체")
        assertOneError(read(templatesFile(tpl(days = listOf(day(lines = listOf("\"스쿼트\"")))))), "exercises[0]", "객체")
    }

    @Test
    fun `counts are limited`() {
        val small = ImportContext(limits = ImportLimits.EXTERNAL.copy(maxExercises = 2, maxTemplates = 1, maxDaysPerTemplate = 1, maxItemsPerDay = 1))

        assertThat(read(exercisesFile(ex("가"), ex("나")), small).isValid).isTrue()
        assertOneError(read(exercisesFile(ex("가"), ex("나"), ex("다")), small), "exercises", "3개", "2개까지")
        assertOneError(read(templatesFile(tpl(id = "a", days = listOf(day("가"))), tpl(id = "b", days = listOf(day("나")))), small), "templates", "2개", "1개까지")
        assertOneError(read(templatesFile(tpl(days = listOf(day("가"), day("나")))), small), "days", "2개", "1개까지")
        assertOneError(read(templatesFile(tpl(days = listOf(day(lines = listOf(line("가"), line("나")))))), small), "exercises", "2개", "1개까지")
    }

    @Test
    fun `the external limits are the documented ones`() {
        val limits = ImportLimits.EXTERNAL
        assertThat(limits.maxBytes).isEqualTo(256 * 1024)
        assertThat(limits.maxDepth).isEqualTo(8)
        assertThat(limits.maxExercises).isEqualTo(100)
        assertThat(limits.maxTemplates).isEqualTo(30)
        assertThat(limits.maxDaysPerTemplate).isEqualTo(14)
        assertThat(limits.maxItemsPerDay).isEqualTo(30)
    }

    // ----- 종목 -----

    @Test
    fun `duplicate exercise names in a file are rejected`() {
        assertOneError(read(exercisesFile(ex("스쿼트"), ex("벤치프레스"), ex("스쿼트"))), "exercises[2] '스쿼트'", "두 번")
    }

    @Test
    fun `an exercise that already exists is skipped with a warning`() {
        val report = read(exercisesFile(ex("스쿼트"), ex("새 운동")), strict)

        assertThat(report.isValid).isTrue()
        assertThat(report.exercises.map { it.name }).containsExactly("새 운동")
        assertContainsAll(report.warnings.single().toString(), "스쿼트", "이미 있는 종목")
    }

    @Test
    fun `imported exercises are custom and bundled ones are built in`() {
        assertThat(read(exercisesFile(ex("새 운동")), strict).exercises.single().isBuiltIn).isFalse()
        assertThat(read(exercisesFile(ex("새 운동"))).exercises.single().isBuiltIn).isTrue()
    }

    @Test
    fun `an invalid exercise is left out but the valid ones are kept`() {
        val report = read(exercisesFile(ex("좋은 운동"), ex("나쁜 운동", category = "NOPE")))

        assertThat(report.isValid).isFalse()
        assertThat(report.exercises.map { it.name }).containsExactly("좋은 운동")
    }

    // ----- 템플릿 -----

    @Test
    fun `template id must be unique and not taken by a bundled template`() {
        assertOneError(read(templatesFile(tpl(id = "a", days = listOf(day("가"))), tpl(id = "a", days = listOf(day("나"))))), "templates[1] 'a'", "두 번")

        val context = ImportContext(reservedTemplateIds = setOf("ppl"))
        assertOneError(templateErrors(tpl(id = "ppl"), context), "'ppl'", "앱에 포함된 템플릿")
    }

    @Test
    fun `an already imported template id is replaced with a warning`() {
        val report = templateErrors(tpl(id = "mine"), ImportContext(replaceableTemplateIds = setOf("mine")))

        assertThat(report.isValid).isTrue()
        assertThat(report.templates).hasSize(1)
        assertContainsAll(report.warnings.single().toString(), "'mine'", "바꿉니다")
    }

    @Test
    fun `a template needs a title a description and days`() {
        assertOneError(read("""{"templates":[{"id":"t","category":"SPLIT","title":"제목","description":"설명"}]}"""), "days", "필수")
        assertOneError(read("""{"templates":[{"id":"t","category":"SPLIT","title":"제목","description":"설명","days":[]}]}"""), "days", "하루도 없습니다")
        assertOneError(templateErrors(tpl(title = " ")), "title", "비어")
        assertOneError(templateErrors(tpl(description = " ")), "description", "비어")
        assertOneError(templateErrors(tpl(category = "NOPE")), "category", "BEGINNER")
    }

    @Test
    fun `a day needs a name and exercises`() {
        assertOneError(templateErrors(tpl(days = listOf(day(" ")))), "days[0]", "name", "비어")
        assertOneError(templateErrors(tpl(days = listOf("""{"name":"하루","exercises":[]}"""))), "exercises", "하나도 없습니다")
        assertOneError(templateErrors(tpl(days = listOf("""{"name":"하루"}"""))), "exercises", "필수")
    }

    @Test
    fun `routine names must be unique inside a file`() {
        assertOneError(templateErrors(tpl(days = listOf(day("같은"), day("같은")))), "days[1] '같은'", "겹칩니다")
        assertOneError(
            read(templatesFile(tpl(id = "a", days = listOf(day("같은"))), tpl(id = "b", days = listOf(day("같은"))))),
            "templates[1]", "겹칩니다",
        )
    }

    @Test
    fun `a routine name that another template already has only warns`() {
        val report = templateErrors(tpl(days = listOf(day("푸시 (PPL)"))), ImportContext(otherRoutineNames = mapOf("ppl" to setOf("푸시 (PPL)"))))

        assertThat(report.isValid).isTrue()
        assertContainsAll(report.warnings.single().toString(), "푸시 (PPL)", "다른 템플릿")
    }

    @Test
    fun `a day may not repeat an exercise`() {
        val report = templateErrors(tpl(days = listOf(day(lines = listOf(line("스쿼트"), line("벤치프레스"), line("스쿼트"))))))

        assertOneError(report, "exercises[2] '스쿼트'", "두 번")
    }

    @Test
    fun `scheduled days are validated`() {
        fun days(value: String) = templateErrors(tpl(days = listOf(day(extra = ""","scheduledDays":$value"""))))
        assertThat(days("[]").isValid).isTrue()
        assertThat(days("null").isValid).isTrue()
        assertOneError(days("""["TUESDYA"]"""), "scheduledDays[0]", "'TUESDAY' 를 쓰려던 건가요")
        assertOneError(days("""["MONDAY","MONDAY"]"""), "scheduledDays[1]", "두 번")
        assertOneError(days("[1]"), "scheduledDays[0]", "요일 이름")
        assertOneError(days("\"MONDAY\""), "scheduledDays", "배열")
        assertOneError(days("""["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY","MONDAY"]"""), "scheduledDays", "8개", "7개까지")
    }

    // ----- 슈퍼셋 -----

    private fun supersetDay(vararg labels: String?) = day(
        lines = labels.mapIndexed { i, label ->
            line("종목${i + 1}", extra = label?.let { ""","superset":"$it"""" }.orEmpty())
        },
    )

    @Test
    fun `adjacent exercises with the same label form a superset and two groups may follow each other`() {
        val report = read(templatesFile(tpl(days = listOf(supersetDay(null, "A", "A", "B", "B", "B", null)))))

        assertThat(report.isValid).isTrue()
        assertThat(report.templates.single().days.single().supersets)
            .containsExactly(listOf("종목2", "종목3"), listOf("종목4", "종목5", "종목6")).inOrder()
    }

    @Test
    fun `a superset needs at least two exercises`() {
        assertOneError(templateErrors(tpl(days = listOf(supersetDay(null, "A", null)))), "superset 'A'", "하나뿐")
    }

    @Test
    fun `superset exercises must be next to each other`() {
        assertOneError(templateErrors(tpl(days = listOf(supersetDay("A", null, "A")))), "superset 'A'", "이어서")

        // 두 묶음이 서로 끼어 있으면 둘 다 문제다.
        val crossed = templateErrors(tpl(days = listOf(supersetDay("A", "B", "A", "B"))))
        assertThat(crossed.errors).hasSize(2)
        assertThat(crossed.errorTexts().all { "이어서" in it }).isTrue()
    }

    @Test
    fun `a superset label is a non blank short text`() {
        val blank = templateErrors(tpl(days = listOf(supersetDay(" ", " "))))
        assertThat(blank.errors).hasSize(2)
        assertThat(blank.errorTexts().all { "superset" in it && "비어" in it }).isTrue()

        assertThat(read(templatesFile(tpl(days = listOf(supersetDay("A".repeat(8), "A".repeat(8)))))).isValid).isTrue()
        val long = templateErrors(tpl(days = listOf(supersetDay("A".repeat(9), "A".repeat(9)))))
        assertThat(long.errors).hasSize(2)
        assertThat(long.errorTexts().all { "9자" in it && "8자까지" in it }).isTrue()
    }

    // ----- 종목 이름 확인(외부 파일) -----

    @Test
    fun `an unknown exercise is an error with the nearest known name`() {
        val report = templateErrors(tpl(days = listOf(day(lines = listOf(line("레그컬"))))), strict)

        assertOneError(report, "exercises[0] '레그컬' › name", "'레그컬' 종목이 없습니다", "'레그 컬' 를 쓰려던 건가요")
    }

    @Test
    fun `the new exercise hint is given only once`() {
        val report = templateErrors(tpl(days = listOf(day(lines = listOf(line("없는 가"), line("없는 나"), line("없는 다"))))), strict)

        assertThat(report.errors).hasSize(3)
        assertThat(report.errors.count { "exercises 에 함께" in it.message }).isEqualTo(1)
    }

    @Test
    fun `an exercise defined in the same file counts as known`() {
        val json = """{"exercises":[${ex("새 운동")}],"templates":[${tpl(days = listOf(day(lines = listOf(line("새 운동")))))}]}"""

        assertThat(read(json, strict).isValid).isTrue()
    }

    @Test
    fun `names are not checked when the context does not ask for it`() {
        assertThat(templateErrors(tpl(days = listOf(day(lines = listOf(line("어딘가의 종목")))))).isValid).isTrue()
    }

    @Test
    fun `a skipped existing exercise still counts as defined`() {
        val json = """{"exercises":[${ex("스쿼트")}],"templates":[${tpl(days = listOf(day(lines = listOf(line("스쿼트")))))}]}"""

        val report = read(json, strict)

        assertThat(report.isValid).isTrue()
        assertThat(report.exercises).isEmpty()
    }

    // ----- 모아서 알려 주기 -----

    @Test
    fun `all problems are reported in one pass with their paths`() {
        val json = templatesFile(
            tpl(
                id = "mine",
                days = listOf(
                    day("첫째", lines = listOf(line("스쿼트", sets = "0"), line("벤치프레스", extra = ""","rest":601"""))),
                    day("둘째", lines = listOf(line("딥스", extra = ""","superset":"A""""), line("해머 컬"))),
                ),
            ),
        )

        val texts = read(json, strict).errorTexts()

        assertThat(texts).hasSize(3)
        assertThat(texts[0]).startsWith("templates[0] 'mine' › days[0] '첫째' › exercises[0] '스쿼트' › sets")
        assertThat(texts[1]).startsWith("templates[0] 'mine' › days[0] '첫째' › exercises[1] '벤치프레스' › rest")
        assertThat(texts[2]).startsWith("templates[0] 'mine' › days[1] '둘째' › superset 'A'")
    }

    @Test
    fun `only fully valid templates are returned`() {
        val report = read(templatesFile(tpl(id = "good", days = listOf(day("좋은"))), tpl(id = "bad", days = listOf(day("나쁜", lines = listOf(line(sets = "0")))))))

        assertThat(report.isValid).isFalse()
        assertThat(report.templates.map { it.id }).containsExactly("good")
    }

    /**
     * 오류가 있으면 만들 수 있는 항목도 받아들이지 않는다. 필드 하나가 비는 오류는 항목이 못 만들어져 저절로 빠지지만,
     * 모르는 키 · 겹침처럼 만들 수는 있는 오류는 이 규칙이 막는다.
     */
    @Test
    fun `an item with an error is left out even when it could be built`() {
        val typo = """{"id":"typo","category":"SPLIT","title":"t","description":"d","extra":1,"days":[${day("오타 하루")}]}"""
        val dup = tpl(id = "dup", days = listOf(day("겹침 하루", lines = listOf(line("스쿼트"), line("스쿼트")))))
        val crossed = tpl(id = "crossed", days = listOf(supersetDay("A", null, "A").replace("\"하루\"", "\"끼임 하루\"")))

        val report = read(templatesFile(tpl(id = "good", days = listOf(day("좋은"))), typo, dup, crossed))

        assertThat(report.isValid).isFalse()
        assertThat(report.errors).hasSize(3)
        assertThat(report.templates.map { it.id }).containsExactly("good")
    }

    @Test
    fun `an exercise with an unknown key is left out`() {
        val report = read(exercisesFile(ex("좋은 운동"), ex("오타 운동", extra = ""","memo":"x"""")))

        assertThat(report.isValid).isFalse()
        assertThat(report.exercises.map { it.name }).containsExactly("좋은 운동")
    }

    @Test
    fun `too many problems are summarised`() {
        val context = ImportContext(limits = ImportLimits.EXTERNAL.copy(maxIssues = 3))
        val items = (1..6).map { ex(name = "운동$it", category = "NOPE") }

        val report = read(exercisesFile(*items.toTypedArray()), context)

        assertThat(report.issues).hasSize(4)
        assertThat(report.issues.last().isError).isTrue()
        assertThat(report.issues.last().message).contains("오류 3개가 더 있습니다")
        assertThat(report.isValid).isFalse()
    }

    @Test
    fun `too many warnings are summarised as a warning`() {
        val context = ImportContext(
            limits = ImportLimits.EXTERNAL.copy(maxIssues = 2),
            knownExerciseNames = setOf("가", "나", "다", "라"),
        )

        val report = read(exercisesFile(ex("가"), ex("나"), ex("다"), ex("라")), context)

        assertThat(report.isValid).isTrue()
        assertThat(report.issues).hasSize(3)
        assertThat(report.issues.last().severity).isEqualTo(IssueSeverity.WARNING)
        assertThat(report.issues.last().message).contains("알림 2개가 더 있습니다")
    }

    @Test
    fun `requireValid throws with every error and the source name`() {
        val report = read(exercisesFile(ex(category = "NOPE"), ex(name = "둘", bodyPart = "NOPE")))

        val e = runCatching { report.requireValid("my.json") }.exceptionOrNull() as CatalogFormatException

        assertThat(e.message).startsWith("my.json: 문제 2개")
        assertThat(e.message).contains("category")
        assertThat(e.message).contains("bodyPart")
        assertThat(e.issues).hasSize(2)
    }

    @Test
    fun `requireValid returns the report when it is valid`() {
        val report = read(exercisesFile(ex()))

        assertThat(report.requireValid("x")).isSameInstanceAs(report)
    }

    @Test
    fun `warnings alone never make a file invalid`() {
        val report = read(exercisesFile(ex("스쿼트")), strict)

        assertThat(report.issues).hasSize(1)
        assertThat(report.isValid).isTrue()
        assertThat(report.requireValid("x")).isSameInstanceAs(report)
    }

    @Test
    fun `issue text joins path and message`() {
        assertThat(CatalogIssue(IssueSeverity.ERROR, "a › b", "문제").toString()).isEqualTo("a › b: 문제")
        assertThat(CatalogIssue(IssueSeverity.ERROR, "", "문제").toString()).isEqualTo("문제")
    }

    // ----- 보이지 않는 문자(유니코드 분류) · 저장된 글 · 한도 -----

    private fun cp(code: Int) = String(Character.toChars(code))

    private val stored = ImportContext.STORED

    /** 목록에 적은 몇 개가 아니라 분류로 거른다: 폭이 0인 공백 · 소프트 하이픈 · 태그 · 사설 영역 · 한글 채움 문자 등. */
    @Test
    fun `invisible characters are refused by category`() {
        listOf(
            0x200B, 0x200C, 0x200D, 0x2060, 0x00AD, 0x180E, 0xE0001, 0xE007F, 0xE000,
            0x3164, 0x115F, 0x1160, 0xFFA0, 0x2800, 0x17B4, 0x17B5,
        ).forEach { code ->
            val report = read(exercisesFile(ex(name = "스쿼트" + cp(code))))
            assertOneError(report, "쓸 수 없는 문자", "U+" + code.toString(16).uppercase().padStart(4, '0'))
        }
    }

    @Test
    fun `a name made only of a blank looking character is refused`() {
        assertOneError(read(exercisesFile(ex(name = cp(0x3164)))), "쓸 수 없는 문자")
        assertOneError(read(exercisesFile(ex(name = cp(0x200B)))), "쓸 수 없는 문자")
    }

    /** 같아 보이는데 이름이 달라지는 공백은 거절한다(보통 공백 U+0020 만 쓴다). */
    @Test
    fun `spaces other than the plain space are refused inside a name`() {
        listOf(0x00A0, 0x3000, 0x2003, 0x202F).forEach { code ->
            val report = read(exercisesFile(ex(name = "벤치" + cp(code) + "프레스")))
            assertOneError(report, "쓸 수 없는 문자", "U+" + code.toString(16).uppercase().padStart(4, '0'))
        }
        assertThat(read(exercisesFile(ex(name = "벤치 프레스"))).isValid).isTrue()
    }

    @Test
    fun `a description may join emoji but a name may not and zero width spaces are refused everywhere`() {
        val family = cp(0x1F468) + cp(0x200D) + cp(0x1F469) + cp(0x200D) + cp(0x1F467)

        assertThat(read(templatesFile(tpl(description = "가족 $family"))).isValid).isTrue()
        assertOneError(read(exercisesFile(ex(name = "가족 $family"))), "U+200D")
        assertOneError(templateErrors(tpl(description = "설명" + cp(0x200B))), "description", "U+200B")
    }

    @Test
    fun `ordinary text with emoji punctuation and hangul is not refused`() {
        val name = "벤치프레스 · 변형 (A) – 😀 ① ㉠ ×2"

        assertThat(read(exercisesFile(ex(name = name))).exercises.single().name).isEqualTo(name)
    }

    @Test
    fun `stored files may repeat a routine name across templates but not inside one`() {
        val across = templatesFile(tpl(id = "a", days = listOf(day("같은"))), tpl(id = "b", days = listOf(day("같은"))))

        assertThat(read(across, stored).isValid).isTrue()
        assertThat(read(across, stored).templates.map { it.id }).containsExactly("a", "b").inOrder()
        assertOneError(read(templatesFile(tpl(days = listOf(day("같은"), day("같은")))), stored), "겹칩니다")
        // 가져오기 규칙으로는 여전히 오류다.
        assertThat(read(across).isValid).isFalse()
    }

    /** 옛 버전이 받아들여 저장한 글을 새 글자 규칙으로 거절하면 저장해 둔 템플릿이 통째로 읽히지 않는다. */
    @Test
    fun `stored files may hold text an older version accepted`() {
        val old = templatesFile(tpl(title = "제목" + cp(0x200B), description = "설명" + cp(0x3164)))

        assertThat(read(old, stored).isValid).isTrue()
        assertThat(read(old, stored).templates.single().title).isEqualTo("제목" + cp(0x200B))
        assertThat(read(old).isValid).isFalse()
    }

    @Test
    fun `stored reading still refuses a broken structure`() {
        assertThat(read("{", stored).isValid).isFalse()
        assertThat(read("""{"templates":[{"id":"x"}]}""", stored).isValid).isFalse()
    }

    @Test
    fun `a file written by the writer reads back identically with the stored rules`() {
        val two = listOf(
            RoutineTemplates.Template("a", RoutineTemplates.Category.SPLIT, "제목", "설명", listOf(RoutineTemplates.Day("같은", listOf("스쿼트" to 3)))),
            RoutineTemplates.Template("b", RoutineTemplates.Category.SPLIT, "제목", "설명", listOf(RoutineTemplates.Day("같은", listOf("스쿼트" to 3)))),
        )

        assertThat(read(CatalogWriter.write(two), stored).templates).isEqualTo(two)
    }

    /** 경고가 먼저 많이 나와도 뒤의 진짜 오류가 숫자로만 남지 않는다. */
    @Test
    fun `warnings never push real errors out of the list`() {
        val known = (1..60).map { "종목$it" }.toSet()
        val items = (known.map { ex(it) } + ex("진짜 오류", category = "NOPE")).toTypedArray()

        val report = read(exercisesFile(*items), ImportContext(limits = ImportLimits.EXTERNAL, knownExerciseNames = known))

        assertThat(report.isValid).isFalse()
        val listed = report.errors.filter { !it.message.startsWith("그 밖에") }
        assertThat(listed).hasSize(1)
        assertThat(listed.single().toString()).contains("category")
        assertThat(report.warnings).hasSize(51)
        assertThat(report.warnings.last().message).contains("알림 10개가 더 있습니다")
    }

    @Test
    fun `errors are listed before warnings`() {
        val report = read(
            exercisesFile(ex("스쿼트"), ex("오류", category = "NOPE")),
            ImportContext(knownExerciseNames = setOf("스쿼트")),
        )

        assertThat(report.issues.map { it.severity }).containsExactly(IssueSeverity.ERROR, IssueSeverity.WARNING).inOrder()
    }

    @Test
    fun `errors and warnings each have their own limit`() {
        val context = ImportContext(limits = ImportLimits.EXTERNAL.copy(maxIssues = 2), knownExerciseNames = setOf("가", "나", "다"))
        val items = listOf(ex("가"), ex("나"), ex("다"), ex("x1", category = "N"), ex("x2", category = "N"), ex("x3", category = "N"))

        val report = read(exercisesFile(*items.toTypedArray()), context)

        assertThat(report.errors).hasSize(3)
        assertThat(report.errors.last().message).contains("오류 1개가 더 있습니다")
        assertThat(report.warnings).hasSize(3)
        assertThat(report.warnings.last().message).contains("알림 1개가 더 있습니다")
    }

    // ----- 비슷한 이름 찾기 횟수 -----

    private fun manyDaysWith(vararg names: String): String {
        val days = names.mapIndexed { i, name -> day("날 $i", lines = listOf(line(name))) }
        // 한 템플릿의 날 수 한도(14)에 맞게 나눠 담는다.
        return templatesFile(*days.chunked(14).mapIndexed { i, chunk -> tpl(id = "t$i", days = chunk) }.toTypedArray())
    }

    private fun countingContext() = ImportContext(
        limits = ImportLimits.EXTERNAL.copy(maxTemplates = 100),
        knownExerciseNames = setOf("스쿼트"),
        checkExerciseReferences = true,
    )

    @Test
    fun `a name with no close match is looked up once however often it repeats`() {
        var calls = 0

        val report = CatalogReader.read(manyDaysWith(*Array(50) { "전혀 다른 이름" }), countingContext()) { _, _ ->
            calls++
            null
        }

        assertThat(report.errors).hasSize(50)
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `a name with a suggestion is also looked up once`() {
        var calls = 0

        val report = CatalogReader.read(manyDaysWith(*Array(30) { "스쿼터" }), countingContext()) { _, _ ->
            calls++
            "스쿼트"
        }

        assertThat(calls).isEqualTo(1)
        assertThat(report.errors.count { "'스쿼트' 를 쓰려던 건가요" in it.message }).isEqualTo(30)
    }

    @Test
    fun `at most twenty different names are looked up`() {
        var calls = 0

        CatalogReader.read(manyDaysWith(*Array(40) { "없는 이름 $it" }), countingContext()) { _, _ ->
            calls++
            null
        }

        assertThat(calls).isEqualTo(20)
    }

    @Test
    fun `the candidates are the known names and the names the file defines`() {
        var seen: Collection<String> = emptyList()
        val json = """{"exercises":[${ex("파일 종목")}],"templates":[${tpl(days = listOf(day(lines = listOf(line("없는 이름")))))}]}"""

        CatalogReader.read(json, countingContext()) { _, candidates ->
            seen = candidates.toList()
            null
        }

        assertThat(seen).containsExactly("스쿼트", "파일 종목")
    }
}
