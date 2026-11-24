package com.windowhyun.health.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 키보드가 올라오면 화면(Scaffold)을 그만큼 줄여, 입력칸이 키보드에 가리지 않게 한다.
 *
 * 이 앱은 화면 끝까지 그리는(edge-to-edge) 방식이라 `adjustResize` 가 창 크기를 줄여 주지 않는다.
 *
 * **목록이 아니라 Scaffold 에 붙인다.** 목록에만 붙이면 Scaffold 의 아래 막대(휴식 타이머 등)는 키보드 밑에 가려진 채로
 * 목록이 그 높이만큼 자리를 비워 두어, 키보드 위에 큰 빈 띠가 생긴다. Scaffold 째로 줄이면 아래 막대가 키보드 바로 위에
 * 붙고 그 위가 목록이 된다.
 *
 * 바깥 틀([com.windowhyun.health.ui.AppFrame])이 시스템 막대 여백을 이미 비웠다고 표시해 두었으므로, 여기서는
 * 키보드 높이에서 그만큼이 자동으로 빠진다. 키보드가 없으면 아무것도 더하지 않는다.
 */
@Composable
fun Modifier.liftAboveKeyboard(): Modifier = windowInsetsPadding(WindowInsets.ime)
