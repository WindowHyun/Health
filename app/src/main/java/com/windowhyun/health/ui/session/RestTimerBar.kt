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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val outline = BorderStroke(1.dp, colors.onPanel.copy(alpha = 0.45f))
            HealthButton(
                onClick = { onAdjust(-10) },
                modifier = Modifier.weight(1f),
                container = Color.Transparent,
                content = colors.onPanel,
                border = outline,
                compact = true,
            ) { Text("-10초") }
            HealthButton(
                onClick = { onAdjust(10) },
                modifier = Modifier.weight(1f),
                container = Color.Transparent,
                content = colors.onPanel,
                border = outline,
                compact = true,
            ) { Text("+10초") }
            HealthButton(
                onClick = onTogglePause,
                modifier = Modifier.weight(1f),
                container = Color.Transparent,
                content = colors.onPanel,
                border = outline,
                compact = true,
            ) { Text(if (state.paused) "계속" else "일시정지") }
            HealthButton(onClick = onSkip, modifier = Modifier.weight(1f), compact = true) { Text("건너뛰기") }
        }
    }
}
