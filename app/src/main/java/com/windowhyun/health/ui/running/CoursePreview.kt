package com.windowhyun.health.ui.running

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.windowhyun.health.domain.model.CoursePoint
import kotlin.math.cos
import kotlin.math.min

/** 코스의 윤곽만 그린 작은 미리보기(지도 없이). 출발은 파란 점, 도착은 검은 점. */
@Composable
fun CoursePreview(points: List<CoursePoint>, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val line = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.padding(12.dp)) {
        val meanLat = points.map { it.latitude }.average()
        // 위도 1도와 경도 1도의 길이가 달라서 경도에 cos(위도) 를 곱해야 모양이 안 찌그러진다.
        val xs = points.map { it.longitude * cos(Math.toRadians(meanLat)) }
        val ys = points.map { -it.latitude }
        val w = (xs.max() - xs.min()).coerceAtLeast(1e-9)
        val h = (ys.max() - ys.min()).coerceAtLeast(1e-9)
        val scale = min(size.width / w, size.height / h).toFloat()
        val offsetX = (size.width - (w * scale).toFloat()) / 2f
        val offsetY = (size.height - (h * scale).toFloat()) / 2f
        fun at(i: Int) = Offset(
            offsetX + ((xs[i] - xs.min()) * scale).toFloat(),
            offsetY + ((ys[i] - ys.min()) * scale).toFloat(),
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(at(0).x, at(0).y)
            for (i in 1 until points.size) lineTo(at(i).x, at(i).y)
        }
        drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Color(0xFF2F80ED), radius = 5.dp.toPx(), center = at(0))
        drawCircle(Color.Black, radius = 5.dp.toPx(), center = at(points.lastIndex))
    }
}
