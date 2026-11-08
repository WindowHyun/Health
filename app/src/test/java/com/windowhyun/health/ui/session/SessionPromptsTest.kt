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

    @Test
    fun `unchecked sets with values offer to be saved too`() {
        val prompt = finishPrompt(completedSets = 9, incompleteSets = 3, uncheckedWithValues = 2)

        assertThat(prompt.completeUncheckedLabel).isEqualTo("체크 안 한 2세트도 완료로 저장하고 종료")
        // 안내 글은 그대로다(미리 채운 값 때문에 늘 뜨는 문장으로 화면을 어지럽히지 않는다).
        assertThat(prompt.message).isEqualTo("완료한 9세트는 저장되고, 체크하지 않은 3세트는 저장되지 않습니다.")
    }

    @Test
    fun `no such option when nothing unchecked has values`() {
        assertThat(finishPrompt(9, 3, uncheckedWithValues = 0).completeUncheckedLabel).isNull()
        assertThat(finishPrompt(12, 0).completeUncheckedLabel).isNull()
    }

    /** 한 세트도 체크하지 않았다면 미리 채워진 값을 완료로 바꾸는 선택지를 주지 않는다(하지 않은 운동이 기록되면 안 된다). */
    @Test
    fun `the discard prompt never offers to turn pre filled sets into records`() {
        val prompt = finishPrompt(completedSets = 0, incompleteSets = 4, uncheckedWithValues = 4)

        assertThat(prompt.confirmLabel).isEqualTo("버리고 종료")
        assertThat(prompt.completeUncheckedLabel).isNull()
    }

    @Test
    fun `notification permission is asked once on Android 13 or newer`() {
        assertThat(shouldAskNotificationPermission(sdkInt = 34, granted = false, alreadyAsked = false)).isTrue()
        assertThat(shouldAskNotificationPermission(sdkInt = 34, granted = true, alreadyAsked = false)).isFalse()
        assertThat(shouldAskNotificationPermission(sdkInt = 34, granted = false, alreadyAsked = true)).isFalse()
        assertThat(shouldAskNotificationPermission(sdkInt = 32, granted = false, alreadyAsked = false)).isFalse()
        assertThat(shouldAskNotificationPermission(sdkInt = 33, granted = false, alreadyAsked = false)).isTrue()
    }
}
