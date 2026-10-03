package com.windowhyun.health.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.windowhyun.health.ui.components.Hairline
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    ) {
        if (exercises != null && exercises.isEmpty()) {
            item {
                EmptyMessage(
                    title = "아직 종목별 기록이 없습니다",
                    description = "운동을 마치면 종목마다 성장 그래프와 전체 기록이 쌓입니다.",
                )
            }
        }

        items(exercises.orEmpty(), key = { it.exerciseId }) { exercise ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Hairline()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenExercise(exercise.exerciseId) }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = exercise.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${exercise.bodyPart.label} · 기록 ${exercise.sessionCount}회 · " +
                                "최근 ${exercise.lastDate.formatKorean()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
