package com.windowhyun.health.ui.session

import com.windowhyun.health.core.model.SetType
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.core.util.formatDuration
import com.windowhyun.health.core.util.formatVolume
import com.windowhyun.health.core.util.formatWeight
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.model.WorkoutExerciseRecord
import com.windowhyun.health.domain.model.WorkoutSet
import com.windowhyun.health.domain.usecase.SupersetGroups
import com.windowhyun.health.ui.components.ConfirmDialog
import com.windowhyun.health.ui.components.Hairline
import com.windowhyun.health.ui.components.HealthOutlinedButton
import com.windowhyun.health.ui.components.MetricValue
import com.windowhyun.health.ui.components.liftAboveKeyboard
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
    // 확인 없이 지워지던 종목: 완료한 세트가 있으면 먼저 묻는다.
    var pendingRemoveExercise by remember { mutableStateOf<WorkoutExerciseRecord?>(null) }
    val snackbarHost = remember { SnackbarHostState() }

    // 운동 중에는 화면이 꺼지지 않게 한다(설정에서 끌 수 있다). 세트 사이 휴식 때마다 잠금을 푸는 일이 없게 한다.
    val view = LocalView.current
    val keepScreenOn = state.settings.keepScreenOnDuringWorkout
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Android 13 이상에서는 알림 권한이 있어야 휴식 종료 알림이 뜬다. 헬스만 쓰는 사람은 러닝 화면을 거치지 않으므로
    // 운동 화면에서 한 번만 묻는다(거절해도 진동은 울리고, 다시 묻지 않는다).
    val context = LocalContext.current
    val askNotification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val settingsLoaded = state.workout != null
    LaunchedEffect(settingsLoaded) {
        if (!settingsLoaded) return@LaunchedEffect
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (shouldAskNotificationPermission(Build.VERSION.SDK_INT, granted, state.settings.notificationPromptShown)) {
            viewModel.markNotificationPrompted()
            askNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is WorkoutSessionEvent.Finished -> onFinished(event.workoutId)
                WorkoutSessionEvent.Discarded -> onDiscarded()
                WorkoutSessionEvent.SetRemoved -> {
                    // 이전 안내가 떠 있으면 바로 새 것으로 바꾼다.
                    snackbarHost.currentSnackbarData?.dismiss()
                    val result = snackbarHost.showSnackbar(
                        message = "세트를 삭제했어요",
                        actionLabel = "실행 취소",
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoRemoveSet()
                }
            }
        }
    }

    // 뒤로가기로 실수로 나가지 않도록 확인을 받는다.
    BackHandler { showFinishDialog = true }

    val workout = state.workout

    // 종목의 마지막 세트를 끝내면 다음 종목이 보이게 화면을 옮긴다. 종목이 줄줄이 이어진 긴 화면이라, 안 옮기면
    // 땀 젖은 손으로 다음 종목을 찾아 내려야 한다. 같은 종목 안에서 세트를 끝낼 때는 옮기지 않는다(화면이 튀지 않게).
    val listState = rememberLazyListState()
    val activeExerciseId = activeExerciseId(workout?.exercises.orEmpty())
    var previousActiveExerciseId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(activeExerciseId) {
        val previous = previousActiveExerciseId
        previousActiveExerciseId = activeExerciseId
        if (shouldScrollToActive(previous, activeExerciseId)) {
            val index = workout?.exercises.orEmpty().indexOfFirst { it.id == activeExerciseId }
            // 목록의 첫 칸은 요약 줄이라 종목은 한 칸씩 뒤에 있다.
            if (index >= 0) listState.animateScrollToItem(index + 1)
        }
    }

    Scaffold(
        modifier = Modifier.liftAboveKeyboard(),
        snackbarHost = { SnackbarHost(snackbarHost) },
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
            state = listState,
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

            val records = workout?.exercises.orEmpty()
            val groups = records.map { it.supersetGroup }
            // 세트 종류(워밍업 · 드롭 · 실패)를 바꾸는 방법은 세트 번호를 누르는 것뿐이라, 처음 운동 화면에서는 한 줄로 알려 준다.
            val showSetTypeHint = (workout?.totalCompletedSets ?: 0) == 0 &&
                records.none { record -> record.sets.any { it.setType != SetType.NORMAL } }
            itemsIndexed(records, key = { _, record -> record.id }) { index, record ->
                ExerciseBlock(
                    showSetTypeHint = showSetTypeHint && index == 0,
                    record = record,
                    supersetLabel = SupersetGroups.label(groups, index),
                    restsAfter = SupersetGroups.restAfter(groups, index),
                    canLinkWithPrevious = index > 0 && !SupersetGroups.isLinkedWithPrevious(groups, index),
                    onLinkSuperset = { viewModel.linkSuperset(record.id) },
                    onUnlinkSuperset = { viewModel.unlinkSuperset(record.id) },
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
                    onRemoveExercise = {
                        if (record.completedSets.isEmpty()) viewModel.removeExercise(record.id) else pendingRemoveExercise = record
                    },
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
            // 고른 뒤에도 시트를 닫지 않는다. 여러 종목을 연달아 담을 수 있다(완료 버튼으로 닫는다).
            onPick = { viewModel.addExercise(it.id) },
            onCreate = { name, category, bodyPart, trackingType ->
                viewModel.createAndAddExercise(name, category, bodyPart, trackingType)
                showPicker = false
            },
            onDismiss = { showPicker = false },
            manager = viewModel.exerciseManager,
            addedCounts = workout?.exercises.orEmpty().groupingBy { it.exercise.id }.eachCount(),
        )
    }

    if (showFinishDialog) {
        val exercises = workout?.exercises.orEmpty()
        val completed = exercises.sumOf { it.completedSets.size }
        val unchecked = exercises.sumOf { it.rescuableSets(state.editedSetIds).size }
        val prompt = finishPrompt(completed, incompleteSets = exercises.sumOf { it.sets.size } - completed, uncheckedWithValues = unchecked)
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text(prompt.title) },
            text = { Text(prompt.message) },
            confirmButton = {
                // 기록을 더하는 선택지는 아래에 둔다. 위에 두면 눈에 띄는 첫 버튼을 눌러 하지 않은 세트가 기록되기 쉽다.
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        showFinishDialog = false
                        viewModel.finishWorkout()
                    }) { Text(prompt.confirmLabel) }
                    prompt.completeUncheckedLabel?.let { label ->
                        TextButton(onClick = {
                            showFinishDialog = false
                            viewModel.finishWorkout(completeUnchecked = true)
                        }) { Text(label) }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showFinishDialog = false }) { Text("취소") } },
        )
    }

    pendingRemoveExercise?.let { record ->
        ConfirmDialog(
            title = "종목을 뺄까요?",
            message = removeExercisePrompt(record.exercise.name, record.completedSets.size),
            confirmLabel = "빼기",
            onConfirm = {
                pendingRemoveExercise = null
                viewModel.removeExercise(record.id)
            },
            onDismiss = { pendingRemoveExercise = null },
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
    showSetTypeHint: Boolean,
    record: WorkoutExerciseRecord,
    supersetLabel: String?,
    restsAfter: Boolean,
    canLinkWithPrevious: Boolean,
    onLinkSuperset: () -> Unit,
    onUnlinkSuperset: () -> Unit,
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
                if (supersetLabel != null) {
                    Text(
                        text = "슈퍼셋 $supersetLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = record.exercise.name,
                    style = MaterialTheme.typography.headlineSmall,
                )
                val restText = if (restsAfter) {
                    "휴식 ${record.restSeconds ?: defaultRestSeconds}초"
                } else {
                    "쉬지 않고 다음 운동으로"
                }
                Text(
                    text = "${record.exercise.bodyPart.label} · $restText",
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

        if (canLinkWithPrevious) {
            TextButton(onClick = onLinkSuperset) { Text("앞 운동과 슈퍼셋으로 묶기") }
        } else if (supersetLabel != null) {
            TextButton(onClick = onUnlinkSuperset) { Text("슈퍼셋에서 빼기") }
        }

        LastPerformanceBlock(
            lastSets = lastSets,
            trackingType = record.exercise.trackingType,
            weightUnit = weightUnit,
        )

        if (showSetTypeHint) {
            Text(
                text = "세트 번호를 누르면 워밍업 · 드롭 · 실패로 바뀌어요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        // 지금 할 세트(처음으로 안 끝낸 세트)에만 ± 버튼을 보인다.
        val currentSetId = record.sets.firstOrNull { !it.completed }?.id
        record.sets.forEach { set ->
            SetRow(
                set = set,
                quickAdjust = set.id == currentSetId,
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
private fun LastPerformanceBlock(
    lastSets: List<WorkoutSet>,
    trackingType: ExerciseTrackingType,
    weightUnit: WeightUnit,
) {
    Text(
        text = lastPerformanceText(lastSets, trackingType, weightUnit),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** 지금 하고 있는 종목: 아직 안 끝낸 세트가 남은 첫 종목. 모두 끝났으면 null. */
internal fun activeExerciseId(records: List<WorkoutExerciseRecord>): Long? =
    records.firstOrNull { record -> record.sets.any { !it.completed } }?.id

/**
 * 하고 있는 종목이 바뀌었을 때만 화면을 옮긴다. 처음 그릴 때(이전이 없음)나 모두 끝나 다음이 없을 때는 옮기지 않는다.
 * 종목을 추가해 하고 있는 종목이 새 종목으로 바뀌는 경우도 옮긴다(방금 추가한 곳이 보인다).
 */
internal fun shouldScrollToActive(previous: Long?, current: Long?): Boolean =
    previous != null && current != null && previous != current
/**
 * "지난 운동" 한 줄. 종목의 기록 방식대로 적는다.
 *
 * 시간이나 횟수만 재는 종목은 중량·횟수가 0 으로 저장된다. 전부 "중량×횟수"로 쓰면
 * 플랭크가 "0kg×0", 풀업이 "0kg×12" 로 나온다.
 */
internal fun lastPerformanceText(
    lastSets: List<WorkoutSet>,
    trackingType: ExerciseTrackingType,
    weightUnit: WeightUnit,
): String {
    if (lastSets.isEmpty()) return "지난 기록 없음 — 첫 기록을 만들어 보세요."
    return "지난 운동  " + lastSets.joinToString(" · ") { set ->
        when (trackingType) {
            ExerciseTrackingType.WEIGHT_REPS -> "${formatWeight(set.weightKg, weightUnit)}×${set.reps}"
            ExerciseTrackingType.REPS_ONLY -> "${set.reps}회"
            ExerciseTrackingType.TIME -> formatDuration(set.durationSeconds.toLong())
        }
    }
}
