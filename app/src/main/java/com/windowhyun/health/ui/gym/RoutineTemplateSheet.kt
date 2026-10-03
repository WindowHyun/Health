package com.windowhyun.health.ui.gym

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import com.windowhyun.health.ui.components.Hairline
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 이름난 루틴 템플릿 선택 시트.
 *
 * 하나 고르면 그 프로그램의 루틴이 한 번에 만들어진다(예: PPL 을 고르면
 * 푸시/풀/레그 3개가 동시에 생긴다). 요일 지정은 하지 않으므로 만든 뒤
 * 루틴 수정에서 원하는 요일을 붙이면 된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineTemplateSheet(
    onPick: (RoutineTemplates.Template) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "루틴 템플릿",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "많이 알려진 프로그램을 그대로 넣습니다. 만든 뒤에는 자유롭게 고칠 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            LazyColumn {
                items(RoutineTemplates.all, key = { it.id }) { template ->
                    TemplateCard(template = template, onClick = { onPick(template) })
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(template: RoutineTemplates.Template, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
            Text("→", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
