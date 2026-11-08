package com.windowhyun.health.ui.running

import androidx.compose.runtime.compositionLocalOf
import com.windowhyun.health.domain.model.MapStyleMode

/**
 * 지금 쓰는 지도 방식. 앱 맨 바깥(HealthRoot)에서 설정 값을 넣어 준다.
 * 기본이 [MapStyleMode.CLASSIC] 이라, 값을 넣지 않는 곳(테스트 등)은 예전 지도를 그대로 쓴다.
 */
val LocalMapStyleMode = compositionLocalOf { MapStyleMode.CLASSIC }
