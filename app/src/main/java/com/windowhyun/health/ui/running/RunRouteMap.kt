package com.windowhyun.health.ui.running

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.sp
import com.windowhyun.health.BuildConfig
import com.windowhyun.health.domain.model.RunPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

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
    /** 타일을 받아 오는 방법. 비워 두면 OpenStreetMap 에서 받는다(화면 캡처 테스트가 미리 받아 둔 타일을 넣는 자리). */
    tileProvider: MapTileProviderBase? = null,
) {
    val context = LocalContext.current
    if (LocalInspectionMode.current) return

    // 지도 바탕은 앱 테마를 따른다. 어두운 앱에서는 어두운 지도 위에 밝은 경로가, 밝은 앱에서는 옅은 회색 지도 위에 짙은 경로가 보인다.
    val darkMap = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ramp = remember(darkMap) { RouteColors.ramp(darkMap) }
    val startColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val endColor = ramp.last()
    val outlineColor = if (darkMap) 0xCC0B0D10.toInt() else 0xE6FFFFFF.toInt()
    // 타일을 아직 못 받았을 때 배경. osmdroid 기본값은 분홍빛 격자라 "고장난 화면"처럼 보이므로 지도 바탕색으로 바꾼다(이 색도 아래 색 변환을 거친다).
    val emptyTileColor = RouteMapStyle.TILE_PLACEHOLDER

    val mapView = remember {
        initOsmdroid(context)
        (if (tileProvider != null) MapView(context, tileProvider) else MapView(context)).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    val paced = remember(route) { RoutePace.fractions(route.downsample()).any { !it.isNaN() && it != 0.5f } }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.overlayManager.tilesOverlay.apply {
                    setLoadingBackgroundColor(emptyTileColor)
                    setLoadingLineColor(emptyTileColor)
                    // 색색의 도로 · 건물 · 글자가 경로를 묻지 않도록 바탕 지도를 차분한 회색조로 바꾼다.
                    setColorFilter(ColorMatrixColorFilter(ColorMatrix(RouteMapStyle.matrix(darkMap))))
                }
                view.overlays.clear()

                val displayed = route.downsample()
                if (displayed.size >= 2) {
                    view.overlays.add(
                        PaceRouteOverlay(
                            points = displayed,
                            fractions = RoutePace.fractions(displayed),
                            ramp = ramp,
                            outlineColor = outlineColor,
                            density = view.resources.displayMetrics.density,
                        ),
                    )
                }

                val start = route.firstOrNull()
                val end = route.lastOrNull()
                if (start != null && end != null) {
                    view.overlays.add(
                        EndpointsOverlay(start, end, startColor, endColor, view.resources.displayMetrics.density, showStart = !followLatest),
                    )
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

        if (!followLatest && paced) {
            PaceLegend(ramp, Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
        // OpenStreetMap 데이터를 쓰려면 출처를 화면에 밝혀야 한다.
        Text(
            text = "© OpenStreetMap",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

/** 지도 한쪽에 두는 작은 설명: 선 색이 느림에서 빠름으로 바뀐다. */
@Composable
private fun PaceLegend(ramp: IntArray, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("느림", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier
                .padding(horizontal = 6.dp)
                .width(46.dp)
                .height(5.dp)
                .background(Brush.horizontalGradient(ramp.map { ComposeColor(it) }), RoundedCornerShape(50)),
        )
        Text("빠름", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 경로를 구간마다 빠르기에 맞는 색으로 그린다. 선 아래에 굵은 반투명 외곽선을 한 번 더 깔아, 어떤 지도 위에서도 선이 떠 보이게 한다.
 *
 * osmdroid 의 Polyline 은 한 색밖에 못 쓰고 탭하면 빈 말풍선을 띄우므로 직접 그린다.
 */
private class PaceRouteOverlay(
    private val points: List<RunPoint>,
    private val fractions: List<Float>,
    private val ramp: IntArray,
    outlineColor: Int,
    density: Float,
) : Overlay() {

    private val outlinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = OUTLINE_WIDTH_DP * density
        color = outlineColor
    }
    private val linePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = LINE_WIDTH_DP * density
    }
    private val pixel = Point()
    private val xs = FloatArray(points.size)
    private val ys = FloatArray(points.size)
    private val outline = Path()

    override fun draw(canvas: Canvas, projection: Projection) {
        for (i in points.indices) {
            projection.toPixels(GeoPoint(points[i].latitude, points[i].longitude), pixel)
            xs[i] = pixel.x.toFloat()
            ys[i] = pixel.y.toFloat()
        }

        // 일시정지로 끊긴 곳은 이어 그리지 않는다.
        outline.rewind()
        for (i in 1 until points.size) {
            if (points[i].isSegmentStart) continue
            outline.moveTo(xs[i - 1], ys[i - 1])
            outline.lineTo(xs[i], ys[i])
        }
        canvas.drawPath(outline, outlinePaint)

        for (i in 1 until points.size) {
            val fraction = fractions.getOrNull(i - 1) ?: continue
            if (points[i].isSegmentStart) continue
            linePaint.color = RoutePace.colorAt(if (fraction.isNaN()) 0.5f else fraction, ramp)
            canvas.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i], linePaint)
        }
    }

    private companion object {
        const val LINE_WIDTH_DP = 5f
        const val OUTLINE_WIDTH_DP = 9f
    }
}

/**
 * 시작/끝 점. 끝(기록 중에는 지금 위치)에는 은은한 둥근 빛을 둘러 눈에 띄게 한다.
 *
 * osmdroid 의 Marker 를 쓰지 않는 이유: Marker 는 기본 InfoWindow 를 달고 다녀서
 * 탭했을 때 빈 말풍선이 뜬다. 점 두 개를 그리는 데 그런 기능은 필요 없다.
 */
private class EndpointsOverlay(
    private val start: RunPoint,
    private val end: RunPoint,
    private val startColor: Int,
    private val endColor: Int,
    private val density: Float,
    private val showStart: Boolean,
) : Overlay() {

    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Color.WHITE
    }
    private val point = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        if (showStart) drawDot(canvas, projection, start, startColor, radiusDp = 5f, halo = false)
        drawDot(canvas, projection, end, endColor, radiusDp = 7f, halo = true)
    }

    private fun drawDot(canvas: Canvas, projection: Projection, at: RunPoint, color: Int, radiusDp: Float, halo: Boolean) {
        projection.toPixels(GeoPoint(at.latitude, at.longitude), point)
        val x = point.x.toFloat()
        val y = point.y.toFloat()
        val radius = radiusDp * density
        if (halo) {
            fillPaint.color = (color and 0x00FFFFFF) or 0x40000000
            canvas.drawCircle(x, y, radius * 2.4f, fillPaint)
        }
        fillPaint.color = color
        canvas.drawCircle(x, y, radius, fillPaint)
        canvas.drawCircle(x, y, radius, borderPaint)
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
