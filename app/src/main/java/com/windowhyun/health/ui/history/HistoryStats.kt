package com.windowhyun.health.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.windowhyun.health.core.designsystem.theme.chartColors
import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.core.util.formatDistance
import com.windowhyun.health.core.util.formatVolume
import com.windowhyun.health.domain.usecase.BodyPartStat
import com.windowhyun.health.domain.usecase.TrainingStats
import com.windowhyun.health.domain.usecase.WeekStat
import com.windowhyun.health.ui.components.EmptyMessage
import com.windowhyun.health.ui.components.Hairline
import com.windowhyun.health.ui.components.MetricValue
import com.windowhyun.health.ui.components.SectionLabel
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

private val ChartHeight = 96.dp

/** 기록 탭의 통계. 기간을 고르면 주간 추이와 부위별 비중을 보여 준다. */
@Composable
internal fun HistoryStats(
    state: StatsUiState,
    onSelectRange: (StatsRange) -> Unit,
) {
    val stats = state.stats
    val colors = chartColors()
    val weightUnit = state.settings.weightUnit
    val distanceUnit = state.settings.distanceUnit

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatsRange.entries.forEach { range ->
                    FilterChip(
                        selected = range == state.range,
                        onClick = { onSelectRange(range) },
                        label = { Text(range.label) },
                    )
                }
            }
        }

        if (stats == null) return@LazyColumn

        if (stats.isEmpty) {
            item {
                EmptyMessage(
                    title = "이 기간의 기록이 없습니다",
                    description = "운동이나 러닝을 마치면 주간 추이와 부위별 비중이 여기에 쌓입니다.",
                )
            }
            return@LazyColumn
        }

        item { StatsSummary(stats, weightUnit, distanceUnit) }

        item {
            WeeklyChart(
                title = "주간 볼륨",
                weeks = stats.weeks,
                value = { it.volumeKg },
                format = { formatVolume(it, weightUnit) },
                color = colors.gym,
            )
        }

        if (stats.runCount > 0) {
            item {
                WeeklyChart(
                    title = "주간 러닝 거리",
                    weeks = stats.weeks,
                    value = { it.runDistanceMeters },
                    format = { formatDistance(it, distanceUnit) },
                    color = colors.run,
                )
            }
        }

        if (stats.bodyParts.isNotEmpty()) {
            item { BodyPartSection(stats.bodyParts, colors.gym) }
        }
    }
}

@Composable
private fun StatsSummary(stats: TrainingStats, weightUnit: WeightUnit, distanceUnit: DistanceUnit) {
    val volume = formatVolume(stats.totalVolumeKg, weightUnit)
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            SummaryCell("헬스", "${stats.workoutCount}", "회")
            SummaryCell("러닝", "${stats.runCount}", "회")
            SummaryCell("주당", String.format(Locale.US, "%.1f", stats.workoutsPerWeek), "회")
        }
        Spacer(12)
        Text(
            text = "총 볼륨 $volume · ${stats.totalSets}세트" +
                if (stats.runCount > 0) " · 러닝 ${formatDistance(stats.runDistanceMeters, distanceUnit)}" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(20)
        Hairline()
    }
}

@Composable
private fun SummaryCell(label: String, number: String, unit: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MetricValue(number = number, unit = unit)
    }
}

/**
 * 주마다 막대 하나. 색은 한 가지로 두고 가장 큰 주의 값만 글자로 적는다.
 * 막대마다 숫자를 달면 읽을 수 없고, 정확한 값은 접근성 설명에 담는다.
 */
@Composable
private fun WeeklyChart(
    title: String,
    weeks: List<WeekStat>,
    value: (WeekStat) -> Double,
    format: (Double) -> String,
    color: Color,
) {
    val values = weeks.map(value)
    val peak = values.maxOrNull() ?: 0.0
    val latest = values.lastOrNull() ?: 0.0

    Column(modifier = Modifier.padding(top = 24.dp)) {
        SectionLabel(title)
        Spacer(4)
        Text(
            text = "최고 ${format(peak)} · 이번 주 ${format(latest)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(12)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            weeks.forEachIndexed { index, week ->
                val amount = values[index]
                val fraction = if (peak > 0) (amount / peak).toFloat() else 0f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { contentDescription = "${week.weekStart.weekLabel()} 주 ${format(amount)}" },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // 값이 있는 주는 아무리 작아도 보이도록 최소 높이를 둔다. 0 은 가는 바닥선만.
                    val barHeight = if (amount > 0) maxOf(ChartHeight * fraction, 3.dp) else 1.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .background(
                                color = if (amount > 0) color else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                            ),
                    )
                }
            }
        }
        Spacer(6)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = weeks.first().weekStart.weekLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "이번 주",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(20)
        Hairline()
    }
}

@Composable
private fun BodyPartSection(parts: List<BodyPartStat>, color: Color) {
    val totalSets = parts.sumOf { it.sets }.coerceAtLeast(1)
    val maxSets = parts.maxOf { it.sets }.coerceAtLeast(1)

    Column(modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)) {
        SectionLabel("부위별 세트")
        Spacer(4)
        Text(
            text = "워밍업을 뺀 완료 세트 기준입니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(12)
        parts.forEach { part ->
            val percent = (part.sets * 100f / totalSets).roundToInt()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .semantics { contentDescription = "${part.bodyPart.label} ${part.sets}세트 $percent%" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = part.bodyPart.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.width(52.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(part.sets.toFloat() / maxSets)
                            .height(10.dp)
                            .background(color, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                    )
                }
                Text(
                    text = "${part.sets}세트 · $percent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .width(84.dp)
                        .padding(start = 8.dp),
                )
            }
        }
    }
}

private fun LocalDate.weekLabel(): String = "${monthValue}/${dayOfMonth}"

@Composable
private fun Spacer(heightDp: Int) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(heightDp.dp))
}
