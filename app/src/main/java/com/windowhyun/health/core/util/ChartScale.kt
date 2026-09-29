package com.windowhyun.health.core.util

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * 그래프 세로축 눈금. 1·2·5 × 10^n 간격의 깔끔한 숫자로 데이터를 감싼다.
 *
 * 성장 그래프는 변화를 보는 선 그래프라 0 에서 시작하지 않는다(막대와 다르다).
 * 다만 음수 눈금은 만들지 않는다.
 */
fun niceTicks(min: Double, max: Double, targetCount: Int = 4): List<Double> {
    require(targetCount >= 2)
    var lo = min
    var hi = max
    if (hi - lo < 1e-9) {
        // 값이 하나뿐이면 위아래로 여유를 준다.
        val pad = if (abs(hi) < 1e-9) 1.0 else abs(hi) * 0.1
        lo -= pad
        hi += pad
    }
    val step = niceStep((hi - lo) / (targetCount - 1))
    val start = (floor(lo / step) * step).coerceAtLeast(0.0)
    val end = ceil(hi / step) * step
    val ticks = mutableListOf<Double>()
    var value = start
    while (value <= end + step * 1e-6) {
        ticks += value
        value += step
    }
    return ticks
}

private fun niceStep(raw: Double): Double {
    val exponent = floor(log10(raw))
    val magnitude = 10.0.pow(exponent)
    val fraction = raw / magnitude
    val nice = when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}
