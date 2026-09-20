package com.windowhyun.health.ui.running

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.domain.model.RunGoalType

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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                Text("러닝 모드", style = MaterialTheme.typography.titleMedium)
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
                        value = String.format(java.util.Locale.US, "%.1f km", state.goalDistanceKm),
                        onMinus = { viewModel.changeGoalDistance(-0.5) },
                        onPlus = { viewModel.changeGoalDistance(0.5) },
                    )
                }

                RunGoalType.DURATION -> item {
                    GoalStepper(
                        label = "목표 시간",
                        value = "${state.goalDurationMinutes}분",
                        onMinus = { viewModel.changeGoalDuration(-5) },
                        onPlus = { viewModel.changeGoalDuration(5) },
                    )
                }
            }

            item {
                Text(
                    text = "자동 Lap: ${state.settings.autoLapMeters}m 마다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Button(
                    onClick = viewModel::startRun,
                    enabled = state.canStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                    Text(
                        text = "러닝 시작",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
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

@Composable
private fun GpsStatusCard(
    hasPermission: Boolean,
    gpsEnabled: Boolean,
    onRequestPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
) {
    val ready = hasPermission && gpsEnabled
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ready) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (ready) Icons.Filled.GpsFixed else Icons.Filled.GpsOff,
                    contentDescription = null,
                )
                Text(
                    text = when {
                        !hasPermission -> "위치 권한이 필요합니다"
                        !gpsEnabled -> "위치(GPS)가 꺼져 있습니다"
                        else -> "GPS 준비됨"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (!hasPermission) {
                Text(
                    text = "러닝 경로를 기록하려면 위치 권한을 허용해 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedButton(
                    onClick = onRequestPermission,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("권한 허용하기") }
            } else if (!gpsEnabled) {
                Text(
                    text = "설정에서 위치를 켜 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedButton(
                    onClick = onOpenLocationSettings,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("위치 설정 열기") }
            }
        }
    }
}

@Composable
private fun GoalStepper(
    label: String,
    value: String,
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
            Text(value, style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, contentDescription = "늘리기") }
        }
    }
}
