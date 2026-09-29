package com.windowhyun.health.core.util

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.ui.history.calendarCells
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** 그래프 눈금과 달력 칸 계산. */
class ChartScaleTest {

    /** 눈금은 데이터를 감싸고, 1·2·5 간격의 깔끔한 숫자다. */
    @Test
    fun `ticks wrap the data with clean steps`() {
        val ticks = niceTicks(62.5, 117.0)

        assertThat(ticks.first()).isAtMost(62.5)
        assertThat(ticks.last()).isAtLeast(117.0)
        val steps = ticks.zipWithNext { a, b -> b - a }.distinct()
        assertThat(steps).hasSize(1)
        assertThat(steps.single()).isAnyOf(10.0, 20.0, 50.0)
    }

    /** 선 그래프라 0 에서 시작하지 않지만, 음수 눈금은 만들지 않는다. */
    @Test
    fun `never goes below zero`() {
        assertThat(niceTicks(1.0, 3.0).first()).isAtLeast(0.0)
        assertThat(niceTicks(0.0, 0.0).first()).isAtLeast(0.0)
    }

    /** 값이 하나뿐이어도 범위가 생긴다(0 으로 나누지 않는다). */
    @Test
    fun `handles a single value`() {
        val ticks = niceTicks(100.0, 100.0)

        assertThat(ticks.size).isAtLeast(2)
        assertThat(ticks.first()).isLessThan(100.0)
        assertThat(ticks.last()).isGreaterThan(100.0)
    }

    /** 큰 볼륨 값도 눈금이 너무 많아지지 않는다. */
    @Test
    fun `keeps the tick count small`() {
        assertThat(niceTicks(1_230.0, 8_760.0).size).isAtMost(6)
        assertThat(niceTicks(30.0, 95.0).size).isAtMost(6)
    }

    /** 2026년 9월 1일은 화요일. 일요일 시작이면 앞에 빈칸 2개. */
    @Test
    fun `places the first day under the right weekday`() {
        val cells = calendarCells(YearMonth.of(2026, 9), DayOfWeek.SUNDAY)

        assertThat(cells.take(2)).containsExactly(null, null)
        assertThat(cells[2]).isEqualTo(LocalDate.of(2026, 9, 1))
        assertThat(cells.size % 7).isEqualTo(0)
        assertThat(cells.filterNotNull()).hasSize(30)
    }

    /** 첫날이 주의 첫 요일이면 빈칸이 없다. */
    @Test
    fun `no leading blanks when the month starts on the first weekday`() {
        // 2026년 2월 1일은 일요일
        val cells = calendarCells(YearMonth.of(2026, 2), DayOfWeek.SUNDAY)

        assertThat(cells.first()).isEqualTo(LocalDate.of(2026, 2, 1))
        assertThat(cells).hasSize(28)
    }
}
