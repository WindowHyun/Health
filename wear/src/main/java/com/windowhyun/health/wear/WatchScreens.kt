package com.windowhyun.health.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.windowhyun.health.shared.WatchCommand
import com.windowhyun.health.shared.WatchRunStatus

// 폰 앱과 같은 색. 어두운 바탕에 라임 하나.
private val Lime = Color(0xFFC6F432)
private val OnLime = Color(0xFF0A0A0A)
private val Muted = Color(0xFF9B9B9B)
private val Danger = Color(0xFFFF6B5E)

/**
 * 시계 앱 전체 화면. 상태를 받아 휴식 · 러닝 · 대기 중 하나를 그린다.
 *
 * 192dp 짜리 둥근 화면에서도 스크롤 없이 필요한 숫자와 버튼이 한눈에 들어오게 짰다.
 * 달리는 중에 손목을 들어 스크롤하는 것은 폰을 만지는 것만큼 번거롭다.
 */
@Composable
fun WatchApp(
    state: WatchUiState,
    onCommand: (WatchCommand) -> Unit,
    onRequestStop: () -> Unit,
    onConfirmStop: () -> Unit,
    onCancelStop: () -> Unit,
    onCompleteSet: () -> Unit = {},
    onStartRun: () -> Unit = {},
) {
    MaterialTheme {
        when (state.screen) {
            WatchScreen.REST -> RestScreen(state, onCommand)
            WatchScreen.RUN -> RunScreen(state, onCommand, onRequestStop, onConfirmStop, onCancelStop)
            WatchScreen.WORKOUT -> WorkoutScreen(state, onCompleteSet)
            WatchScreen.IDLE -> IdleScreen(state, onStartRun)
        }
    }
}

/** 화면 위쪽에 한 줄 적는 상태 글. 문제가 있으면 그 이유가 평소 글을 대신한다. */
@Composable
private fun StatusLine(text: String, warning: Boolean) {
    Text(
        text = text,
        color = if (warning) Danger else Muted,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun IdleScreen(state: WatchUiState, onStartRun: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Health", color = Lime, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(
            text = when {
                state.sendFailed -> "폰에 보내지 못했어요"
                state.startPending -> "폰에 요청했어요. 폰 알림에서 '러닝 시작'을 눌러야 할 수 있어요."
                state.run.status == WatchRunStatus.FINISHED -> "러닝이 끝났어요. 결과는 폰에서 확인하세요."
                else -> "폰에서 운동을 시작하면 여기에 나타나요."
            },
            textAlign = TextAlign.Center,
            color = if (state.sendFailed) Danger else Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Chip(
            onClick = onStartRun,
            label = {
                Text(
                    text = "러닝 시작",
                    fontWeight = FontWeight.Bold,
                    color = OnLime,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            colors = ChipDefaults.primaryChipColors(backgroundColor = Lime),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 헬스 중 지금 할 세트. 이름과 값을 크게 보여 주고, 끝내면 "완료" 한 번이다.
 * 값이 비어 있으면(횟수 0) 폰에서도 완료할 수 없어서 버튼 대신 안내만 둔다.
 */
@Composable
private fun WorkoutScreen(state: WatchUiState, onCompleteSet: () -> Unit) {
    val workout = state.workout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (workout.allDone) {
            Text(text = "모든 세트 완료", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Lime)
            Text(
                text = "운동 종료는 폰에서 해 주세요.",
                color = Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            return@Column
        }
        StatusLine(
            text = if (state.sendFailed) "폰에 보내지 못했어요" else "${workout.exerciseName} · ${workout.setNumber}/${workout.setCount}세트",
            warning = state.sendFailed,
        )
        Text(
            text = WatchFormat.setValue(workout),
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        if (workout.canComplete) {
            Chip(
                onClick = onCompleteSet,
                label = {
                    Text(
                        text = "완료",
                        fontWeight = FontWeight.Bold,
                        color = OnLime,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                colors = ChipDefaults.primaryChipColors(backgroundColor = Lime),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = "폰에서 값을 입력해 주세요.",
                color = Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RestScreen(state: WatchUiState, onCommand: (WatchCommand) -> Unit) {
    val rest = state.rest
    val finished = rest.isFinished(state.nowMillis)

    Box(modifier = Modifier.fillMaxSize()) {
        // 화면 가장자리를 도는 남은 시간 고리.
        CircularProgressIndicator(
            progress = rest.fraction(state.nowMillis),
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            indicatorColor = if (finished) Danger else Lime,
            strokeWidth = 4.dp,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusLine(
                text = when {
                    state.sendFailed -> "폰에 보내지 못했어요"
                    finished -> "휴식 끝"
                    rest.paused -> "휴식 · 멈춤"
                    else -> "휴식"
                },
                warning = state.sendFailed || finished,
            )
            Text(
                text = WatchFormat.restClock(state.restRemainingSeconds),
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("-15") { onCommand(WatchCommand.REST_SUB) }
                Action("+15") { onCommand(WatchCommand.REST_ADD) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!finished) {
                    Action(if (rest.paused) "계속" else "멈춤") { onCommand(WatchCommand.REST_TOGGLE_PAUSE) }
                }
                Action("건너뛰기", primary = true) { onCommand(WatchCommand.REST_SKIP) }
            }
        }
    }
}

@Composable
private fun RunScreen(
    state: WatchUiState,
    onCommand: (WatchCommand) -> Unit,
    onRequestStop: () -> Unit,
    onConfirmStop: () -> Unit,
    onCancelStop: () -> Unit,
) {
    val run = state.run
    val paused = run.status == WatchRunStatus.PAUSED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusLine(
            text = when {
                state.sendFailed -> "폰에 보내지 못했어요"
                state.runStale -> "폰과 연결이 끊겼을 수 있어요"
                state.confirmingStop -> "러닝을 끝낼까요?"
                run.autoPaused -> "멈춰서 자동 일시정지"
                paused -> "러닝 · 일시정지"
                run.signalLost -> "러닝 · 위치 신호 없음"
                else -> "러닝"
            },
            warning = state.sendFailed || state.runStale || paused || run.signalLost,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = WatchFormat.distanceNumber(run.distanceMeters, run.useMiles),
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = WatchFormat.distanceUnit(run.useMiles),
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 3.dp, bottom = 6.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Metric("시간", WatchFormat.duration(state.runElapsedSeconds))
            Metric("페이스", WatchFormat.pace(run.currentPaceSecPerKm, run.useMiles))
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.confirmingStop) {
                // 종료는 실수로 누르기 쉬우니 한 번 더 묻는다.
                Action("취소") { onCancelStop() }
                Action("종료", danger = true) { onConfirmStop() }
            } else {
                if (paused) {
                    Action("계속", primary = true) { onCommand(WatchCommand.RUN_RESUME) }
                } else {
                    Action("일시정지", primary = true) { onCommand(WatchCommand.RUN_PAUSE) }
                }
                Action("종료", danger = true) { onRequestStop() }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Muted, fontSize = 10.sp)
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** 한 줄에 둘씩 놓는 작은 버튼. 가장 중요한 하나는 라임, 위험한 동작(종료)은 붉은 글자. */
@Composable
private fun Action(
    label: String,
    primary: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    CompactChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = when {
                    primary -> OnLime
                    danger -> Danger
                    else -> Color.White
                },
            )
        },
        colors = if (primary) {
            ChipDefaults.primaryChipColors(backgroundColor = Lime)
        } else {
            ChipDefaults.secondaryChipColors()
        },
    )
}
