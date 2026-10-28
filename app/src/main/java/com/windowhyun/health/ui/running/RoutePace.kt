package com.windowhyun.health.ui.running

import com.windowhyun.health.domain.model.RunPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 경로의 구간마다 "얼마나 빨랐는가"를 0(느림)~1(빠름)로 계산한다. 지도에서 선 색을 정하는 데 쓴다.
 *
 * 절대 속도가 아니라 **이 러닝 안에서의 상대값**이다. 느리게 달린 날도 빠르게 달린 날도, 그날 중 빠르던 곳과 느리던 곳이 보이게 한다.
 */
internal object RoutePace {

    /** 비슷한 속도로만 달렸다면(차이가 평균의 이 비율보다 작으면) 구간마다 색을 달리하지 않는다. */
    private const val FLAT_RATIO = 0.06

    /** 이보다 빠른 구간은 GPS 가 튄 것으로 보고 색을 정하는 데 쓰지 않는다(m/s, 약 43km/h). */
    private const val MAX_PLAUSIBLE_SPEED = 12.0

    private const val SMOOTHING_RADIUS = 2
    private const val LOW_PERCENTILE = 0.10
    private const val HIGH_PERCENTILE = 0.90

    /** 점 사이 구간([points].size - 1 개)마다의 빠르기. 이어지지 않는 구간(일시정지 뒤)은 NaN. */
    fun fractions(points: List<RunPoint>): List<Float> {
        if (points.size < 2) return emptyList()
        val speeds = DoubleArray(points.size - 1) { speed(points[it], points[it + 1]) }

        val smoothed = DoubleArray(speeds.size) { i ->
            if (speeds[i].isNaN()) return@DoubleArray Double.NaN
            var sum = 0.0
            var count = 0
            for (j in (i - SMOOTHING_RADIUS)..(i + SMOOTHING_RADIUS)) {
                val v = speeds.getOrNull(j) ?: continue
                if (!v.isNaN()) { sum += v; count++ }
            }
            sum / count
        }

        val usable = smoothed.filter { !it.isNaN() && it <= MAX_PLAUSIBLE_SPEED }.sorted()
        if (usable.isEmpty()) return List(speeds.size) { if (speeds[it].isNaN()) Float.NaN else 0.5f }

        val low = percentile(usable, LOW_PERCENTILE)
        val high = percentile(usable, HIGH_PERCENTILE)
        val mean = usable.average()
        val flat = high - low < mean * FLAT_RATIO

        return List(speeds.size) { i ->
            val v = smoothed[i]
            when {
                v.isNaN() -> Float.NaN
                flat -> 0.5f
                else -> ((v - low) / (high - low)).coerceIn(0.0, 1.0).toFloat()
            }
        }
    }

    /** 두 점 사이 속도(m/s). 일시정지로 끊겼거나 시간이 흐르지 않았으면 NaN. */
    private fun speed(from: RunPoint, to: RunPoint): Double {
        if (to.isSegmentStart) return Double.NaN
        val seconds = (to.timestamp - from.timestamp) / 1_000.0
        if (seconds <= 0) return Double.NaN
        return distanceMeters(from, to) / seconds
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        val at = p * (sorted.size - 1)
        val lower = at.toInt()
        val upper = minOf(lower + 1, sorted.lastIndex)
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (at - lower)
    }

    private fun distanceMeters(a: RunPoint, b: RunPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    /** 색 띠 위의 [fraction] 에 해당하는 색(ARGB). 띠는 느림 → 빠름 순으로 둘 이상의 색이다. */
    fun colorAt(fraction: Float, ramp: IntArray): Int {
        require(ramp.size >= 2) { "색이 둘 이상 필요합니다" }
        val f = fraction.coerceIn(0f, 1f) * (ramp.size - 1)
        val lower = f.toInt().coerceAtMost(ramp.size - 2)
        return blend(ramp[lower], ramp[lower + 1], f - lower)
    }

    private fun blend(from: Int, to: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t).toInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}

/** 지도 바탕에 따라 고르는 경로 색 띠(느림 → 빠름). 바탕이 어두우면 밝은 색, 밝으면 짙은 색을 쓴다. */
internal object RouteColors {
    val onDark = intArrayOf(0xFF7C6CFF.toInt(), 0xFF22D3EE.toInt(), 0xFFC6FF00.toInt())
    val onLight = intArrayOf(0xFF4C3FD9.toInt(), 0xFF0E9AA7.toInt(), 0xFFFF6A00.toInt())

    fun ramp(darkMap: Boolean): IntArray = if (darkMap) onDark else onLight
}
