package com.windowhyun.health.ui.running

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.ui.components.ConfirmDialog
import com.windowhyun.health.ui.components.HealthButton

/** 러닝 코스 만들기: 지도를 눌러 가고 싶은 길을 차례로 찍는다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditorScreen(
    onClose: () -> Unit,
    viewModel: CourseEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    val leave = { if (state.points.isNotEmpty()) confirmLeave = true else onClose() }
    BackHandler(onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.courseId == 0L) "코스 만들기" else "코스 고치기") },
                navigationIcon = {
                    IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            CourseEditMap(
                points = state.points,
                center = state.center,
                fit = state.fit,
                onAddPoint = viewModel::addPoint,
                onRemovePoint = viewModel::removePoint,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            // 지도와 겹치지 않게 패널에 배경을 깐다(투명하면 지도가 비쳐 글자가 어지럽다).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (state.points.isEmpty()) "지도를 눌러 출발 지점부터 차례로 찍어 주세요."
                    else "${CourseEditorViewModel.formatKm(state.distanceMeters)} · 지점 ${state.points.size}개",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "찍은 점들을 직선으로 이어요. 길이 꺾이는 곳마다 점을 찍으면 더 정확해요. 찍어 둔 점을 다시 누르면 그 점이 빠져요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("코스 이름 (비우면 거리로 지어요)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::undo, enabled = state.points.isNotEmpty()) { Text("되돌리기") }
                    TextButton(onClick = viewModel::clear, enabled = state.points.isNotEmpty()) { Text("모두 지우기") }
                }
                HealthButton(
                    onClick = { viewModel.save(onClose) },
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.points.size < 2) "지점을 2개 이상 찍어 주세요" else if (state.courseId == 0L) "코스 저장" else "고친 코스 저장") }
            }
        }
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "코스를 저장하지 않고 나갈까요?",
            message = "찍어 둔 지점이 사라져요.",
            confirmLabel = "나가기",
            dismissLabel = "계속 찍기",
            onConfirm = { confirmLeave = false; onClose() },
            onDismiss = { confirmLeave = false },
        )
    }
}
