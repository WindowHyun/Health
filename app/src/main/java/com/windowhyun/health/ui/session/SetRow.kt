package com.windowhyun.health.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.windowhyun.health.core.designsystem.theme.healthColors
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.model.SetType
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.core.util.formatWeightValue
import com.windowhyun.health.domain.model.WorkoutSet

/**
 * 세트 한 줄. 중량/횟수 입력과 완료 버튼을 한 손으로 누를 수 있게 크게 배치한다.
 *
 * 입력값은 화면에서만 보관하다가 값이 바뀔 때마다 [onValuesChange] 로 저장한다.
 * DB 의 값으로 매번 되돌리지 않기 때문에 타이핑 중 커서가 튀지 않는다.
 *
 * 운동의 기록 방식에 따라 보이는 입력칸이 달라진다.
 * 플랭크처럼 시간으로 재는 운동은 중량·횟수 대신 시간만 받는다.
 */
@Composable
fun SetRow(
    set: WorkoutSet,
    weightUnit: WeightUnit,
    onValuesChange: (weightKg: Double, reps: Int, durationSeconds: Int) -> Unit,
    onToggleCompleted: (weightKg: Double, reps: Int, durationSeconds: Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_REPS,
    onCycleSetType: (() -> Unit)? = null,
    /** 완료 버튼과 완료 색칠을 보일지. 끝난 기록을 고치는 화면에서는 끈다. */
    showComplete: Boolean = true,
    /**
     * 지금 할 세트라면 입력칸 아래에 ± 버튼을 보인다. 무게와 횟수를 숫자판 없이 한 번씩 눌러 맞춘다.
     * 운동 중에 숫자판을 열고 닫는 것이 가장 번거로워서, 늘 하던 만큼만 더하고 빼면 되게 한다.
     */
    quickAdjust: Boolean = false,
) {
    // set.id 가 같은 동안에는 화면 입력값을 유지한다.
    var weightText by remember(set.id) {
        mutableStateOf(formatWeightValue(weightUnit.fromKg(set.weightKg)))
    }
    var repsText by remember(set.id) { mutableStateOf(if (set.reps > 0) set.reps.toString() else "") }
    // 시간은 분과 초를 따로 받는다. 숫자 키패드에는 ':' 이 없어서 "1:30" 을 칠 수 없다.
    var minutesText by remember(set.id) {
        mutableStateOf(if (set.durationSeconds >= 60) (set.durationSeconds / 60).toString() else "")
    }
    var secondsText by remember(set.id) {
        mutableStateOf(if (set.durationSeconds > 0) (set.durationSeconds % 60).toString() else "")
    }

    fun weightKg() = weightText.toDoubleOrNull()?.let { weightUnit.toKg(it) } ?: 0.0
    fun reps() = repsText.toIntOrNull() ?: 0
    fun duration() = durationSecondsOf(minutesText, secondsText)

    val isWarmup = set.setType == SetType.WARMUP
    // 끝낸 세트는 라임을 옅게 깐다. 한눈에 "어디까지 했는지"가 보이되 입력칸 글자는 그대로 읽힌다.
    val background = when {
        !showComplete -> Color.Transparent
        set.completed && isWarmup -> MaterialTheme.colorScheme.surfaceVariant
        set.completed -> MaterialTheme.healthColors.accent.copy(alpha = 0.28f)
        else -> Color.Transparent
    }
    // 완료 버튼을 누를 수 있는 조건은 기록 방식에 따라 다르다.
    val hasValue = when (trackingType) {
        ExerciseTrackingType.TIME -> duration() > 0
        else -> reps() > 0
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(background, MaterialTheme.shapes.small)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SetNumberChip(set = set, onCycleSetType = onCycleSetType)

            when (trackingType) {
                ExerciseTrackingType.WEIGHT_REPS -> {
                    NumberField(
                        value = weightText,
                        onValueChange = {
                            weightText = it.filter { ch -> ch.isDigit() || ch == '.' }
                            onValuesChange(weightKg(), reps(), duration())
                        },
                        suffix = weightUnit.label,
                        label = "${set.setNumber}세트 중량",
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = repsText,
                        onValueChange = {
                            repsText = it.filter { ch -> ch.isDigit() }
                            onValuesChange(weightKg(), reps(), duration())
                        },
                        suffix = "회",
                        label = "${set.setNumber}세트 횟수",
                        modifier = Modifier.weight(1f),
                    )
                }

                ExerciseTrackingType.REPS_ONLY -> NumberField(
                    value = repsText,
                    onValueChange = {
                        repsText = it.filter { ch -> ch.isDigit() }
                        onValuesChange(weightKg(), reps(), duration())
                    },
                    suffix = "회",
                    label = "${set.setNumber}세트 횟수",
                    modifier = Modifier.weight(2f),
                )

                ExerciseTrackingType.TIME -> {
                    NumberField(
                        value = minutesText,
                        onValueChange = {
                            minutesText = it.filter { ch -> ch.isDigit() }.take(3)
                            onValuesChange(weightKg(), reps(), duration())
                        },
                        suffix = "분",
                        label = "${set.setNumber}세트 시간(분)",
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = secondsText,
                        onValueChange = {
                            secondsText = it.filter { ch -> ch.isDigit() }.take(4)
                            onValuesChange(weightKg(), reps(), duration())
                        },
                        suffix = "초",
                        label = "${set.setNumber}세트 시간(초)",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (showComplete) {
                CompleteButton(
                    completed = set.completed,
                    enabled = set.completed || hasValue,
                    onClick = { onToggleCompleted(weightKg(), reps(), duration()) },
                )
            }

            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "세트 삭제",
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }

        if (quickAdjust && !set.completed) {
            QuickAdjustRow(
                trackingType = trackingType,
                weightUnit = weightUnit,
                onWeight = { delta ->
                    val next = (weightText.toDoubleOrNull() ?: 0.0) + delta
                    weightText = formatWeightValue(next.coerceAtLeast(0.0))
                    onValuesChange(weightKg(), reps(), duration())
                },
                onReps = { delta ->
                    repsText = ((reps() + delta).coerceAtLeast(0)).let { if (it > 0) it.toString() else "" }
                    onValuesChange(weightKg(), reps(), duration())
                },
                onSeconds = { delta ->
                    val total = (duration() + delta).coerceAtLeast(0)
                    minutesText = if (total >= 60) (total / 60).toString() else ""
                    secondsText = if (total > 0) (total % 60).toString() else ""
                    onValuesChange(weightKg(), reps(), duration())
                },
            )
        }
    }
}

/** 한 번 누를 때 바뀌는 무게(표시 단위 기준). 바벨 원판 한 쌍의 가장 작은 단위에 맞춘다. */
internal fun quickWeightStep(unit: WeightUnit): Double = when (unit) {
    WeightUnit.KG -> 2.5
    WeightUnit.LB -> 5.0
}

internal const val QUICK_SECONDS_STEP = 5

@Composable
private fun QuickAdjustRow(
    trackingType: ExerciseTrackingType,
    weightUnit: WeightUnit,
    onWeight: (Double) -> Unit,
    onReps: (Int) -> Unit,
    onSeconds: (Int) -> Unit,
) {
    val step = quickWeightStep(weightUnit)
    val stepText = formatWeightValue(step)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (trackingType) {
            ExerciseTrackingType.WEIGHT_REPS -> {
                QuickChip("−$stepText", "무게 $stepText${weightUnit.label} 줄이기") { onWeight(-step) }
                QuickChip("+$stepText", "무게 $stepText${weightUnit.label} 늘리기") { onWeight(step) }
                QuickChip("−1회", "횟수 1회 줄이기") { onReps(-1) }
                QuickChip("+1회", "횟수 1회 늘리기") { onReps(1) }
            }

            ExerciseTrackingType.REPS_ONLY -> {
                QuickChip("−1회", "횟수 1회 줄이기") { onReps(-1) }
                QuickChip("+1회", "횟수 1회 늘리기") { onReps(1) }
            }

            ExerciseTrackingType.TIME -> {
                QuickChip("−${QUICK_SECONDS_STEP}초", "시간 ${QUICK_SECONDS_STEP}초 줄이기") { onSeconds(-QUICK_SECONDS_STEP) }
                QuickChip("+${QUICK_SECONDS_STEP}초", "시간 ${QUICK_SECONDS_STEP}초 늘리기") { onSeconds(QUICK_SECONDS_STEP) }
            }
        }
    }
}

@Composable
private fun QuickChip(label: String, description: String, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * 세트 완료 버튼. 운동 중 가장 많이 누르는 버튼이라 크게(52dp) 두고,
 * 끝나면 라임 면에 검정 체크로 바뀐다(색 + 체크 표시 + 접근성 설명이 함께 바뀐다).
 */
@Composable
private fun CompleteButton(completed: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.healthColors
    val ink = MaterialTheme.colorScheme.onSurface
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = Modifier
            .size(52.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(shape)
            .background(if (completed) colors.accent else Color.Transparent)
            .then(if (completed) Modifier else Modifier.border(1.dp, ink, shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = if (completed) "세트 완료 취소" else "세트 완료",
            tint = if (completed) colors.onAccent else ink,
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * 세트 번호. 누르면 종류가 본세트 → 워밍업 → 드롭 → 실패 순으로 바뀐다.
 * 본세트는 숫자만, 그 밖의 종류는 상자로 감싸 구분한다(색에만 기대지 않는다).
 *
 * 종류를 고르는 별도 메뉴를 두지 않는 이유는, 운동 중 조작을 한 번으로 끝내기 위해서다.
 */
@Composable
private fun SetNumberChip(set: WorkoutSet, onCycleSetType: (() -> Unit)?) {
    val special = set.setType != SetType.NORMAL
    val label = set.setType.shortLabel.ifEmpty { "${set.setNumber}" }
    val ink = MaterialTheme.colorScheme.onSurface
    val tag = Modifier
        .size(30.dp)
        .then(if (special) Modifier.border(1.dp, ink, MaterialTheme.shapes.extraSmall) else Modifier)

    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 48.dp)
            .then(
                if (onCycleSetType != null) Modifier.clickable(role = Role.Button, onClick = onCycleSetType) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = tag, contentAlignment = Alignment.Center) {
            Text(text = label, style = MaterialTheme.typography.titleMedium, color = ink)
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        label = null,
        // 입력한 값이 이 화면의 핵심이라 크고 굵은 숫자로, 폭을 맞춰 자리가 흔들리지 않게 한다.
        textStyle = MaterialTheme.typography.displaySmall.copy(
            fontSize = 22.sp,
            lineHeight = 28.sp,
            textAlign = TextAlign.Center,
        ),
        suffix = { Text(suffix, style = MaterialTheme.typography.labelMedium) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/**
 * 분·초 입력을 초로 바꾼다. 초 칸에 90 처럼 60 이상을 넣어도 그대로 더한다
 * (1분 30초로 읽는 편이 되묻는 것보다 운동 중에 덜 번거롭다).
 */
internal fun durationSecondsOf(minutesText: String, secondsText: String): Int {
    val minutes = minutesText.toIntOrNull() ?: 0
    val seconds = secondsText.toIntOrNull() ?: 0
    return minutes * 60 + seconds
}
