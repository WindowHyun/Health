package com.windowhyun.health.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.windowhyun.health.core.designsystem.theme.HealthLime
import com.windowhyun.health.ui.components.HealthButton
import com.windowhyun.health.ui.components.HealthOutlinedButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 카드는 테마와 상관없이 늘 같은 모양이어야 한다(저장된 이미지가 라이트/다크에 따라 달라지면 안 된다).
private val CardBackground = Color(0xFF0B0B0B)
private val CardInk = Color(0xFFFFFFFF)
private val CardMuted = Color(0xFF9B9B9B)
private val CardLine = Color(0xFF2B2B2B)

/** 카드의 논리 크기(dp). 4:5 세로형이라 인스타그램 등에 올리기 좋다. */
private const val CARD_WIDTH_DP = 360
private const val CARD_HEIGHT_DP = 450

/**
 * 정리 카드 미리보기와 "공유 / 갤러리에 저장" 버튼.
 *
 * 카드는 화면 크기와 상관없이 360x450dp 로 그린 것을 그대로 이미지로 뜬다(미리보기만 화면 폭에 맞춰 줄인다).
 * 그래서 저장된 이미지는 기기마다 같은 구성이고, 시스템 글자 크기에도 흔들리지 않는다.
 */
@Composable
fun ShareCardSection(
    fileName: String,
    description: String,
    modifier: Modifier = Modifier,
    card: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun run(action: suspend (android.graphics.Bitmap) -> String) {
        if (working) return
        working = true
        message = null
        scope.launch {
            try {
                val bitmap = layer.toImageBitmap().asAndroidBitmap()
                message = action(bitmap)
                failed = false
            } catch (e: Exception) {
                message = e.message ?: "이미지를 만들지 못했습니다."
                failed = true
            } finally {
                working = false
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val scale = (maxWidth / CARD_WIDTH_DP.dp).coerceAtMost(1f)
            val density = LocalDensity.current
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size((CARD_WIDTH_DP * scale).dp, (CARD_HEIGHT_DP * scale).dp)
                    .clip(RoundedCornerShape(10.dp))
                    .semantics { contentDescription = description },
            ) {
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .requiredSize(CARD_WIDTH_DP.dp, CARD_HEIGHT_DP.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                        // 줄이기 전 크기 그대로 기록해 둔다. 이미지로 뜰 때는 이 기록을 쓴다.
                        .drawWithContent {
                            // 기기 화면은 항상 하드웨어 그리기다. 소프트웨어 캔버스(화면 캡처 테스트)는
                            // 기록한 레이어를 그리지 못하므로 그때는 그냥 그린다.
                            if (drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                                layer.record { this@drawWithContent.drawContent() }
                                drawLayer(layer)
                            } else {
                                drawContent()
                            }
                        },
                ) {
                    // 시스템 글자 크기를 키워도 카드 안 글자가 넘치지 않게 고정한다.
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                        card()
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            HealthButton(
                onClick = {
                    run { bitmap ->
                        withContext(Dispatchers.IO) { ImageSharing.share(context, bitmap, fileName) }
                        "공유 창을 열었습니다."
                    }
                },
                enabled = !working,
                modifier = Modifier.weight(1f),
            ) { Text("공유") }
            if (ImageSharing.canSaveToGallery) {
                HealthOutlinedButton(
                    onClick = {
                        run { bitmap ->
                            withContext(Dispatchers.IO) { ImageSharing.saveToGallery(context, bitmap, fileName) }
                            "갤러리(Pictures/Health)에 저장했습니다."
                        }
                    },
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) { Text("갤러리에 저장") }
            }
        }
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * 이미 끝난 기록의 정리 카드를 창으로 띄운다. 기록 상세 화면의 공유 버튼이 연다.
 * 카드 · 버튼 · 저장은 운동 직후 화면과 같은 [ShareCardSection] 이다.
 */
@Composable
fun ShareCardDialog(
    fileName: String,
    description: String,
    onDismiss: () -> Unit,
    card: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                ShareCardSection(fileName = fileName, description = description, card = card)
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("닫기") }
            }
        }
    }
}

@Composable
private fun CardHeader(date: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = "HEALTH",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp),
            color = HealthLime,
        )
        Text(text = date, style = MaterialTheme.typography.labelMedium, color = CardMuted)
    }
}

@Composable
private fun CardStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = CardMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = CardInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CardBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(HealthLime)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF0A0A0A),
        )
    }
}

/** 헬스 정리 카드. 360x450dp. */
@Composable
fun WorkoutShareCard(data: WorkoutCardData, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CardBackground)
            .padding(24.dp),
    ) {
        CardHeader(data.date)
        Spacer(Modifier.height(22.dp))
        Text(
            text = data.title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = CardInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Text(text = "운동 시간", style = MaterialTheme.typography.labelMedium, color = CardMuted)
        Text(
            text = data.durationText,
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.ExtraBold),
            color = CardInk,
            maxLines = 1,
        )
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            CardStat("총 볼륨", data.volumeText, Modifier.weight(1.4f))
            CardStat("세트", "${data.totalSets}", Modifier.weight(1f))
            CardStat("운동", "${data.exerciseCount}개", Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = CardLine)
        Spacer(Modifier.height(6.dp))
        data.exercises.forEach { exercise ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CardInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                )
                Text(text = exercise.best, style = MaterialTheme.typography.bodyMedium, color = CardMuted, maxLines = 1)
            }
        }
        if (data.hiddenExerciseCount > 0) {
            Text(
                text = "외 ${data.hiddenExerciseCount}개 운동",
                style = MaterialTheme.typography.labelMedium,
                color = CardMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (data.personalRecordCount > 0) CardBadge("신기록 ${data.personalRecordCount}개")
    }
}

/** 러닝 정리 카드. 360x450dp. */
@Composable
fun RunShareCard(data: RunCardData, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CardBackground)
            .padding(24.dp),
    ) {
        CardHeader(data.date)
        Spacer(Modifier.height(22.dp))
        Text(
            text = "러닝",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = CardInk,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = data.distanceNumber,
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.ExtraBold),
                color = CardInk,
                maxLines = 1,
            )
            Text(
                text = data.distanceUnit,
                style = MaterialTheme.typography.titleLarge,
                color = CardMuted,
                modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            CardStat("시간", data.durationText, Modifier.weight(1f))
            CardStat("평균 페이스", data.paceText, Modifier.weight(1f))
            CardStat("칼로리", "${data.calories}kcal", Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            data.route?.let { RouteCanvas(it) }
        }
        if (data.isPersonalBest) {
            Spacer(Modifier.height(10.dp))
            CardBadge("신기록")
        }
    }
}

/** 지도 없이 길 모양만. 주어진 칸 안에서 실제 가로세로 비율을 지켜 가운데에 그린다. */
@Composable
private fun RouteCanvas(shape: RouteShape, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val pad = 8.dp.toPx()
        val availW = (size.width - pad * 2).coerceAtLeast(1f)
        val availH = (size.height - pad * 2).coerceAtLeast(1f)
        val drawW: Float
        val drawH: Float
        if (availW / availH > shape.aspect) {
            drawH = availH
            drawW = availH * shape.aspect
        } else {
            drawW = availW
            drawH = availW / shape.aspect
        }
        val origin = Offset((size.width - drawW) / 2f, (size.height - drawH) / 2f)
        fun point(p: Pair<Float, Float>) = Offset(origin.x + p.first * drawW, origin.y + p.second * drawH)

        shape.segments.forEach { segment ->
            val path = Path()
            segment.forEachIndexed { index, p ->
                val o = point(p)
                if (index == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(
                path = path,
                color = HealthLime,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        val start = point(shape.segments.first().first())
        val end = point(shape.segments.last().last())
        drawCircle(color = CardInk, radius = 5.dp.toPx(), center = start)
        drawCircle(color = CardBackground, radius = 7.dp.toPx(), center = end)
        drawCircle(color = HealthLime, radius = 5.dp.toPx(), center = end)
    }
}
