package com.windowhyun.health.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import com.windowhyun.health.ui.components.PersonalRecordsPanel
import com.windowhyun.health.ui.components.HealthButton
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.core.util.formatDurationKorean
import com.windowhyun.health.core.util.formatVolume
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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Text(
                    text = workout?.displayName ?: "운동",
                    style = MaterialTheme.typography.headlineLarge,
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    Text(
                        text = "새로운 개인 기록 ${state.personalRecords.size}개",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                item {
                    PersonalRecordsPanel(records = state.personalRecords, weightUnit = state.settings.weightUnit)
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
                HealthButton(
                    onClick = close,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) { Text("저장하고 닫기") }
            }
        }
    }
}
