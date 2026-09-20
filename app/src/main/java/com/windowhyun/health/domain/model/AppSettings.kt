package com.windowhyun.health.domain.model

import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.model.WeightUnit

/** 앱 테마 모드. */
enum class ThemeMode(val label: String) {
    SYSTEM("시스템 설정"),
    LIGHT("라이트"),
    DARK("다크"),
}

/** DataStore 에 저장되는 사용자 설정. */
data class AppSettings(
    val defaultRestSeconds: Int = 60,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val vibrationEnabled: Boolean = true,
    val restTimerAutoStart: Boolean = true,
    val autoLapMeters: Int = 1000,
    /** 러닝 칼로리 추정에 쓰는 체중(kg). */
    val bodyWeightKg: Double = 70.0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val healthConnectEnabled: Boolean = false,
    val keepScreenOnDuringWorkout: Boolean = true,
)
