package com.windowhyun.health.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.core.util.formatDuration
import com.windowhyun.health.core.util.formatVolume
import com.windowhyun.health.core.util.formatWeight
import com.windowhyun.health.domain.model.WorkoutExerciseRecord
import com.windowhyun.health.domain.model.WorkoutSet
import com.windowhyun.health.ui.components.ConfirmDialog
import com.windowhyun.health.ui.components.Hairline
import com.windowhyun.health.ui.components.HealthOutlinedButton
import com.windowhyun.health.ui.components.MetricValue
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
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // 경과 시간이 이 화면의 제목이다. 숫자 폭을 맞춰 초가 바뀌어도 흔들리지 않는다.
                        Text(
                            text = formatDuration(state.elapsedSeconds),
                            style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp, lineHeight = 32.sp),
                        )
                    }
                },
                actions = {
                    HealthOutlinedButton(
                        onClick = { showFinishDialog = true },
                        compact = true,
                        modifier = Modifier.padding(end = 12.dp),
                    ) { Text("운동 종료") }
                },
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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
            item {
                SessionSummaryRow(
                    completedSets = workout?.totalCompletedSets ?: 0,
                    totalVolume = workout?.totalVolume ?: 0.0,
                    weightUnit = state.settings.weightUnit,
                )
            }

            items(workout?.exercises.orEmpty(), key = { it.id }) { record ->
                ExerciseBlock(
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
                Hairline()
            }

            item {
                HealthOutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("운동 추가")
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
    val volume = formatVolume(totalVolume, weightUnit)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column {
            Text(
                text = "완료 세트",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MetricValue(number = "$completedSets", unit = "세트")
        }
        Column {
            Text(
                text = "총 볼륨",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MetricValue(number = volume.removeSuffix(weightUnit.label), unit = weightUnit.label)
        }
    }
    Hairline()
}

/**
 * 종목 하나. 카드로 감싸지 않고 위아래 가는 선으로만 나눈다.
 * 세트 행이 화면의 주인공이라 머리 부분은 이름과 한 줄 정보만 둔다.
 */
@Composable
private fun ExerciseBlock(
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
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.exercise.name,
                    style = MaterialTheme.typography.headlineSmall,
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
                Icon(Icons.Filled.Delete, contentDescription = "운동 빼기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        LastPerformanceBlock(lastSets = lastSets, weightUnit = weightUnit)

        Spacer(Modifier.height(12.dp))

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
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }

        HealthOutlinedButton(
            onClick = onAddSet,
            compact = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("세트 추가")
        }
    }
}

@Composable
private fun LastPerformanceBlock(lastSets: List<WorkoutSet>, weightUnit: WeightUnit) {
    val text = if (lastSets.isEmpty()) {
        "지난 기록 없음 — 첫 기록을 만들어 보세요."
    } else {
        "지난 운동  " + lastSets.joinToString(" · ") { "${formatWeight(it.weightKg, weightUnit)}×${it.reps}" }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}
