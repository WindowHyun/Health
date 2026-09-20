package com.windowhyun.health.ui.running

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.core.util.formatDistance
import com.windowhyun.health.core.util.formatDuration
import com.windowhyun.health.core.util.formatKoreanFull
import com.windowhyun.health.core.util.formatPace
import com.windowhyun.health.ui.components.ConfirmDialog
import com.windowhyun.health.ui.components.StatCard

/** 러닝 결과. 거리·시간·페이스·Lap·경로·개인기록을 한 화면에 모아 보여 준다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunSummaryScreen(
    onClose: () -> Unit,
    viewModel: RunSummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }
    val run = state.run

    LaunchedEffect(state.closed) {
        if (state.closed) onClose()
    }

    BackHandler { viewModel.save() }

    Scaffold(topBar = { TopAppBar(title = { Text("러닝 완료") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = run?.date?.formatKoreanFull().orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatDistance(run?.distanceMeters ?: 0.0, state.settings.distanceUnit),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (state.personalBests.isLongestDistance || state.personalBests.isFastestAveragePace) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.EmojiEvents, contentDescription = null)
                                Text(
                                    text = "개인 기록 갱신",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            if (state.personalBests.isLongestDistance) {
                                Text(
                                    text = "최장 거리 (이전 " +
                                        formatDistance(
                                            state.personalBests.previousLongestMeters,
                                            state.settings.distanceUnit,
                                        ) + ")",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (state.personalBests.isFastestAveragePace) {
                                Text(
                                    text = "최고 평균 페이스" + (
                                        state.personalBests.previousBestPaceSecPerKm
                                            ?.let { " (이전 ${formatPace(it)})" } ?: ""
                                        ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        label = "총 시간",
                        value = formatDuration(run?.durationSeconds ?: 0),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "평균 페이스",
                        value = formatPace(run?.averagePaceSecPerKm ?: 0.0),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        label = "최고 페이스",
                        value = formatPace(run?.bestPaceSecPerKm ?: 0.0),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "칼로리",
                        value = "${run?.calories ?: 0}kcal",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Text("이동 경로", style = MaterialTheme.typography.titleMedium)
            }
            item {
                RouteMap(route = run?.route.orEmpty())
            }

            if (!run?.laps.isNullOrEmpty()) {
                item {
                    Text("Lap", style = MaterialTheme.typography.titleMedium)
                }
                items(run.laps, key = { it.lapNumber }) { lap ->
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${lap.lapNumber} " +
                                    formatDistance(lap.distanceMeters, state.settings.distanceUnit),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "${formatPace(lap.paceSecPerKm)}  ${formatDuration(lap.durationSeconds)}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.memo,
                    onValueChange = viewModel::setMemo,
                    label = { Text("러닝 메모") },
                    placeholder = { Text("예) 후반 페이스가 잘 유지됨. 다음엔 6km 도전.") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
            }

            item {
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) { Text("저장", style = MaterialTheme.typography.titleMedium) }
            }

            item {
                TextButton(
                    onClick = { showDiscardDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("이 기록 삭제", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDiscardDialog) {
        ConfirmDialog(
            title = "기록을 삭제할까요?",
            message = "이번 러닝 기록이 모두 삭제됩니다. 되돌릴 수 없습니다.",
            confirmLabel = "삭제",
            onConfirm = {
                showDiscardDialog = false
                viewModel.discard()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }
}
