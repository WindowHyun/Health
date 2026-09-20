package com.windowhyun.health.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** 삭제/종료처럼 되돌리기 어려운 동작에 쓰는 확인 다이얼로그. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "확인",
    dismissLabel: String = "취소",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}
