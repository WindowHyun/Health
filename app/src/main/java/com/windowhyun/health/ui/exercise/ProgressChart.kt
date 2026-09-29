package com.windowhyun.health.ui.exercise

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.windowhyun.health.core.designsystem.theme.chartColors
import com.windowhyun.health.core.util.niceTicks
import com.windowhyun.health.domain.model.ProgressPoint
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * 종목 성장 그래프(선 하나).
 *
 * - 선이 하나라 범례를 두지 않는다. 위의 지표 칩이 무엇을 그렸는지 알려 준다.
 * - 가로축은 날짜 간격대로 놓아 쉬었던 기간이 보이게 한다.
 * - 값은 마지막 점에만 직접 적고, 나머지는 눈금과 탭으로 본다. 탭하면 가장 가까운
 *   기록의 날짜와 값이 뜬다. 모든 값은 아래 전체 기록 목록에도 있다.
 * - 색은 선·점·면에만 쓰고, 글자는 본문 글자색으로 쓴다.
 *
 * [points] 의 값은 화면 단위(kg/lb 등)로 바꿔서 넘긴다.
 */
@Composable
fun ProgressChart(
    points: List<ProgressPoint>,
    formatValue: (Double) -> String,
    formatTick: (Double) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val lineColor = chartColors().gym
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val mutedText = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val tooltipBackground = MaterialTheme.colorScheme.inverseSurface
    val tooltipText = MaterialTheme.colorScheme.inverseOnSurface
    val labelStyle = MaterialTheme.typography.labelSmall
    val valueStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    var selected by remember(points) { mutableStateOf<Int?>(null) }

    // 탭 위치를 점으로 바꾸려면 그릴 때 계산한 x 가 필요하다. 그리는 중에 상태를 바꾸지
    // 않도록 화면 갱신과 무관한 보관함에 담는다.
    val pointXs = remember(points) { ArrayList<Float>() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(points) {
                detectTapGestures { tap ->
                    if (pointXs.isEmpty()) return@detectTapGestures
                    val nearest = pointXs.indices.minBy { abs(pointXs[it] - tap.x) }
                    selected = if (selected == nearest) null else nearest
                }
            },
    ) {
        if (points.isEmpty()) return@Canvas

        val values = points.map { it.value }
        val ticks = niceTicks(values.min(), values.max())
        val yMin = ticks.first()
        val yMax = ticks.last()

        val tickLabels = ticks.map { measurer.measure(formatTick(it), labelStyle.copy(color = mutedText)) }
        val left = (tickLabels.maxOf { it.size.width } + 8.dp.toPx())
        val top = 32.dp.toPx() // 마지막 값 · 말풍선 자리
        val bottomLabelHeight = measurer.measure("0", labelStyle).size.height
        val bottom = size.height - bottomLabelHeight - 8.dp.toPx()
        val right = size.width - 12.dp.toPx()

        fun y(value: Double): Float =
            (bottom - (value - yMin) / (yMax - yMin) * (bottom - top)).toFloat()

        // 가로축: 날짜 간격대로. 모두 같은 날이면 순서대로 고르게.
        val firstDay = points.first().date.toEpochDay()
        val span = points.last().date.toEpochDay() - firstDay
        val xs = points.mapIndexed { index, point ->
            val fraction = when {
                points.size == 1 -> 0.5
                span == 0L -> index.toDouble() / (points.size - 1)
                else -> (point.date.toEpochDay() - firstDay).toDouble() / span
            }
            (left + fraction * (right - left)).toFloat()
        }
        pointXs.clear()
        pointXs.addAll(xs)

        // 격자와 눈금: 1px 실선, 배경에서 한 단계만 떨어진 색.
        ticks.forEachIndexed { index, tick ->
            val ty = y(tick)
            drawLine(gridColor, Offset(left, ty), Offset(right, ty), strokeWidth = 1f)
            val label = tickLabels[index]
            drawText(label, topLeft = Offset(left - 8.dp.toPx() - label.size.width, ty - label.size.height / 2f))
        }

        // 가로축 날짜: 처음과 끝만.
        val pattern = if (points.first().date.year != points.last().date.year) "yy.M.d" else "M/d"
        val dateFormat = DateTimeFormatter.ofPattern(pattern)
        val firstLabel = measurer.measure(points.first().date.format(dateFormat), labelStyle.copy(color = mutedText))
        drawText(firstLabel, topLeft = Offset(xs.first() - firstLabel.size.width / 2f, bottom + 8.dp.toPx())
            .clampX(0f, size.width - firstLabel.size.width))
        if (points.size > 1) {
            val lastLabel = measurer.measure(points.last().date.format(dateFormat), labelStyle.copy(color = mutedText))
            drawText(lastLabel, topLeft = Offset(xs.last() - lastLabel.size.width / 2f, bottom + 8.dp.toPx())
                .clampX(0f, size.width - lastLabel.size.width))
        }

        // 선과 옅은 면(약 10%).
        if (points.size > 1) {
            val line = Path().apply {
                moveTo(xs[0], y(values[0]))
                for (i in 1 until points.size) lineTo(xs[i], y(values[i]))
            }
            val area = Path().apply {
                addPath(line)
                lineTo(xs.last(), bottom)
                lineTo(xs.first(), bottom)
                close()
            }
            drawPath(area, lineColor.copy(alpha = 0.10f))
            drawPath(
                line,
                lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // 점: 지름 8dp, 배경색 테두리 2dp. 점이 많으면 마지막과 고른 점만 찍는다.
        val radius = 4.dp.toPx()
        val ring = 2.dp.toPx()
        points.indices
            .filter { points.size <= 24 || it == points.lastIndex || it == selected }
            .forEach { i ->
                val center = Offset(xs[i], y(values[i]))
                val r = if (i == selected) radius + 1.dp.toPx() else radius
                drawCircle(surface, r + ring, center)
                drawCircle(lineColor, r, center)
            }

        // 마지막 값만 직접 적는다.
        val selectedIndex = selected
        if (selectedIndex == null) {
            val endLabel = measurer.measure(formatValue(values.last()), valueStyle.copy(color = ink))
            drawText(
                endLabel,
                topLeft = Offset(xs.last() - endLabel.size.width / 2f, y(values.last()) - radius - ring - endLabel.size.height - 2.dp.toPx())
                    .clampX(0f, size.width - endLabel.size.width)
                    .clampY(0f, size.height),
            )
        } else {
            // 고른 기록: 세로 안내선 + 맨 위 말풍선.
            val sx = xs[selectedIndex]
            drawLine(gridColor, Offset(sx, top), Offset(sx, bottom), strokeWidth = 1.dp.toPx())
            val text = "${points[selectedIndex].date.format(DateTimeFormatter.ofPattern("yyyy.M.d"))} · " +
                formatValue(values[selectedIndex])
            val bubble = measurer.measure(text, valueStyle.copy(color = tooltipText))
            val padH = 8.dp.toPx()
            val padV = 4.dp.toPx()
            val bubbleSize = Size(bubble.size.width + padH * 2, bubble.size.height + padV * 2)
            val bubbleTopLeft = Offset(sx - bubbleSize.width / 2f, 0f).clampX(0f, size.width - bubbleSize.width)
            drawRoundRect(tooltipBackground, bubbleTopLeft, bubbleSize, CornerRadius(6.dp.toPx()))
            drawText(bubble, topLeft = Offset(bubbleTopLeft.x + padH, bubbleTopLeft.y + padV))
        }
    }
}

private fun Offset.clampX(min: Float, max: Float) = Offset(x.coerceIn(min, max.coerceAtLeast(min)), y)

private fun Offset.clampY(min: Float, max: Float) = Offset(x, y.coerceIn(min, max.coerceAtLeast(min)))
