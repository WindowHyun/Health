package com.windowhyun.health.data.catalog

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/** 밖에서 받은 글을 앱 안으로 들이는 첫 관문: 크기 · 인코딩. */
class ExternalTextTest {

    private fun read(bytes: ByteArray, max: Int = 1000) = ExternalText.read(ByteArrayInputStream(bytes), max)

    @Test
    fun `reads utf8 text`() {
        val result = read("{\"이름\":\"한글\"}".toByteArray(Charsets.UTF_8))

        assertThat(result.text).isEqualTo("{\"이름\":\"한글\"}")
        assertThat(result.problem).isNull()
    }

    @Test
    fun `a utf8 byte order mark is dropped`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "{}".toByteArray()

        assertThat(read(bytes).text).isEqualTo("{}")
    }

    @Test
    fun `utf16 is refused with a hint`() {
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), '{'.code.toByte(), 0, '}'.code.toByte(), 0)
        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0, '{'.code.toByte(), 0, '}'.code.toByte())

        assertThat(read(le).problem).contains("UTF-16")
        assertThat(read(be).problem).contains("UTF-16")
        assertThat(read(le).text).isNull()
    }

    @Test
    fun `broken utf8 is refused instead of being patched with replacement characters`() {
        // 0xC3 뒤에 이어지는 바이트가 없다.
        val result = read(byteArrayOf('{'.code.toByte(), 0xC3.toByte(), '}'.code.toByte()))

        assertThat(result.text).isNull()
        assertThat(result.problem).contains("UTF-8")
    }

    @Test
    fun `an empty file is refused`() {
        assertThat(read(ByteArray(0)).problem).contains("비어")
    }

    @Test
    fun `exactly the limit is allowed and one more byte is not`() {
        assertThat(read(ByteArray(1000) { 'a'.code.toByte() }, max = 1000).text).hasLength(1000)

        val over = read(ByteArray(1001) { 'a'.code.toByte() }, max = 1000)
        assertThat(over.text).isNull()
        assertThat(over.problem).contains("너무 큽니다")
    }

    /** 끝이 없는 스트림(느린 서버 · 가짜 파일)도 한도까지만 읽고 멈춘다. */
    @Test
    fun `an endless stream is cut off at the limit`() {
        val endless = object : InputStream() {
            var served = 0L
            override fun read(): Int {
                served++
                return 'a'.code
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                java.util.Arrays.fill(b, off, off + len, 'a'.code.toByte())
                served += len
                return len
            }
        }

        val result = ExternalText.read(endless, maxBytes = 5000)

        assertThat(result.problem).contains("너무 큽니다")
        assertThat(endless.served).isAtMost(5000L + 8 * 1024)
    }

    @Test
    fun `the limit message tells the size in kilobytes`() {
        assertThat(read(ByteArray(3000), max = 2048).problem).contains("2KB")
    }

    @Test
    fun `pasted text is limited by its byte size not its length`() {
        // 한글 한 글자는 3바이트다.
        val korean = "가".repeat(400)

        assertThat(ExternalText.fromPasted(korean, maxBytes = 1000).problem).contains("너무 큽니다")
        assertThat(ExternalText.fromPasted(korean, maxBytes = 1200).text).isEqualTo(korean)
    }

    @Test
    fun `a pasted text with a leading byte order mark is cleaned`() {
        assertThat(ExternalText.fromPasted("\uFEFF{}", 100).text).isEqualTo("{}")
    }

    @Test
    fun `an empty paste is refused`() {
        assertThat(ExternalText.fromPasted("", 100).problem).contains("비어")
    }
}
