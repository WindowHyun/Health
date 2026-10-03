package com.windowhyun.health.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.windowhyun.health.core.designsystem.theme.healthColors

/**
 * 화면 전체가 같이 쓰는 "에디토리얼" 조각들.
 *
 * 카드로 감싸지 않고 가는 선과 여백으로 나눈다. 값은 크고 굵은 숫자에 작은 단위를 붙여 읽는다.
 */

/** 1px 구분선. 카드 대신 이것으로 영역을 나눈다. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/** 구역 이름. 작고 흐리게, 제목이 값을 누르지 않도록. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** 큰 숫자 + 작은 단위. 두 글자 크기가 달라도 글자 밑선이 맞는다. */
@Composable
fun MetricValue(
    number: String,
    unit: String,
    modifier: Modifier = Modifier,
    numberStyle: TextStyle = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp, lineHeight = 34.sp),
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier = modifier) {
        Text(
            text = number,
            style = numberStyle,
            color = color,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .alignByBaseline()
                .padding(start = 2.dp),
        )
    }
}

/**
 * 직사각형에 가까운(모서리 4dp) 버튼. 기본 알약 버튼 대신 쓴다.
 *
 * [container] 가 투명이면 [border] 만 그린다. [body] 는 Material 버튼처럼 줄 안에 놓이고
 * 아이콘 · 글자가 [content] 색과 굵은 글자체를 받는다.
 */
@Composable
fun HealthButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.healthColors.accent,
    content: Color = MaterialTheme.healthColors.onAccent,
    border: BorderStroke? = null,
    compact: Boolean = false,
    body: @Composable RowScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .heightIn(min = if (compact) 44.dp else 52.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(shape)
            .background(container)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 20.dp, vertical = if (compact) 8.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            ProvideTextStyle(if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    content = body,
                )
            }
        }
    }
}

/** 테두리만 있는 [HealthButton]. 두 번째로 중요한 동작. */
@Composable
fun HealthOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    body: @Composable RowScope.() -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    HealthButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        container = Color.Transparent,
        content = ink,
        border = BorderStroke(1.dp, ink),
        compact = compact,
        body = body,
    )
}

/** 글자만 있는 단순한 [HealthButton] 모양. */
@Composable
private fun BlockButton(
    text: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier,
    enabled: Boolean,
    border: BorderStroke? = null,
) {
    HealthButton(onClick, modifier, enabled, container, content, border) { Text(text) }
}

/** 가장 눈에 띄어야 하는 동작(시작). 화면에 하나씩만 쓴다. */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.healthColors
    BlockButton(text, onClick, colors.accent, colors.onAccent, modifier, enabled)
}

/** 어두운 패널 위나 라임 면 위에서 쓰는 단색 버튼. */
@Composable
fun PanelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.healthColors
    BlockButton(text, onClick, colors.panel, colors.onPanel, modifier, enabled)
}

/** 테두리만 있는 버튼. 두 번째로 중요한 동작. */
@Composable
fun OutlineBlockButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    BlockButton(text, onClick, Color.Transparent, ink, modifier, enabled, border = BorderStroke(1.dp, ink))
}

/** 진행 중인 운동 · 러닝으로 돌아가는 띠. 라임 면이라 다른 어떤 것보다 눈에 띈다. */
@Composable
fun StatusStrip(
    label: String,
    title: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.healthColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(colors.accent)
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onAccent.copy(alpha = 0.7f))
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onAccent)
        }
        PanelButton(text = action, onClick = onClick)
    }
}
