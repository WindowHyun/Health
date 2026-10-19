package com.windowhyun.health.ui.gym

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.data.catalog.ExternalText
import com.windowhyun.health.data.catalog.ImportLimits
import org.junit.Test

/** 붙여넣기 입력 칸이 큰 글을 자르더라도, 너무 크다는 사실은 검사에서 그대로 알려 줘야 한다. */
class PasteLimitTest {

    private val limit = ImportLimits.EXTERNAL.maxBytes

    @Test
    fun `the clamp is one character past the byte limit`() {
        assertThat(MAX_PASTE_CHARS).isEqualTo(limit + 1)
    }

    @Test
    fun `a text within the limit is kept as it is`() {
        val text = "a".repeat(limit)

        assertThat(clampPasted(text)).isEqualTo(text)
        assertThat(ExternalText.fromPasted(clampPasted(text), limit).text).isEqualTo(text)
    }

    /** 예전에는 글자 수 한도(=바이트 한도)에서 잘라, 잘린 글이 한도 안으로 들어와 '너무 큽니다' 대신 문법 오류가 났다. */
    @Test
    fun `a longer text is still reported as too large after the clamp`() {
        val clamped = clampPasted("a".repeat(limit * 2))

        assertThat(clamped).hasLength(limit + 1)
        assertThat(ExternalText.fromPasted(clamped, limit).problem).contains("너무 큽니다")
    }

    @Test
    fun `a cut that falls inside an emoji is still reported as too large`() {
        val text = "a".repeat(limit) + String(Character.toChars(0x1F600))

        val clamped = clampPasted(text)

        assertThat(clamped).hasLength(limit + 1)
        assertThat(ExternalText.fromPasted(clamped, limit).problem).contains("너무 큽니다")
    }

    @Test
    fun `korean text over the limit is reported too`() {
        val clamped = clampPasted("가".repeat(limit))

        assertThat(ExternalText.fromPasted(clamped, limit).problem).contains("너무 큽니다")
    }
}
