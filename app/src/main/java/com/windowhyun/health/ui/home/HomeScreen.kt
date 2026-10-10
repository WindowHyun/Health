package com.windowhyun.health.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.windowhyun.health.ui.components.runTitle
import com.windowhyun.health.ui.components.StatusStrip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.core.designsystem.theme.healthColors
import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.core.util.formatDurationKorean
import com.windowhyun.health.core.util.formatPace
import com.windowhyun.health.core.util.formatVolume
import com.windowhyun.health.core.util.startOfWeek
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.RunStatus
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.usecase.GoalProgress
import com.windowhyun.health.ui.components.AccentButton
import com.windowhyun.health.ui.components.Hairline
import com.windowhyun.health.ui.components.MetricValue
import com.windowhyun.health.ui.components.OutlineBlockButton
import com.windowhyun.health.ui.components.PanelButton
import com.windowhyun.health.ui.components.SectionLabel
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * 홈. 목표는 "앱을 켜고 두 번 안에 운동을 시작하는 것"이다.
 * 그래서 시작할 수 있는 것(오늘의 루틴 · 헬스 · 러닝)을 화면 위쪽에 크게 둔다.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onStartWorkout: (Long) -> Unit,
    onOpenGym: () -> Unit,
    onOpenRunning: () -> Unit,
    onOpenRunResult: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onOpenRun: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startedWorkoutId.collectLatest { onStartWorkout(it) }
    }

    val recent = remember(state.recentWorkouts, state.recentRuns, state.settings) {
        recentRecords(state.recentWorkouts, state.recentRuns, state.settings.weightUnit, state.settings.distanceUnit)
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
        ) {
            item {
                Header(
                    today = state.today,
                    pipSpaceEnabled = state.settings.pipSpaceEnabled,
                    onTogglePipSpace = { viewModel.setPipSpaceEnabled(!state.settings.pipSpaceEnabled) },
                    onOpenSettings = onOpenSettings,
                )
            }

            item {
                Spacer(Modifier.height(28.dp))
                WeekSummary(state, onOpenSettings)
            }

            // 값을 먼저 꺼내 둔다. 카드 안에서 state 를 다시 읽으면, 운동이 끝나 값이 비는 순간
            // 목록이 카드를 빼기 전에 카드가 먼저 다시 그려져 빈 값을 만날 수 있다.
            val activeWorkout = state.activeWorkout
            if (activeWorkout != null) {
                item {
                    Spacer(Modifier.height(32.dp))
                    StatusStrip(
                        label = "진행 중인 운동",
                        title = activeWorkout.displayName,
                        action = "이어하기",
                        onClick = { viewModel.startWorkout(null) },
                    )
                }
            }

            if (state.activeRun.isActive) {
                item {
                    Spacer(Modifier.height(if (activeWorkout != null) 12.dp else 32.dp))
                    StatusStrip(
                        label = if (state.activeRun.status == RunStatus.PAUSED) "러닝 일시정지" else "러닝 기록 중",
                        title = distanceParts(state.activeRun.distanceMeters, state.settings.distanceUnit).let { (n, u) ->
                            "$n$u · ${formatDurationKorean(state.activeRun.durationSeconds)}"
                        },
                        action = "돌아가기",
                        onClick = onOpenRunning,
                    )
                }
            }

            // 알림의 종료 버튼으로 끝낸 러닝은 결과 화면을 거치지 않았다. 여기서 이어서 볼 수 있다.
            if (state.activeRun.status == RunStatus.FINISHED) {
                item {
                    Spacer(Modifier.height(if (activeWorkout != null) 12.dp else 32.dp))
                    StatusStrip(
                        label = "방금 끝낸 러닝",
                        title = distanceParts(state.activeRun.distanceMeters, state.settings.distanceUnit).let { (n, u) ->
                            "$n$u · ${formatDurationKorean(state.activeRun.durationSeconds)}"
                        },
                        action = "결과 보기",
                        onClick = onOpenRunResult,
                    )
                }
            }

            items(state.todayRoutines, key = { "routine-${it.id}" }) { routine ->
                Spacer(Modifier.height(if (routine == state.todayRoutines.first()) 32.dp else 12.dp))
                TodayRoutine(
                    routine = routine,
                    enabled = state.activeWorkout == null,
                    onStart = { viewModel.startWorkout(routine.id) },
                )
            }

            item {
                Spacer(Modifier.height(if (state.todayRoutines.isEmpty()) 32.dp else 12.dp))
                StartTiles(onOpenGym = onOpenGym, onOpenRunning = onOpenRunning)
            }

            item {
                Spacer(Modifier.height(40.dp))
                SectionLabel("최근 기록")
                Spacer(Modifier.height(10.dp))
                Hairline()
            }

            if (recent.isEmpty()) {
                item {
                    Text(
                        text = "아직 기록이 없습니다. 루틴을 만들고 첫 운동을 시작해 보세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            } else {
                // 헬스와 러닝은 id 가 따로 매겨져 같은 숫자가 나온다(헬스 1번, 러닝 1번).
                // 한 목록에서 키가 겹치면 그리는 순간 앱이 죽으므로 종류를 붙인다.
                items(recent, key = { it.key }) { record ->
                    RecentRow(
                        record = record,
                        onClick = {
                            when (record) {
                                is RecentRecord.Gym -> onOpenWorkout(record.id)
                                is RecentRecord.Running -> onOpenRun(record.id)
                            }
                        },
                    )
                    Hairline()
                }
            }
        }
    }
}

@Composable
private fun Header(
    today: LocalDate,
    pipSpaceEnabled: Boolean,
    onTogglePipSpace: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.padding(top = 12.dp)) {
            Text(
                text = "${today.year}년 ${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = String.format(Locale.US, "%02d.%02d", today.monthValue, today.dayOfMonth),
                style = MaterialTheme.typography.displayMedium,
                // 큰 글자는 왼쪽 여백이 있어 위의 작은 글자보다 안쪽에서 시작해 보인다. 눈으로 맞춘다.
                modifier = Modifier.offset(x = (-2).dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // PiP 자리 켜기/끄기. 켜져 있으면 라임 면에 검은 아이콘으로 바뀐다(글자 설명도 함께 바뀐다).
            val colors = MaterialTheme.healthColors
            IconButton(
                onClick = onTogglePipSpace,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(if (pipSpaceEnabled) colors.accent else Color.Transparent),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PictureInPictureAlt,
                    contentDescription = if (pipSpaceEnabled) "PiP 자리 끄기" else "PiP 자리 켜기",
                    tint = if (pipSpaceEnabled) colors.onAccent else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "설정")
            }
        }
    }
}

/** 이번 주 요약: 세 값을 가는 세로선으로 나누고, 그 아래에 요일별로 운동한 날을 막대로 보여 준다. */
@Composable
private fun WeekSummary(state: HomeUiState, onOpenSettings: () -> Unit) {
    val weekly = state.weekly
    val (distance, distanceUnit) = distanceParts(weekly.runDistanceMeters, state.settings.distanceUnit)
    val hours = weekly.totalDurationSeconds / 3600
    val minutes = (weekly.totalDurationSeconds % 3600) / 60

    Column {
        SectionLabel("이번 주")
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            WeekMetric("헬스", Modifier.weight(1f)) { MetricValue("${weekly.workoutCount}", "회") }
            VerticalHairline()
            WeekMetric("러닝", Modifier.weight(1.2f).padding(start = 14.dp)) { MetricValue(distance, distanceUnit) }
            VerticalHairline()
            WeekMetric("운동 시간", Modifier.weight(1.4f).padding(start = 14.dp)) {
                Row {
                    if (hours > 0) {
                        MetricValue("$hours", "시간")
                        Spacer(Modifier.width(6.dp))
                    }
                    MetricValue("$minutes", "분")
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        WeekStrip(today = state.today, activeDays = weekly.activeDays)
        Spacer(Modifier.height(16.dp))
        WeeklyGoals(state, onOpenSettings)
    }
}

/**
 * 주간 목표 진행. 목표를 하나도 안 정했으면 정하러 가는 작은 문구만 둔다(홈을 어지럽히지 않는다).
 * 달성하면 막대가 라임으로 가득 차고 글자도 "달성"으로 바뀐다(색에만 기대지 않는다).
 */
@Composable
private fun WeeklyGoals(state: HomeUiState, onOpenSettings: () -> Unit) {
    val settings = state.settings
    val workout = GoalProgress.of(state.weekly.workoutCount.toDouble(), settings.weeklyWorkoutGoal.toDouble())
    val run = GoalProgress.of(state.weekly.runDistanceMeters, settings.weeklyRunGoalMeters.toDouble())

    if (workout == null && run == null) {
        Text(
            text = "이번 주 목표 정하기",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onOpenSettings)
                .padding(vertical = 8.dp),
        )
        return
    }

    val unit = settings.distanceUnit
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (workout != null) {
            GoalRow(
                label = "헬스",
                text = "${workout.current.toInt()} / ${workout.goal.toInt()}회",
                progress = workout,
            )
        }
        if (run != null) {
            val current = String.format(Locale.US, "%.1f", unit.fromMeters(run.current))
            val goal = unit.fromMeters(run.goal).let { String.format(Locale.US, "%.0f", it) }
            GoalRow(label = "러닝", text = "$current / $goal${unit.label}", progress = run)
        }
    }
}

@Composable
private fun GoalRow(label: String, text: String, progress: GoalProgress) {
    val colors = MaterialTheme.healthColors
    val summary = if (progress.achieved) "$label 목표 달성, $text" else "$label $text"
    Column(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summary }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (progress.achieved) "달성 · $text" else text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (progress.achieved) FontWeight.ExtraBold else FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.fraction)
                    .height(6.dp)
                    .background(colors.accent),
            )
        }
    }
}

@Composable
private fun WeekMetric(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        value()
    }
}

@Composable
private fun VerticalHairline() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** 일주일을 칸 일곱으로. 운동한 날은 라임, 오늘은 테두리로 표시한다(색만으로 구분하지 않는다). */
@Composable
private fun WeekStrip(today: LocalDate, activeDays: Set<LocalDate>) {
    val start = today.startOfWeek()
    val days = remember(start) { List(7) { start.plusDays(it.toLong()) } }
    val doneNames = days.filter { it in activeDays }
        .joinToString(", ") { it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN) }
    val description = if (doneNames.isEmpty()) "이번 주 운동한 날 없음" else "이번 주 운동한 날: $doneNames"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val colors = MaterialTheme.healthColors
        days.forEach { day ->
            val isToday = day == today
            val done = day in activeDays
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.KOREAN),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(if (done) colors.accent else MaterialTheme.colorScheme.outlineVariant)
                        .then(
                            if (isToday) {
                                Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.extraSmall)
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

/** 오늘 예정된 루틴. 화면에서 유일하게 어두운 덩어리라 "지금 할 것"이 먼저 보인다. */
@Composable
private fun TodayRoutine(routine: Routine, enabled: Boolean, onStart: () -> Unit) {
    val colors = MaterialTheme.healthColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(colors.panel)
            .padding(20.dp),
    ) {
        Text("오늘의 루틴", style = MaterialTheme.typography.labelMedium, color = colors.onPanel.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        Text(routine.name, style = MaterialTheme.typography.headlineMedium, color = colors.onPanel)
        Text(
            text = "운동 ${routine.exerciseCount}개 · ${routine.totalSets}세트",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onPanel.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(20.dp))
        AccentButton(text = "시작", onClick = onStart, enabled = enabled, modifier = Modifier.fillMaxWidth())
    }
}

/** 헬스 · 러닝을 바로 시작하는 큰 두 칸. 헬스는 라임 면, 러닝은 테두리. */
@Composable
private fun StartTiles(onOpenGym: () -> Unit, onOpenRunning: () -> Unit) {
    val colors = MaterialTheme.healthColors
    val ink = MaterialTheme.colorScheme.onSurface
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StartTile(
            title = "헬스",
            caption = "운동 시작",
            container = colors.accent,
            content = colors.onAccent,
            borderColor = null,
            onClick = onOpenGym,
            modifier = Modifier.weight(1f),
        )
        StartTile(
            title = "러닝",
            caption = "기록 시작",
            container = Color.Transparent,
            content = ink,
            borderColor = ink,
            onClick = onOpenRunning,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StartTile(
    title: String,
    caption: String,
    container: Color,
    content: Color,
    borderColor: Color?,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val shape = MaterialTheme.shapes.small
    Column(
        modifier = modifier
            .height(116.dp)
            .clip(shape)
            .background(container)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = content)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(caption, style = MaterialTheme.typography.labelLarge, color = content)
            Text("→", style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), color = content)
        }
    }
}

/** 최근 기록 한 줄. 헬스와 러닝을 한 목록에 섞어 시간 순으로 보여 준다. */
private sealed interface RecentRecord {
    val key: String
    val startTime: Long
    val kind: String
    val date: LocalDate
    val title: String
    val detail: String
    val number: String
    val unit: String

    data class Gym(
        val id: Long,
        override val startTime: Long,
        override val date: LocalDate,
        override val title: String,
        override val detail: String,
        override val number: String,
        override val unit: String,
    ) : RecentRecord {
        override val key get() = "workout-$id"
        override val kind get() = "헬스"
    }

    data class Running(
        val id: Long,
        override val startTime: Long,
        override val date: LocalDate,
        override val title: String,
        override val detail: String,
        override val number: String,
        override val unit: String,
    ) : RecentRecord {
        override val key get() = "run-$id"
        override val kind get() = "러닝"
    }
}

private const val RECENT_ROWS = 5

private fun recentRecords(
    workouts: List<Workout>,
    runs: List<Run>,
    weightUnit: WeightUnit,
    distanceUnit: DistanceUnit,
): List<RecentRecord> {
    val gym = workouts.map { workout ->
        val volume = formatVolume(workout.totalVolume, weightUnit)
        RecentRecord.Gym(
            id = workout.id,
            startTime = workout.startTime,
            date = workout.date,
            title = workout.displayName,
            detail = "${formatDurationKorean(workout.durationSeconds)} · ${workout.totalCompletedSets}세트",
            number = volume.removeSuffix(weightUnit.label),
            unit = weightUnit.label,
        )
    }
    val running = runs.map { run ->
        val (number, unit) = distanceParts(run.distanceMeters, distanceUnit)
        RecentRecord.Running(
            id = run.id,
            startTime = run.startTime,
            date = run.date,
            title = runTitle(run),
            detail = "${formatDurationKorean(run.durationSeconds)} · 평균 ${formatPace(run.averagePaceSecPerKm, distanceUnit)}",
            number = number,
            unit = unit,
        )
    }
    return (gym + running).sortedByDescending { it.startTime }.take(RECENT_ROWS)
}

@Composable
private fun RecentRow(record: RecentRecord, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(record.kind, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "  ${recentDate(record.date)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(record.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = record.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(12.dp))
        MetricValue(
            number = record.number,
            unit = record.unit,
            numberStyle = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp, lineHeight = 28.sp),
        )
    }
}

private fun recentDate(date: LocalDate): String =
    String.format(Locale.US, "%02d.%02d", date.monthValue, date.dayOfMonth) + " " +
        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.KOREAN)

/** 거리를 숫자와 단위로 나눈다. 큰 숫자에 작은 단위를 붙여 보여 주기 위해서다. */
private fun distanceParts(meters: Double, unit: DistanceUnit): Pair<String, String> =
    String.format(Locale.US, "%.1f", unit.fromMeters(meters)) to unit.label
