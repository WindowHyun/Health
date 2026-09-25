package com.windowhyun.health.ui.running

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.core.util.formatDistance
import com.windowhyun.health.core.util.formatKoreanFull
import com.windowhyun.health.core.util.formatTimeOfDay
import com.windowhyun.health.ui.components.ConfirmDialog

/**
 * 저장된 러닝 상세.
 *
 * 러닝 완료 화면과 같은 지표를 보여 주되, 메모 수정과 삭제만 가능하다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailScreen(
    onBack: () -> Unit,
    viewModel: RunDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val run = state.run

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    // 메모를 쓰다가 뒤로 가도 날아가지 않게 한다.
    val saveOnLeave by rememberUpdatedState { viewModel.saveMemoIfNeeded() }
    DisposableEffect(Unit) {
        onDispose { saveOnLeave() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("러닝 기록") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "삭제",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (run == null) {
            // 불러오는 중이거나 이미 지워진 기록이다. 잠시 뒤 화면이 닫힌다.
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = run.date.formatKoreanFull() + " " + run.startTime.formatTimeOfDay(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatDistance(run.distanceMeters, state.settings.distanceUnit),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            item {
                RunStatGrid(run = run, distanceUnit = state.settings.distanceUnit)
            }

            item {
                Text("이동 경로", style = MaterialTheme.typography.titleMedium)
            }
            item {
                RunRouteSection(run = run)
            }

            if (run.laps.isNotEmpty()) {
                item {
                    Text("구간 기록", style = MaterialTheme.typography.titleMedium)
                }
                runLapItems(laps = run.laps, distanceUnit = state.settings.distanceUnit)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("메모", style = MaterialTheme.typography.titleMedium)
                    if (!state.editingMemo) {
                        TextButton(onClick = viewModel::startEditingMemo) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            Text(if (run.memo.isNullOrBlank()) "메모 추가" else "수정")
                        }
                    }
                }
            }

            item {
                if (state.editingMemo) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.memo,
                            onValueChange = viewModel::setMemo,
                            placeholder = { Text("예) 후반 페이스가 잘 유지됨.") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                        )
                        TextButton(
                            onClick = viewModel::saveMemo,
                            modifier = Modifier.align(Alignment.End),
                        ) { Text("저장") }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Text(
                            text = run.memo?.takeIf { it.isNotBlank() } ?: "메모가 없습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (run.memo.isNullOrBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "기록을 삭제할까요?",
            message = "이 러닝 기록이 모두 삭제됩니다. 되돌릴 수 없습니다.",
            confirmLabel = "삭제",
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}
