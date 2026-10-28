package com.windowhyun.health.ui.running

import android.graphics.ColorMatrix
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 바탕 지도를 회색조로 바꾸는 색 변환. */
@RunWith(RobolectricTestRunner::class)
class RouteMapStyleTest {

    private fun apply(matrix: FloatArray, r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
        fun row(o: Int) = (matrix[o] * r + matrix[o + 1] * g + matrix[o + 2] * b + matrix[o + 4]).toInt().coerceIn(0, 255)
        return Triple(row(0), row(5), row(10))
    }

    @Test
    fun `the matrix has twenty values and keeps alpha`() {
        listOf(true, false).forEach { dark ->
            val m = RouteMapStyle.matrix(dark)
            assertThat(m).hasLength(20)
            assertThat(m.sliceArray(15..19).toList()).containsExactly(0f, 0f, 0f, 1f, 0f).inOrder()
            ColorMatrix(m) // 안드로이드가 받아들이는 모양
        }
    }

    /** OSM 기본 지도의 바탕(연한 베이지)은 어두운 지도에서 어두운 회색이 된다. */
    @Test
    fun `dark map turns the light background dark`() {
        val (r, g, b) = apply(RouteMapStyle.matrix(true), 242, 239, 233)

        assertThat(maxOf(r, g, b)).isLessThan(60)
    }

    /** 길(흰색)은 바탕보다 더 어둡고 물이나 공원은 조금 밝아서, 윤곽은 남되 색은 사라진다. */
    @Test
    fun `dark map keeps shapes visible but quiet`() {
        val m = RouteMapStyle.matrix(true)
        val road = apply(m, 255, 255, 255).second
        val ground = apply(m, 242, 239, 233).second
        val water = apply(m, 170, 211, 223).second

        assertThat(ground).isGreaterThan(road)
        assertThat(water).isGreaterThan(ground)
        assertThat(water - road).isLessThan(80) // 대비가 약해서 경로선이 돋보인다
    }

    @Test
    fun `light map removes color and stays light`() {
        val m = RouteMapStyle.matrix(false)
        val (r, g, b) = apply(m, 170, 211, 223) // 물의 파란색

        assertThat(maxOf(r, g, b) - minOf(r, g, b)).isAtMost(1)
        assertThat(r).isGreaterThan(150)
        assertThat(apply(m, 255, 255, 255).first).isEqualTo(255)
    }

    @Test
    fun `dark map has no strong color cast`() {
        val (r, g, b) = apply(RouteMapStyle.matrix(true), 170, 211, 223)

        assertThat(maxOf(r, g, b) - minOf(r, g, b)).isAtMost(10)
    }

    /** 자리 표시 바탕도 타일과 같은 변환을 거친다. 변환 뒤 색이 진짜 지도 바탕의 변환 뒤 색과 같아야 타일이 오기 전 · 후가 어울린다. */
    @Test
    fun `the placeholder turns into the same color as the real map background`() {
        val ground = Triple(242, 239, 233) // OSM 기본 지도의 바탕
        val placeholder = RouteMapStyle.TILE_PLACEHOLDER
        val p = Triple((placeholder shr 16) and 0xFF, (placeholder shr 8) and 0xFF, placeholder and 0xFF)

        listOf(true, false).forEach { dark ->
            assertThat(apply(RouteMapStyle.matrix(dark), p.first, p.second, p.third))
                .isEqualTo(apply(RouteMapStyle.matrix(dark), ground.first, ground.second, ground.third))
        }
    }
}
