package com.windowhyun.health.ui.gym

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.util.CatalogFormatException
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek

/** 루틴 템플릿 파일(routine_templates.json)을 읽는 규칙. 파일을 손으로 고치다 틀리면 어디가 왜 틀렸는지 알려 줘야 한다. */
class RoutineTemplateCatalogTest {

    private fun ex(name: String, sets: Int = 3, rest: Int? = null, superset: String? = null): String {
        val parts = mutableListOf(""""name":"$name"""", """"sets":$sets""")
        rest?.let { parts += """"rest":$it""" }
        superset?.let { parts += """"superset":"$it"""" }
        return "{${parts.joinToString(",")}}"
    }

    private fun day(name: String = "하루", scheduled: List<String>? = null, vararg exercises: String): String {
        val list = if (exercises.isEmpty()) listOf(ex("스쿼트")) else exercises.toList()
        val schedule = scheduled?.let { s -> """"scheduledDays":[${s.joinToString(",") { "\"$it\"" }}],""" }.orEmpty()
        return """{"name":"$name",$schedule"exercises":[${list.joinToString(",")}]}"""
    }

    private fun tpl(
        id: String = "t",
        category: String = "SPLIT",
        title: String = "제목",
        description: String = "설명",
        vararg days: String,
    ): String {
        val list = if (days.isEmpty()) listOf(day()) else days.toList()
        return """{"id":"$id","category":"$category","title":"$title","description":"$description","days":[${list.joinToString(",")}]}"""
    }

    private fun file(vararg templates: String) = """{"templates":[${templates.joinToString(",")}]}"""

    private fun rejects(json: String): String =
        assertThrows(CatalogFormatException::class.java) { RoutineTemplateCatalog.parse(json, "test.json") }.message.orEmpty()

    /** 한 날짜만 가진 템플릿을 만들어 그 날을 돌려준다. */
    private fun parseDay(vararg exercises: String, scheduled: List<String>? = null): RoutineTemplates.Day =
        RoutineTemplateCatalog.parse(file(tpl(days = arrayOf(day(scheduled = scheduled, exercises = exercises))))).single().days.single()

    @Test
    fun `reads a template with its fields`() {
        val t = RoutineTemplateCatalog.parse(file(tpl(id = "abc", category = "BEGINNER", title = "처음", description = "쉬워요"))).single()

        assertThat(t.id).isEqualTo("abc")
        assertThat(t.category).isEqualTo(RoutineTemplates.Category.BEGINNER)
        assertThat(t.title).isEqualTo("처음")
        assertThat(t.description).isEqualTo("쉬워요")
        assertThat(t.days).hasSize(1)
    }

    @Test
    fun `keeps template and exercise order`() {
        val templates = RoutineTemplateCatalog.parse(
            file(
                tpl(id = "b", days = arrayOf(day("나", exercises = arrayOf(ex("가", 4), ex("다", 2), ex("나", 5))))),
                tpl(id = "a", days = arrayOf(day("다"))),
            ),
        )

        assertThat(templates.map { it.id }).containsExactly("b", "a").inOrder()
        assertThat(templates[0].days.single().exercises).containsExactly("가" to 4, "다" to 2, "나" to 5).inOrder()
    }

    @Test
    fun `rest is kept only for exercises that give one`() {
        val d = parseDay(ex("스쿼트", rest = 120), ex("레그컬"), ex("카프레이즈", rest = 60))

        assertThat(d.restSeconds).containsExactly("스쿼트", 120, "카프레이즈", 60)
    }

    @Test
    fun `a day without schedule has no weekday and a given one is read as a set`() {
        assertThat(parseDay(ex("스쿼트")).scheduledDays).isEmpty()
        assertThat(parseDay(ex("스쿼트"), scheduled = listOf("TUESDAY", "SATURDAY")).scheduledDays)
            .containsExactly(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY)
    }

    // ----- 슈퍼셋 -----

    /** 같은 라벨끼리 묶이고, 묶음 번호는 라벨의 알파벳순이 아니라 처음 나온 순서다. 묶이지 않은 종목은 어느 묶음에도 없다. */
    @Test
    fun `superset labels become groups in order of first appearance`() {
        val d = parseDay(
            ex("벤치프레스"),
            ex("푸시다운", superset = "Z"),
            ex("바벨 컬", superset = "Z"),
            ex("딥스", superset = "A"),
            ex("해머 컬", superset = "A"),
            ex("플랭크"),
        )

        assertThat(d.supersets).containsExactly(listOf("푸시다운", "바벨 컬"), listOf("딥스", "해머 컬")).inOrder()
        assertThat(d.supersets.flatten()).doesNotContain("벤치프레스")
    }

    @Test
    fun `a group may have three exercises`() {
        val d = parseDay(ex("가", superset = "A"), ex("나", superset = "A"), ex("다", superset = "A"))

        assertThat(d.supersets).containsExactly(listOf("가", "나", "다"))
    }

    @Test
    fun `a day without superset has none`() {
        assertThat(parseDay(ex("스쿼트"), ex("벤치프레스")).supersets).isEmpty()
    }

    @Test
    fun `a superset with a single exercise is rejected`() {
        val message = rejects(file(tpl(days = arrayOf(day("하루", exercises = arrayOf(ex("가"), ex("나", superset = "A")))))))

        assertThat(message).contains("superset")
        assertThat(message).contains("'A'")
        assertThat(message).contains("하루")
    }

    @Test
    fun `superset exercises must be next to each other`() {
        val message = rejects(
            file(tpl(days = arrayOf(day("하루", exercises = arrayOf(ex("가", superset = "A"), ex("나"), ex("다", superset = "A")))))),
        )

        assertThat(message).contains("이어서")
        assertThat(message).contains("'A'")
    }

    @Test
    fun `a blank superset label is rejected`() {
        assertThat(rejects(file(tpl(days = arrayOf(day(exercises = arrayOf(ex("가", superset = " "), ex("나", superset = " "))))))))
            .contains("superset")
    }

    // ----- 틀린 파일 -----

    @Test
    fun `unknown category and weekday list the valid values`() {
        val category = rejects(file(tpl(category = "SPLITT")))
        assertThat(category).contains("SPLITT")
        assertThat(category).contains("BEGINNER")

        val weekday = rejects(file(tpl(days = arrayOf(day(scheduled = listOf("TUESDYA"))))))
        assertThat(weekday).contains("TUESDYA")
        assertThat(weekday).contains("MONDAY")
    }

    @Test
    fun `the same weekday twice is rejected`() {
        assertThat(rejects(file(tpl(days = arrayOf(day(scheduled = listOf("MONDAY", "MONDAY"))))))).contains("scheduledDays")
    }

    @Test
    fun `a misspelled key and a missing required field are rejected`() {
        assertThat(rejects("""{"templates":[{"id":"t","category":"SPLIT","title":"제목","descripton":"설명","description":"설명","days":[${day()}]}]}"""))
            .contains("descripton")
        assertThat(rejects("""{"templates":[{"id":"t","category":"SPLIT","title":"제목","days":[${day()}]}]}"""))
            .contains("description")
        assertThat(rejects(file(tpl(days = arrayOf(day(exercises = arrayOf("""{"name":"스쿼트"}"""))))))).contains("sets")
    }

    @Test
    fun `blank id title description and name are rejected`() {
        assertThat(rejects(file(tpl(id = " ")))).contains("id")
        assertThat(rejects(file(tpl(title = " ")))).contains("title")
        assertThat(rejects(file(tpl(description = " ")))).contains("description")
        assertThat(rejects(file(tpl(days = arrayOf(day(name = " ")))))).contains("name")
        assertThat(rejects(file(tpl(days = arrayOf(day(exercises = arrayOf(ex(" "))))))))
            .contains("name")
    }

    @Test
    fun `template without days and day without exercises are rejected`() {
        assertThat(rejects("""{"templates":[{"id":"t","category":"SPLIT","title":"제목","description":"설명","days":[]}]}"""))
            .contains("days")
        assertThat(rejects(file(tpl(days = arrayOf("""{"name":"하루","exercises":[]}"""))))).contains("exercises")
    }

    @Test
    fun `duplicate template ids are rejected`() {
        val message = rejects(file(tpl(id = "t", days = arrayOf(day("가"))), tpl(id = "t", days = arrayOf(day("나")))))

        assertThat(message).contains("id")
        assertThat(message).contains("templates[1]")
    }

    /** 루틴 이름이 겹치면 루틴 목록에 같은 이름이 두 개 생긴다. 같은 템플릿 안이든 다른 템플릿이든 마찬가지다. */
    @Test
    fun `duplicate routine names are rejected inside and across templates`() {
        assertThat(rejects(file(tpl(days = arrayOf(day("같은"), day("같은")))))).contains("같은")
        assertThat(rejects(file(tpl(id = "a", days = arrayOf(day("같은"))), tpl(id = "b", days = arrayOf(day("같은")))))).contains("같은")
    }

    /** 휴식 · 슈퍼셋을 이름으로 찾으므로 한 날에 같은 종목이 두 번 있으면 안 된다. */
    @Test
    fun `an exercise twice in a day is rejected`() {
        val message = rejects(file(tpl(days = arrayOf(day("하루", exercises = arrayOf(ex("스쿼트"), ex("벤치"), ex("스쿼트")))))))

        assertThat(message).contains("스쿼트")
        assertThat(message).contains("exercises[2]")
    }

    @Test
    fun `sets must be between 1 and 20`() {
        assertThat(parseDay(ex("가", sets = 1)).exercises.single().second).isEqualTo(1)
        assertThat(parseDay(ex("가", sets = 20)).exercises.single().second).isEqualTo(20)

        assertThat(rejects(file(tpl(days = arrayOf(day(exercises = arrayOf(ex("가", sets = 0)))))))).contains("sets")
        assertThat(rejects(file(tpl(days = arrayOf(day(exercises = arrayOf(ex("가", sets = 21)))))))).contains("sets")
    }

    @Test
    fun `rest must be between 5 and 600 seconds`() {
        assertThat(parseDay(ex("가", rest = 5)).restSeconds).containsExactly("가", 5)
        assertThat(parseDay(ex("가", rest = 600)).restSeconds).containsExactly("가", 600)

        assertThat(rejects(file(tpl(days = arrayOf(day(exercises = arrayOf(ex("가", rest = 4)))))))).contains("rest")
        assertThat(rejects(file(tpl(days = arrayOf(day(exercises = arrayOf(ex("가", rest = 601)))))))).contains("rest")
    }

    @Test
    fun `the message points at the template day and exercise`() {
        val message = rejects(
            file(tpl(id = "mine", days = arrayOf(day("월요일 루틴", exercises = arrayOf(ex("스쿼트"), ex("벤치프레스", sets = 99)))))),
        )

        assertThat(message).startsWith("test.json")
        assertThat(message).contains("mine")
        assertThat(message).contains("월요일 루틴")
        assertThat(message).contains("벤치프레스")
    }

    @Test
    fun `malformed json is reported with the file name`() {
        assertThat(rejects("{")).startsWith("test.json:")
        assertThat(rejects("")).startsWith("test.json:")
        assertThat(rejects("""{"templates":[{"id":"t"""")).startsWith("test.json:")
    }

    // ----- 앱에 들어간 실제 파일 -----

    @Test
    fun `the bundled file is read and equals what the app exposes`() {
        val bundled = RoutineTemplateCatalog.loadBundled()

        assertThat(bundled).isNotEmpty()
        assertThat(RoutineTemplates.all).isEqualTo(bundled)
    }

    /** 첫 사용 때 한 번만 읽고 같은 목록을 돌려준다. */
    @Test
    fun `the bundled list is loaded once`() {
        assertThat(RoutineTemplates.all).isSameInstanceAs(RoutineTemplates.all)
        assertThat(RoutineTemplates.byCategory).isSameInstanceAs(RoutineTemplates.byCategory)
    }
}
