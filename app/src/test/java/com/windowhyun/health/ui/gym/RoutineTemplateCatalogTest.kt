package com.windowhyun.health.ui.gym

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.data.catalog.CatalogFormatException
import org.junit.Assert.assertThrows
import org.junit.Test

/** 루틴 템플릿 파일(routine_templates.json) 로더. 검사 규칙 자체는 CatalogReaderTest 에서 본다. */
class RoutineTemplateCatalogTest {

    private val valid = """{"templates":[{"id":"t","category":"SPLIT","title":"제목","description":"설명",
        "days":[{"name":"하루","exercises":[{"name":"스쿼트","sets":3}]}]}]}"""

    @Test
    fun `parse returns the templates`() {
        val templates = RoutineTemplateCatalog.parse(valid)

        assertThat(templates.map { it.id }).containsExactly("t")
        assertThat(templates.single().days.single().exercises).containsExactly("스쿼트" to 3)
    }

    @Test
    fun `a file without the templates section is refused`() {
        val e = assertThrows(CatalogFormatException::class.java) { RoutineTemplateCatalog.parse("""{"exercises":[]}""", "test.json") }

        assertThat(e.message).startsWith("test.json:")
        assertThat(e.message).contains("templates")
    }

    @Test
    fun `a wrong file reports every problem with the source name`() {
        val broken = valid.replace("\"sets\":3", "\"sets\":0").replace("\"SPLIT\"", "\"NOPE\"")

        val e = assertThrows(CatalogFormatException::class.java) { RoutineTemplateCatalog.parse(broken, "test.json") }

        assertThat(e.message).startsWith("test.json: 문제 2개")
        assertThat(e.message).contains("sets")
        assertThat(e.message).contains("category")
    }

    @Test
    fun `malformed json is reported with the file name`() {
        listOf("{", "", "[]").forEach { text ->
            val e = assertThrows(CatalogFormatException::class.java) { RoutineTemplateCatalog.parse(text, "test.json") }
            assertThat(e.message).startsWith("test.json:")
        }
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
