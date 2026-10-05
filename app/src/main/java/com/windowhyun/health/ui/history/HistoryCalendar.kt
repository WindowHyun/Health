package com.windowhyun.health.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.windowhyun.health.core.designsystem.theme.chartColors
import com.windowhyun.health.core.util.FIRST_DAY_OF_WEEK
import com.windowhyun.health.core.util.formatDistance
import com.windowhyun.health.core.util.formatKorean
import com.windowhyun.health.domain.model.AppSettings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * 한 달 캘린더.
 *
 * 운동한 날은 숫자 아래 점으로 표시한다(헬스 파랑 · 러닝 주황). 색만으로 구분하지 않도록
 * 달 위에 범례를 두고, 날짜를 누르면 아래에 아이콘이 붙은 기록 카드가 나온다.
 */
@Composable
internal fun HistoryCalendar(
    state: CalendarUiState,
    settings: AppSettings,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onOpenRun: (Long) -> Unit,
) {
    val colors = chartColors()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    ) {
        item {
            MonthHeader(
                month = state.month,
                canGoNext = state.canGoNext,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
                onThisMonth = onThisMonth,
            )
            Text(
                text = "운동 ${state.workoutCount}회 · 러닝 ${state.runCount}회" +
                    if (state.runCount > 0) " · ${formatDistance(state.runDistanceMeters, settings.distanceUnit)}" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendItem(color = colors.gym, label = "헬스")
                LegendItem(color = colors.run, label = "러닝")
            }
        }

        item {
            Box(modifier = Modifier.padding(vertical = 12.dp)) {
                MonthGrid(
                    month = state.month,
                    selected = state.selectedDate,
                    entriesByDate = state.entriesByDate,
                    gymColor = colors.gym,
                    runColor = colors.run,
                    onSelect = onSelectDate,
                )
            }
        }

        item {
            val selected = state.selectedDate
            Text(
                text = when {
                    selected == null -> "날짜를 누르면 그날 기록이 보입니다."
                    state.selectedEntries.isEmpty() -> "${selected.formatKorean()} · 기록 없음"
                    else -> selected.formatKorean()
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
        }

        items(state.selectedEntries, key = { it.key }) { entry ->
            HistoryEntryCard(
                entry = entry,
                settings = settings,
                onOpenWorkout = onOpenWorkout,
                onOpenRun = onOpenRun,
            )
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisMonth: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달")
        }
        Text(
            text = "${month.year}년 ${month.monthValue}월",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달")
        }
        Spacer(modifier = Modifier.weight(1f))
        if (month != YearMonth.now()) {
            TextButton(onClick = onThisMonth) { Text("이번 달") }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate?,
    entriesByDate: Map<LocalDate, List<HistoryEntry>>,
    gymColor: Color,
    runColor: Color,
    onSelect: (LocalDate) -> Unit,
) {
    // 주의 첫 요일은 앱의 주간 요약과 같은 기준(일요일)을 쓴다.
    val firstDayOfWeek = FIRST_DAY_OF_WEEK
    val weekDays = List(7) { firstDayOfWeek.plus(it.toLong()) }
    val cells = calendarCells(month, firstDayOfWeek)
    val today = LocalDate.now()

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                entries = entriesByDate[date].orEmpty(),
                                isSelected = date == selected,
                                isToday = date == today,
                                gymColor = gymColor,
                                runColor = runColor,
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    entries: List<HistoryEntry>,
    isSelected: Boolean,
    isToday: Boolean,
    gymColor: Color,
    runColor: Color,
    onClick: () -> Unit,
) {
    val gym = entries.count { it is HistoryEntry.Gym }
    val run = entries.count { it is HistoryEntry.Running }
    val description = buildString {
        append("${date.monthValue}월 ${date.dayOfMonth}일")
        if (gym > 0) append(", 헬스 ${gym}회")
        if (run > 0) append(", 러닝 ${run}회")
        if (gym == 0 && run == 0) append(", 기록 없음")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val circle = Modifier
            .size(32.dp)
            .let {
                when {
                    isSelected -> it.background(MaterialTheme.colorScheme.primary, CircleShape)
                    isToday -> it.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else -> it
                }
            }
        Box(modifier = circle, contentAlignment = Alignment.Center) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        // 점 사이에 2dp 틈을 두어 두 점이 붙어 보이지 않게 한다.
        Row(
            modifier = Modifier
                .padding(top = 3.dp)
                .height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (gym > 0) Dot(gymColor)
            if (run > 0) Dot(runColor)
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .width(6.dp)
            .height(6.dp)
            .background(color, CircleShape),
    )
}

/**
 * 달력 칸. 첫 주 앞의 빈칸은 null. 마지막 주는 7칸을 채우도록 뒤에도 null 을 붙인다.
 */
internal fun calendarCells(month: YearMonth, firstDayOfWeek: DayOfWeek): List<LocalDate?> {
    val first = month.atDay(1)
    val leading = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val cells = List<LocalDate?>(leading) { null } + days
    val trailing = (7 - cells.size % 7) % 7
    return cells + List(trailing) { null }
}
