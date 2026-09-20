package com.windowhyun.health.ui.running

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.core.designsystem.theme.HugeMetricTextStyle
import com.windowhyun.health.core.designsystem.theme.LargeMetricTextStyle
import com.windowhyun.health.core.util.formatDistance
import com.windowhyun.health.core.util.formatDuration
import com.windowhyun.health.core.util.formatPace
import com.windowhyun.health.domain.model.RunStatus
import java.util.Locale

/**
 * 러닝 진행 화면.
 *
 * 달리면서 보는 화면이므로 거리와 페이스를 가장 크게 두고,
 * 버튼은 화면 아래쪽에 큰 것 하나(일시정지)만 둔다.
 */
@Composable
fun RunActiveScreen(
    onFinished: () -> Unit,
    viewModel: RunActiveViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tracking = state.tracking

    // 기본은 큰 숫자 화면. 지도는 필요할 때만 한 번 눌러서 본다.
    var showMap by rememberSaveable { mutableStateOf(false) }

    // 달리는 동안에는 화면이 꺼지지 않도록 한다.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // 실수로 화면을 벗어나지 않도록 뒤로가기는 막는다(종료는 아래 버튼으로).
    BackHandler(enabled = tracking.isActive) {}

    LaunchedEffect(tracking.status) {
        if (tracking.status == RunStatus.FINISHED) onFinished()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                GpsIndicator(accuracyMeters = tracking.lastAccuracyMeters)
                FilledTonalButton(onClick = { showMap = !showMap }) {
                    Icon(
                        imageVector = if (showMap) Icons.Filled.Numbers else Icons.Filled.Map,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (showMap) "기록" else "지도",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            tracking.goal.progress(tracking.distanceMeters, tracking.durationSeconds)?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showMap) {
                    MapPane(state = state, modifier = Modifier.weight(1f))
                } else {
                    MetricsPane(
                        state = state,
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }

            when (tracking.status) {
                RunStatus.TRACKING -> Button(
                    onClick = viewModel::pause,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp),
                ) {
                    Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(32.dp))
                    Text(
                        text = "일시정지",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                RunStatus.PAUSED -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = viewModel::resume,
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(32.dp))
                        Text("계속", style = MaterialTheme.typography.titleLarge)
                    }
                    Button(
                        onClick = viewModel::finish,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp),
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(32.dp))
                        Text("종료", style = MaterialTheme.typography.titleLarge)
                    }
                }

                else -> Unit
            }

            androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        }
    }
}

/** 큰 숫자 화면. 달리면서 흘깃 보는 용도라 거리와 페이스를 가장 크게 둔다. */
@Composable
private fun MetricsPane(state: RunActiveUiState, modifier: Modifier = Modifier) {
    val tracking = state.tracking
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "거리",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatDistance(tracking.distanceMeters, state.settings.distanceUnit),
            style = HugeMetricTextStyle,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "현재 페이스",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = formatPace(tracking.currentPaceSecPerKm),
            style = HugeMetricTextStyle,
            color = MaterialTheme.colorScheme.primary,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricColumn(label = "시간", value = formatDuration(tracking.durationSeconds))
            MetricColumn(label = "평균 페이스", value = formatPace(tracking.averagePaceSecPerKm))
        }

        if (tracking.stepCountAvailable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MetricColumn(
                    label = "걸음",
                    value = String.format(Locale.US, "%,d", tracking.steps),
                )
                MetricColumn(
                    label = "케이던스",
                    value = "${tracking.cadenceStepsPerMinute} spm",
                )
            }
        }

        if (tracking.laps.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text("Lap", style = MaterialTheme.typography.labelLarge)
                // 최근 Lap 이 위로 오게 뒤집어 보여 준다.
                tracking.laps.reversed().take(3).forEach { lap ->
                    Text(
                        text = "${lap.lapNumber}. ${formatDistance(lap.distanceMeters, state.settings.distanceUnit)}" +
                            "  ${formatPace(lap.paceSecPerKm)}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

/** 지도 화면. 지도 위에 핵심 숫자만 겹쳐 보여 준다. */
@Composable
private fun MapPane(state: RunActiveUiState, modifier: Modifier = Modifier) {
    val tracking = state.tracking
    Column(modifier = modifier.fillMaxWidth()) {
        RunRouteMap(
            route = tracking.route,
            followLatest = true,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricColumn(
                label = "거리",
                value = formatDistance(tracking.distanceMeters, state.settings.distanceUnit),
            )
            MetricColumn(label = "페이스", value = formatPace(tracking.currentPaceSecPerKm))
            if (tracking.stepCountAvailable) {
                MetricColumn(label = "걸음", value = String.format(Locale.US, "%,d", tracking.steps))
            } else {
                MetricColumn(label = "시간", value = formatDuration(tracking.durationSeconds))
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = LargeMetricTextStyle)
    }
}

@Composable
private fun GpsIndicator(accuracyMeters: Float?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val good = accuracyMeters != null && accuracyMeters <= 20f
        Icon(
            imageVector = if (good) Icons.Filled.GpsFixed else Icons.Filled.GpsNotFixed,
            contentDescription = null,
            tint = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = accuracyMeters?.let { "GPS ±${it.toInt()}m" } ?: "GPS 신호 찾는 중",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
