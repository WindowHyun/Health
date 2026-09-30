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
 *
 * [minStep] 은 라벨이 보여 줄 수 있는 가장 작은 차이다. 이보다 촘촘하면 반올림된 라벨이
 * 겹친다(10, 10.5, 11 -> "10", "11", "11").
 */
fun niceTicks(min: Double, max: Double, targetCount: Int = 4, minStep: Double = 0.0): List<Double> {
    require(targetCount >= 2)
    var lo = min
    var hi = max
    if (hi - lo < 1e-9) {
        // 값이 하나뿐이면 위아래로 여유를 준다.
        val pad = if (abs(hi) < 1e-9) 1.0 else abs(hi) * 0.1
        lo -= pad
        hi += pad
    }
    // 라벨을 정수로 적는 지표(횟수 · 초)에 0.5 간격을 쓰면 같은 숫자가 두 번 찍힌다.
    val step = maxOf(niceStep((hi - lo) / (targetCount - 1)), minStep)
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

/**
 * 그래프 점의 가로 위치. 시각 간격대로 놓되, 이웃한 점 사이를 최소 [minGap] 만큼 벌린다.
 *
 * 같은 날 오전 · 저녁처럼 가까운 기록은 시각대로만 놓으면 한 점으로 겹쳐 뒤의 것을 누를
 * 수 없다. 벌린 만큼 오른쪽이 넘치면 끝에서부터 당겨 [left]..[right] 안에 넣는다.
 *
 * @param times 오래된 순으로 정렬된 시각(epoch millis)
 */
fun chartXPositions(times: List<Long>, left: Float, right: Float, minGap: Float): List<Float> {
    if (times.isEmpty()) return emptyList()
    if (times.size == 1) return listOf((left + right) / 2f)
    val width = right - left
    val span = (times.last() - times.first()).toDouble()
    val raw = times.mapIndexed { index, time ->
        val fraction = if (span <= 0.0) index.toDouble() / (times.size - 1) else (time - times.first()) / span
        (left + fraction * width).toFloat()
    }
    // 점이 많아 최소 간격을 다 줄 수 없으면 가능한 만큼만 벌린다.
    val gap = minOf(minGap, width / (times.size - 1))
    val spread = raw.toMutableList()
    for (i in 1 until spread.size) {
        spread[i] = maxOf(spread[i], spread[i - 1] + gap)
    }
    // 오른쪽으로 넘쳤으면 끝에서부터 되짚어 당긴다. 전체를 줄이면 벌려 둔 간격도 같이
    // 좁아지므로 쓰지 않는다. gap 이 width / (n - 1) 이하라 첫 점은 left 밖으로 나가지 않는다.
    spread[spread.lastIndex] = minOf(spread.last(), right)
    for (i in spread.lastIndex - 1 downTo 0) {
        spread[i] = minOf(spread[i], spread[i + 1] - gap)
    }
    return spread
}
