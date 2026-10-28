package com.windowhyun.health.ui.running

/**
 * 바탕 지도를 차분한 회색조로 바꾸는 색 변환. OpenStreetMap 기본 지도는 색이 많아 경로선이 묻히므로,
 * 지도에서는 길과 지형의 윤곽만 은은하게 남기고 색은 경로선이 독차지하게 한다.
 *
 * Android 의 ColorMatrix(4x5, 행마다 R G B A 의 계수와 더하는 값) 모양의 배열을 만든다.
 */
internal object RouteMapStyle {

    // 밝기(휘도) 계수
    private const val LR = 0.299f
    private const val LG = 0.587f
    private const val LB = 0.114f

    /**
     * 어두운 지도: 밝기를 뒤집어 밝은 바탕을 짙은 남색 회색으로 만들고, 변화 폭을 줄여 지도가 뒤로 물러나게 한다.
     * 결과 = 어두운 바닥 + (255 - 밝기) × 폭.
     */
    private fun dark(): FloatArray {
        val gain = 0.78f
        val floor = 20f
        fun row(tint: Float) = floatArrayOf(-LR * gain, -LG * gain, -LB * gain, 0f, 255f * gain + floor + tint)
        return floatArrayOf(
            *row(-2f),
            *row(0f),
            *row(6f),
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** 밝은 지도: 색을 빼고 대비를 줄여 옅은 회색으로 만든다. 결과 = 밝기 × 폭 + 더하는 값. */
    private fun light(): FloatArray {
        val gain = 0.86f
        val lift = 255f * (1f - gain)
        fun row() = floatArrayOf(LR * gain, LG * gain, LB * gain, 0f, lift)
        return floatArrayOf(
            *row(),
            *row(),
            *row(),
            0f, 0f, 0f, 1f, 0f,
        )
    }

    fun matrix(darkMap: Boolean): FloatArray = if (darkMap) dark() else light()

    /**
     * 타일이 아직 안 왔을 때 칠하는 바탕색. 이 색도 타일과 **같은 변환을 거치므로**, 원하는 색이 아니라
     * OpenStreetMap 기본 지도의 바탕색(연한 베이지)을 줘야 변환 뒤에 지도 바탕과 같은 색이 된다.
     */
    const val TILE_PLACEHOLDER: Int = 0xFFF2EFE9.toInt()
}
