package com.windowhyun.health.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import com.windowhyun.health.ui.components.PersonalRecordsPanel
import com.windowhyun.health.ui.components.StatCard
import com.windowhyun.health.ui.components.Hairline
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.core.util.formatDurationKorean
import com.windowhyun.health.core.util.formatKoreanFull
import com.windowhyun.health.core.util.formatVolume
import com.windowhyun.health.core.util.formatWeight
import com.windowhyun.health.ui.components.ConfirmDialog
import com.windowhyun.health.ui.session.SetRow
import com.windowhyun.health.ui.share.ShareCardDialog
import com.windowhyun.health.ui.share.WorkoutCardData
import com.windowhyun.health.ui.share.WorkoutShareCard

/** 과거 헬스 기록 상세. 세트 값과 메모를 수정할 수 있다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    onBack: () -> Unit,
    onOpenExercise: (Long) -> Unit,
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCard by remember { mutableStateOf(false) }
    val workout = state.workout

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workout?.displayName ?: "운동 기록") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.saveMemo()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    // 이미 끝난 운동도 정리 카드로 만들어 저장 · 공유할 수 있다.
                    IconButton(onClick = { showCard = true }, enabled = workout != null) {
                        Icon(Icons.Filled.Share, contentDescription = "정리 카드 만들기")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "기록 삭제")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = workout?.date?.formatKoreanFull().orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatCard(
                            label = "운동 시간",
                            value = formatDurationKorean(workout?.durationSeconds ?: 0),
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            label = "총 볼륨",
                            value = formatVolume(workout?.totalVolume ?: 0.0, state.settings.weightUnit),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatCard(
                            label = "세트",
                            value = "${workout?.totalCompletedSets ?: 0}",
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            label = "반복",
                            value = "${workout?.totalReps ?: 0}회",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (state.personalRecords.isNotEmpty()) {
                item {
                    PersonalRecordsPanel(records = state.personalRecords, weightUnit = state.settings.weightUnit)
                }
            }

            items(workout?.exercises.orEmpty(), key = { it.id }) { record ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Hairline()
                    Column(modifier = Modifier.padding(vertical = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = record.exercise.name,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.weight(1f),
                            )
                            // 이 종목의 지난 기록 전체와 성장 그래프로 간다.
                            TextButton(onClick = { onOpenExercise(record.exercise.id) }) {
                                Text("기록 · 그래프")
                            }
                        }
                        Text(
                            // 위쪽 합계처럼 워밍업은 세지 않는다.
                            text = "${record.countedSets.size}세트 · " +
                                formatVolume(record.totalVolume, state.settings.weightUnit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        record.sets.forEach { set ->
                            SetRow(
                                set = set,
                                weightUnit = state.settings.weightUnit,
                                trackingType = record.exercise.trackingType,
                                onValuesChange = { weightKg, reps, duration ->
                                    viewModel.updateSet(set, weightKg, reps, duration)
                                },
                                // 이미 끝난 기록이라 완료 버튼도, 완료 색칠도 없다.
                                showComplete = false,
                                onToggleCompleted = { _, _, _ -> },
                                onCycleSetType = { viewModel.cycleSetType(set) },
                                onRemove = { viewModel.deleteSet(set.id) },
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.memo,
                    onValueChange = viewModel::setMemo,
                    label = { Text("운동 메모") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
            }
        }
    }

    if (showCard && workout != null) {
        val data = remember(workout, state.personalRecords, state.settings.weightUnit) {
            WorkoutCardData.from(workout, state.personalRecords, state.settings.weightUnit)
        }
        ShareCardDialog(
            fileName = "health-workout-${workout.date}",
            description = "${data.title} ${data.durationText}, 볼륨 ${data.volumeText}, ${data.totalSets}세트",
            onDismiss = { showCard = false },
        ) { WorkoutShareCard(data) }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "기록 삭제",
            message = "이 운동 기록을 삭제할까요? 되돌릴 수 없습니다.",
            confirmLabel = "삭제",
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteWorkout()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}
