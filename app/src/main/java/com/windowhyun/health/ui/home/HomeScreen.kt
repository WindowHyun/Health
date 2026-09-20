package com.windowhyun.health.ui.home

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.core.util.formatDistance
import com.windowhyun.health.core.util.formatDurationKorean
import com.windowhyun.health.core.util.formatKorean
import com.windowhyun.health.core.util.formatPace
import com.windowhyun.health.core.util.formatVolume
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.RunStatus
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.ui.components.EmptyMessage
import com.windowhyun.health.ui.components.SectionHeader
import com.windowhyun.health.ui.components.StatCard
import kotlinx.coroutines.flow.collectLatest

/**
 * 홈. 목표는 "앱을 켜고 두 번 안에 운동을 시작하는 것"이다.
 * 그래서 시작 버튼을 화면 위쪽 큰 영역에 둔다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onStartWorkout: (Long) -> Unit,
    onOpenGym: () -> Unit,
    onOpenRunning: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.startedWorkoutId.collectLatest { onStartWorkout(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = state.today.formatKorean(), style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "오늘도 기록해 봅시다",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "설정")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { WeeklySummaryRow(state) }

            if (state.activeWorkout != null) {
                item {
                    ResumeWorkoutCard(
                        workout = state.activeWorkout!!,
                        onResume = { viewModel.startWorkout(null) },
                    )
                }
            }

            if (state.activeRun.isActive) {
                item {
                    ActiveRunCard(
                        distanceText = formatDistance(
                            state.activeRun.distanceMeters,
                            state.settings.distanceUnit,
                        ),
                        durationText = formatDurationKorean(state.activeRun.durationSeconds),
                        paused = state.activeRun.status == RunStatus.PAUSED,
                        onOpen = onOpenRunning,
                    )
                }
            }

            item {
                StartButtons(
                    todayRoutines = state.todayRoutines,
                    hasActiveWorkout = state.activeWorkout != null,
                    onStartRoutine = { routineId -> viewModel.startWorkout(routineId) },
                    onOpenGym = onOpenGym,
                    onOpenRunning = onOpenRunning,
                )
            }

            item {
                SectionHeader(
                    title = "최근 헬스 기록",
                    trailing = {
                        if (state.recentWorkouts.isNotEmpty()) {
                            Text(
                                text = "${state.recentWorkouts.size}건",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
            if (state.recentWorkouts.isEmpty()) {
                item {
                    EmptyMessage(
                        icon = Icons.Filled.FitnessCenter,
                        title = "아직 헬스 기록이 없습니다",
                        description = "루틴을 만들고 첫 운동을 시작해 보세요.",
                    )
                }
            } else {
                items(state.recentWorkouts, key = { it.id }) { workout ->
                    RecentWorkoutCard(
                        workout = workout,
                        weightUnitLabel = state.settings.weightUnit,
                        onClick = { onOpenWorkout(workout.id) },
                    )
                }
            }

            item { SectionHeader(title = "최근 러닝 기록") }
            if (state.recentRuns.isEmpty()) {
                item {
                    EmptyMessage(
                        icon = Icons.AutoMirrored.Filled.DirectionsRun,
                        title = "아직 러닝 기록이 없습니다",
                        description = "러닝 탭에서 GPS 기록을 시작할 수 있습니다.",
                    )
                }
            } else {
                items(state.recentRuns, key = { it.id }) { run ->
                    RecentRunCard(run = run, distanceUnit = state.settings.distanceUnit)
                }
            }

            item { androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun WeeklySummaryRow(state: HomeUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(
            label = "이번 주 헬스",
            value = "${state.weekly.workoutCount}회",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "이번 주 러닝",
            value = formatDistance(state.weekly.runDistanceMeters, state.settings.distanceUnit),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "총 운동시간",
            value = formatDurationKorean(state.weekly.totalDurationSeconds),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ResumeWorkoutCard(workout: Workout, onResume: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("진행 중인 운동", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = workout.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Button(onClick = onResume) { Text("이어하기") }
        }
    }
}

@Composable
private fun ActiveRunCard(
    distanceText: String,
    durationText: String,
    paused: Boolean,
    onOpen: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (paused) "러닝 일시정지" else "러닝 기록 중",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "$distanceText · $durationText",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Button(onClick = onOpen) { Text("돌아가기") }
        }
    }
}

@Composable
private fun StartButtons(
    todayRoutines: List<Routine>,
    hasActiveWorkout: Boolean,
    onStartRoutine: (Long?) -> Unit,
    onOpenGym: () -> Unit,
    onOpenRunning: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (todayRoutines.isNotEmpty()) {
            Text("오늘 예정된 루틴", style = MaterialTheme.typography.titleMedium)
            todayRoutines.forEach { routine ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = routine.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "운동 ${routine.exerciseCount}개 · ${routine.totalSets}세트",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(
                            onClick = { onStartRoutine(routine.id) },
                            enabled = !hasActiveWorkout,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text("시작", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }

        Button(
            onClick = onOpenGym,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Icon(Icons.Filled.FitnessCenter, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                text = "헬스 운동 시작",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        OutlinedButton(
            onClick = onOpenRunning,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.outlinedButtonColors(),
        ) {
            Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                text = "러닝 시작",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun RecentWorkoutCard(
    workout: Workout,
    weightUnitLabel: com.windowhyun.health.core.model.WeightUnit,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = workout.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = workout.date.formatKorean(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${formatDurationKorean(workout.durationSeconds)} · " +
                    "${workout.totalCompletedSets}세트 · " +
                    formatVolume(workout.totalVolume, weightUnitLabel),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun RecentRunCard(run: Run, distanceUnit: com.windowhyun.health.core.model.DistanceUnit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = formatDistance(run.distanceMeters, distanceUnit),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = run.date.formatKorean(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${formatDurationKorean(run.durationSeconds)} · " +
                    "평균 ${formatPace(run.averagePaceSecPerKm)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
