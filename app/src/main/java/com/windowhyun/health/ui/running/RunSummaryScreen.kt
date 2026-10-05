package com.windowhyun.health.ui.running

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import com.windowhyun.health.ui.components.SectionLabel
import com.windowhyun.health.ui.components.HealthButton
import com.windowhyun.health.core.designsystem.theme.healthColors
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.core.util.formatDistance
import com.windowhyun.health.core.util.formatPace
import com.windowhyun.health.ui.components.ConfirmDialog
import com.windowhyun.health.ui.share.RunCardData
import com.windowhyun.health.ui.share.RunShareCard
import com.windowhyun.health.ui.share.ShareCardSection

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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // 러닝이 끝나면 한 장으로 정리한 카드를 바로 보여 주고, 저장 · 공유할 수 있게 한다.
            if (run != null) {
                item {
                    val isBest = state.personalBests.isLongestDistance || state.personalBests.isFastestAveragePace
                    val data = remember(run, state.settings.distanceUnit, isBest) {
                        RunCardData.from(run, state.settings.distanceUnit, isBest)
                    }
                    ShareCardSection(
                        fileName = "health-run-${run.date}",
                        description = "러닝 ${data.distanceNumber}${data.distanceUnit}, ${data.durationText}, 평균 페이스 ${data.paceText}",
                    ) { RunShareCard(data) }
                }
            }

            if (state.personalBests.isLongestDistance || state.personalBests.isFastestAveragePace) {
                item {
                    // 기록 갱신은 라임 면으로. 이 화면에서 가장 눈에 띄어야 하는 소식이다.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.healthColors.accent)
                            .padding(16.dp),
                    ) {
                        val onAccent = MaterialTheme.healthColors.onAccent
                        Text(
                            text = "개인 기록 갱신",
                            style = MaterialTheme.typography.titleMedium,
                            color = onAccent,
                        )
                        if (state.personalBests.isLongestDistance) {
                            Text(
                                text = "최장 거리 (이전 " +
                                    formatDistance(
                                        state.personalBests.previousLongestMeters,
                                        state.settings.distanceUnit,
                                    ) + ")",
                                style = MaterialTheme.typography.bodyMedium,
                                color = onAccent,
                            )
                        }
                        if (state.personalBests.isFastestAveragePace) {
                            Text(
                                text = "최고 평균 페이스" + (
                                    state.personalBests.previousBestPaceSecPerKm
                                        ?.let { " (이전 ${formatPace(it, state.settings.distanceUnit)})" } ?: ""
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = onAccent,
                            )
                        }
                    }
                }
            }

            item {
                RunStatGrid(
                    run = run ?: return@item,
                    distanceUnit = state.settings.distanceUnit,
                )
            }

            item {
                SectionLabel("이동 경로")
            }
            item {
                RunRouteSection(run = run ?: return@item)
            }

            if (!run?.laps.isNullOrEmpty()) {
                item {
                    SectionLabel("구간 기록")
                }
                runLapItems(laps = run.laps, distanceUnit = state.settings.distanceUnit)
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
                HealthButton(
                    onClick = viewModel::save,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) { Text("저장") }
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
