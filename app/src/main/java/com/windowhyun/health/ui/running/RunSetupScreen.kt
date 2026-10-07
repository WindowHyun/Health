package com.windowhyun.health.ui.running

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.core.designsystem.theme.healthColors
import com.windowhyun.health.core.util.formatDuration
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.RunInterval
import com.windowhyun.health.ui.components.HealthButton
import com.windowhyun.health.ui.components.HealthOutlinedButton
import com.windowhyun.health.ui.components.MetricValue
import com.windowhyun.health.ui.components.SectionLabel

/**
 * 러닝 시작 화면.
 *
 * 시작 전에 확인해야 하는 것(권한 · GPS · 목표)을 한 화면에 모아 두고,
 * 조건이 갖춰지면 큰 시작 버튼 하나만 누르면 되도록 한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunSetupScreen(
    onRunStarted: () -> Unit,
    viewModel: RunSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshLocationStatus() }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 걸음 수를 세려면 신체활동 권한이 필요하다. 거부해도 러닝 기록은 된다.
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    // 설정 화면에서 권한이나 GPS 를 바꾸고 돌아올 수 있으므로 매번 다시 확인한다.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshLocationStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 서비스가 기록을 시작하면 바로 진행 화면으로 넘어간다.
    LaunchedEffect(state.tracking.isActive) {
        if (state.tracking.isActive) onRunStarted()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("러닝") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                GpsStatusCard(
                    hasPermission = state.hasLocationPermission,
                    gpsEnabled = state.gpsEnabled,
                    onRequestPermission = { permissionLauncher.launch(requiredPermissions) },
                    onOpenLocationSettings = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                )
            }

            item {
                SectionLabel("러닝 모드")
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunGoalType.entries.forEach { type ->
                        FilterChip(
                            selected = state.goalType == type,
                            onClick = { viewModel.setGoalType(type) },
                            label = { Text(type.label) },
                        )
                    }
                }
            }

            when (state.goalType) {
                RunGoalType.FREE -> item {
                    Text(
                        text = "거리와 시간 제한 없이 기록합니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                RunGoalType.DISTANCE -> item {
                    GoalStepper(
                        label = "목표 거리",
                        number = String.format(java.util.Locale.US, "%.1f", state.goalDistanceKm),
                        unit = "km",
                        onMinus = { viewModel.changeGoalDistance(-0.5) },
                        onPlus = { viewModel.changeGoalDistance(0.5) },
                    )
                }

                RunGoalType.DURATION -> item {
                    GoalStepper(
                        label = "목표 시간",
                        number = "${state.goalDurationMinutes}",
                        unit = "분",
                        onMinus = { viewModel.changeGoalDuration(-5) },
                        onPlus = { viewModel.changeGoalDuration(5) },
                    )
                }
            }

            item {
                IntervalSection(
                    enabled = state.intervalEnabled,
                    interval = state.interval,
                    onEnabledChange = viewModel::setIntervalEnabled,
                    onRunChange = viewModel::changeIntervalRun,
                    onWalkChange = viewModel::changeIntervalWalk,
                    onRoundsChange = viewModel::changeIntervalRounds,
                )
            }

            item {
                Column {
                    Text(
                        text = "자동 Lap: ${state.settings.autoLapMeters}m 마다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = when {
                            !state.stepSensorAvailable -> "걸음 수: 이 기기에는 걸음 센서가 없습니다"
                            !state.stepPermissionGranted ->
                                "걸음 수: 신체활동 권한이 없어 세지 않습니다 (러닝 기록은 정상)"
                            else -> "걸음 수: 기록합니다"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                HealthButton(
                    onClick = viewModel::startRun,
                    enabled = state.canStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                    Text(text = "러닝 시작", style = MaterialTheme.typography.headlineSmall)
                }
            }

            item {
                Text(
                    text = "화면이 꺼져도 기록은 계속됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** 시작 전 점검 띠. 준비되면 라임, 막혀 있으면 오류색 면으로 알린다(글자도 함께 바뀐다). */
@Composable
private fun GpsStatusCard(
    hasPermission: Boolean,
    gpsEnabled: Boolean,
    onRequestPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
) {
    val ready = hasPermission && gpsEnabled
    val container = if (ready) MaterialTheme.healthColors.accent else MaterialTheme.colorScheme.errorContainer
    val content = if (ready) MaterialTheme.healthColors.onAccent else MaterialTheme.colorScheme.onErrorContainer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (ready) Icons.Filled.GpsFixed else Icons.Filled.GpsOff,
                contentDescription = null,
                tint = content,
            )
            Text(
                text = when {
                    !hasPermission -> "위치 권한이 필요합니다"
                    !gpsEnabled -> "위치(GPS)가 꺼져 있습니다"
                    else -> "GPS 준비됨"
                },
                style = MaterialTheme.typography.titleMedium,
                color = content,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (!hasPermission) {
            Text(
                text = "러닝 경로를 기록하려면 위치 권한을 허용해 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = content,
                modifier = Modifier.padding(top = 4.dp),
            )
            HealthOutlinedButton(
                onClick = onRequestPermission,
                compact = true,
                modifier = Modifier.padding(top = 12.dp),
            ) { Text("권한 허용하기", color = content) }
        } else if (!gpsEnabled) {
            Text(
                text = "설정에서 위치를 켜 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = content,
                modifier = Modifier.padding(top = 4.dp),
            )
            HealthOutlinedButton(
                onClick = onOpenLocationSettings,
                compact = true,
                modifier = Modifier.padding(top = 12.dp),
            ) { Text("위치 설정 열기", color = content) }
        }
    }
}

/**
 * 인터벌 설정. 켜면 달리기와 걷기가 바뀔 때마다 진동(과 음성)으로 알려 준다.
 * 화면을 볼 필요 없이 몸으로 구간을 알 수 있어서, 운동 중 조작을 늘리지 않는다.
 */
@Composable
private fun IntervalSection(
    enabled: Boolean,
    interval: RunInterval,
    onEnabledChange: (Boolean) -> Unit,
    onRunChange: (Int) -> Unit,
    onWalkChange: (Int) -> Unit,
    onRoundsChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("인터벌", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "달리기와 걷기를 번갈아 합니다. 바뀔 때마다 진동으로 알려 줍니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (enabled) {
            GoalStepper(
                label = "달리기",
                number = formatDuration(interval.runSeconds.toLong()),
                unit = "",
                onMinus = { onRunChange(-10) },
                onPlus = { onRunChange(10) },
            )
            GoalStepper(
                label = "걷기",
                number = formatDuration(interval.walkSeconds.toLong()),
                unit = "",
                onMinus = { onWalkChange(-10) },
                onPlus = { onWalkChange(10) },
            )
            GoalStepper(
                label = "반복",
                number = "${interval.rounds}",
                unit = "회",
                onMinus = { onRoundsChange(-1) },
                onPlus = { onRoundsChange(1) },
            )
            Text(
                text = "총 ${formatDuration(interval.totalSeconds.toLong())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalStepper(
    label: String,
    number: String,
    unit: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, contentDescription = "줄이기") }
            MetricValue(number = number, unit = unit, modifier = Modifier.padding(horizontal = 4.dp))
            IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, contentDescription = "늘리기") }
        }
    }
}
