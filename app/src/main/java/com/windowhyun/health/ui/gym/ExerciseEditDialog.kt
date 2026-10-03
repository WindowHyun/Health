package com.windowhyun.health.ui.gym

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.ExerciseEditResult
import com.windowhyun.health.domain.model.ExerciseUsage
import com.windowhyun.health.ui.components.ConfirmDialog
import kotlinx.coroutines.launch

/**
 * 직접 만든 종목을 고치거나 지우는 창.
 *
 * 운동 기록에 들어 있는 종목은 지울 수 없고 기록 방식도 바꿀 수 없다. 지우면 그 기록이 함께
 * 지워지고, 중량 기록이 횟수 기록으로 바뀌면 숫자의 뜻이 달라지기 때문이다.
 */
@Composable
fun ExerciseEditDialog(
    exercise: Exercise,
    manager: ExerciseManager,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<ExerciseUsage?>(null) }
    LaunchedEffect(exercise.id) { usage = manager.usage(exercise.id) }

    var name by remember { mutableStateOf(exercise.name) }
    var category by remember { mutableStateOf(exercise.category) }
    var bodyPart by remember { mutableStateOf(exercise.bodyPart) }
    var trackingType by remember { mutableStateOf(exercise.trackingType) }
    var error by remember { mutableStateOf<String?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    val trackingLocked = usage?.hasHistory == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("종목 수정") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("부위", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BodyPart.entries) { part ->
                        FilterChip(
                            selected = bodyPart == part,
                            onClick = { bodyPart = part },
                            label = { Text(part.label) },
                        )
                    }
                }
                Text("종류", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ExerciseCategory.entries) { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text(option.label) },
                        )
                    }
                }
                Text("기록 방식", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ExerciseTrackingType.entries) { type ->
                        FilterChip(
                            selected = trackingType == type,
                            onClick = { trackingType = type },
                            enabled = !trackingLocked,
                            label = { Text(type.label) },
                        )
                    }
                }
                if (trackingLocked) {
                    Text(
                        text = "운동 기록이 있어 기록 방식은 바꿀 수 없습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { askDelete = true }, enabled = usage != null) {
                    Text("이 종목 지우기", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val result = manager.edit(exercise.id, name, category, bodyPart, trackingType)
                        if (result == ExerciseEditResult.Saved) onDismiss() else error = editErrorMessage(result)
                    }
                },
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )

    val current = usage
    if (askDelete && current != null) {
        if (current.hasHistory) {
            AlertDialog(
                onDismissRequest = { askDelete = false },
                title = { Text("지울 수 없는 종목") },
                text = { Text(deleteConfirmText(current)) },
                confirmButton = { TextButton(onClick = { askDelete = false }) { Text("확인") } },
            )
        } else {
            ConfirmDialog(
                title = "종목을 지울까요?",
                message = deleteConfirmText(current),
                confirmLabel = "지우기",
                onConfirm = {
                    askDelete = false
                    scope.launch {
                        manager.delete(exercise.id)
                        onDismiss()
                    }
                },
                onDismiss = { askDelete = false },
            )
        }
    }
}

/** 저장하지 못한 이유를 사람이 읽는 말로. 저장됐으면 null. */
internal fun editErrorMessage(result: ExerciseEditResult): String? = when (result) {
    ExerciseEditResult.Saved -> null
    ExerciseEditResult.NameBlank -> "이름을 입력해 주세요."
    ExerciseEditResult.NameTaken -> "같은 이름의 종목이 이미 있습니다."
    ExerciseEditResult.NotEditable -> "이 종목은 고칠 수 없습니다."
    ExerciseEditResult.TrackingTypeLocked -> "운동 기록이 있어 기록 방식은 바꿀 수 없습니다."
}

/** 지우기 전에 보여 줄 안내. 기록이 있으면 왜 못 지우는지, 없으면 루틴에서도 빠진다는 것을. */
internal fun deleteConfirmText(usage: ExerciseUsage): String = when {
    usage.hasHistory ->
        "운동 기록에 ${usage.workoutCount}번 들어 있어 지울 수 없습니다. 지우면 그 기록도 함께 사라지기 때문입니다. " +
            "이름이나 부위는 바꿀 수 있습니다."
    usage.routineCount > 0 -> "루틴 ${usage.routineCount}개에서도 이 종목이 빠집니다. 되돌릴 수 없습니다."
    else -> "이 종목을 지웁니다. 되돌릴 수 없습니다."
}
