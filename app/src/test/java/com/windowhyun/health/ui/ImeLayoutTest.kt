package com.windowhyun.health.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.common.truth.Truth.assertWithMessage
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.ui.components.liftAboveKeyboard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * 키보드가 올라올 때 입력 화면 아래에 빈 공간이 남지 않는지.
 *
 * 앱 뼈대([AppFrame])와 같은 모양에 상태 · 내비게이션 막대 · 키보드 인셋을 흉내 내서, 화면 안쪽 영역의 아래 끝이
 * 키보드 바로 위에 닿는지 잰다. 이 앱은 edge-to-edge 라 창이 줄어들지 않고 인셋으로만 키보드를 피한다.
 *
 * 숫자는 창 높이 860dp · 상태 막대 24dp · 내비게이션 막대 24dp · 키보드 300dp(내비게이션 막대 영역 포함)다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class ImeLayoutTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val statusDp = 24
    private val navDp = 24
    private val keyboardDp = 300

    private class Measured(
        val topBarTopDp: Float,
        val topBarHeightDp: Float,
        val probeBottomDp: Float,
        val barBottomDp: Float?,
        val rootHeightDp: Float,
    ) {
        override fun toString() =
            "위 막대 시작=${topBarTopDp}dp 높이=${topBarHeightDp}dp · 안쪽 영역 아래 끝=${probeBottomDp}dp · " +
                "아래 막대 아래 끝=${barBottomDp}dp · 창 높이=${rootHeightDp}dp"
    }

    /**
     * @param framed 앱 뼈대([AppFrame])를 쓰는지. false 면 시스템 막대 여백을 "적용됨"으로 표시하지 않던 옛 뼈대다.
     * @param lifted 화면 Scaffold 를 [liftAboveKeyboard] 로 키보드 위로 올리는지.
     */
    private fun measure(
        bottomBarDp: Int,
        keyboardVisible: Boolean,
        framed: Boolean = true,
        lifted: Boolean = true,
    ): Measured {
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            HealthTheme {
                val screen: @Composable () -> Unit = {
                    Scaffold(
                        modifier = if (lifted) Modifier.liftAboveKeyboard() else Modifier,
                        topBar = { TopAppBar(title = { Text("제목") }, modifier = Modifier.testTag("topbar")) },
                        bottomBar = {
                            if (bottomBarDp > 0) Box(Modifier.fillMaxWidth().height(bottomBarDp.dp).testTag("bar"))
                        },
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding).testTag("probe"))
                    }
                }
                if (framed) {
                    AppFrame { screen() }
                } else {
                    Scaffold(Modifier.fillMaxSize()) { outer -> Column(Modifier.padding(outer)) { screen() } }
                }
            }
        }
        compose.waitForIdle()

        val px = density
        val builder = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (statusDp * px).toInt(), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, (navDp * px).toInt()))
        if (keyboardVisible) {
            builder.setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, (keyboardDp * px).toInt()))
            builder.setVisible(WindowInsetsCompat.Type.ime(), true)
        }
        compose.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, builder.build())
        }
        compose.waitForIdle()

        val root = compose.onRoot().fetchSemanticsNode().size.height / px
        val topBar = compose.onNodeWithTag("topbar").fetchSemanticsNode().boundsInRoot
        val probe = compose.onNodeWithTag("probe").fetchSemanticsNode().boundsInRoot
        val bar = if (bottomBarDp > 0) compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot.bottom / px else null
        return Measured(topBar.top / px, topBar.height / px, probe.bottom / px, bar, root)
    }

    private fun assertNear(label: String, actual: Float, expected: Float, m: Measured) {
        assertWithMessage("$label: 기대 ${expected}dp, 실제 ${actual}dp. $m").that(abs(actual - expected)).isLessThan(1f)
    }

    @Test
    fun `the content ends right above the keyboard`() {
        val m = measure(bottomBarDp = 0, keyboardVisible = true)
        assertNear("안쪽 영역 아래 끝", m.probeBottomDp, m.rootHeightDp - keyboardDp, m)
    }

    @Test
    fun `a bottom bar rides right above the keyboard instead of leaving a blank band`() {
        val m = measure(bottomBarDp = 80, keyboardVisible = true)
        // 아래 막대(휴식 타이머 등)가 키보드 바로 위에 붙고, 내용은 그 막대 위에서 끝난다.
        assertNear("아래 막대 아래 끝", m.barBottomDp!!, m.rootHeightDp - keyboardDp, m)
        assertNear("안쪽 영역 아래 끝", m.probeBottomDp, m.rootHeightDp - keyboardDp - 80, m)
    }

    @Test
    fun `without a keyboard the navigation bar space is left once`() {
        val m = measure(bottomBarDp = 0, keyboardVisible = false)
        assertNear("안쪽 영역 아래 끝", m.probeBottomDp, m.rootHeightDp - navDp, m)
    }

    @Test
    fun `the top bar is not padded for the status bar twice`() {
        val m = measure(bottomBarDp = 0, keyboardVisible = false)
        assertNear("위 막대 시작", m.topBarTopDp, statusDp.toFloat(), m)
        // 위 막대 자체의 높이는 64dp. 상태 막대 여백이 안쪽에 또 들어가면 88dp 가 된다.
        assertNear("위 막대 높이", m.topBarHeightDp, 64f, m)
    }

    /** 왜 [AppFrame] 이 필요한지: 여백을 "적용됨"으로 표시하지 않으면 안쪽 Scaffold 가 같은 여백을 한 번 더 둔다. */
    @Test
    fun `without marking the insets as used the screen pads the bars twice`() {
        val m = measure(bottomBarDp = 0, keyboardVisible = false, framed = false)
        assertNear("위 막대 높이(옛 뼈대)", m.topBarHeightDp, 64f + statusDp, m)
        assertNear("안쪽 영역 아래 끝(옛 뼈대)", m.probeBottomDp, m.rootHeightDp - 2 * navDp, m)
    }
}
