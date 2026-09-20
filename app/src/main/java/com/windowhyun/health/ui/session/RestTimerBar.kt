package com.windowhyun.health.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.windowhyun.health.core.designsystem.theme.LargeMetricTextStyle
import com.windowhyun.health.core.util.formatDuration

/**
 * 휴식 타이머 바. 세트를 완료하면 화면 하단에 자동으로 나타난다.
 * 버튼은 크게, 개수는 최소로(+10 / -10 / 일시정지 / 건너뛰기).
 */
@Composable
fun RestTimerBar(
    state: RestTimerState,
    onAdjust: (Int) -> Unit,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (state.paused) "휴식 일시정지" else "휴식",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = formatDuration(state.remainingSeconds.toLong()),
                    style = LargeMetricTextStyle,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { onAdjust(-10) }, modifier = Modifier.weight(1f)) {
                    Text("-10초")
                }
                OutlinedButton(onClick = { onAdjust(10) }, modifier = Modifier.weight(1f)) {
                    Text("+10초")
                }
                OutlinedButton(onClick = onTogglePause, modifier = Modifier.weight(1f)) {
                    Text(if (state.paused) "계속" else "일시정지")
                }
                Button(onClick = onSkip, modifier = Modifier.weight(1f)) {
                    Text("건너뛰기")
                }
            }
        }
    }
}
