package com.windowhyun.health.wear

import android.content.ComponentName
import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors

/**
 * 시계 타일. 한 번 쓸어 넘기면 지금 러닝이나 세트를 보고, 누르면 앱이 열린다.
 * 보여 줄 운동이 없으면 안내 문구가 나온다.
 *
 * 타일은 시간이 흐르는 숫자를 스스로 갱신하지 못한다. 폰이 소식을 보낼 때마다(러닝은 몇 초마다)
 * [PhoneStateListenerService] 가 [requestUpdate] 로 새로 그리게 한다.
 */
class HealthTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            // Data Layer 읽기는 화면 스레드에서 할 수 없다.
            IO.execute {
                try {
                    val snapshots = kotlinx.coroutines.runBlocking { PhoneDataSource.readCurrent(applicationContext) }
                        ?: WatchSnapshots()
                    val content = OngoingPresenter.describe(
                        snapshots.run, snapshots.rest, snapshots.workout, System.currentTimeMillis(),
                    )
                    completer.set(buildTile(applicationContext, OngoingPresenter.tileLines(content)))
                } catch (e: Exception) {
                    completer.setException(e)
                }
            }
            "health-tile"
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
            "health-tile-resources"
        }

    companion object {
        private const val RESOURCES_VERSION = "1"
        private val IO = Executors.newSingleThreadExecutor()

        private const val LIME = 0xFFC6F432.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val MUTED = 0xFF9B9B9B.toInt()

        /** 상태가 바뀌었으니 타일을 새로 그리라고 알린다. 타일을 추가하지 않았다면 아무 일도 없다. */
        fun requestUpdate(context: Context) {
            runCatching { getUpdater(context).requestUpdate(HealthTileService::class.java) }
        }

        internal fun buildTile(context: Context, lines: List<String>): TileBuilders.Tile =
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(context, lines)))
                .build()

        /**
         * 줄마다 글을 쌓은 화면. 첫 줄은 제목(라임), 둘째 줄은 가장 큰 숫자, 나머지는 작은 글.
         * 화면 어디를 눌러도 앱이 열린다.
         */
        internal fun layout(context: Context, lines: List<String>): LayoutElementBuilders.LayoutElement {
            val open = ModifiersBuilders.Clickable.Builder()
                .setId("open_app")
                .setOnClick(
                    ActionBuilders.LaunchAction.Builder()
                        .setAndroidActivity(
                            ActionBuilders.AndroidActivity.Builder()
                                .setPackageName(context.packageName)
                                .setClassName(WatchActivity::class.java.name)
                                .build(),
                        )
                        .build(),
                )
                .build()

            val column = LayoutElementBuilders.Column.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open).build())
            lines.forEachIndexed { index, line ->
                column.addContent(text(line, index))
            }
            // 위아래로 가운데에 놓는다.
            return LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(column.build())
                .build()
        }

        private fun text(line: String, index: Int): LayoutElementBuilders.LayoutElement {
            val (size, color) = when (index) {
                0 -> 14f to LIME
                1 -> 30f to WHITE
                else -> 13f to MUTED
            }
            return LayoutElementBuilders.Text.Builder()
                .setText(line)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(size))
                        .setColor(ColorBuilders.argb(color))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .build(),
                )
                .build()
        }
    }
}
