package com.windowhyun.health.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 슈퍼셋 묶음 계산은 순수 함수라 JVM 에서 바로 검증한다. */
class SupersetGroupsTest {

    private val none = SupersetGroups.NONE

    @Test
    fun `linking two exercises makes a group`() {
        // 1번째 운동을 앞(0번째)과 묶는다 -> 0, 1번째가 한 묶음.
        assertThat(SupersetGroups.linkWithPrevious(listOf(none, none, none), 1))
            .containsExactly(1, 1, none).inOrder()
        assertThat(SupersetGroups.linkWithPrevious(listOf(none, none, none), 2))
            .containsExactly(none, 1, 1).inOrder()
    }

    @Test
    fun `linking a third exercise joins the existing group`() {
        val groups = SupersetGroups.linkWithPrevious(listOf(1, 1, none), 2)
        assertThat(groups).containsExactly(1, 1, 1).inOrder()
    }

    @Test
    fun `the first exercise cannot link with a previous one`() {
        assertThat(SupersetGroups.linkWithPrevious(listOf(none, none), 0))
            .containsExactly(none, none).inOrder()
    }

    @Test
    fun `unlinking one of two dissolves the group`() {
        assertThat(SupersetGroups.unlink(listOf(1, 1, none), 1))
            .containsExactly(none, none, none).inOrder()
    }

    @Test
    fun `unlinking one of three keeps the others together only when adjacent`() {
        // 끝을 빼면 앞의 둘은 그대로 묶여 있다.
        assertThat(SupersetGroups.unlink(listOf(1, 1, 1), 2)).containsExactly(1, 1, none).inOrder()
        // 가운데를 빼면 양 끝이 떨어져 둘 다 혼자가 된다.
        assertThat(SupersetGroups.unlink(listOf(1, 1, 1), 1)).containsExactly(none, none, none).inOrder()
    }

    @Test
    fun `normalize drops lone members and renumbers from one`() {
        assertThat(SupersetGroups.normalize(listOf(none, 5, 5, none, 9, none, 9, 9)))
            .containsExactly(none, 1, 1, none, none, none, 2, 2).inOrder()
    }

    @Test
    fun `the same number in two separate runs becomes two groups`() {
        assertThat(SupersetGroups.normalize(listOf(3, 3, none, 3, 3)))
            .containsExactly(1, 1, none, 2, 2).inOrder()
    }

    @Test
    fun `labels count the position inside the group`() {
        val groups = listOf(none, 1, 1, none, 2, 2, 2)
        assertThat(SupersetGroups.label(groups, 0)).isNull()
        assertThat(SupersetGroups.label(groups, 1)).isEqualTo("A1")
        assertThat(SupersetGroups.label(groups, 2)).isEqualTo("A2")
        assertThat(SupersetGroups.label(groups, 3)).isNull()
        assertThat(SupersetGroups.label(groups, 4)).isEqualTo("B1")
        assertThat(SupersetGroups.label(groups, 6)).isEqualTo("B3")
    }

    @Test
    fun `rest is skipped only in the middle of a group`() {
        val groups = listOf(none, 1, 1, 2, 2, 2)
        assertThat(SupersetGroups.restAfter(groups, 0)).isTrue()
        assertThat(SupersetGroups.restAfter(groups, 1)).isFalse()
        assertThat(SupersetGroups.restAfter(groups, 2)).isTrue()
        assertThat(SupersetGroups.restAfter(groups, 3)).isFalse()
        assertThat(SupersetGroups.restAfter(groups, 4)).isFalse()
        assertThat(SupersetGroups.restAfter(groups, 5)).isTrue()
    }

    @Test
    fun `a lone group number never skips rest`() {
        // 저장 중 어긋난 데이터가 와도 쉬지 않는 운동이 생기면 안 된다.
        assertThat(SupersetGroups.restAfter(listOf(7, none), 0)).isTrue()
    }

    @Test
    fun `linked with previous is true only inside a group`() {
        val groups = listOf(none, 1, 1, none)
        assertThat(SupersetGroups.isLinkedWithPrevious(groups, 1)).isFalse()
        assertThat(SupersetGroups.isLinkedWithPrevious(groups, 2)).isTrue()
        assertThat(SupersetGroups.isLinkedWithPrevious(groups, 3)).isFalse()
    }
}
