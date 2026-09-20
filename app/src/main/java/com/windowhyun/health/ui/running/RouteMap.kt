package com.windowhyun.health.ui.running

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.windowhyun.health.domain.model.RunPoint
import kotlin.math.cos
import kotlin.math.max

/**
 * GPS 이동 경로를 Canvas 로 그린다.
 *
 * 지도 SDK 를 쓰지 않는 이유:
 * - API 키와 네트워크가 필요해 "오프라인 · 서버 없음" 원칙에 맞지 않는다
 * - 개인 기록 확인에는 경로 모양만으로 충분하다
 *
 * 투영은 등장방형도법(equirectangular)을 쓰고, 경도는 위도에 따라 줄여
 * 짧은 거리에서 모양이 찌그러지지 않게 한다.
 */
@Composable
fun RouteMap(
    route: List<RunPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (route.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "기록된 경로가 없습니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        return
    }

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val startColor = MaterialTheme.colorScheme.tertiary
    val endColor = MaterialTheme.colorScheme.error

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        drawRect(color = surfaceColor)

        val centerLatRad = Math.toRadians(route.sumOf { it.latitude } / route.size)
        val lonScale = cos(centerLatRad)

        val xs = route.map { it.longitude * lonScale }
        val ys = route.map { -it.latitude } // 화면 y 는 아래로 증가하므로 뒤집는다

        val minX = xs.min()
        val maxX = xs.max()
        val minY = ys.min()
        val maxY = ys.max()

        val padding = 16.dp.toPx()
        val availableWidth = size.width - padding * 2
        val availableHeight = size.height - padding * 2

        // 가로세로 비율을 유지하도록 더 큰 쪽에 맞춘다.
        val spanX = max(maxX - minX, 1e-9)
        val spanY = max(maxY - minY, 1e-9)
        val scale = minOf(availableWidth / spanX, availableHeight / spanY).toFloat()

        val drawnWidth = (spanX * scale).toFloat()
        val drawnHeight = (spanY * scale).toFloat()
        val offsetX = padding + (availableWidth - drawnWidth) / 2f
        val offsetY = padding + (availableHeight - drawnHeight) / 2f

        fun project(index: Int) = Offset(
            x = offsetX + ((xs[index] - minX) * scale).toFloat(),
            y = offsetY + ((ys[index] - minY) * scale).toFloat(),
        )

        // 일시정지로 끊긴 구간은 선을 잇지 않는다.
        val path = Path()
        var started = false
        route.indices.forEach { index ->
            val point = project(index)
            if (!started || route[index].isSegmentStart) {
                path.moveTo(point.x, point.y)
                started = true
            } else {
                path.lineTo(point.x, point.y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 6.dp.toPx()),
        )

        drawCircle(color = startColor, radius = 7.dp.toPx(), center = project(0))
        drawCircle(color = endColor, radius = 7.dp.toPx(), center = project(route.lastIndex))
    }
}
