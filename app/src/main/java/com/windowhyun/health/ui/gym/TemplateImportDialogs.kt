package com.windowhyun.health.ui.gym

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.windowhyun.health.data.catalog.CatalogIssue
import com.windowhyun.health.data.catalog.ImportLimits
import com.windowhyun.health.data.catalog.ImportPreview

/** 검사 결과 요약. 무엇이 들어오고 무엇이 바뀌는지 한눈에 보이게 한다. */
internal fun importSummaryLines(preview: ImportPreview): List<String> = buildList {
    if (preview.templates.isNotEmpty()) {
        add("템플릿 ${preview.templates.size}개 · 루틴 ${preview.routineCount}개: ${preview.templates.joinToString(", ") { it.title }}")
    }
    if (preview.newExercises.isNotEmpty()) {
        val names = preview.newExercises.take(MAX_NAMES_SHOWN).joinToString(", ") { it.name }
        val more = preview.newExercises.size - MAX_NAMES_SHOWN
        add("새 종목 ${preview.newExercises.size}개: $names" + if (more > 0) " 외 ${more}개" else "")
    }
    if (preview.replacedTitles.isNotEmpty()) {
        add("새 내용으로 바뀌는 템플릿: ${preview.replacedTitles.joinToString(", ")}")
    }
    if (preview.errors.isNotEmpty()) {
        add("오류 ${preview.errors.size}개 — 파일을 고친 뒤 다시 불러오세요. 아무것도 저장되지 않았습니다.")
    }
}

/** 문제 목록을 글로 바꾼다(복사해서 고칠 곳을 물어볼 수 있게). */
internal fun issuesAsText(issues: List<CatalogIssue>): String =
    issues.joinToString("\n") { "[${if (it.isError) "오류" else "알림"}] $it" }

private const val MAX_NAMES_SHOWN = 5

/** 붙여넣을 수 있는 글자 수. 한도(바이트)보다 한 글자 많게 두어, 넘으면 검사가 늘 "너무 큽니다" 로 알려 준다. */
internal val MAX_PASTE_CHARS: Int = ImportLimits.EXTERNAL.maxBytes + 1

/**
 * 너무 큰 글이 입력 칸을 멈추지 않게 자른다. 글자는 적어도 1바이트이므로(짝 없는 서로게이트도 `?` 한 바이트로 바뀐다),
 * 이 길이까지 남은 글이 한도를 넘었다면 바이트로도 넘는다 — 잘렸다는 사실이 검사에서 감춰지지 않는다.
 */
internal fun clampPasted(text: String): String = text.take(MAX_PASTE_CHARS)

/**
 * 밖에서 받은 템플릿을 검사한 결과. 오류가 하나라도 있으면 가져올 수 없고, 알림은 알려 주기만 한다.
 * 사용자가 확인해야 비로소 저장한다.
 */
@Composable
fun TemplateImportDialog(
    preview: ImportPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onCopyProblems: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (preview.canImport) "가져올까요?" else "가져올 수 없습니다",
                modifier = Modifier.testTag("import-title"),
            )
        },
        text = {
            Column {
                importSummaryLines(preview).forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .testTag("import-summary"),
                    )
                }
                if (preview.issues.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .testTag("import-issues"),
                    ) {
                        items(preview.issues) { issue -> IssueRow(issue) }
                    }
                    TextButton(
                        onClick = { onCopyProblems(issuesAsText(preview.issues)) },
                        modifier = Modifier.testTag("import-copy"),
                    ) { Text("문제 목록 복사") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = preview.canImport,
                modifier = Modifier.testTag("import-confirm"),
            ) { Text("가져오기") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("import-dismiss")) { Text("닫기") }
        },
    )
}

@Composable
private fun IssueRow(issue: CatalogIssue) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(
            text = if (issue.isError) "오류" else "알림",
            style = MaterialTheme.typography.labelMedium,
            color = if (issue.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
        )
        if (issue.path.isNotEmpty()) {
            Text(
                text = issue.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = issue.message, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 템플릿 글을 붙여넣는 창. 검사 버튼을 눌러야 검사하고, 그 결과를 보고 나서야 저장한다. */
@Composable
fun TemplatePasteDialog(
    onCheck: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("붙여넣기") },
        text = {
            Column {
                Text(
                    text = "템플릿 JSON 글을 붙여넣으세요. 저장하기 전에 먼저 검사해서 문제가 있으면 알려 드립니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = clampPasted(it) },
                    minLines = 6,
                    maxLines = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paste-field"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCheck(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("paste-check"),
            ) { Text("검사") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("paste-cancel")) { Text("취소") }
        },
    )
}
