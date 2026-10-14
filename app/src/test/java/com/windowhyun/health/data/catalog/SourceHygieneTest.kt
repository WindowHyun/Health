package com.windowhyun.health.data.catalog

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * 소스와 데이터 파일에 보이지 않는 문자(제어 문자 · 글자 방향을 바꾸는 문자 등)가 실제 글자로 들어 있지 않아야 한다.
 * 눈에 안 보이는데 코드가 다르게 읽히게 만들 수 있고, 밖에서 받은 글에서 거절하는 바로 그 문자들이다.
 * 필요하면 `\uXXXX` 로 적는다.
 */
class SourceHygieneTest {

    private fun filesUnder(dir: String, vararg extensions: String): List<File> =
        File(dir).walkTopDown().filter { it.isFile && extensions.any { ext -> it.name.endsWith(ext) } }.toList()

    private fun suspicious(file: File): List<String> {
        val text = file.readText(Charsets.UTF_8)
        val found = ArrayList<String>()
        var line = 1
        text.forEach { c ->
            if (c == '\n') line++
            val type = Character.getType(c)
            val invisible = type == Character.FORMAT.toInt() || type == Character.LINE_SEPARATOR.toInt() ||
                type == Character.PARAGRAPH_SEPARATOR.toInt() || (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')
            if (invisible) found += "${file.path}:$line U+%04X".format(c.code)
        }
        return found
    }

    @Test
    fun `source and data files contain no invisible characters`() {
        val files = filesUnder("src/main", ".kt", ".json", ".xml") + filesUnder("src/test", ".kt", ".json")

        // 경로가 틀려 아무것도 검사하지 않으면 통과해 버리므로, 파일을 실제로 찾았는지 확인한다.
        assertThat(files.size).isGreaterThan(100)
        assertThat(files.any { it.name == "CatalogReader.kt" }).isTrue()
        assertThat(files.any { it.name == "routine_templates.json" }).isTrue()
        assertThat(files.flatMap { suspicious(it) }).isEmpty()
    }

    @Test
    fun `the detector finds the characters it is meant to find`() {
        val dir = java.nio.file.Files.createTempDirectory("hygiene").toFile()
        try {
            val file = File(dir, "x.kt")
            listOf("\u202E", "\u200E", "\uFEFF", "\u2028", "\u0007", "\u0000", "\u061C", "\u2066", "\u2029").forEach { bad ->
                file.writeText("val a = \"가${bad}나\"\n", Charsets.UTF_8)
                assertThat(suspicious(file)).hasSize(1)
            }
            file.writeText("val a = \"가\\u202E나\"\n\tval b = 1\r\n", Charsets.UTF_8)
            assertThat(suspicious(file)).isEmpty()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the report names the file the line and the character`() {
        val dir = java.nio.file.Files.createTempDirectory("hygiene").toFile()
        try {
            val file = File(dir, "y.kt")
            file.writeText("첫 줄\n둘째 줄 \u202E\n", Charsets.UTF_8)

            assertThat(suspicious(file).single()).endsWith("y.kt:2 U+202E")
        } finally {
            dir.deleteRecursively()
        }
    }
}
