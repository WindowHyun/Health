package com.windowhyun.health.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// 팔레트는 흑백 중립 + 형광 라임 하나다. 라임은 면(버튼 · 진행 표시)에만 쓰고 글자색으로는
// 쓰지 않는다(흰 바탕에서 읽히지 않는다). 글자는 항상 거의 검정/거의 흰색이다.
//
// Material 3 의 색 역할은 서로 맞물려 있어 일부만 지정하면 나머지는 라이브러리 기본값(보라)이
// 남는다. 표면 계열까지 끝까지 지정한다.
//  - primary            : 기본 버튼 · 스위치 · 글자 버튼. 라이트는 검정, 다크는 라임.
//  - primaryContainer   : 강조 면(라임). 두 모드 모두 라임이다.
//  - secondaryContainer : 선택된 칩 · 세그먼트. 라이트는 검정, 다크는 흰색(선택 = 대비가 가장 큰 것).

/** 강조색. 어디서나 같은 라임. */
val HealthLime = Color(0xFFC6F432)

// 라이트
val md_light_primary = Color(0xFF0A0A0A)
val md_light_onPrimary = Color(0xFFFFFFFF)
val md_light_primaryContainer = HealthLime
val md_light_onPrimaryContainer = Color(0xFF0A0A0A)
val md_light_secondary = Color(0xFF3D3D3A)
val md_light_onSecondary = Color(0xFFFFFFFF)
val md_light_secondaryContainer = Color(0xFF0A0A0A)
val md_light_onSecondaryContainer = Color(0xFFFFFFFF)
val md_light_tertiary = Color(0xFF0A0A0A)
val md_light_onTertiary = Color(0xFFFFFFFF)
val md_light_tertiaryContainer = HealthLime
val md_light_onTertiaryContainer = Color(0xFF0A0A0A)
val md_light_error = Color(0xFFBA1A1A)
val md_light_onError = Color(0xFFFFFFFF)
val md_light_errorContainer = Color(0xFFFFDAD6)
val md_light_onErrorContainer = Color(0xFF410002)
val md_light_background = Color(0xFFFFFFFF)
val md_light_onBackground = Color(0xFF0A0A0A)
val md_light_surface = Color(0xFFFFFFFF)
val md_light_onSurface = Color(0xFF0A0A0A)
val md_light_surfaceVariant = Color(0xFFF3F3F0)
val md_light_onSurfaceVariant = Color(0xFF666662) // 흰 바탕 대비 5.7:1
val md_light_outline = Color(0xFF9A9A95)
val md_light_outlineVariant = Color(0xFFE4E4E0) // 구분선
val md_light_surfaceDim = Color(0xFFE9E9E5)
val md_light_surfaceBright = Color(0xFFFFFFFF)
val md_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_light_surfaceContainerLow = Color(0xFFFAFAF8)
val md_light_surfaceContainer = Color(0xFFF5F5F2)
val md_light_surfaceContainerHigh = Color(0xFFEFEFEB)
val md_light_surfaceContainerHighest = Color(0xFFE9E9E5)
val md_light_inverseSurface = Color(0xFF0A0A0A)
val md_light_inverseOnSurface = Color(0xFFF5F5F2)

// 다크
val md_dark_primary = HealthLime
val md_dark_onPrimary = Color(0xFF0B0B0C)
val md_dark_primaryContainer = HealthLime
val md_dark_onPrimaryContainer = Color(0xFF0B0B0C)
val md_dark_secondary = Color(0xFFD4D4D8)
val md_dark_onSecondary = Color(0xFF0B0B0C)
val md_dark_secondaryContainer = Color(0xFFF4F4F2)
val md_dark_onSecondaryContainer = Color(0xFF0B0B0C)
val md_dark_tertiary = HealthLime
val md_dark_onTertiary = Color(0xFF0B0B0C)
val md_dark_tertiaryContainer = HealthLime
val md_dark_onTertiaryContainer = Color(0xFF0B0B0C)
val md_dark_error = Color(0xFFFFB4AB)
val md_dark_onError = Color(0xFF690005)
val md_dark_errorContainer = Color(0xFF93000A)
val md_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_dark_background = Color(0xFF0B0B0C)
val md_dark_onBackground = Color(0xFFF4F4F2)
val md_dark_surface = Color(0xFF0B0B0C)
val md_dark_onSurface = Color(0xFFF4F4F2)
val md_dark_surfaceVariant = Color(0xFF1B1B1E)
val md_dark_onSurfaceVariant = Color(0xFFA0A0A4) // 검정 바탕 대비 7.6:1
val md_dark_outline = Color(0xFF6E6E73)
val md_dark_outlineVariant = Color(0xFF26262A) // 구분선
val md_dark_surfaceDim = Color(0xFF0B0B0C)
val md_dark_surfaceBright = Color(0xFF2A2A2E)
val md_dark_surfaceContainerLowest = Color(0xFF070708)
val md_dark_surfaceContainerLow = Color(0xFF111113)
val md_dark_surfaceContainer = Color(0xFF151517)
val md_dark_surfaceContainerHigh = Color(0xFF1B1B1E)
val md_dark_surfaceContainerHighest = Color(0xFF222226)
val md_dark_inverseSurface = Color(0xFFF4F4F2)
val md_dark_inverseOnSurface = Color(0xFF0B0B0C)
