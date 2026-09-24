package com.windowhyun.health.core

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.windowhyun.health.core.designsystem.theme.DarkColors
import com.windowhyun.health.core.designsystem.theme.LightColors
import org.junit.Test
import kotlin.math.pow

/**
 * 색 구성표가 팔레트 밖으로 새지 않는지 확인한다.
 *
 * Material 3 의 색 역할은 서로 맞물려 있어서, 일부만 지정하면 나머지는
 * 라이브러리 기본값(보라 계열)이 그대로 남는다. 빌드도 통과하고 경고도 없기 때문에
 * 화면을 직접 보기 전까지 알아채기 어렵다.
 * 실제로 Card 배경(surfaceContainerHighest)과 구분선(outlineVariant)이 그랬다.
 */
class ColorSchemeTest {

    private fun rgb(c: Color): Triple<Int, Int, Int> {
        val v = (c.value shr 32).toLong() and 0xFFFFFFFFL
        return Triple(((v shr 16) and 0xFF).toInt(), ((v shr 8) and 0xFF).toInt(), (v and 0xFF).toInt())
    }

    private fun hex(c: Color): String {
        val (r, g, b) = rgb(c)
        return "#%02X%02X%02X".format(r, g, b)
    }

    /** WCAG 상대 휘도. */
    private fun luminance(c: Color): Double {
        val (r, g, b) = rgb(c)
        fun channel(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** 표면(중립) 계열 역할. 이 팔레트에서는 초록 쪽으로 기울어 있어야 한다. */
    private fun neutralRoles(s: ColorScheme) = listOf(
        "background" to s.background,
        "surface" to s.surface,
        "surfaceVariant" to s.surfaceVariant,
        "surfaceDim" to s.surfaceDim,
        "surfaceBright" to s.surfaceBright,
        "surfaceContainerLowest" to s.surfaceContainerLowest,
        "surfaceContainerLow" to s.surfaceContainerLow,
        "surfaceContainer" to s.surfaceContainer,
        "surfaceContainerHigh" to s.surfaceContainerHigh,
        "surfaceContainerHighest" to s.surfaceContainerHighest,
        "onSurface" to s.onSurface,
        "onSurfaceVariant" to s.onSurfaceVariant,
        "outline" to s.outline,
        "outlineVariant" to s.outlineVariant,
        "inverseSurface" to s.inverseSurface,
        "inverseOnSurface" to s.inverseOnSurface,
    )

    /**
     * 중립 계열은 초록 쪽으로 기울어 있어 파랑이 초록을 넘지 않는다.
     * Material 3 기본값은 보라 계열이라 파랑이 초록보다 크다. 그것으로 누락을 잡는다.
     */
    @Test
    fun `neutral roles stay in the green palette`() {
        for ((schemeName, scheme) in listOf("light" to LightColors, "dark" to DarkColors)) {
            for ((role, color) in neutralRoles(scheme)) {
                val (_, g, b) = rgb(color)
                assertWithMessage("$schemeName.$role=${hex(color)} (파랑 $b > 초록 $g)")
                    .that(b <= g)
                    .isTrue()
            }
        }
    }

    /** 반전 강조색은 반대 테마의 기본 강조색이어야 한다. */
    @Test
    fun `inverse primary mirrors the other theme`() {
        assertThat(LightColors.inversePrimary).isEqualTo(DarkColors.primary)
        assertThat(DarkColors.inversePrimary).isEqualTo(LightColors.primary)
    }

    /** Card 배경 위의 본문과 보조 텍스트가 WCAG AA(4.5:1)를 넘어야 한다. */
    @Test
    fun `card text meets contrast requirements`() {
        for ((schemeName, scheme) in listOf("light" to LightColors, "dark" to DarkColors)) {
            val cardBackground = scheme.surfaceContainerHighest
            assertWithMessage("$schemeName onSurface on card")
                .that(contrast(scheme.onSurface, cardBackground))
                .isAtLeast(4.5)
            assertWithMessage("$schemeName onSurfaceVariant on card")
                .that(contrast(scheme.onSurfaceVariant, cardBackground))
                .isAtLeast(4.5)
        }
    }

    /** 화면 배경 위의 본문 텍스트도 마찬가지다. */
    @Test
    fun `body text meets contrast on the background`() {
        for ((schemeName, scheme) in listOf("light" to LightColors, "dark" to DarkColors)) {
            assertWithMessage("$schemeName onSurface on background")
                .that(contrast(scheme.onSurface, scheme.background))
                .isAtLeast(4.5)
            assertWithMessage("$schemeName onSurfaceVariant on background")
                .that(contrast(scheme.onSurfaceVariant, scheme.background))
                .isAtLeast(4.5)
        }
    }

    /** 주요 버튼(채움)의 글자 대비. */
    @Test
    fun `filled button text meets contrast`() {
        for ((schemeName, scheme) in listOf("light" to LightColors, "dark" to DarkColors)) {
            assertWithMessage("$schemeName onPrimary on primary")
                .that(contrast(scheme.onPrimary, scheme.primary))
                .isAtLeast(4.5)
            assertWithMessage("$schemeName onPrimaryContainer on primaryContainer")
                .that(contrast(scheme.onPrimaryContainer, scheme.primaryContainer))
                .isAtLeast(4.5)
            assertWithMessage("$schemeName onSecondaryContainer on secondaryContainer")
                .that(contrast(scheme.onSecondaryContainer, scheme.secondaryContainer))
                .isAtLeast(4.5)
        }
    }

    /** 카드와 화면 배경은 서로 구분되어야 한다(경계선이 없으므로). */
    @Test
    fun `card is distinguishable from the background`() {
        for ((schemeName, scheme) in listOf("light" to LightColors, "dark" to DarkColors)) {
            val ratio = contrast(scheme.surfaceContainerHighest, scheme.background)
            assertWithMessage("$schemeName card vs background").that(ratio).isGreaterThan(1.1)
        }
    }
}
