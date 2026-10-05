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

    /** 정수로 적는 지표는 눈금 간격이 1 보다 작아지지 않는다(10, 10.5, 11 -> "10","11","11" 방지). */
    @Test
    fun `respects the minimum tick step`() {
        val ticks = niceTicks(10.0, 11.0, minStep = 1.0)

        val labels = ticks.map { String.format(java.util.Locale.US, "%,.0f", it) }
        assertThat(labels).containsNoDuplicates()
        assertThat(ticks.zipWithNext { a, b -> b - a }.all { it >= 1.0 }).isTrue()
    }

    private val hour = 3_600_000L
    private val day = 24 * hour

    /** 같은 날 오전 · 저녁 기록도 최소 간격만큼 떨어져 따로 누를 수 있다. */
    @Test
    fun `separates records on the same day`() {
        val times = listOf(0L, 12 * hour, 60 * day) // 9/1 오전, 9/1 저녁, 두 달 뒤
        val xs = chartXPositions(times, left = 0f, right = 1_000f, minGap = 36f)

        assertThat(xs[1] - xs[0]).isAtLeast(36f)
        assertThat(xs).isInOrder()
        assertThat(xs.first()).isAtLeast(0f)
        assertThat(xs.last()).isAtMost(1_000f)
    }

    /** 시각 간격대로 놓는다: 쉬었던 기간이 보여야 한다. */
    @Test
    fun `keeps time proportions when there is room`() {
        val xs = chartXPositions(listOf(0L, 10 * day, 40 * day), left = 0f, right = 400f, minGap = 10f)

        assertThat(xs[0]).isWithin(0.01f).of(0f)
        assertThat(xs[1]).isWithin(0.01f).of(100f)
        assertThat(xs[2]).isWithin(0.01f).of(400f)
    }

    /** 끝에 몰린 기록들도 오른쪽 끝을 넘지 않고, 간격은 지켜진다. */
    @Test
    fun `pulls crowded points back inside`() {
        val times = listOf(0L, 100 * day, 100 * day + hour, 100 * day + 2 * hour)
        val xs = chartXPositions(times, left = 0f, right = 300f, minGap = 30f)

        assertThat(xs.last()).isAtMost(300f)
        xs.zipWithNext { a, b -> assertThat(b - a).isAtLeast(30f - 0.01f) }
    }

    /** 모두 같은 시각이면 고르게 편다. 점이 너무 많으면 가능한 만큼만 벌린다. */
    @Test
    fun `spreads identical times evenly`() {
        val xs = chartXPositions(List(5) { 0L }, left = 0f, right = 100f, minGap = 50f)

        assertThat(xs).containsExactly(0f, 25f, 50f, 75f, 100f).inOrder()
    }
}
