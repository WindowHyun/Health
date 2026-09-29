package com.windowhyun.health.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 차트·캘린더에서 헬스와 러닝을 구분하는 색.
 *
 * 앱의 초록(primary)과 청록(tertiary)은 색각 이상이 없어도 거의 구분되지 않고
 * (ΔE 6.6), 초록과 주황은 적색맹에게 구분되지 않는다(ΔE 3.2). 그래서 데이터에는
 * 따로 검증한 파랑/주황 쌍을 쓴다. 라이트·다크 각각 배경 대비 3:1 이상,
 * 색각 이상 시뮬레이션 ΔE 24 이상을 확인했다.
 *
 * 색은 표시(점·선)에만 쓰고, 글자는 항상 본문 글자색으로 쓴다.
 */
data class ChartColors(
    val gym: Color,
    val run: Color,
)

private val LightChartColors = ChartColors(gym = Color(0xFF2A78D6), run = Color(0xFFEB6834))
private val DarkChartColors = ChartColors(gym = Color(0xFF3987E5), run = Color(0xFFD95926))

/** 앱 테마 설정(시스템/라이트/다크)을 따르도록 실제 배경 밝기로 고른다. */
@Composable
fun chartColors(): ChartColors =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) DarkChartColors else LightChartColors
