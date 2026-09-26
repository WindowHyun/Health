package com.windowhyun.health.ui.session

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 시간 운동의 분·초 입력. 숫자 키패드에 ':' 이 없어 두 칸으로 받는다. */
class DurationInputTest {

    @Test
    fun `adds minutes and seconds`() {
        assertThat(durationSecondsOf("1", "30")).isEqualTo(90)
        assertThat(durationSecondsOf("2", "")).isEqualTo(120)
        assertThat(durationSecondsOf("", "45")).isEqualTo(45)
    }

    @Test
    fun `empty input is zero`() {
        assertThat(durationSecondsOf("", "")).isEqualTo(0)
    }

    /** 초 칸에 60 이상을 넣으면 되묻지 않고 그대로 더한다. */
    @Test
    fun `accepts seconds over sixty`() {
        assertThat(durationSecondsOf("", "90")).isEqualTo(90)
        assertThat(durationSecondsOf("1", "75")).isEqualTo(135)
    }
}
