package com.windowhyun.health.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.core.util.formatDistance
import com.windowhyun.health.core.util.formatDurationKorean
import com.windowhyun.health.core.util.formatKoreanFull
import com.windowhyun.health.core.util.formatPace
import com.windowhyun.health.core.util.formatTimeOfDay
import com.windowhyun.health.core.util.formatVolume
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.ui.components.EmptyMessage

/** 기록 탭. 헬스와 러닝을 하나의 목록에 날짜 역순으로 보여 준다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenWorkout: (Long) -> Unit,
    onOpenRun: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val entries = state.visibleEntries

    Scaffold(topBar = { TopAppBar(title = { Text("기록") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 기록이 둘 다 없으면 필터를 보여 줄 이유가 없다.
            if (state.entries.isNotEmpty()) {
                item {
                    FilterChips(
                        selected = state.filter,
                        gymCount = state.gymCount,
                        runCount = state.runCount,
                        total = state.entries.size,
                        onSelect = viewModel::setFilter,
                    )
                }
            }

            if (!state.loading && entries.isEmpty()) {
                item {
                    EmptyMessage(
                        icon = Icons.Filled.CalendarMonth,
                        title = if (state.entries.isEmpty()) {
                            "저장된 기록이 없습니다"
                        } else {
                            "이 종류의 기록이 없습니다"
                        },
                        description = "운동을 완료하면 여기에 날짜별로 쌓입니다.",
                    )
                }
            }

            itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                // 날짜가 바뀌는 지점에만 머리글을 둔다.
                val isFirstOfDay = index == 0 || entries[index - 1].date != entry.date
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isFirstOfDay) {
                        Text(
                            text = entry.date.formatKoreanFull(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when (entry) {
                        is HistoryEntry.Gym -> GymEntryCard(
                            workout = entry.workout,
                            weightUnit = state.settings.weightUnit,
                            onClick = { onOpenWorkout(entry.workout.id) },
                        )

                        is HistoryEntry.Running -> RunEntryCard(
                            run = entry.run,
                            distanceUnit = state.settings.distanceUnit,
                            onClick = { onOpenRun(entry.run.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChips(
    selected: HistoryFilter,
    gymCount: Int,
    runCount: Int,
    total: Int,
    onSelect: (HistoryFilter) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryFilter.entries.forEach { filter ->
            val count = when (filter) {
                HistoryFilter.ALL -> total
                HistoryFilter.GYM -> gymCount
                HistoryFilter.RUNNING -> runCount
            }
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text("${filter.label} $count") },
            )
        }
    }
}

@Composable
private fun GymEntryCard(
    workout: Workout,
    weightUnit: WeightUnit,
    onClick: () -> Unit,
) {
    EntryCard(
        icon = Icons.Filled.FitnessCenter,
        title = workout.displayName,
        subtitle = "${workout.startTime.formatTimeOfDay()} 시작 · " +
            formatDurationKorean(workout.durationSeconds),
        detail = "${workout.performedExerciseCount}개 운동 · " +
            "${workout.totalCompletedSets}세트 · " +
            formatVolume(workout.totalVolume, weightUnit),
        memo = workout.memo,
        onClick = onClick,
    )
}

@Composable
private fun RunEntryCard(
    run: Run,
    distanceUnit: DistanceUnit,
    onClick: () -> Unit,
) {
    EntryCard(
        icon = Icons.AutoMirrored.Filled.DirectionsRun,
        title = formatDistance(run.distanceMeters, distanceUnit),
        subtitle = "${run.startTime.formatTimeOfDay()} 시작 · " +
            formatDurationKorean(run.durationSeconds),
        detail = "평균 ${formatPace(run.averagePaceSecPerKm)} · ${run.calories}kcal",
        memo = run.memo,
        onClick = onClick,
    )
}

@Composable
private fun EntryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    detail: String,
    memo: String?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.CenterVertically),
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (!memo.isNullOrBlank()) {
                    Text(
                        text = "\"$memo\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
