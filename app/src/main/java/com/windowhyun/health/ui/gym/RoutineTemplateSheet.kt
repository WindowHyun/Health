package com.windowhyun.health.ui.gym

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.ui.components.ConfirmDialog
import com.windowhyun.health.ui.components.Hairline
import com.windowhyun.health.ui.components.HealthOutlinedButton

/**
 * 루틴 템플릿 선택 시트.
 *
 * 하나 고르면 그 프로그램의 루틴이 한 번에 만들어진다(예: PPL 을 고르면
 * 푸시/풀/레그 3개가 동시에 생긴다). 요일이 정해진 템플릿(일부 분할 프로그램)은 요일도 함께
 * 붙어 홈의 "오늘의 루틴"에 나오고, 정해지지 않은 템플릿은 만든 뒤 루틴 수정에서 요일을 붙이면 된다.
 *
 * 앱에 포함된 템플릿 말고도, 파일이나 붙여넣은 글로 템플릿을 가져올 수 있다. 밖에서 받은 글은 저장하기 전에
 * 먼저 검사해서 결과를 보여 주고, 사용자가 확인해야 저장한다(docs/DATA.md).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineTemplateSheet(
    onPick: (RoutineTemplates.Template) -> Unit,
    onDismiss: () -> Unit,
    library: TemplateLibraryViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by library.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var showPaste by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<RoutineTemplates.Template?>(null) }

    // 어떤 종류의 파일이든 고를 수 있게 한다(구글 드라이브 등이 JSON 을 다른 종류로 표시하기도 한다). 검사는 내용으로 한다.
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) library.importFromUri(uri)
    }
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) library.writeExport(uri) else library.cancelExport()
    }
    LaunchedEffect(library) { library.exportReady.collect { createFile.launch(it) } }
    // 시트를 닫으면 확인을 기다리던 결과와 안내를 치운다. 다시 열었을 때 옛 창이 뜨지 않게.
    DisposableEffect(library) {
        onDispose {
            library.dismissPreview()
            library.clearMessage()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        TemplateSheetBody(
            state = state,
            groups = RoutineTemplates.byCategory,
            onPick = onPick,
            onImportFile = { openFile.launch(arrayOf("*/*")) },
            onPaste = { showPaste = true },
            onExport = library::prepareExport,
            onDeleteUserTemplate = { toDelete = it },
        )
    }

    if (showPaste) {
        TemplatePasteDialog(
            onCheck = { text ->
                showPaste = false
                library.importFromText(text)
            },
            onDismiss = { showPaste = false },
        )
    }

    state.preview?.let { preview ->
        TemplateImportDialog(
            preview = preview,
            onConfirm = library::confirmImport,
            onDismiss = library::dismissPreview,
            onCopyProblems = { clipboard.setText(AnnotatedString(it)) },
        )
    }

    toDelete?.let { template ->
        ConfirmDialog(
            title = "템플릿 삭제",
            message = "'${template.title}' 템플릿을 목록에서 지울까요? 이미 만든 루틴은 그대로 남습니다.",
            confirmLabel = "삭제",
            onConfirm = {
                library.deleteUserTemplate(template.id)
                toDelete = null
            },
            onDismiss = { toDelete = null },
        )
    }
}

/** 시트의 내용. 상태만 받아 그리므로 시트 밖에서도 그려 볼 수 있다. */
@Composable
fun TemplateSheetBody(
    state: TemplateLibraryState,
    groups: List<Pair<RoutineTemplates.Category, List<RoutineTemplates.Template>>>,
    onPick: (RoutineTemplates.Template) -> Unit,
    onImportFile: () -> Unit,
    onPaste: () -> Unit,
    onExport: () -> Unit,
    onDeleteUserTemplate: (RoutineTemplates.Template) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "루틴 템플릿",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "이름난 프로그램과 장비 · 목적별 구성을 한 번에 넣습니다. 만든 뒤에는 자유롭게 고칠 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            HealthOutlinedButton(
                onClick = onImportFile,
                enabled = !state.working,
                compact = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("template-import-file"),
            ) { Text("파일에서 불러오기") }
            HealthOutlinedButton(
                onClick = onPaste,
                enabled = !state.working,
                compact = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("template-import-paste"),
            ) { Text("붙여넣기") }
        }
        TextButton(
            onClick = onExport,
            enabled = !state.working,
            modifier = Modifier.testTag("template-export"),
        ) { Text("내 루틴을 파일로 내보내기") }

        state.message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .testTag("template-message"),
            )
        }

        LazyColumn {
            if (state.userTemplates.isNotEmpty()) {
                item(key = "category-imported") { CategoryLabel("가져온 템플릿") }
                items(state.userTemplates, key = { "user-${it.id}" }) { template ->
                    TemplateCard(
                        template = template,
                        onClick = { onPick(template) },
                        onDelete = { onDeleteUserTemplate(template) },
                        modifier = Modifier.testTag("user-template-${template.id}"),
                    )
                }
            }
            groups.forEach { (category, templates) ->
                item(key = "category-${category.name}") { CategoryLabel(category.label) }
                items(templates, key = { it.id }) { template ->
                    TemplateCard(template = template, onClick = { onPick(template) })
                }
            }
        }
    }
}

@Composable
private fun CategoryLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun TemplateCard(
    template: RoutineTemplates.Template,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = template.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = template.days.joinToString(" · ") { it.routineName },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("user-template-delete-${template.id}"),
                ) { Icon(Icons.Filled.Delete, contentDescription = "${template.title} 삭제") }
            } else {
                Text("→", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
