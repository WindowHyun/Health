package com.windowhyun.health.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.windowhyun.health.core.model.PersonalRecord
import com.windowhyun.health.core.model.PersonalRecordType
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.core.util.formatDurationKorean
import com.windowhyun.health.core.util.formatPersonalRecordValue
import com.windowhyun.health.core.util.formatVolume
import com.windowhyun.health.core.util.formatWeight
import com.windowhyun.health.ui.components.StatCard

/** 운동 종료 요약. 새 PR 은 가장 눈에 띄게 보여 준다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSummaryScreen(
    onClose: () -> Unit,
    viewModel: WorkoutSummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val workout = state.workout

    val close = {
        viewModel.saveMemo()
        onClose()
    }
    BackHandler { close() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("운동 완료") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = workout?.displayName ?: "운동",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        label = "총 운동시간",
                        value = formatDurationKorean(workout?.durationSeconds ?: 0),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "완료한 운동",
                        value = "${workout?.performedExerciseCount ?: 0}개",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        label = "총 세트",
                        value = "${workout?.totalCompletedSets ?: 0}",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "총 반복",
                        value = "${workout?.totalReps ?: 0}회",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "총 볼륨",
                        value = formatVolume(workout?.totalVolume ?: 0.0, state.settings.weightUnit),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (state.personalRecords.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "새로운 개인 기록 ${state.personalRecords.size}개",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                items(state.personalRecords, key = { "${it.exerciseId}-${it.type}" }) { record ->
                    PersonalRecordCard(record = record, weightUnit = state.settings.weightUnit)
                }
            }

            item {
                OutlinedTextField(
                    value = state.memo,
                    onValueChange = viewModel::setMemo,
                    label = { Text("운동 메모") },
                    placeholder = { Text("예) 벤치 60kg 가벼웠음. 다음엔 62.5kg 도전.") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
            }

            item {
                Button(
                    onClick = close,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) { Text("저장하고 닫기", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
private fun PersonalRecordCard(record: PersonalRecord, weightUnit: WeightUnit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = record.exerciseName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(text = record.type.label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = formatPersonalRecordValue(record.type, record.value, weightUnit),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            record.previousValue?.let { previous ->
                Text(
                    text = "이전 기록 " + formatPersonalRecordValue(record.type, previous, weightUnit),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (record.type == PersonalRecordType.MAX_ESTIMATED_ONE_RM &&
                record.weightKg != null && record.reps != null
            ) {
                Text(
                    text = "${formatWeight(record.weightKg, weightUnit)} × ${record.reps} 기준 (Epley)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
