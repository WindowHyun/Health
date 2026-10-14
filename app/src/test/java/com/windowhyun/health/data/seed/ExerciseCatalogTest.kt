package com.windowhyun.health.data.seed

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.data.catalog.BundledData
import com.windowhyun.health.data.catalog.CatalogFormatException
import org.junit.Assert.assertThrows
import org.junit.Test

/** 기본 종목 파일(exercises.json) 로더. 검사 규칙 자체는 CatalogReaderTest 에서 본다. */
class ExerciseCatalogTest {

    private fun file(vararg items: String) = """{"exercises":[${items.joinToString(",")}]}"""

    private fun item(name: String = "벤치프레스", category: String = "BARBELL", bodyPart: String = "CHEST") =
        """{"name":"$name","category":"$category","bodyPart":"$bodyPart"}"""

    @Test
    fun `parse returns the exercises as built in entries`() {
        val result = ExerciseCatalog.parse(file(item("벤치프레스"), item("플랭크", "BODYWEIGHT", "CORE")))

        assertThat(result.map { it.name }).containsExactly("벤치프레스", "플랭크").inOrder()
        assertThat(result.all { it.isBuiltIn }).isTrue()
        assertThat(result.first().trackingType).isEqualTo(ExerciseTrackingType.WEIGHT_REPS)
        assertThat(result.first().defaultRestSeconds).isNull()
        assertThat(result.first().id).isEqualTo(0L)
    }

    @Test
    fun `an empty list is allowed`() {
        assertThat(ExerciseCatalog.parse(file())).isEmpty()
    }

    @Test
    fun `a file without the exercises section is refused`() {
        val e = assertThrows(CatalogFormatException::class.java) { ExerciseCatalog.parse("""{"templates":[]}""", "test.json") }

        assertThat(e.message).startsWith("test.json:")
        assertThat(e.message).contains("exercises")
    }

    /** 틀린 파일은 모든 문제를 파일 이름과 함께 알려 준다. */
    @Test
    fun `a wrong file reports every problem with the source name`() {
        val e = assertThrows(CatalogFormatException::class.java) {
            ExerciseCatalog.parse(file(item(category = "NOPE"), item("둘", bodyPart = "NOPE")), "test.json")
        }

        assertThat(e.message).startsWith("test.json: 문제 2개")
        assertThat(e.issues).hasSize(2)
    }

    @Test
    fun `malformed json is reported with the file name`() {
        listOf("{", "", "[]").forEach { text ->
            val e = assertThrows(CatalogFormatException::class.java) { ExerciseCatalog.parse(text, "test.json") }
            assertThat(e.message).startsWith("test.json:")
        }
    }

    // ----- 앱에 들어간 실제 파일 -----

    @Test
    fun `the bundled file is read and equals what the app exposes`() {
        val bundled = ExerciseCatalog.loadBundled()

        assertThat(bundled).isNotEmpty()
        assertThat(DefaultExercises.all).isEqualTo(bundled)
        assertThat(bundled.map { it.name }).containsNoDuplicates()
        assertThat(bundled.all { it.isBuiltIn }).isTrue()
    }

    /** 첫 사용 때 한 번만 읽고 같은 목록을 돌려준다. */
    @Test
    fun `the bundled list is loaded once`() {
        assertThat(DefaultExercises.all).isSameInstanceAs(DefaultExercises.all)
    }

    @Test
    fun `a missing bundled file is reported with its path`() {
        val e = assertThrows(CatalogFormatException::class.java) { BundledData.readText("health/없는파일.json") }

        assertThat(e.message).contains("health/없는파일.json")
    }
}
