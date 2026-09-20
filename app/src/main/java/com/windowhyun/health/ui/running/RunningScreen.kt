package com.windowhyun.health.ui.running

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.windowhyun.health.ui.components.EmptyMessage

/**
 * 러닝 탭 (Phase 2 에서 구현 예정).
 *
 * Phase 2 에서 여기에 GPS 상태 확인 / 러닝 모드 선택 / 시작 버튼이 들어가고,
 * Foreground Service 가 위치 추적을 담당한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text("러닝") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyMessage(
                icon = Icons.AutoMirrored.Filled.DirectionsRun,
                title = "러닝 기능은 Phase 2 에서 추가됩니다",
                description = "GPS 권한 · Foreground Service · 자동 Lap · 페이스 계산이 순서대로 붙습니다.\n" +
                    "데이터베이스 스키마(run / run_lap / run_location)는 이미 준비되어 있습니다.",
            )
        }
    }
}
