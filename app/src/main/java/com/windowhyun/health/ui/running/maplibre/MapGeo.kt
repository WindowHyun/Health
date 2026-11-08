package com.windowhyun.health.ui.running.maplibre

import com.windowhyun.health.domain.model.CoursePoint
import com.windowhyun.health.domain.model.RunPoint
import com.windowhyun.health.ui.running.RoutePace
import java.util.Locale
import kotlin.math.sqrt

/**
 * MapLibre 지도에 올릴 GeoJSON 글과 지도 계산. 안드로이드나 지도 없이 JVM 에서 확인할 수 있게 순수한 함수로 둔다.
 */
internal object MapGeo {

    /** OpenFreeMap 스타일 주소. 키가 필요 없다. 어두운 앱에서는 어두운 스타일을 쓴다. */
    fun styleUrl(dark: Boolean): String =
        if (dark) "https://tiles.openfreemap.org/styles/dark" else "https://tiles.openfreemap.org/styles/positron"

    private fun coord(lon: Double, lat: Double) = String.format(Locale.ROOT, "[%.6f,%.6f]", lon, lat)

    private fun hex(color: Int) = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)

    private fun collection(features: List<String>) =
        """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

    /**
     * 달린 경로: 구간마다 속도 색을 가진 선분 하나씩. 일시정지로 끊긴 곳은 잇지 않는다.
     * [fractions] 는 구간별 0..1(알 수 없으면 NaN) 속도 비율, [ramp] 는 느림 → 빠름 색.
     */
    fun routeSegments(points: List<RunPoint>, fractions: List<Float>, ramp: IntArray): String {
        val features = ArrayList<String>(points.size)
        for (i in 1 until points.size) {
            if (points[i].isSegmentStart) continue
            val fraction = fractions.getOrNull(i - 1)?.takeUnless { it.isNaN() } ?: 0.5f
            val color = RoutePace.colorAt(fraction, ramp)
            features += """{"type":"Feature","properties":{"c":"${hex(color)}"},"geometry":{"type":"LineString","coordinates":[${coord(points[i - 1].longitude, points[i - 1].latitude)},${coord(points[i].longitude, points[i].latitude)}]}}"""
        }
        return collection(features)
    }

    /** 코스(점선으로 그릴 한 줄). 점이 2개 미만이면 빈 컬렉션. */
    fun courseLine(points: List<CoursePoint>): String =
        if (points.size < 2) {
            collection(emptyList())
        } else {
            collection(
                listOf(
                    """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[${points.joinToString(",") { coord(it.longitude, it.latitude) }}]}}""",
                ),
            )
        }

    /** 코스 만들기에서 찍은 점들. 첫 점은 크게 보이도록 반지름을 다르게 준다. */
    fun editPoints(points: List<CoursePoint>, color: Int): String = collection(
        points.mapIndexed { i, p ->
            """{"type":"Feature","properties":{"c":"${hex(color)}","r":${if (i == 0) 9 else 6}},"geometry":{"type":"Point","coordinates":${coord(p.longitude, p.latitude)}}}"""
        },
    )

    /** 시작 · 끝 점. 끝(기록 중에는 지금 위치)에는 은은한 빛(halo)을 둔다. [showStart] 가 false 면 시작 점은 그리지 않는다. */
    fun endpoints(start: RunPoint, end: RunPoint, startColor: Int, endColor: Int, showStart: Boolean): String {
        val features = ArrayList<String>(2)
        if (showStart) {
            features += """{"type":"Feature","properties":{"c":"${hex(startColor)}","r":5,"hr":0},"geometry":{"type":"Point","coordinates":${coord(start.longitude, start.latitude)}}}"""
        }
        features += """{"type":"Feature","properties":{"c":"${hex(endColor)}","r":7,"hr":17},"geometry":{"type":"Point","coordinates":${coord(end.longitude, end.latitude)}}}"""
        return collection(features)
    }

    fun emptyEndpoints(): String = collection(emptyList())

    /** 위도 · 경도 범위. 최소 범위를 둬서 아주 짧은 러닝도 주변이 보이게 한다(약 55m). */
    data class Bounds(val south: Double, val west: Double, val north: Double, val east: Double)

    fun bounds(latitudes: List<Double>, longitudes: List<Double>, minHalfSpanDegrees: Double = 0.00025): Bounds {
        val centerLat = (latitudes.max() + latitudes.min()) / 2
        val centerLon = (longitudes.max() + longitudes.min()) / 2
        val halfLat = maxOf((latitudes.max() - latitudes.min()) / 2, minHalfSpanDegrees)
        val halfLon = maxOf((longitudes.max() - longitudes.min()) / 2, minHalfSpanDegrees)
        return Bounds(centerLat - halfLat, centerLon - halfLon, centerLat + halfLat, centerLon + halfLon)
    }

    /** 화면 좌표 [screen] 중 (x, y) 에서 [radius] 안에서 가장 가까운 것의 번호. 없으면 -1. */
    fun nearestWithin(screen: List<Pair<Float, Float>>, x: Float, y: Float, radius: Float): Int {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        screen.forEachIndexed { i, (px, py) ->
            val dx = px - x
            val dy = py - y
            val d = sqrt(dx * dx + dy * dy)
            if (d <= radius && d < bestDistance) {
                best = i
                bestDistance = d
            }
        }
        return best
    }
}
