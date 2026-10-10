package com.windowhyun.health.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.windowhyun.health.domain.model.PipSpace
import com.windowhyun.health.domain.model.PipSpacePosition

/** 화면 끝에 비워 둔 PiP 자리를 테스트에서 찾는 이름표. */
internal const val PIP_SPACE_TAG = "pip_space"

/**
 * 앱 전체를 감싸서, 켜 두면 화면 위나 아래 끝에 PiP 창만큼 빈 자리를 만든다.
 * 유튜브 같은 앱을 PiP 로 띄워 두고 운동을 기록할 때, 작은 창이 버튼과 숫자를 가리지 않게 하려는 것이다.
 *
 * - 아래: 하단 탭 **아래**에 빈 자리를 둔다. PiP 는 화면 맨 아래에 뜨기 때문에 탭이 아니라 이 자리를 덮는다.
 * - 위: 상단 바 **위**에 둔다.
 * - 시스템 막대(상태 · 내비게이션 막대) 높이는 이 자리가 대신 받아서, 안쪽 화면이 같은 여백을 한 번 더 두지 않는다.
 * - 꺼져 있으면 아무것도 바꾸지 않는다.
 */
@Composable
fun PipSpaceHost(
    pipSpace: PipSpace,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!pipSpace.enabled) {
        Box(modifier = modifier.fillMaxSize()) { content() }
        return
    }
    val atTop = pipSpace.position == PipSpacePosition.TOP
    Column(modifier = modifier.fillMaxSize()) {
        if (atTop) PipSpaceBand(pipSpace.heightDp, atTop = true)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // 자리가 막대 높이를 차지했으니 안쪽 화면은 그 막대 여백을 다시 두지 않는다.
                .consumeWindowInsets(if (atTop) WindowInsets.statusBars else WindowInsets.navigationBars),
        ) {
            content()
        }
        if (!atTop) PipSpaceBand(pipSpace.heightDp, atTop = false)
    }
}

/** 빈 자리. 바탕색 그대로 비워 두고, 점선 테두리와 작은 글자로 "일부러 비운 곳"임만 알린다. */
@Composable
private fun PipSpaceBand(heightDp: Int, atTop: Boolean) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (atTop) it.statusBarsPadding() else it.navigationBarsPadding() }
            .height(heightDp.dp)
            .testTag(PIP_SPACE_TAG)
            .drawBehind {
                val stroke = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
                val y = if (atTop) size.height else 0f
                drawLine(outline, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke.width, pathEffect = stroke.pathEffect)
            }
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "PiP 자리",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/**
 * 앱 맨 바깥. 테마를 입히고, 켜 두었다면 유튜브 같은 앱의 PiP 창이 가리지 않도록 화면 끝을 비운다.
 * Activity 에서 떼어 둔 이유는, 이 배치를 Hilt 없이 테스트로 확인하기 위해서다.
 */
@Composable
fun HealthRoot(
    settings: com.windowhyun.health.domain.model.AppSettings,
    content: @Composable () -> Unit,
) {
    com.windowhyun.health.core.designsystem.theme.HealthTheme(themeMode = settings.themeMode) {
        PipSpaceHost(settings.pipSpace, content = content)
    }
}
