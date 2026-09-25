package com.windowhyun.health.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
) {
    // set.id 가 같은 동안에는 화면 입력값을 유지한다.
    var weightText by remember(set.id) {
        mutableStateOf(formatWeightValue(weightUnit.fromKg(set.weightKg)))
    }
    var repsText by remember(set.id) { mutableStateOf(if (set.reps > 0) set.reps.toString() else "") }
    var durationText by remember(set.id) {
        mutableStateOf(if (set.durationSeconds > 0) set.durationSeconds.toString() else "")
    }

    fun weightKg() = weightText.toDoubleOrNull()?.let { weightUnit.toKg(it) } ?: 0.0
    fun reps() = repsText.toIntOrNull() ?: 0
    fun duration() = durationText.toIntOrNull() ?: 0

    val isWarmup = set.setType == SetType.WARMUP
    val background = when {
        set.completed && isWarmup -> MaterialTheme.colorScheme.secondaryContainer
        set.completed -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    // 완료 버튼을 누를 수 있는 조건은 기록 방식에 따라 다르다.
    val hasValue = when (trackingType) {
        ExerciseTrackingType.TIME -> duration() > 0
        else -> reps() > 0
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
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

            ExerciseTrackingType.TIME -> NumberField(
                value = durationText,
                onValueChange = {
                    durationText = it.filter { ch -> ch.isDigit() }
                    onValuesChange(weightKg(), reps(), duration())
                },
                suffix = "초",
                label = "${set.setNumber}세트 시간",
                modifier = Modifier.weight(2f),
            )
        }

        FilledIconButton(
            onClick = { onToggleCompleted(weightKg(), reps(), duration()) },
            enabled = set.completed || hasValue,
            modifier = Modifier.size(52.dp),
            colors = if (set.completed) {
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                )
            } else {
                IconButtonDefaults.filledIconButtonColors()
            },
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = if (set.completed) "세트 완료 취소" else "세트 완료",
                modifier = Modifier.size(28.dp),
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
}

/**
 * 세트 번호. 누르면 종류가 본세트 → 워밍업 → 드롭 → 실패 순으로 바뀐다.
 *
 * 종류를 고르는 별도 메뉴를 두지 않는 이유는, 운동 중 조작을 한 번으로 끝내기 위해서다.
 */
@Composable
private fun SetNumberChip(set: WorkoutSet, onCycleSetType: (() -> Unit)?) {
    val label = set.setType.shortLabel.ifEmpty { "${set.setNumber}" }
    val color = if (set.setType == SetType.NORMAL) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.primary
    }

    if (onCycleSetType == null) {
        Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        return
    }

    TextButton(
        onClick = onCycleSetType,
        modifier = Modifier.width(40.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
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
        textStyle = TextStyle(
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
        suffix = { Text(suffix, style = MaterialTheme.typography.labelMedium) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
