package com.windowhyun.health.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.windowhyun.health.R

/** Pretendard. 한글과 숫자가 같은 결로 이어지고 숫자 폭을 맞추는 기능(tnum)이 있다. */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
)

/**
 * 숫자 폭을 같게 맞춘다. 타이머처럼 값이 바뀌는 숫자가 흔들리지 않고, 목록의 숫자가 줄을 맞춘다.
 * 본문 글자에는 쓰지 않는다(1 이 넓게 벌어져 보인다).
 */
private const val TABULAR = "tnum"

private fun style(
    size: Int,
    weight: FontWeight,
    lineHeight: Int,
    tracking: Double = 0.0,
    tabular: Boolean = false,
) = TextStyle(
    fontFamily = Pretendard,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = if (tabular) TABULAR else null,
)

/**
 * 위계는 굵기와 크기 차이로 만든다. 큰 숫자(display)는 아주 굵고 자간을 조이고,
 * 본문은 가볍게 둔다. 중간 굵기를 줄이는 쪽이 눈에 더 잘 들어온다.
 */
val HealthTypography = Typography(
    displayLarge = style(56, FontWeight.ExtraBold, 60, -1.5, tabular = true),
    displayMedium = style(44, FontWeight.ExtraBold, 48, -1.0, tabular = true),
    displaySmall = style(34, FontWeight.ExtraBold, 40, -0.5, tabular = true),
    headlineLarge = style(30, FontWeight.Bold, 36, -0.5),
    headlineMedium = style(26, FontWeight.Bold, 32, -0.3),
    headlineSmall = style(22, FontWeight.Bold, 28, -0.2),
    titleLarge = style(20, FontWeight.Bold, 26, -0.1),
    titleMedium = style(16, FontWeight.Bold, 22),
    titleSmall = style(14, FontWeight.Bold, 20),
    bodyLarge = style(16, FontWeight.Normal, 24),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(12, FontWeight.Normal, 16),
    labelLarge = style(14, FontWeight.Medium, 20),
    labelMedium = style(12, FontWeight.Medium, 16, 0.1),
    labelSmall = style(11, FontWeight.Medium, 16, 0.2),
)

/** 러닝 화면의 거리/페이스처럼 아주 크게 보여 줘야 하는 값. */
val HugeMetricTextStyle = style(64, FontWeight.ExtraBold, 68, -2.0, tabular = true)

/** 운동 중 화면의 큰 숫자(중량/횟수/타이머). */
val LargeMetricTextStyle = style(34, FontWeight.ExtraBold, 40, -0.5, tabular = true)
