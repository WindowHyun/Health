package com.windowhyun.health.ui.running

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.core.designsystem.theme.HugeMetricTextStyle
import com.windowhyun.health.core.designsystem.theme.LargeMetricTextStyle
import com.windowhyun.health.core.designsystem.theme.healthColors
import com.windowhyun.health.core.util.formatDistance
import com.windowhyun.health.core.util.formatDistanceValue
import com.windowhyun.health.core.util.formatDuration
import com.windowhyun.health.core.util.formatPace
import com.windowhyun.health.domain.model.RunStatus
import com.windowhyun.health.ui.components.Hairline
import com.windowhyun.health.ui.components.HealthButton
import com.windowhyun.health.ui.components.HealthOutlinedButton
import com.windowhyun.health.ui.components.MetricValue
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
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    GpsIndicator(accuracyMeters = tracking.lastAccuracyMeters, signalLost = tracking.signalLost)
                }
                HealthOutlinedButton(onClick = { showMap = !showMap }, compact = true) {
                    Icon(
                        imageVector = if (showMap) Icons.Filled.Numbers else Icons.Filled.Map,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(text = if (showMap) "기록" else "지도")
                }
            }

            if (tracking.autoPaused) {
                Text(
                    text = "멈춰서 자동 일시정지했어요 · 움직이면 이어집니다",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            tracking.goal.progress(tracking.distanceMeters, tracking.durationSeconds)?.let { progress ->
                GoalProgress(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
                RunStatus.TRACKING -> HealthButton(
                    onClick = viewModel::pause,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp),
                ) {
                    Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(32.dp))
                    Text(text = "일시정지", style = MaterialTheme.typography.headlineSmall)
                }

                RunStatus.PAUSED -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HealthButton(
                        onClick = viewModel::resume,
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(32.dp))
                        Text("계속", style = MaterialTheme.typography.titleLarge)
                    }
                    HealthButton(
                        onClick = viewModel::finish,
                        container = MaterialTheme.colorScheme.error,
                        content = MaterialTheme.colorScheme.onError,
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

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 목표 대비 진행. 가는 구분선 위에 라임 선을 굵게 얹는다. */
@Composable
private fun GoalProgress(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(6.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .background(MaterialTheme.healthColors.accent),
        )
    }
}

/**
 * 큰 숫자 화면. 달리면서 흘깃 보는 용도라 거리를 가장 크게, 그다음 현재 페이스를 크게 둔다.
 * 값 사이는 가는 선으로만 나눈다.
 */
@Composable
private fun MetricsPane(state: RunActiveUiState, modifier: Modifier = Modifier) {
    val tracking = state.tracking
    val unit = state.settings.distanceUnit
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        BigMetric(
            label = "거리",
            number = formatDistanceValue(tracking.distanceMeters, unit),
            unit = unit.label,
            size = 96.sp,
        )
        Hairline(Modifier.padding(vertical = 12.dp))
        BigMetric(
            label = "현재 페이스",
            number = formatPace(tracking.currentPaceSecPerKm, unit),
            unit = "/${unit.label}",
            size = 64.sp,
        )
        Hairline(Modifier.padding(vertical = 12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            MetricColumn(label = "시간", value = formatDuration(tracking.durationSeconds), modifier = Modifier.weight(1f))
            MetricColumn(
                label = "평균 페이스",
                value = formatPace(tracking.averagePaceSecPerKm, unit),
                modifier = Modifier.weight(1f),
            )
        }

        if (tracking.stepCountAvailable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                MetricColumn(
                    label = "걸음",
                    value = String.format(Locale.US, "%,d", tracking.steps),
                    modifier = Modifier.weight(1f),
                )
                MetricColumn(
                    label = "케이던스",
                    value = "${tracking.cadenceStepsPerMinute} spm",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (tracking.laps.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Lap",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Hairline(Modifier.padding(top = 8.dp))
            // 최근 Lap 이 위로 오게 뒤집어 보여 준다.
            tracking.laps.reversed().take(3).forEach { lap ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${lap.lapNumber}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = formatDistance(lap.distanceMeters, unit),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = formatPace(lap.paceSecPerKm, unit),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Hairline()
            }
        }
    }
}

/** 지도 화면. 지도 아래에 핵심 숫자만 둔다. */
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
        ) {
            MetricColumn(
                label = "거리",
                value = formatDistance(tracking.distanceMeters, state.settings.distanceUnit),
                modifier = Modifier.weight(1f),
            )
            MetricColumn(
                label = "페이스",
                value = formatPace(tracking.currentPaceSecPerKm, state.settings.distanceUnit),
                modifier = Modifier.weight(1f),
            )
            if (tracking.stepCountAvailable) {
                MetricColumn(
                    label = "걸음",
                    value = String.format(Locale.US, "%,d", tracking.steps),
                    modifier = Modifier.weight(1f),
                )
            } else {
                MetricColumn(
                    label = "시간",
                    value = formatDuration(tracking.durationSeconds),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 가장 크게 보이는 값 하나(거리 · 현재 페이스). 큰 숫자에 작은 단위를 붙인다. */
@Composable
private fun BigMetric(label: String, number: String, unit: String, size: TextUnit) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MetricValue(
            number = number,
            unit = unit,
            numberStyle = HugeMetricTextStyle.copy(fontSize = size, lineHeight = size),
        )
    }
}

@Composable
private fun MetricColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = LargeMetricTextStyle)
    }
}

@Composable
private fun GpsIndicator(accuracyMeters: Float?, signalLost: Boolean) {
    if (signalLost) {
        // 거리가 쌓이지 않는 것을 모르고 계속 달리지 않도록 눈에 띄게 알린다.
        Text(
            text = "위치를 받지 못하고 있어요\n거리가 기록되지 않습니다",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val good = accuracyMeters != null && accuracyMeters <= 20f
        Icon(
            imageVector = if (good) Icons.Filled.GpsFixed else Icons.Filled.GpsNotFixed,
            contentDescription = null,
            tint = if (good) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
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
