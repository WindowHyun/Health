package com.windowhyun.health.ui.running

import android.content.Context
import android.graphics.Color as AndroidColor
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
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * OpenStreetMap 위에 러닝 경로를 그린다.
 *
 * 지도 SDK 로 osmdroid 를 쓰는 이유:
 * - API 키와 결제 계정이 필요 없어 설치하면 바로 지도가 뜬다
 * - 받은 타일을 기기에 캐시하므로 한 번 본 지역은 오프라인에서도 보인다
 *
 * 타일을 못 받는 상황(비행기 모드 등)에서도 **경로 선과 시작/끝 표시는 그대로 보인다.**
 * 배경만 비어 있을 뿐 어디를 어떻게 달렸는지는 확인할 수 있다.
 */
@Composable
fun RunRouteMap(
    route: List<RunPoint>,
    modifier: Modifier = Modifier,
    /** 기록 중에는 마지막 위치를 따라가고, 결과 화면에서는 전체 경로를 맞춰 보여 준다. */
    followLatest: Boolean = false,
    interactive: Boolean = true,
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    val lineColor = MaterialTheme.colorScheme.primary.toArgb()
    val startColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val endColor = MaterialTheme.colorScheme.error.toArgb()

    if (isPreview) return

    val mapView = remember {
        initOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(interactive)
            setUseDataConnection(true)
            // 확대/축소 버튼은 화면을 어지럽히므로 숨기고 손가락 조작만 쓴다.
            zoomController.setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER,
            )
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

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.overlays.clear()

            val segments = route.downsample().toSegments()
            segments.forEach { segment ->
                if (segment.size < 2) return@forEach
                val polyline = Polyline(view).apply {
                    setPoints(segment.map { GeoPoint(it.latitude, it.longitude) })
                    outlinePaint.color = lineColor
                    outlinePaint.strokeWidth = 12f
                    outlinePaint.isAntiAlias = true
                }
                view.overlays.add(polyline)
            }

            route.firstOrNull()?.let { view.overlays.add(view.dot(it, startColor)) }
            route.lastOrNull()?.let { view.overlays.add(view.dot(it, endColor)) }

            when {
                route.isEmpty() -> Unit

                followLatest -> {
                    // 기록 중에는 마지막 위치를 화면 가운데에 둔다.
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

private fun MapView.dot(point: RunPoint, color: Int): Marker = Marker(this).apply {
    position = GeoPoint(point.latitude, point.longitude)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    icon = CircleDrawable(color)
    setInfoWindow(null)
}

/** 경로 전체가 화면에 들어오도록 맞춘다. */
private fun MapView.zoomToRoute(route: List<RunPoint>) {
    val latitudes = route.map { it.latitude }
    val longitudes = route.map { it.longitude }
    val box = BoundingBox(
        latitudes.max(),
        longitudes.max(),
        latitudes.min(),
        longitudes.min(),
    )
    // 레이아웃이 아직 안 잡혔으면 zoomToBoundingBox 가 무시되므로 post 로 미룬다.
    post { zoomToBoundingBox(box.increaseByScale(1.3f), false) }
}

/** osmdroid 는 전역 설정을 한 번 해 주어야 타일 캐시를 쓸 수 있다. */
private fun initOsmdroid(context: Context) {
    val configuration = Configuration.getInstance()
    // osmdroid 전용 SharedPreferences 를 쓴다(앱 기본 설정과 섞이지 않게).
    configuration.load(context, context.getSharedPreferences(OSMDROID_PREFS, Context.MODE_PRIVATE))
    // OSM 타일 서버 정책상 앱을 구분할 수 있는 User-Agent 를 반드시 지정해야 한다.
    configuration.userAgentValue = BuildConfig.APPLICATION_ID
    configuration.osmdroidBasePath = context.getExternalFilesDir(null) ?: context.filesDir
    configuration.osmdroidTileCache = configuration.osmdroidBasePath.resolve("tiles")
}

private const val OSMDROID_PREFS = "osmdroid"
private const val RUNNING_ZOOM = 17.0
private const val MAX_DISPLAY_POINTS = 600

/** 시작/끝을 표시하는 작은 원. 별도 리소스를 만들지 않으려고 직접 그린다. */
private class CircleDrawable(private val color: Int) : android.graphics.drawable.Drawable() {

    private val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = this@CircleDrawable.color
        style = android.graphics.Paint.Style.FILL
    }
    private val borderPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = AndroidColor.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 4f
    }

    override fun draw(canvas: android.graphics.Canvas) {
        val radius = minOf(bounds.width(), bounds.height()) / 2f
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        canvas.drawCircle(cx, cy, radius, paint)
        canvas.drawCircle(cx, cy, radius, borderPaint)
    }

    override fun getIntrinsicWidth(): Int = SIZE
    override fun getIntrinsicHeight(): Int = SIZE
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit

    @Deprecated("Drawable 의 추상 메서드라 구현만 해 둔다.")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

    private companion object {
        const val SIZE = 28
    }
}
