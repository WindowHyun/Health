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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.windowhyun.health.core.util.formatDurationKorean
import com.windowhyun.health.core.util.formatKoreanFull
import com.windowhyun.health.core.util.formatVolume
import com.windowhyun.health.core.util.formatWeight
import com.windowhyun.health.ui.components.ConfirmDialog
import com.windowhyun.health.ui.session.SetRow

/** 과거 헬스 기록 상세. 세트 값과 메모를 수정할 수 있다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    onBack: () -> Unit,
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(
                        text = workout?.date?.formatKoreanFull().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${formatDurationKorean(workout?.durationSeconds ?: 0)} · " +
                            "${workout?.totalCompletedSets ?: 0}세트 · " +
                            "${workout?.totalReps ?: 0}회 · " +
                            formatVolume(workout?.totalVolume ?: 0.0, state.settings.weightUnit),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            if (state.personalRecords.isNotEmpty()) {
                items(state.personalRecords, key = { "${it.exerciseId}-${it.type}" }) { record ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = null)
                            Text(
                                text = "${record.exerciseName} · ${record.type.label} " +
                                    formatWeight(record.value, state.settings.weightUnit),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }

            items(workout?.exercises.orEmpty(), key = { it.id }) { record ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = record.exercise.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${record.completedSets.size}세트 · " +
                                formatVolume(record.totalVolume, state.settings.weightUnit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        record.sets.forEach { set ->
                            SetRow(
                                set = set,
                                weightUnit = state.settings.weightUnit,
                                onValuesChange = { weightKg, reps ->
                                    viewModel.updateSet(set, weightKg, reps)
                                },
                                onToggleCompleted = { _, _ -> },
                                onRemove = { viewModel.deleteSet(set.id) },
                                modifier = Modifier.padding(vertical = 2.dp),
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
