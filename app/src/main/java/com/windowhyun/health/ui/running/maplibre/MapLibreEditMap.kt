package com.windowhyun.health.ui.running.maplibre

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.windowhyun.health.domain.model.CoursePoint
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * MapLibre 로 만든 코스 찍기 지도. 한 번 누르면 새 점, 이미 찍은 점(약 24dp 안)을 누르면 그 점을 뺀다.
 * [com.windowhyun.health.ui.running.CourseEditMap] 과 같은 입력을 받는다.
 */
@Composable
internal fun MapLibreEditMap(
    points: List<CoursePoint>,
    center: CoursePoint?,
    fit: List<CoursePoint>?,
    onAddPoint: (CoursePoint) -> Unit,
    onRemovePoint: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) return
    val darkMap = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val pointsNow by rememberUpdatedState(points)
    val onAdd by rememberUpdatedState(onAddPoint)
    val onRemove by rememberUpdatedState(onRemovePoint)
    val casingColor = if (darkMap) 0xFF0B0D10.toInt() else 0xFFFFFFFF.toInt()

    val holder = rememberMapLibre(darkMap) { map ->
        map.cameraPosition = CameraPosition.Builder().target(LatLng(DEFAULT_LAT, DEFAULT_LON)).zoom(14.5).build()
        map.addOnMapClickListener { clicked ->
            val tap = map.projection.toScreenLocation(clicked)
            val hit = MapGeo.nearestWithin(map.screenPoints(pointsNow), tap.x, tap.y, HIT_RADIUS_DP * holderDensity(map))
            if (hit >= 0) onRemove(hit) else onAdd(CoursePoint(clicked.latitude, clicked.longitude))
            true
        }
    }
    val style = holder.style

    LaunchedEffect(style, casingColor) {
        val s = style ?: return@LaunchedEffect
        installEditLayers(s, casingColor)
    }
    LaunchedEffect(style, points) {
        val s = style ?: return@LaunchedEffect
        s.setJson("edit-line-src", MapGeo.courseLine(points))
        s.setJson("edit-pts-src", MapGeo.editPoints(points, COURSE_COLOR))
    }
    LaunchedEffect(holder.map, center) {
        val c = center ?: return@LaunchedEffect
        holder.map?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(c.latitude, c.longitude)))
    }
    LaunchedEffect(holder.map, fit) {
        if (fit != null && fit.size >= 2) holder.fit(fit.map { it.latitude }, fit.map { it.longitude })
    }

    Box(modifier = modifier) {
        AndroidView(factory = { holder.view }, modifier = Modifier.fillMaxSize())
        Attribution(Modifier.align(Alignment.BottomEnd))
    }
}

private fun holderDensity(@Suppress("UNUSED_PARAMETER") map: org.maplibre.android.maps.MapLibreMap): Float =
    android.content.res.Resources.getSystem().displayMetrics.density

private fun installEditLayers(style: Style, casingColor: Int) {
    if (style.getSource("edit-line-src") != null) return
    val empty = """{"type":"FeatureCollection","features":[]}"""
    style.addSource(GeoJsonSource("edit-line-src", empty))
    style.addSource(GeoJsonSource("edit-pts-src", empty))
    style.addLayer(
        LineLayer("edit-line-casing", "edit-line-src").withProperties(
            PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round"),
            PropertyFactory.lineWidth(8f), PropertyFactory.lineColor(casingColor), PropertyFactory.lineOpacity(0.85f),
        ),
    )
    style.addLayer(
        LineLayer("edit-line", "edit-line-src").withProperties(
            PropertyFactory.lineJoin("round"), PropertyFactory.lineWidth(4f), PropertyFactory.lineColor("#2F80ED"),
            PropertyFactory.lineDasharray(arrayOf(2.5f, 1.8f)),
        ),
    )
    style.addLayer(
        CircleLayer("edit-pts", "edit-pts-src").withProperties(
            PropertyFactory.circleRadius(Expression.get("r")),
            PropertyFactory.circleColor(Expression.toColor(Expression.get("c"))),
            PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(2f),
        ),
    )
}

private const val DEFAULT_LAT = 37.5665
private const val DEFAULT_LON = 126.9780
private const val HIT_RADIUS_DP = 24f
private const val COURSE_COLOR = 0x2F80ED
