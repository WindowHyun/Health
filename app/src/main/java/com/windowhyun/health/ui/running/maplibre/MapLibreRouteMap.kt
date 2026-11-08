package com.windowhyun.health.ui.running.maplibre

import android.content.Context
import android.graphics.PointF
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.windowhyun.health.domain.model.Course
import com.windowhyun.health.domain.model.CoursePoint
import com.windowhyun.health.domain.model.RunPoint
import com.windowhyun.health.ui.running.RouteColors
import com.windowhyun.health.ui.running.RoutePace
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * MapLibre + OpenFreeMap 으로 그린 러닝 지도. 연한 바탕 위에 경로선이 돋보이게 한다(속도에 따라 색이 바뀌는 굵은 선, 흰/어두운 외곽선).
 * [com.windowhyun.health.ui.running.RunRouteMap] 과 같은 입력을 받는다.
 */
@Composable
internal fun MapLibreRouteMap(
    route: List<RunPoint>,
    course: Course?,
    followLatest: Boolean,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) return
    val darkMap = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ramp = remember(darkMap) { RouteColors.ramp(darkMap) }
    val startColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val endColor = ramp.last()
    val casingColor = if (darkMap) 0xFF0B0D10.toInt() else 0xFFFFFFFF.toInt()

    val holder = rememberMapLibre(darkMap)
    val style = holder.style

    val displayed = remember(route) { route.thin() }
    val segmentsJson = remember(displayed, ramp) { MapGeo.routeSegments(displayed, RoutePace.fractions(displayed), ramp) }
    val courseJson = remember(course) { MapGeo.courseLine(course?.points.orEmpty()) }
    val endpointsJson = remember(route, followLatest, startColor, endColor) {
        val s = route.firstOrNull()
        val e = route.lastOrNull()
        if (s != null && e != null) MapGeo.endpoints(s, e, startColor, endColor, showStart = !followLatest) else MapGeo.emptyEndpoints()
    }

    LaunchedEffect(style, casingColor) {
        val s = style ?: return@LaunchedEffect
        installRouteLayers(s, casingColor, holder.density)
    }
    LaunchedEffect(style, segmentsJson, courseJson, endpointsJson) {
        val s = style ?: return@LaunchedEffect
        s.setJson("course-src", courseJson)
        s.setJson("route-src", segmentsJson)
        s.setJson("endpoints-src", endpointsJson)
    }
    LaunchedEffect(style, route.size, route.lastOrNull(), followLatest, course) {
        val map = holder.map ?: return@LaunchedEffect
        if (style == null) return@LaunchedEffect
        when {
            route.isNotEmpty() && followLatest -> route.last().let {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), RUNNING_ZOOM))
            }
            route.size >= 2 -> holder.fit(route.map { it.latitude }, route.map { it.longitude })
            route.size == 1 -> map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(route[0].latitude, route[0].longitude), RUNNING_ZOOM))
            course != null && course.points.size >= 2 -> holder.fit(course.points.map { it.latitude }, course.points.map { it.longitude })
        }
    }

    // 지도가 자기 영역 밖(아래 패널 뒤)까지 그려지지 않게 잘라 낸다.
    Box(modifier = modifier.clipToBounds()) {
        AndroidView(factory = { holder.view }, modifier = Modifier.fillMaxSize())
        Attribution(Modifier.align(Alignment.BottomEnd))
    }
}

/** OpenFreeMap · OpenStreetMap 데이터를 쓰려면 출처를 화면에 밝혀야 한다. */
@Composable
internal fun Attribution(modifier: Modifier = Modifier) {
    Text(
        text = "© OpenFreeMap © OpenMapTiles · OpenStreetMap",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/** 화면에 그릴 점 수를 줄인다(기록 중에는 자주 다시 그리므로). 시작 · 끝 · 구간 시작점은 남긴다. */
private fun List<RunPoint>.thin(max: Int = 600): List<RunPoint> {
    if (size <= max) return this
    val step = size / max + 1
    return filterIndexed { i, p -> i == 0 || i == lastIndex || p.isSegmentStart || i % step == 0 }
}

internal const val RUNNING_ZOOM = 16.5

// ----- 지도 틀 -----

/** 만들어진 MapLibre 지도와 그 상태. */
internal class MapLibreHolder(val view: MapView, val density: Float) {
    var map: MapLibreMap? by mutableStateOf(null)
    var style: Style? by mutableStateOf(null)

    /** 위도 · 경도 범위가 화면에 들어오게 맞춘다(아직 크기가 정해지지 않았으면 다음 틀에서). */
    fun fit(latitudes: List<Double>, longitudes: List<Double>) {
        val m = map ?: return
        val b = MapGeo.bounds(latitudes, longitudes)
        val bounds = LatLngBounds.Builder().include(LatLng(b.south, b.west)).include(LatLng(b.north, b.east)).build()
        view.post { runCatching { m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, (48 * density).toInt())) } }
    }
}

/**
 * MapLibre 지도를 만들고, 화면의 생명 주기에 맞춰 시작 · 멈춤을 전달한다. 어둡고 밝은 모양이 바뀌면 스타일을 다시 불러온다.
 */
@Composable
internal fun rememberMapLibre(darkMap: Boolean, configure: (MapLibreMap) -> Unit = {}): MapLibreHolder {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember {
        MapLibre.getInstance(context.applicationContext)
        val view = MapView(context).also { it.onCreate(null) }
        MapLibreHolder(view, context.resources.displayMetrics.density)
    }
    val configureState by rememberUpdatedState(configure)

    DisposableEffect(holder, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> holder.view.onStart()
                Lifecycle.Event.ON_RESUME -> holder.view.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.view.onPause()
                Lifecycle.Event.ON_STOP -> holder.view.onStop()
                else -> Unit
            }
        }
        // 이미 시작된 화면에 붙는 경우를 위해 지금 상태까지 따라잡는다.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) holder.view.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) holder.view.onResume()
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) holder.view.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) holder.view.onStop()
            holder.view.onDestroy()
        }
    }

    LaunchedEffect(holder) {
        holder.view.getMapAsync { map ->
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = false
            map.uiSettings.isCompassEnabled = false
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.isTiltGesturesEnabled = false
            configureState(map)
            holder.map = map
        }
    }
    LaunchedEffect(holder.map, darkMap) {
        val map = holder.map ?: return@LaunchedEffect
        holder.style = null
        map.setStyle(Style.Builder().fromUri(MapGeo.styleUrl(darkMap))) { style -> holder.style = style }
    }
    return holder
}

internal fun Style.setJson(sourceId: String, json: String) {
    getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(json)
}

private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

/** 코스 · 경로 · 끝점 층을 스타일에 얹는다(이미 있으면 건너뛴다). 경로가 코스 위에 보이도록 코스를 먼저 얹는다. */
internal fun installRouteLayers(style: Style, casingColor: Int, density: Float) {
    if (style.getSource("route-src") != null) return
    style.addSource(GeoJsonSource("course-src", EMPTY))
    style.addSource(GeoJsonSource("route-src", EMPTY))
    style.addSource(GeoJsonSource("endpoints-src", EMPTY))

    style.addLayer(
        LineLayer("course-casing", "course-src").withProperties(
            PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round"),
            PropertyFactory.lineWidth(8f), PropertyFactory.lineColor(casingColor), PropertyFactory.lineOpacity(0.85f),
        ),
    )
    style.addLayer(
        LineLayer("course-line", "course-src").withProperties(
            PropertyFactory.lineJoin("round"),
            PropertyFactory.lineWidth(4f), PropertyFactory.lineColor(COURSE_COLOR_HEX),
            PropertyFactory.lineDasharray(arrayOf(2.5f, 1.8f)),
        ),
    )
    style.addLayer(
        LineLayer("route-casing", "route-src").withProperties(
            PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round"),
            PropertyFactory.lineWidth(10f), PropertyFactory.lineColor(casingColor), PropertyFactory.lineOpacity(0.9f),
        ),
    )
    style.addLayer(
        LineLayer("route-line", "route-src").withProperties(
            PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round"),
            PropertyFactory.lineWidth(5.5f), PropertyFactory.lineColor(Expression.toColor(Expression.get("c"))),
        ),
    )
    style.addLayer(
        CircleLayer("endpoints-halo", "endpoints-src").withProperties(
            PropertyFactory.circleRadius(Expression.get("hr")),
            PropertyFactory.circleColor(Expression.toColor(Expression.get("c"))), PropertyFactory.circleOpacity(0.28f),
        ),
    )
    style.addLayer(
        CircleLayer("endpoints", "endpoints-src").withProperties(
            PropertyFactory.circleRadius(Expression.get("r")),
            PropertyFactory.circleColor(Expression.toColor(Expression.get("c"))),
            PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(2.5f),
        ),
    )
}

private const val COURSE_COLOR_HEX = "#2F80ED"

/** 화면 좌표로 바꾼 점들(터치 판정용). */
internal fun MapLibreMap.screenPoints(points: List<CoursePoint>): List<Pair<Float, Float>> =
    points.map { p ->
        val pt: PointF = projection.toScreenLocation(LatLng(p.latitude, p.longitude))
        pt.x to pt.y
    }

