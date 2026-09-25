package com.windowhyun.health.ui.running

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.util.formatDistance
import com.windowhyun.health.core.util.formatDuration
import com.windowhyun.health.core.util.formatPace
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.RunLap
import com.windowhyun.health.ui.components.StatCard
import java.util.Locale

/**
 * 러닝 결과와 러닝 상세가 같은 내용을 보여 주므로, 화면이 갈라지지 않도록
 * 지표·경로·Lap 을 한곳에 모아 두고 양쪽에서 가져다 쓴다.
 */

/** 시간·페이스·칼로리, 그리고 걸음 센서가 잡혔으면 걸음·케이던스·보폭까지. */
@Composable
fun RunStatGrid(
    run: Run,
    distanceUnit: DistanceUnit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                label = "총 시간",
                value = formatDuration(run.durationSeconds),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "평균 페이스",
                value = formatPace(run.averagePaceSecPerKm),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                label = "최고 페이스",
                value = formatPace(run.bestPaceSecPerKm),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "칼로리",
                value = "${run.calories}kcal",
                modifier = Modifier.weight(1f),
            )
        }
        if (run.steps > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    label = "걸음",
                    value = String.format(Locale.US, "%,d", run.steps),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "케이던스",
                    value = "${run.cadenceStepsPerMinute} spm",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "보폭",
                    value = String.format(Locale.US, "%.2f m", run.strideMeters),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // 목표를 걸고 뛴 러닝이면 얼마나 채웠는지 같이 보여 준다.
        RunGoalLine(run = run, distanceUnit = distanceUnit)
    }
}

@Composable
private fun RunGoalLine(run: Run, distanceUnit: DistanceUnit) {
    val goal = runGoalText(run, distanceUnit) ?: return
    Text(
        text = goal,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 목표 대비 달성률. 자유 러닝이면 null. */
internal fun runGoalText(run: Run, distanceUnit: DistanceUnit): String? {
    if (run.goalValue <= 0.0) return null
    return when (run.goalType) {
        com.windowhyun.health.domain.model.RunGoalType.FREE -> null
        com.windowhyun.health.domain.model.RunGoalType.DISTANCE -> {
            val percent = (run.distanceMeters / run.goalValue * 100).toInt()
            "목표 ${formatDistance(run.goalValue, distanceUnit)} 중 $percent% 달성"
        }
        com.windowhyun.health.domain.model.RunGoalType.DURATION -> {
            val percent = (run.durationSeconds / run.goalValue * 100).toInt()
            "목표 ${formatDuration(run.goalValue.toLong())} 중 $percent% 달성"
        }
    }
}

/** 지도와, 타일을 못 받았을 때의 안내 문구. */
@Composable
fun RunRouteSection(run: Run, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        RunRouteMap(
            route = run.route,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        val tileStatus by rememberMapTileStatus(hasRoute = run.route.isNotEmpty())
        tileStatus?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * 구간별 페이스. 숫자만 나열하면 어디서 처졌는지 한눈에 안 들어와서
 * 가장 빠른 구간을 꽉 찬 막대로 두고 상대 길이로 그린다.
 */
fun LazyListScope.runLapItems(
    laps: List<RunLap>,
    distanceUnit: DistanceUnit,
) {
    if (laps.isEmpty()) return
    val paces = laps.map { it.paceSecPerKm }.filter { it > 0 }
    val fastest = paces.minOrNull() ?: 0.0
    val slowest = paces.maxOrNull() ?: 0.0

    items(laps, key = { it.lapNumber }) { lap ->
        // 느린 구간도 최소 35% 는 채워서 막대가 사라지지 않게 한다.
        val ratio = when {
            lap.paceSecPerKm <= 0.0 -> 0f
            slowest <= fastest -> 1f
            else -> (0.35 + 0.65 * (slowest - lap.paceSecPerKm) / (slowest - fastest)).toFloat()
        }
        LapRow(
            lap = lap,
            distanceUnit = distanceUnit,
            ratio = ratio,
            isFastest = lap.paceSecPerKm > 0 && lap.paceSecPerKm == fastest && laps.size > 1,
        )
    }
}

@Composable
private fun LapRow(
    lap: RunLap,
    distanceUnit: DistanceUnit,
    ratio: Float,
    isFastest: Boolean,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${lap.lapNumber}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
            )
            Text(
                text = formatPace(lap.paceSecPerKm),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isFastest) FontWeight.Bold else FontWeight.Medium,
                color = if (isFastest) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.width(72.dp),
            )
            PaceBar(ratio = ratio, highlight = isFastest, modifier = Modifier.weight(1f))
            Text(
                text = formatDuration(lap.durationSeconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        if (lap.distanceMeters > 0) {
            Text(
                text = formatDistance(lap.distanceMeters, distanceUnit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp),
            )
        }
    }
}

@Composable
private fun PaceBar(ratio: Float, highlight: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (highlight) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    },
                ),
        )
    }
}
