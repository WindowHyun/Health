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
    val keepScreenOnDuringWorkout: Boolean = true,
    /** 러닝 중 1km 구간 · 목표 달성 · 자동 일시정지를 진동으로 알린다. */
    val runVibrationCues: Boolean = true,
    /** 구간(Lap) · 목표 · 인터벌 전환을 음성으로 읽어 준다. */
    val runVoiceCues: Boolean = true,
    /** 주간 헬스 목표(회). 0 이면 목표 없음. */
    val weeklyWorkoutGoal: Int = 0,
    /** 주간 러닝 목표 거리(m). 0 이면 목표 없음. */
    val weeklyRunGoalMeters: Int = 0,
    /** 멈추면 러닝을 자동으로 일시정지하고, 다시 움직이면 이어 간다. */
    val autoPauseRun: Boolean = true,
    // 자동 백업. 기기마다 다른 값이라 백업 파일에는 넣지 않는다.
    /** 자동 백업을 저장할 폴더(SAF 트리 URI). null 이면 자동 백업이 꺼져 있다. */
    val autoBackupFolderUri: String? = null,
    /** 자동 백업 간격(일). */
    val autoBackupEveryDays: Int = 7,
    /** 마지막으로 자동 백업에 성공한 시각(epoch ms). 0 이면 아직 없다. */
    val lastAutoBackupAt: Long = 0,
    /** 마지막 시도가 실패했다면 그 이유. 성공하면 지운다. */
    val lastAutoBackupError: String? = null,
) {
    val autoBackupEnabled: Boolean get() = autoBackupFolderUri != null
}
