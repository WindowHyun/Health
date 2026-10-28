package com.windowhyun.health.ui.screenshot

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.domain.model.RunPoint
import com.windowhyun.health.domain.model.ThemeMode
import com.windowhyun.health.ui.running.RunRouteMap
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 러닝 지도를 실제 OpenStreetMap 타일 위에 그려 PNG 로 남긴다(눈으로 보는 용도, 검증은 하지 않는다).
 *
 * 타일은 네트워크 없이 그리려고 미리 받아 둔 폴더(환경 변수 ROUTE_MAP_TILES, `z/x/y.png` 구조)에서 읽는다.
 * 폴더가 없으면 건너뛴다(CI 에서는 돌지 않는다). 저장소에 지도 타일을 넣지 않는 이유는 OSM 데이터의 사용 조건과 크기 때문이다.
 * 없는 타일은 build/screenshots/route-map-needed-tiles.txt 에 적는다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h700dp-xxhdpi")
class RouteMapScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val tileDir: File? = System.getenv("ROUTE_MAP_TILES")?.let(::File)?.takeIf { it.isDirectory }
    private val missing = linkedSetOf<String>()

    private inner class FolderTiles : MapTileProviderBase(TileSourceFactory.MAPNIK) {
        private val cache = HashMap<Long, Drawable?>()

        override fun getMapTile(index: Long): Drawable? = cache.getOrPut(index) {
            val z = MapTileIndex.getZoom(index)
            val x = MapTileIndex.getX(index)
            val y = MapTileIndex.getY(index)
            val file = File(tileDir, "$z/$x/$y.png")
            if (!file.isFile) {
                missing += "$z/$x/$y"
                null
            } else {
                BitmapFactory.decodeFile(file.path)?.let { BitmapDrawable(compose.activity.resources, it) }
            }
        }

        override fun getMinimumZoomLevel() = 0
        override fun getMaximumZoomLevel() = 19
        override fun getQueueSize() = 0L
        override fun getTileWriter(): IFilesystemCache = object : IFilesystemCache {
            override fun saveFile(s: ITileSource?, i: Long, input: java.io.InputStream?, expiry: Long?) = false
            override fun exists(s: ITileSource?, i: Long) = false
            override fun onDetach() = Unit
            override fun remove(s: ITileSource?, i: Long) = false
            override fun getExpirationTimestamp(s: ITileSource?, i: Long): Long? = null
            override fun loadTile(s: ITileSource?, i: Long): Drawable? = null
        }
    }

    /**
     * 여의도를 한 바퀴 도는 약 5km 가짜 경로. 초반은 천천히, 가운데는 빠르게, 끝은 다시 느리게 달리고
     * 중간에 한 번 멈췄다 다시 달린다. 실제 도로를 따르지는 않는다(보여 주려는 것은 선의 모양과 색이다).
     */
    private fun sampleRoute(): List<RunPoint> {
        val centerLat = 37.5265
        val centerLon = 126.9300
        val points = mutableListOf<RunPoint>()
        var time = 0L
        val count = 330
        for (i in 0..count) {
            val t = i.toDouble() / count
            val angle = 2 * PI * t - PI / 2
            val wobble = 1.0 + 0.10 * sin(7 * angle) + 0.05 * cos(3 * angle)
            val lat = centerLat + 0.0062 * sin(angle) * wobble
            val lon = centerLon + 0.0105 * cos(angle) * wobble
            // 앞 · 뒤는 느리고 가운데가 빠르다(걸리는 시간이 짧을수록 빠르다).
            val stepSeconds = 4.2 - 1.6 * sin(PI * t)
            time += (stepSeconds * 1_000).toLong()
            val pause = i == count / 2
            if (pause) time += 45_000
            points += RunPoint(lat, lon, timestamp = time, isSegmentStart = pause)
        }
        return points
    }

    private fun shot(name: String, dark: Boolean) {
        compose.setContent {
            HealthTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        RunRouteMap(route = sampleRoute(), modifier = Modifier.fillMaxSize(), tileProvider = FolderTiles())
                    }
                }
            }
        }
        // 지도 맞춤은 레이아웃 뒤에 post 로 실행되고, 타일은 그다음 그려질 때 요청된다.
        repeat(6) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
        }
        findMap(compose.activity.window.decorView)?.let { map ->
            println("ROUTEMAP zoom=${map.zoomLevelDouble} center=${map.mapCenter} box=${map.boundingBox} size=${map.width}x${map.height}")
        }
        compose.saveScreenshot(name)
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "route-map-needed-tiles.txt").writeText(missing.joinToString("\n"))
    }

    private fun findMap(view: android.view.View): org.osmdroid.views.MapView? {
        if (view is org.osmdroid.views.MapView) return view
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) findMap(view.getChildAt(i))?.let { return it }
        return null
    }

    @Test
    fun darkMap() {
        assumeTrue("ROUTE_MAP_TILES 폴더가 없어 건너뜁니다", tileDir != null)
        shot("route_map_dark", dark = true)
    }

    @Test
    fun lightMap() {
        assumeTrue("ROUTE_MAP_TILES 폴더가 없어 건너뜁니다", tileDir != null)
        shot("route_map_light", dark = false)
    }
}
