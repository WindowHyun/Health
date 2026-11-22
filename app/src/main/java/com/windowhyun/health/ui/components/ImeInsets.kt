package com.windowhyun.health.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 키보드가 올라오면 그만큼 아래를 비운다.
 *
 * 이 앱은 화면 끝까지 그리는(edge-to-edge) 방식이라 `adjustResize` 가 창 크기를 줄여 주지 않는다.
 * 그대로 두면 키보드가 아래쪽 입력칸(세트 · 메모)을 가린다.
 *
 * 하단 시스템 막대 높이는 바깥 Scaffold 가 이미 비워 두므로, 키보드 높이에서 그만큼을 뺀다.
 * 키보드가 없으면 아무것도 더하지 않는다. 하단 탭이 없는 전체 화면의 스크롤 영역에 쓴다.
 */
@Composable
fun Modifier.imeAboveNavigationBar(): Modifier =
    windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
