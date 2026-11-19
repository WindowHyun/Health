package com.windowhyun.health.ui.session

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.domain.model.WorkoutSet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 세트 입력칸: 누르면 값 전체가 선택되고(80 을 85 로 바로 덮어쓴다), 소수점 쉼표도 점으로 읽는다. */
@RunWith(RobolectricTestRunner::class)
class SetInputUsabilityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val changes = mutableListOf<Triple<Double, Int, Int>>()

    private fun show() {
        compose.setContent {
            HealthTheme {
                SetRow(
                    set = WorkoutSet(id = 1, setNumber = 1, weightKg = 80.0, reps = 8, completed = false),
                    weightUnit = WeightUnit.KG,
                    onValuesChange = { w, r, d -> changes += Triple(w, r, d) },
                    onToggleCompleted = { _, _, _ -> },
                    onRemove = {},
                )
            }
        }
    }

    private fun fields() = compose.onAllNodes(hasSetTextAction())

    private fun selection(index: Int) =
        fields()[index].fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]

    @Test
    fun `tapping the weight field selects all of it`() {
        show()

        fields()[0].performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()

        val range = selection(0)
        assertThat(range.start).isEqualTo(0)
        assertThat(range.end).isEqualTo("80".length)
    }

    @Test
    fun `typing over the selected value replaces it`() {
        show()

        fields()[0].performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        fields()[0].performTextReplacement("85")
        compose.waitForIdle()

        assertThat(changes.last().first).isEqualTo(85.0)
        assertThat(changes.last().second).isEqualTo(8)
    }

    /** 소수점 키가 쉼표로 나오는 키보드에서도 62,5 가 62.5 로 읽힌다. */
    @Test
    fun `a decimal comma is read as a point`() {
        show()

        fields()[0].performTextReplacement("62,5")
        compose.waitForIdle()

        assertThat(changes.last().first).isEqualTo(62.5)
    }

    @Test
    fun `tapping the reps field selects its value too`() {
        show()

        fields()[1].performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()

        val range = selection(1)
        assertThat(range.start).isEqualTo(0)
        assertThat(range.end).isEqualTo("8".length)
    }
}
