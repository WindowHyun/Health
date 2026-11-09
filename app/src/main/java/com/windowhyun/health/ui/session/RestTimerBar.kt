package com.windowhyun.health.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.windowhyun.health.core.designsystem.theme.LargeMetricTextStyle
import com.windowhyun.health.core.designsystem.theme.healthColors
import com.windowhyun.health.core.util.formatDuration
import com.windowhyun.health.ui.components.HealthButton

/**
 * 휴식 타이머 바. 세트를 완료하면 화면 하단에 자동으로 나타난다.
 * 어두운 패널에 라임 숫자라 운동 중 곁눈질로도 남은 시간이 읽힌다.
 * 버튼은 크게, 개수는 최소로(-10 / +10 / 일시정지 / 건너뛰기).
 */
@Composable
fun RestTimerBar(
    state: RestTimerState,
    onAdjust: (Int) -> Unit,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.healthColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.panel)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (state.paused) "휴식 일시정지" else "휴식",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onPanel.copy(alpha = 0.7f),
            )
            Text(
                text = formatDuration(state.remainingSeconds.toLong()),
                style = LargeMetricTextStyle,
                color = colors.accent,
            )
        }

        // 남은 비율을 굵은 선으로. 기본 진행 표시줄은 얇고 둥글어 이 화면에 어울리지 않는다.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(colors.onPanel.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(colors.accent),
            )
        }

        val outline = BorderStroke(1.dp, colors.onPanel.copy(alpha = 0.45f))
        // 글자를 크게 쓰면 4버튼이 한 줄에 못 들어가 글자가 세로로 쪼개진다. 그때는 2줄로 나눈다.
        val twoRows = LocalDensity.current.fontScale > 1.3f
        @Composable
        fun Minus(m: Modifier) = HealthButton(
            onClick = { onAdjust(-10) }, modifier = m, container = Color.Transparent,
            content = colors.onPanel, border = outline, compact = true,
        ) { Text("-10초", maxLines = 1) }
        @Composable
        fun Plus(m: Modifier) = HealthButton(
            onClick = { onAdjust(10) }, modifier = m, container = Color.Transparent,
            content = colors.onPanel, border = outline, compact = true,
        ) { Text("+10초", maxLines = 1) }
        @Composable
        fun Pause(m: Modifier) = HealthButton(
            onClick = onTogglePause, modifier = m, container = Color.Transparent,
            content = colors.onPanel, border = outline, compact = true,
        ) { Text(if (state.paused) "계속" else "일시정지", maxLines = 1) }
        @Composable
        fun Skip(m: Modifier) = HealthButton(onClick = onSkip, modifier = m, compact = true) { Text("건너뛰기", maxLines = 1) }

        val w = Modifier.weight(1f)
        if (twoRows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Minus(w); Plus(w) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pause(w); Skip(w) }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Minus(w); Plus(w); Pause(w); Skip(w)
            }
        }
    }
}
