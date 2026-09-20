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
import androidx.compose.foundation.text.KeyboardOptions
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.core.util.formatWeightValue
import com.windowhyun.health.domain.model.WorkoutSet

/**
 * 세트 한 줄. 중량/횟수 입력과 완료 버튼을 한 손으로 누를 수 있게 크게 배치한다.
 *
 * 입력값은 화면에서만 보관하다가 값이 바뀔 때마다 [onValuesChange] 로 저장한다.
 * DB 의 값으로 매번 되돌리지 않기 때문에 타이핑 중 커서가 튀지 않는다.
 */
@Composable
fun SetRow(
    set: WorkoutSet,
    weightUnit: WeightUnit,
    onValuesChange: (weightKg: Double, reps: Int) -> Unit,
    onToggleCompleted: (weightKg: Double, reps: Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // set.id 가 같은 동안에는 화면 입력값을 유지한다.
    var weightText by remember(set.id) {
        mutableStateOf(formatWeightValue(weightUnit.fromKg(set.weightKg)))
    }
    var repsText by remember(set.id) { mutableStateOf(if (set.reps > 0) set.reps.toString() else "") }

    val weightKg = weightText.toDoubleOrNull()?.let { weightUnit.toKg(it) } ?: 0.0
    val reps = repsText.toIntOrNull() ?: 0

    val background = if (set.completed) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "${set.setNumber}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        NumberField(
            value = weightText,
            onValueChange = {
                weightText = it.filter { ch -> ch.isDigit() || ch == '.' }
                onValuesChange(
                    weightText.toDoubleOrNull()?.let { v -> weightUnit.toKg(v) } ?: 0.0,
                    repsText.toIntOrNull() ?: 0,
                )
            },
            suffix = weightUnit.label,
            modifier = Modifier.weight(1f),
        )

        NumberField(
            value = repsText,
            onValueChange = {
                repsText = it.filter { ch -> ch.isDigit() }
                onValuesChange(
                    weightText.toDoubleOrNull()?.let { v -> weightUnit.toKg(v) } ?: 0.0,
                    repsText.toIntOrNull() ?: 0,
                )
            },
            suffix = "회",
            modifier = Modifier.weight(1f),
        )

        FilledIconButton(
            onClick = { onToggleCompleted(weightKg, reps) },
            enabled = set.completed || reps > 0,
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

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
        suffix = { Text(suffix, style = MaterialTheme.typography.labelMedium) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
