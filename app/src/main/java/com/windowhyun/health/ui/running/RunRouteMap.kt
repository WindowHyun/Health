package com.windowhyun.health.ui.running

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import com.windowhyun.health.BuildConfig
import com.windowhyun.health.domain.model.RunPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline

/**
 * OpenStreetMap 위에 러닝 경로를 그린다.
 *
 * 지도 SDK 로 osmdroid 를 쓰는 이유:
 * - API 키와 결제 계정이 필요 없어 설치하면 바로 지도가 뜬다
 * - 받은 타일을 기기에 캐시하므로 한 번 본 지역은 오프라인에서도 보인다
 *
 * 타일을 못 받는 상황(오프라인 등)에서도 **경로 선과 시작/끝 표시는 그대로 보인다.**
 * 배경만 비어 있을 뿐 어디를 어떻게 달렸는지는 확인할 수 있다.
 */
@Composable
fun RunRouteMap(
    route: List<RunPoint>,
    modifier: Modifier = Modifier,
    /** 기록 중에는 마지막 위치를 따라가고, 결과 화면에서는 전체 경로를 맞춰 보여 준다. */
    followLatest: Boolean = false,
) {
    val context = LocalContext.current
    if (LocalInspectionMode.current) return

    val lineColor = MaterialTheme.colorScheme.primary.toArgb()
    val startColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val endColor = MaterialTheme.colorScheme.error.toArgb()
    // 타일을 아직 못 받았을 때 배경. osmdroid 기본값은 분홍빛 격자라
    // "고장난 화면"처럼 보이므로 앱 배경색으로 바꾼다.
    val emptyTileColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()

    val mapView = remember {
        initOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            overlayManager.tilesOverlay.setLoadingBackgroundColor(emptyTileColor)
            overlayManager.tilesOverlay.setLoadingLineColor(emptyTileColor)
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.overlays.clear()

            val displayed = route.downsample()
            displayed.toSegments().forEach { segment ->
                if (segment.size < 2) return@forEach
                val polyline = Polyline(view).apply {
                    setPoints(segment.map { GeoPoint(it.latitude, it.longitude) })
                    outlinePaint.color = lineColor
                    outlinePaint.strokeWidth = 12f
                    outlinePaint.isAntiAlias = true
                    // 기본 동작은 탭하면 빈 말풍선(InfoWindow)을 띄운다.
                    // 보여 줄 내용이 없으므로 탭을 아예 무시한다.
                    setOnClickListener { _, _, _ -> false }
                    infoWindow = null
                }
                view.overlays.add(polyline)
            }

            val start = route.firstOrNull()
            val end = route.lastOrNull()
            if (start != null && end != null) {
                view.overlays.add(EndpointsOverlay(start, end, startColor, endColor))
            }

            when {
                route.isEmpty() -> Unit

                followLatest -> {
                    val last = route.last()
                    view.controller.setZoom(RUNNING_ZOOM)
                    view.controller.setCenter(GeoPoint(last.latitude, last.longitude))
                }

                route.size == 1 -> {
                    view.controller.setZoom(RUNNING_ZOOM)
                    view.controller.setCenter(GeoPoint(route[0].latitude, route[0].longitude))
                }

                else -> view.zoomToRoute(route)
            }
            view.invalidate()
        },
    )
}

/**
 * 시작/끝 점.
 *
 * osmdroid 의 Marker 를 쓰지 않는 이유: Marker 는 기본 InfoWindow 를 달고 다녀서
 * 탭했을 때 빈 말풍선이 뜬다. 점 두 개를 그리는 데 그런 기능은 필요 없다.
 */
private class EndpointsOverlay(
    private val start: RunPoint,
    private val end: RunPoint,
    private val startColor: Int,
    private val endColor: Int,
) : Overlay() {

    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    private val point = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        drawDot(canvas, projection, start, startColor)
        drawDot(canvas, projection, end, endColor)
    }

    private fun drawDot(canvas: Canvas, projection: Projection, at: RunPoint, color: Int) {
        projection.toPixels(GeoPoint(at.latitude, at.longitude), point)
        fillPaint.color = color
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), DOT_RADIUS, fillPaint)
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), DOT_RADIUS, borderPaint)
    }

    private companion object {
        const val DOT_RADIUS = 14f
    }
}

/**
 * 화면에 그릴 점 수를 [MAX_DISPLAY_POINTS] 이하로 줄인다.
 *
 * 긴 러닝은 점이 수천 개가 되는데, 기록 중에는 1초마다 다시 그리므로
 * 그대로 두면 점점 무거워진다. 시작/끝과 구간 시작점은 항상 남긴다.
 */
private fun List<RunPoint>.downsample(): List<RunPoint> {
    if (size <= MAX_DISPLAY_POINTS) return this
    val step = size / MAX_DISPLAY_POINTS + 1
    return filterIndexed { index, point ->
        index == 0 || index == lastIndex || point.isSegmentStart || index % step == 0
    }
}

/** 일시정지로 끊긴 지점에서 선을 나눈다. */
private fun List<RunPoint>.toSegments(): List<List<RunPoint>> {
    if (isEmpty()) return emptyList()
    val segments = mutableListOf<MutableList<RunPoint>>()
    forEachIndexed { index, point ->
        if (index == 0 || point.isSegmentStart || segments.isEmpty()) {
            segments += mutableListOf(point)
        } else {
            segments.last() += point
        }
    }
    return segments
}

/**
 * 경로 전체가 화면에 들어오도록 맞춘다.
 *
 * 아주 짧은 러닝(수십 미터)은 그대로 맞추면 최대 배율까지 당겨져서
 * 주변이 하나도 안 보인다. 최소 범위를 두어 주변 지형이 같이 보이게 한다.
 */
private fun MapView.zoomToRoute(route: List<RunPoint>) {
    val latitudes = route.map { it.latitude }
    val longitudes = route.map { it.longitude }
    val centerLat = latitudes.average()
    val centerLon = longitudes.average()

    val halfLatSpan = maxOf((latitudes.max() - latitudes.min()) / 2, MIN_HALF_SPAN_DEGREES)
    val halfLonSpan = maxOf((longitudes.max() - longitudes.min()) / 2, MIN_HALF_SPAN_DEGREES)

    val box = BoundingBox(
        centerLat + halfLatSpan,
        centerLon + halfLonSpan,
        centerLat - halfLatSpan,
        centerLon - halfLonSpan,
    )
    // 레이아웃이 아직 안 잡혔으면 zoomToBoundingBox 가 무시되므로 post 로 미룬다.
    post { zoomToBoundingBox(box.increaseByScale(1.2f), false, 0, MAX_ZOOM, null) }
}

/** osmdroid 는 전역 설정을 한 번 해 주어야 타일 캐시를 쓸 수 있다. */
private fun initOsmdroid(context: Context) {
    val configuration = Configuration.getInstance()
    // osmdroid 전용 SharedPreferences 를 쓴다(앱 기본 설정과 섞이지 않게).
    configuration.load(context, context.getSharedPreferences(OSMDROID_PREFS, Context.MODE_PRIVATE))

    // OSM 타일 서버 정책상 앱을 구분할 수 있는 User-Agent 를 반드시 지정해야 한다.
    // 패키지명만 보내면 거부당할 수 있어 앱 이름과 버전까지 붙인다.
    configuration.userAgentValue =
        "HealthTracker/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"

    // 캐시 경로를 직접 지정하면 osmdroid 가 디렉터리를 만들어 주지 않는다.
    // 없는 폴더를 가리키면 타일이 저장되지 않아 매번 다시 받게 되므로 직접 만든다.
    val basePath = context.getExternalFilesDir(null) ?: context.filesDir
    val tileCache = basePath.resolve("osmdroid/tiles")
    if (tileCache.exists() || tileCache.mkdirs()) {
        configuration.osmdroidBasePath = basePath.resolve("osmdroid")
        configuration.osmdroidTileCache = tileCache
    }
}

private const val OSMDROID_PREFS = "osmdroid"
private const val RUNNING_ZOOM = 17.0

/** OSM 타일이 존재하는 최대 배율은 19 이지만, 18 이 주변 파악에 더 낫다. */
private const val MAX_ZOOM = 18.0

/** 약 55m. 이보다 짧은 러닝도 주변이 보이도록 범위를 넓힌다. */
private const val MIN_HALF_SPAN_DEGREES = 0.00025

private const val MAX_DISPLAY_POINTS = 600
