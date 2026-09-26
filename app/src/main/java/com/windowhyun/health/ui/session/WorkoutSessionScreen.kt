package com.windowhyun.health.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.core.util.formatDuration
import com.windowhyun.health.core.util.formatVolume
import com.windowhyun.health.core.util.formatWeight
import com.windowhyun.health.domain.model.WorkoutExerciseRecord
import com.windowhyun.health.domain.model.WorkoutSet
import com.windowhyun.health.ui.components.ConfirmDialog
import com.windowhyun.health.ui.gym.ExercisePickerSheet
import kotlinx.coroutines.flow.collectLatest

/**
 * 헬스 운동 진행 화면.
 *
 * 화면 조작을 줄이기 위해:
 * - 지난 기록을 기본값으로 이미 채워 둔다
 * - 세트 완료는 큰 버튼 한 번
 * - 완료하면 휴식 타이머가 알아서 뜬다
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSessionScreen(
    onFinished: (Long) -> Unit,
    onDiscarded: () -> Unit,
    viewModel: WorkoutSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val restTimer by viewModel.restTimer.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()

    var showFinishDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is WorkoutSessionEvent.Finished -> onFinished(event.workoutId)
                WorkoutSessionEvent.Discarded -> onDiscarded()
            }
        }
    }

    // 뒤로가기로 실수로 나가지 않도록 확인을 받는다.
    BackHandler { showFinishDialog = true }

    val workout = state.workout

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = workout?.displayName ?: "운동",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = formatDuration(state.elapsedSeconds),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showFinishDialog = true }) { Text("운동 종료") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        },
        bottomBar = {
            if (restTimer.visible) {
                RestTimerBar(
                    state = restTimer,
                    onAdjust = viewModel::adjustRestTimer,
                    onTogglePause = viewModel::toggleRestPause,
                    onSkip = viewModel::stopRestTimer,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SessionSummaryRow(
                    completedSets = workout?.totalCompletedSets ?: 0,
                    totalVolume = workout?.totalVolume ?: 0.0,
                    weightUnit = state.settings.weightUnit,
                )
            }

            items(workout?.exercises.orEmpty(), key = { it.id }) { record ->
                ExerciseCard(
                    record = record,
                    lastSets = state.lastPerformance[record.exercise.id].orEmpty(),
                    weightUnit = state.settings.weightUnit,
                    defaultRestSeconds = state.settings.defaultRestSeconds,
                    onValuesChange = viewModel::updateSetValues,
                    onToggleCompleted = { set, weightKg, reps, duration ->
                        viewModel.toggleSetCompleted(set, weightKg, reps, duration, record.restSeconds)
                    },
                    onCycleSetType = viewModel::cycleSetType,
                    onAddSet = { viewModel.addSet(record.id) },
                    onRemoveSet = viewModel::removeSet,
                    onRemoveExercise = { viewModel.removeExercise(record.id) },
                    onStartRest = { seconds -> viewModel.startRestTimer(seconds) },
                )
            }

            item {
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("운동 추가", modifier = Modifier.padding(start = 8.dp))
                }
            }

            item {
                TextButton(
                    onClick = { showDiscardDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("이 운동 기록 버리기", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            exercises = exercises,
            onPick = {
                viewModel.addExercise(it.id)
                showPicker = false
            },
            onCreate = { name, category, bodyPart, trackingType ->
                viewModel.createAndAddExercise(name, category, bodyPart, trackingType)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }

    if (showFinishDialog) {
        ConfirmDialog(
            title = "운동을 종료할까요?",
            message = "완료하지 않은 세트는 저장되지 않습니다.",
            confirmLabel = "종료",
            onConfirm = {
                showFinishDialog = false
                viewModel.finishWorkout()
            },
            onDismiss = { showFinishDialog = false },
        )
    }

    if (showDiscardDialog) {
        ConfirmDialog(
            title = "기록을 버릴까요?",
            message = "이번 운동의 기록이 모두 삭제됩니다. 되돌릴 수 없습니다.",
            confirmLabel = "버리기",
            onConfirm = {
                showDiscardDialog = false
                viewModel.discardWorkout()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }
}

@Composable
private fun SessionSummaryRow(completedSets: Int, totalVolume: Double, weightUnit: WeightUnit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("완료 세트", style = MaterialTheme.typography.labelMedium)
            Text("$completedSets", style = MaterialTheme.typography.titleLarge)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("총 볼륨", style = MaterialTheme.typography.labelMedium)
            Text(formatVolume(totalVolume, weightUnit), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ExerciseCard(
    record: WorkoutExerciseRecord,
    lastSets: List<WorkoutSet>,
    weightUnit: WeightUnit,
    defaultRestSeconds: Int,
    onValuesChange: (WorkoutSet, Double, Int, Int) -> Unit,
    onToggleCompleted: (WorkoutSet, Double, Int, Int) -> Unit,
    onCycleSetType: (WorkoutSet) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Long) -> Unit,
    onRemoveExercise: () -> Unit,
    onStartRest: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.exercise.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${record.exercise.bodyPart.label} · 휴식 ${record.restSeconds ?: defaultRestSeconds}초",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onStartRest(record.restSeconds ?: defaultRestSeconds) }) {
                    Text("휴식 시작")
                }
                IconButton(onClick = onRemoveExercise) {
                    Icon(Icons.Filled.Delete, contentDescription = "운동 빼기")
                }
            }

            LastPerformanceBlock(lastSets = lastSets, weightUnit = weightUnit)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "오늘",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            record.sets.forEach { set ->
                SetRow(
                    set = set,
                    weightUnit = weightUnit,
                    trackingType = record.exercise.trackingType,
                    onValuesChange = { weightKg, reps, duration ->
                        onValuesChange(set, weightKg, reps, duration)
                    },
                    onToggleCompleted = { weightKg, reps, duration ->
                        onToggleCompleted(set, weightKg, reps, duration)
                    },
                    onCycleSetType = { onCycleSetType(set) },
                    onRemove = { onRemoveSet(set.id) },
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }

            Button(
                onClick = onAddSet,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("세트 추가", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun LastPerformanceBlock(lastSets: List<WorkoutSet>, weightUnit: WeightUnit) {
    if (lastSets.isEmpty()) {
        Text(
            text = "지난 기록 없음 — 첫 기록을 만들어 보세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 8.dp),
        )
        return
    }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = "지난 운동",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        lastSets.forEach { set ->
            Text(
                text = "${formatWeight(set.weightKg, weightUnit)} × ${set.reps}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
