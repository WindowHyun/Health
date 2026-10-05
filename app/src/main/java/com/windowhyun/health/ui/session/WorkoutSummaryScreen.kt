package com.windowhyun.health.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import com.windowhyun.health.ui.share.ShareCardSection
import com.windowhyun.health.ui.share.WorkoutCardData
import com.windowhyun.health.ui.share.WorkoutShareCard
import com.windowhyun.health.ui.components.HealthButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
            // 운동이 끝나면 한 장으로 정리한 카드를 바로 보여 주고, 저장 · 공유할 수 있게 한다.
            if (workout != null) {
                item {
                    val data = remember(workout, state.personalRecords, state.settings.weightUnit) {
                        WorkoutCardData.from(workout, state.personalRecords, state.settings.weightUnit)
                    }
                    ShareCardSection(
                        fileName = "health-workout-${workout.date}",
                        description = "${data.title} ${data.durationText}, 볼륨 ${data.volumeText}, ${data.totalSets}세트",
                    ) { WorkoutShareCard(data) }
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
