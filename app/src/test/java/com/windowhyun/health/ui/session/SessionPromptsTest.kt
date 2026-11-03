package com.windowhyun.health.ui.session

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 운동 종료 · 종목 빼기 확인창의 글: 무엇이 저장되고 무엇이 사라지는지 숫자로 알려 준다. */
class SessionPromptsTest {

    @Test
    fun `ending with unchecked sets says how many are kept and how many are dropped`() {
        val prompt = finishPrompt(completedSets = 9, incompleteSets = 3)

        assertThat(prompt.title).isEqualTo("운동을 종료할까요?")
        assertThat(prompt.message).contains("완료한 9세트는 저장")
        assertThat(prompt.message).contains("체크하지 않은 3세트는 저장되지 않습니다")
        assertThat(prompt.confirmLabel).isEqualTo("종료")
    }

    @Test
    fun `ending with everything checked just says what is recorded`() {
        val prompt = finishPrompt(completedSets = 12, incompleteSets = 0)

        assertThat(prompt.message).contains("12세트를 기록")
        assertThat(prompt.message).doesNotContain("저장되지 않습니다")
    }

    /** 한 세트도 안 했으면 기록이 남지 않는다. 그 사실을 먼저 알려야 한다. */
    @Test
    fun `ending with nothing done warns that nothing is saved`() {
        val prompt = finishPrompt(completedSets = 0, incompleteSets = 6)

        assertThat(prompt.title).isEqualTo("기록할 세트가 없어요")
        assertThat(prompt.message).contains("저장되지 않고 사라집니다")
        assertThat(prompt.confirmLabel).isEqualTo("버리고 종료")
    }

    @Test
    fun `removing an exercise names it and the sets that go with it`() {
        val text = removeExercisePrompt("벤치프레스", completedSets = 3)

        assertThat(text).contains("'벤치프레스'")
        assertThat(text).contains("완료한 3세트")
    }
}
