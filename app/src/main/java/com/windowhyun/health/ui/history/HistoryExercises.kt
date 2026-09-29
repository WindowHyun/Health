package com.windowhyun.health.ui.history

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.windowhyun.health.core.util.formatKorean
import com.windowhyun.health.domain.model.ExerciseHistorySummary
import com.windowhyun.health.ui.components.EmptyMessage

/** 기록이 있는 종목 목록. 누르면 종목 상세(성장 그래프 · 전체 기록)로 간다. */
@Composable
internal fun HistoryExerciseList(
    exercises: List<ExerciseHistorySummary>?,
    onOpenExercise: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (exercises != null && exercises.isEmpty()) {
            item {
                EmptyMessage(
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                    title = "아직 종목별 기록이 없습니다",
                    description = "운동을 마치면 종목마다 성장 그래프와 전체 기록이 쌓입니다.",
                )
            }
        }

        items(exercises.orEmpty(), key = { it.exerciseId }) { exercise ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenExercise(exercise.exerciseId) },
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${exercise.bodyPart.label} · 기록 ${exercise.sessionCount}회 · " +
                                "최근 ${exercise.lastDate.formatKorean()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
