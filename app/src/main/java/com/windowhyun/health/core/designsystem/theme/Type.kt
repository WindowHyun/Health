package com.windowhyun.health.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val HealthTypography = Typography()

/** 러닝 화면의 거리/페이스처럼 아주 크게 보여 줘야 하는 값. */
val HugeMetricTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 64.sp,
    lineHeight = 68.sp,
)

/** 운동 중 화면의 큰 숫자(중량/횟수/타이머). */
val LargeMetricTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    lineHeight = 40.sp,
)
