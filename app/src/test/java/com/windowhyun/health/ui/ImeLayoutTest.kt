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
import com.windowhyun.health.ui.components.imeAboveNavigationBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * 키보드가 올라올 때 입력 화면 아래에 빈 공간이 남지 않는지.
 *
 * 앱 뼈대(바깥 Scaffold 안에 화면 Scaffold)와 같은 모양에, 상태 · 내비게이션 막대 · 키보드 인셋을 흉내 내서
 * 화면 안쪽 영역의 아래 끝이 키보드 바로 위에 닿는지 잰다. 이 앱은 edge-to-edge 라 창이 줄어들지 않고
 * 인셋으로만 키보드를 피한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class ImeLayoutTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val statusDp = 24
    private val navDp = 24
    private val keyboardDp = 300 // 내비게이션 막대 영역을 포함한 키보드 높이(창 아래에서부터)

    private class Measured(val topBarTopDp: Float, val probeBottomDp: Float, val rootHeightDp: Float)

    private fun measure(bottomBarDp: Int, keyboardVisible: Boolean, nested: Boolean = true): Measured {
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            HealthTheme {
                val screen: @Composable () -> Unit = {
                    Scaffold(
                        topBar = { TopAppBar(title = { Text("제목") }, modifier = Modifier.testTag("topbar")) },
                        bottomBar = {
                            if (bottomBarDp > 0) Box(Modifier.fillMaxWidth().height(bottomBarDp.dp).testTag("bar"))
                        },
                    ) { padding ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .imeAboveNavigationBar()
                                .testTag("probe"),
                        )
                    }
                }
                if (nested) {
                    // HealthApp 과 같다: 바깥 Scaffold 의 안쪽 여백만 주고, 안쪽 화면이 또 Scaffold 를 쓴다.
                    Scaffold(Modifier.fillMaxSize()) { outer ->
                        Column(Modifier.padding(outer)) { screen() }
                    }
                } else {
                    screen()
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
        val topBar = compose.onNodeWithTag("topbar").fetchSemanticsNode().boundsInRoot.top / px
        val probe = compose.onNodeWithTag("probe").fetchSemanticsNode().boundsInRoot.bottom / px
        return Measured(topBar, probe, root)
    }

    private fun describe(label: String, m: Measured) =
        "$label: 위 막대 시작=${m.topBarTopDp}dp 안쪽 영역 아래 끝=${m.probeBottomDp}dp 창 높이=${m.rootHeightDp}dp"

    @Test
    fun `the screen content ends right above the keyboard`() {
        val m = measure(bottomBarDp = 0, keyboardVisible = true)
        val expectedBottom = m.rootHeightDp - keyboardDp
        val gap = expectedBottom - m.probeBottomDp
        assertWithMessage("키보드 위쪽=${expectedBottom}dp 와의 차이 ${gap}dp. " + describe("막대 없음", m)).that(abs(gap)).isLessThan(1f)
    }

    @Test
    fun `a bottom bar under the keyboard does not leave a blank band`() {
        val m = measure(bottomBarDp = 80, keyboardVisible = true)
        val expectedBottom = m.rootHeightDp - keyboardDp
        val gap = expectedBottom - m.probeBottomDp
        assertWithMessage("키보드 위쪽=${expectedBottom}dp 와의 차이 ${gap}dp. " + describe("아래 막대 80dp", m)).that(abs(gap)).isLessThan(1f)
    }

    @Test
    fun `the top bar sits right under the status bar once`() {
        val m = measure(bottomBarDp = 0, keyboardVisible = false)
        assertWithMessage(describe("키보드 없음", m)).that(abs(m.topBarTopDp - statusDp)).isLessThan(1f)
    }
}
