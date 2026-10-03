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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import com.windowhyun.health.ui.components.runTitle
import com.windowhyun.health.ui.components.MetricValue
import com.windowhyun.health.ui.components.Hairline
import com.windowhyun.health.core.util.formatDistanceValue
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.core.util.formatDurationKorean
import com.windowhyun.health.core.util.formatKoreanFull
import com.windowhyun.health.core.util.formatPace
import com.windowhyun.health.core.util.formatTimeOfDay
import com.windowhyun.health.core.util.formatVolume
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.ui.components.EmptyMessage

/**
 * 기록 탭. 목록 · 캘린더 · 종목 세 가지로 본다.
 *
 * - 목록: 헬스와 러닝을 날짜 역순으로.
 * - 캘린더: 한 달을 한눈에. 날짜를 누르면 그날 기록.
 * - 종목: 기록이 있는 종목. 누르면 성장 그래프와 전체 기록.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenWorkout: (Long) -> Unit,
    onOpenRun: (Long) -> Unit,
    onOpenExercise: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("기록") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                HistoryMode.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = mode == option,
                        onClick = { viewModel.setMode(option) },
                        // 알약 모양 대신 각진 모서리로, 선택 표시(체크)는 검정 면으로 대신한다.
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = HistoryMode.entries.size,
                            baseShape = MaterialTheme.shapes.small,
                        ),
                        icon = {},
                    ) { Text(option.label) }
                }
            }

            when (mode) {
                HistoryMode.LIST -> HistoryList(
                    state = state,
                    onFilter = viewModel::setFilter,
                    onLoadOlder = viewModel::loadOlder,
                    onOpenWorkout = onOpenWorkout,
                    onOpenRun = onOpenRun,
                )

                HistoryMode.CALENDAR -> {
                    val calendar by viewModel.calendar.collectAsStateWithLifecycle()
                    HistoryCalendar(
                        state = calendar,
                        settings = state.settings,
                        onPreviousMonth = viewModel::showPreviousMonth,
                        onNextMonth = viewModel::showNextMonth,
                        onThisMonth = viewModel::showThisMonth,
                        onSelectDate = viewModel::selectDate,
                        onOpenWorkout = onOpenWorkout,
                        onOpenRun = onOpenRun,
                    )
                }

                HistoryMode.EXERCISES -> {
                    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
                    HistoryExerciseList(exercises = exercises, onOpenExercise = onOpenExercise)
                }
            }
        }
    }
}

@Composable
private fun HistoryList(
    state: HistoryUiState,
    onFilter: (HistoryFilter) -> Unit,
    onLoadOlder: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onOpenRun: (Long) -> Unit,
) {
    val entries = state.visibleEntries
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    ) {
        // 기록이 둘 다 없으면 필터를 보여 줄 이유가 없다.
        if (state.totalCount > 0) {
            item {
                FilterChips(
                    selected = state.filter,
                    gymCount = state.gymCount,
                    runCount = state.runCount,
                    total = state.totalCount,
                    onSelect = onFilter,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }

        if (!state.loading && entries.isEmpty() && state.olderCount == 0) {
            item {
                EmptyMessage(
                    title = if (state.totalCount == 0) {
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
            Column {
                if (isFirstOfDay) {
                    Text(
                        text = entry.date.formatKoreanFull(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                    )
                }
                HistoryEntryCard(
                    entry = entry,
                    settings = state.settings,
                    onOpenWorkout = onOpenWorkout,
                    onOpenRun = onOpenRun,
                )
            }
        }

        if (state.olderCount > 0) {
            item(key = "load-older") {
                TextButton(onClick = onLoadOlder, modifier = Modifier.fillMaxWidth()) {
                    Text("이전 기록 ${state.olderCount}개 더 보기")
                }
            }
        }
    }
}

/** 기록 한 건 카드. 목록과 캘린더가 같이 쓴다. */
@Composable
internal fun HistoryEntryCard(
    entry: HistoryEntry,
    settings: AppSettings,
    onOpenWorkout: (Long) -> Unit,
    onOpenRun: (Long) -> Unit,
) {
    when (entry) {
        is HistoryEntry.Gym -> GymEntryCard(
            workout = entry.workout,
            weightUnit = settings.weightUnit,
            onClick = { onOpenWorkout(entry.workout.id) },
        )

        is HistoryEntry.Running -> RunEntryCard(
            run = entry.run,
            distanceUnit = settings.distanceUnit,
            onClick = { onOpenRun(entry.run.id) },
        )
    }
}

@Composable
private fun FilterChips(
    selected: HistoryFilter,
    gymCount: Int,
    runCount: Int,
    total: Int,
    onSelect: (HistoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
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
    val volume = formatVolume(workout.totalVolume, weightUnit)
    EntryRow(
        kind = "헬스",
        time = "${workout.startTime.formatTimeOfDay()} 시작",
        title = workout.displayName,
        detail = "${formatDurationKorean(workout.durationSeconds)} · " +
            "${workout.performedExerciseCount}개 운동 · ${workout.totalCompletedSets}세트",
        number = volume.removeSuffix(weightUnit.label),
        unit = weightUnit.label,
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
    EntryRow(
        kind = "러닝",
        time = "${run.startTime.formatTimeOfDay()} 시작",
        title = runTitle(run),
        detail = "${formatDurationKorean(run.durationSeconds)} · " +
            "평균 ${formatPace(run.averagePaceSecPerKm, distanceUnit)} · ${run.calories}kcal",
        number = formatDistanceValue(run.distanceMeters, distanceUnit),
        unit = distanceUnit.label,
        memo = run.memo,
        onClick = onClick,
    )
}

/**
 * 기록 한 줄. 카드 없이 위에 가는 선만 긋는다. 종류와 시각은 작은 글자로,
 * 그 기록을 대표하는 값(볼륨 · 거리)은 오른쪽에 크게 둔다.
 */
@Composable
private fun EntryRow(
    kind: String,
    time: String,
    title: String,
    detail: String,
    number: String,
    unit: String,
    memo: String?,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row {
                    Text(kind, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "  $time",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!memo.isNullOrBlank()) {
                    Text(
                        text = "\"$memo\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            MetricValue(
                number = number,
                unit = unit,
                modifier = Modifier.padding(start = 12.dp),
                numberStyle = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp, lineHeight = 28.sp),
            )
        }
    }
}
