package com.windowhyun.health.data.catalog

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 오타일 때 "혹시 이거?" 를 찾는 규칙. */
class SuggestionsTest {

    private val names = listOf("레그 컬", "레그프레스", "벤치프레스", "덤벨 컬", "스쿼트", "데드리프트", "BARBELL", "CHEST")

    @Test
    fun `finds the name that differs only by spaces`() {
        assertThat(Suggestions.closest("레그컬", names)).isEqualTo("레그 컬")
        assertThat(Suggestions.closest("벤치 프레스", names)).isEqualTo("벤치프레스")
    }

    @Test
    fun `ignores case`() {
        assertThat(Suggestions.closest("barbell", names)).isEqualTo("BARBELL")
    }

    @Test
    fun `finds a one letter typo`() {
        assertThat(Suggestions.closest("벤치프레쓰", names)).isEqualTo("벤치프레스")
    }

    @Test
    fun `finds a two letter typo in a longer name`() {
        assertThat(Suggestions.closest("데드리픗트트", names)).isEqualTo("데드리프트")
    }

    /** 짧은 이름에서 두 글자나 다르면 전혀 다른 말이다. */
    @Test
    fun `allows only one difference for short names`() {
        assertThat(Suggestions.closest("스쿼", names)).isEqualTo("스쿼트")
        assertThat(Suggestions.closest("푸시", names)).isNull()
    }

    @Test
    fun `returns null when nothing is close`() {
        assertThat(Suggestions.closest("바이시클 크런치", names)).isNull()
    }

    @Test
    fun `an empty input or an empty list gives nothing`() {
        assertThat(Suggestions.closest("", names)).isNull()
        // 한 글자 이름과 거리가 1 이라도, 빈 입력은 무엇의 오타도 아니다.
        assertThat(Suggestions.closest("", listOf("a"))).isNull()
        assertThat(Suggestions.closest("  ", listOf("a"))).isNull()
        assertThat(Suggestions.closest("   ", names)).isNull()
        assertThat(Suggestions.closest("벤치프레스", emptyList())).isNull()
    }

    @Test
    fun `prefers the closest of several`() {
        assertThat(Suggestions.closest("레그프레쓰", listOf("레그프레스", "레그 컬"))).isEqualTo("레그프레스")
        assertThat(Suggestions.closest("덤벨컬", listOf("덤벨 컬", "덤벨컬스"))).isEqualTo("덤벨 컬")
    }

    @Test
    fun `edit distance counts insert delete and replace`() {
        assertThat(Suggestions.distance("abc", "abc", 3)).isEqualTo(0)
        assertThat(Suggestions.distance("abc", "abd", 3)).isEqualTo(1)
        assertThat(Suggestions.distance("abc", "ab", 3)).isEqualTo(1)
        assertThat(Suggestions.distance("abc", "abcd", 3)).isEqualTo(1)
        assertThat(Suggestions.distance("kitten", "sitting", 5)).isEqualTo(3)
    }

    @Test
    fun `edit distance gives up early when the limit is exceeded`() {
        assertThat(Suggestions.distance("abcdef", "uvwxyz", 2)).isEqualTo(3)
        assertThat(Suggestions.distance("a", "abcdefgh", 2)).isEqualTo(3)
    }

    @Test
    fun `a short input is not matched to a name two letters away`() {
        assertThat(Suggestions.closest("abcd", listOf("abxy"))).isNull()
        assertThat(Suggestions.closest("abcde", listOf("abxye"))).isEqualTo("abxye")
    }

    @Test
    fun `edit distance at exactly the limit is still reported`() {
        assertThat(Suggestions.distance("abc", "abcd", 1)).isEqualTo(1)
        assertThat(Suggestions.distance("abc", "abd", 1)).isEqualTo(1)
        assertThat(Suggestions.distance("abc", "xyz", 3)).isEqualTo(3)
    }

    @Test
    fun `any amount of spacing is ignored`() {
        assertThat(Suggestions.closest("벤 치 프 레 스", names)).isEqualTo("벤치프레스")
        assertThat(Suggestions.closest(" 레 그 컬 ", names)).isEqualTo("레그 컬")
    }
}
