package com.windowhyun.health.ui.running

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.windowhyun.health.domain.model.CoursePoint
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

/**
 * 코스를 찍는 지도. 지도를 한 번 누를 때마다 [onAddPoint] 로 그 자리가 전달되고, 지점들은 선으로 이어 보인다.
 * 손가락으로 끌어 옮기거나 두 손가락으로 확대 · 축소하는 것은 지도 기본 동작 그대로다(그 동작은 지점을 찍지 않는다).
 */
@Composable
fun CourseEditMap(
    points: List<CoursePoint>,
    /** 처음 보여 줄 중심. 바뀌면 그 자리로 옮긴다(현재 위치를 찾은 순간). */
    center: CoursePoint?,
    /** 코스를 불러온 직후 전체가 보이게 맞출 점들. */
    fit: List<CoursePoint>? = null,
    onAddPoint: (CoursePoint) -> Unit,
    onRemovePoint: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tileProvider: MapTileProviderBase? = null,
) {
    val context = LocalContext.current
    if (LocalInspectionMode.current) return
    val darkMap = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val outline = if (darkMap) 0xCC0B0D10.toInt() else 0xE6FFFFFF.toInt()
    val onAdd by rememberUpdatedState(onAddPoint)
    val onRemove by rememberUpdatedState(onRemovePoint)

    val mapView = remember {
        initOsmdroid(context)
        (if (tileProvider != null) MapView(context, tileProvider) else MapView(context)).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(DEFAULT_LAT, DEFAULT_LON))
        }
    }
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }
    LaunchedEffect(fit) {
        if (fit != null && fit.size >= 2) mapView.zoomToCourse(fit)
    }
    LaunchedEffect(center) {
        center?.let { mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude)) }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.overlayManager.tilesOverlay.apply {
                    setLoadingBackgroundColor(RouteMapStyle.TILE_PLACEHOLDER)
                    setLoadingLineColor(RouteMapStyle.TILE_PLACEHOLDER)
                    setColorFilter(ColorMatrixColorFilter(ColorMatrix(RouteMapStyle.matrix(darkMap))))
                }
                view.overlays.clear()
                if (points.size >= 2) view.overlays.add(CourseOverlay(points, outline, view.resources.displayMetrics.density))
                view.overlays.add(PointsOverlay(points, view.resources.displayMetrics.density))
                view.overlays.add(
                    TapOverlay(points, view.resources.displayMetrics.density, onAdd = { geo -> onAdd(CoursePoint(geo.latitude, geo.longitude)) }, onRemove = { onRemove(it) }),
                )
                view.invalidate()
            },
        )
        Text(
            text = "© OpenStreetMap",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

/**
 * 한 번 누르면 새 점을 찍는다. 이미 찍은 점(약 24dp 안)을 누르면 그 점을 뺀다.
 * 끌기 · 두 번 누르기(확대) · 여러 손가락은 지도가 처리한다.
 */
private class TapOverlay(
    private val points: List<CoursePoint>,
    private val density: Float,
    private val onAdd: (GeoPoint) -> Unit,
    private val onRemove: (Int) -> Unit,
) : Overlay() {
    private val pixel = Point()

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val projection = mapView.projection
        val hit = HIT_RADIUS_DP * density
        var nearest = -1
        var nearestDistance = Float.MAX_VALUE
        points.forEachIndexed { i, p ->
            projection.toPixels(GeoPoint(p.latitude, p.longitude), pixel)
            val dx = pixel.x - e.x
            val dy = pixel.y - e.y
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            if (d <= hit && d < nearestDistance) {
                nearest = i
                nearestDistance = d
            }
        }
        if (nearest >= 0) onRemove(nearest) else onAdd(projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint)
        return true
    }

    private companion object {
        const val HIT_RADIUS_DP = 24f
    }
}

/** 찍은 지점마다 번호 없는 점을 그린다(처음 점은 구분되게 더 크게). */
private class PointsOverlay(private val points: List<CoursePoint>, private val density: Float) : Overlay() {
    private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = CourseOverlay.COURSE_COLOR }
    private val ring = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 2f * density; color = Color.WHITE }
    private val pixel = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        points.forEachIndexed { i, p ->
            projection.toPixels(GeoPoint(p.latitude, p.longitude), pixel)
            val r = (if (i == 0) 8f else 5f) * density
            canvas.drawCircle(pixel.x.toFloat(), pixel.y.toFloat(), r, fill)
            canvas.drawCircle(pixel.x.toFloat(), pixel.y.toFloat(), r, ring)
        }
    }
}

private const val DEFAULT_LAT = 37.5665
private const val DEFAULT_LON = 126.9780
