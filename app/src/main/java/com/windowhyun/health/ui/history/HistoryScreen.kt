package com.windowhyun.health.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.core.util.formatDurationKorean
import com.windowhyun.health.core.util.formatKoreanFull
import com.windowhyun.health.core.util.formatTimeOfDay
import com.windowhyun.health.core.util.formatVolume
import com.windowhyun.health.ui.components.EmptyMessage

/** 기록 탭. Phase 1 에서는 날짜 역순 목록만 제공한다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenWorkout: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("기록") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.loading && state.workouts.isEmpty()) {
                item {
                    EmptyMessage(
                        icon = Icons.Filled.CalendarMonth,
                        title = "저장된 기록이 없습니다",
                        description = "운동을 완료하면 여기에 날짜별로 쌓입니다.",
                    )
                }
            }

            items(state.workouts, key = { it.id }) { workout ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenWorkout(workout.id) },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = workout.date.formatKoreanFull(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = workout.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${workout.startTime.formatTimeOfDay()} 시작 · " +
                                formatDurationKorean(workout.durationSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${workout.performedExerciseCount}개 운동 · " +
                                "${workout.totalCompletedSets}세트 · " +
                                formatVolume(workout.totalVolume, state.settings.weightUnit),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (!workout.memo.isNullOrBlank()) {
                            Text(
                                text = "\"${workout.memo}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
