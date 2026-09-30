package com.windowhyun.health.ui.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.model.SetType
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.core.util.formatDuration
import com.windowhyun.health.core.util.formatKoreanFull
import com.windowhyun.health.core.util.formatPersonalRecordValue
import com.windowhyun.health.core.util.formatVolume
import com.windowhyun.health.core.util.formatWeight
import com.windowhyun.health.core.util.formatWeightValue
import com.windowhyun.health.domain.model.ExerciseSession
import com.windowhyun.health.domain.model.ProgressMetric
import com.windowhyun.health.domain.model.WorkoutSet
import com.windowhyun.health.ui.components.EmptyMessage
import com.windowhyun.health.ui.components.StatCard
import java.util.Locale

/**
 * 종목 상세: 현재 기록 → 성장 그래프 → 전체 기록.
 *
 * 전체 기록 목록이 그래프의 표 역할도 한다(그래프에 적지 않은 값을 모두 볼 수 있다).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unit = state.settings.weightUnit
    val exercise = state.exercise

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exercise?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
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
            if (exercise != null) {
                item {
                    Text(
                        text = "${exercise.bodyPart.label} · ${exercise.trackingType.label} · " +
                            "기록 ${state.sessions.size}회",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!state.loading && state.sessions.isEmpty()) {
                item {
                    EmptyMessage(
                        icon = Icons.AutoMirrored.Filled.ShowChart,
                        title = "아직 기록이 없습니다",
                        description = "이 종목으로 운동을 마치면 여기에 기록과 그래프가 쌓입니다.",
                    )
                }
                return@LazyColumn
            }

            if (state.records.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.records.forEach { record ->
                            StatCard(
                                label = record.type.label,
                                value = formatPersonalRecordValue(record.type, record.value, unit),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item {
                Text("성장 그래프", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (state.metrics.size > 1) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.metrics.forEach { metric ->
                            FilterChip(
                                selected = metric == state.metric,
                                onClick = { viewModel.selectMetric(metric) },
                                label = { Text(metric.label) },
                            )
                        }
                    }
                }
            }

            item {
                val metric = state.metric
                val points = state.points
                if (metric == null || points.size < 2) {
                    Text(
                        text = "기록이 두 번 이상 쌓이면 그래프가 그려집니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val shown = points.map { it.copy(value = metric.toDisplay(it.value, unit)) }
                    val first = shown.first().value
                    val last = shown.last().value
                    val best = shown.maxOf { it.value }
                    ProgressChart(
                        points = shown,
                        formatValue = { metric.format(it, unit) },
                        formatTick = { metric.formatTick(it) },
                        contentDescription = "${metric.label} 그래프. 기록 ${shown.size}회, " +
                            "처음 ${metric.format(first, unit)}, 최근 ${metric.format(last, unit)}, " +
                            "최고 ${metric.format(best, unit)}",
                    )
                    Text(
                        text = "점을 누르면 그날 값이 보입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Text(
                    "전체 기록 ${state.sessions.size}회",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            items(state.sessions, key = { it.workoutId }) { session ->
                SessionCard(
                    session = session,
                    trackingType = exercise?.trackingType ?: ExerciseTrackingType.WEIGHT_REPS,
                    unit = unit,
                    onClick = { onOpenWorkout(session.workoutId) },
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: ExerciseSession,
    trackingType: ExerciseTrackingType,
    unit: WeightUnit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = session.date.formatKoreanFull() + (session.routineName?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = sessionSummary(session, trackingType, unit),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            session.sets.forEach { set ->
                Row {
                    Text(
                        text = "${set.setNumber}" + set.setType.shortLabel.let { if (it.isEmpty()) "" else " $it" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(40.dp),
                    )
                    Text(
                        text = setText(set, trackingType, unit) +
                            if (set.setType == SetType.WARMUP) "  (워밍업 · 집계 제외)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (set.setType == SetType.WARMUP) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

private fun sessionSummary(session: ExerciseSession, type: ExerciseTrackingType, unit: WeightUnit): String =
    when (type) {
        ExerciseTrackingType.WEIGHT_REPS ->
            "최고 ${formatWeight(session.maxWeightKg, unit)} · 1RM ${formatWeight(session.bestOneRepMaxKg, unit)} · " +
                "볼륨 ${formatVolume(session.volumeKg, unit)}"
        ExerciseTrackingType.REPS_ONLY -> "최고 ${session.maxReps}회 · 총 ${session.totalReps}회"
        ExerciseTrackingType.TIME -> "최고 ${formatDuration(session.maxDurationSeconds.toLong())}"
    }

private fun setText(set: WorkoutSet, type: ExerciseTrackingType, unit: WeightUnit): String = when (type) {
    ExerciseTrackingType.WEIGHT_REPS -> "${formatWeight(set.weightKg, unit)} × ${set.reps}회"
    ExerciseTrackingType.REPS_ONLY -> "${set.reps}회"
    ExerciseTrackingType.TIME -> formatDuration(set.durationSeconds.toLong())
}

// ----- 지표별 표기 -----

private val ProgressMetric.isWeight: Boolean
    get() = this == ProgressMetric.ESTIMATED_ONE_RM ||
        this == ProgressMetric.MAX_WEIGHT ||
        this == ProgressMetric.VOLUME

/** 저장 단위(kg · 회 · 초)를 화면 단위로. 무게만 kg/lb 를 바꾼다. */
internal fun ProgressMetric.toDisplay(value: Double, unit: WeightUnit): Double =
    if (isWeight) unit.fromKg(value) else value

/** 화면 단위 값을 글자로. */
internal fun ProgressMetric.format(displayValue: Double, unit: WeightUnit): String = when (this) {
    ProgressMetric.ESTIMATED_ONE_RM, ProgressMetric.MAX_WEIGHT ->
        formatWeightValue(displayValue) + unit.label
    // 기록 카드와 같은 표기를 쓴다(1,000 미만은 소수까지). 따로 만들면 값이 달라 보인다.
    ProgressMetric.VOLUME -> formatVolume(unit.toKg(displayValue), unit)
    ProgressMetric.MAX_REPS, ProgressMetric.TOTAL_REPS -> "${displayValue.toInt()}회"
    ProgressMetric.MAX_DURATION -> formatDuration(displayValue.toLong())
}

/** 눈금은 단위 없이 짧게. 천 단위 쉼표, 시간은 분:초. */
internal fun ProgressMetric.formatTick(displayValue: Double): String = when (this) {
    ProgressMetric.MAX_DURATION -> formatDuration(displayValue.toLong())
    ProgressMetric.ESTIMATED_ONE_RM, ProgressMetric.MAX_WEIGHT -> formatWeightValue(displayValue)
    else -> String.format(Locale.US, "%,.0f", displayValue)
}
