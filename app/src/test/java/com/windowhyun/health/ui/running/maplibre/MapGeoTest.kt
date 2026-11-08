package com.windowhyun.health.ui.running.maplibre

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.domain.model.CoursePoint
import com.windowhyun.health.domain.model.RunPoint
import com.windowhyun.health.domain.model.MapStyleMode
import com.windowhyun.health.ui.running.LocalMapStyleMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** 새 지도(MapLibre)에 올리는 GeoJSON 글과 지도 계산. 지도 엔진 없이 JVM 에서 확인한다. */
class MapGeoTest {

    private fun features(json: String): JsonArray = Json.parseToJsonElement(json).jsonObject.getValue("features").jsonArray

    private val ramp = intArrayOf(0xFF2E7D32.toInt(), 0xFFFFC107.toInt(), 0xFFD32F2F.toInt())

    private fun p(lat: Double, lon: Double, t: Long = 0, seg: Boolean = false) = RunPoint(lat, lon, timestamp = t, isSegmentStart = seg)

    @Test fun `route segments are valid geojson with one feature per segment and a hex color`() {
        val points = listOf(p(37.0, 127.0), p(37.001, 127.0), p(37.002, 127.0))
        val json = MapGeo.routeSegments(points, listOf(0f, 1f), ramp)
        val f = features(json)
        assertThat(f).hasSize(2)
        f.forEach {
            val o = it.jsonObject
            assertThat(o.getValue("properties").jsonObject.getValue("c").jsonPrimitive.content).matches("#[0-9A-F]{6}")
            assertThat(o.getValue("geometry").jsonObject.getValue("type").jsonPrimitive.content).isEqualTo("LineString")
            assertThat(o.getValue("geometry").jsonObject.getValue("coordinates").jsonArray).hasSize(2)
        }
    }

    @Test fun `coordinates are longitude first`() {
        val json = MapGeo.routeSegments(listOf(p(37.5, 126.9), p(37.6, 127.0)), listOf(0.5f), ramp)
        val first = features(json)[0].jsonObject.getValue("geometry").jsonObject.getValue("coordinates").jsonArray[0].jsonArray
        assertThat(first[0].jsonPrimitive.content.toDouble()).isWithin(1e-6).of(126.9)
        assertThat(first[1].jsonPrimitive.content.toDouble()).isWithin(1e-6).of(37.5)
    }

    @Test fun `a pause breaks the line and unknown pace gets the middle color`() {
        val points = listOf(p(37.0, 127.0), p(37.001, 127.0), p(37.01, 127.0, seg = true), p(37.011, 127.0))
        val f = features(MapGeo.routeSegments(points, listOf(Float.NaN, 0.5f, 0.5f), ramp))
        assertThat(f).hasSize(2) // 끊긴 곳(2 -> 3)은 잇지 않는다
        // NaN 은 가운데 색(0.5)과 같다
        val a = f[0].jsonObject.getValue("properties").jsonObject.getValue("c").jsonPrimitive.content
        val b = f[1].jsonObject.getValue("properties").jsonObject.getValue("c").jsonPrimitive.content
        assertThat(a).isEqualTo(b)
    }

    @Test fun `empty or single point routes make an empty collection`() {
        assertThat(features(MapGeo.routeSegments(emptyList(), emptyList(), ramp))).isEmpty()
        assertThat(features(MapGeo.routeSegments(listOf(p(1.0, 1.0)), emptyList(), ramp))).isEmpty()
    }

    @Test fun `course line needs two points`() {
        assertThat(features(MapGeo.courseLine(listOf(CoursePoint(1.0, 1.0))))).isEmpty()
        val f = features(MapGeo.courseLine(listOf(CoursePoint(37.0, 127.0), CoursePoint(37.1, 127.1), CoursePoint(37.2, 127.0))))
        assertThat(f).hasSize(1)
        assertThat(f[0].jsonObject.getValue("geometry").jsonObject.getValue("coordinates").jsonArray).hasSize(3)
    }

    @Test fun `edit points make the first one bigger`() {
        val f = features(MapGeo.editPoints(listOf(CoursePoint(1.0, 1.0), CoursePoint(2.0, 2.0)), 0x2F80ED))
        assertThat(f).hasSize(2)
        val r0 = f[0].jsonObject.getValue("properties").jsonObject.getValue("r").jsonPrimitive.content.toInt()
        val r1 = f[1].jsonObject.getValue("properties").jsonObject.getValue("r").jsonPrimitive.content.toInt()
        assertThat(r0).isGreaterThan(r1)
        assertThat(f[0].jsonObject.getValue("properties").jsonObject.getValue("c").jsonPrimitive.content).isEqualTo("#2F80ED")
    }

    @Test fun `endpoints show the start only when asked and the end always has a halo`() {
        val s = p(37.0, 127.0); val e = p(37.1, 127.1)
        assertThat(features(MapGeo.endpoints(s, e, 0x000000, 0xFF0000, showStart = true))).hasSize(2)
        val onlyEnd = features(MapGeo.endpoints(s, e, 0x000000, 0xFF0000, showStart = false))
        assertThat(onlyEnd).hasSize(1)
        assertThat(onlyEnd[0].jsonObject.getValue("properties").jsonObject.getValue("hr").jsonPrimitive.content.toInt()).isGreaterThan(0)
    }

    @Test fun `bounds keep a minimum span so a very short run still shows its surroundings`() {
        val b = MapGeo.bounds(listOf(37.0, 37.00001), listOf(127.0, 127.00001))
        assertThat(b.north - b.south).isWithin(1e-9).of(0.0005)
        assertThat(b.east - b.west).isWithin(1e-9).of(0.0005)
        val wide = MapGeo.bounds(listOf(37.0, 37.1), listOf(127.0, 127.2))
        assertThat(wide.south).isWithin(1e-9).of(37.0)
        assertThat(wide.east).isWithin(1e-9).of(127.2)
    }

    @Test fun `nearest tap target within the radius`() {
        val pts = listOf(100f to 100f, 200f to 200f, 205f to 205f)
        assertThat(MapGeo.nearestWithin(pts, 203f, 203f, 24f)).isEqualTo(2)
        assertThat(MapGeo.nearestWithin(pts, 150f, 150f, 24f)).isEqualTo(-1)
        assertThat(MapGeo.nearestWithin(emptyList(), 0f, 0f, 24f)).isEqualTo(-1)
    }

    @Test fun `style url follows the app theme and needs no key`() {
        assertThat(MapGeo.styleUrl(dark = false)).isEqualTo("https://tiles.openfreemap.org/styles/positron")
        assertThat(MapGeo.styleUrl(dark = true)).isEqualTo("https://tiles.openfreemap.org/styles/dark")
        assertThat(MapGeo.styleUrl(false)).doesNotContain("key")
    }

    @Test fun `the classic map stays the default so existing screens and tests are unchanged`() {
        assertThat(com.windowhyun.health.domain.model.AppSettings().mapStyle).isEqualTo(MapStyleMode.CLASSIC)
        assertThat(MapStyleMode.entries.map { it.label }).containsExactly("기본", "새 지도").inOrder()
    }
}
