package com.windowhyun.health.domain.model

import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.model.WeightUnit

/** 앱 테마 모드. */
enum class ThemeMode(val label: String) {
    SYSTEM("시스템 설정"),
    LIGHT("라이트"),
    DARK("다크"),
}

/** PiP(작은 창으로 띄운 유튜브 등)가 뜨는 쪽. 앱 화면의 이 끝에 빈 자리를 만든다. */
enum class PipSpacePosition(val label: String) {
    TOP("위"),
    BOTTOM("아래"),
}

/**
 * 다른 앱의 PiP 창이 가리는 만큼 앱 화면 끝을 비워 두는 설정.
 * PiP 창의 위치와 크기는 앱이 알 수 없어서 사용자가 직접 맞춘다.
 */
data class PipSpace(
    val enabled: Boolean = false,
    val position: PipSpacePosition = PipSpacePosition.BOTTOM,
    val heightDp: Int = DEFAULT_HEIGHT_DP,
) {
    companion object {
        /** 유튜브 PiP 기본 크기(가로 약 200dp, 16:9)에 여유를 둔 높이. */
        const val DEFAULT_HEIGHT_DP = 130
        const val MIN_HEIGHT_DP = 60
        const val MAX_HEIGHT_DP = 320
        const val STEP_DP = 10

        /** 자주 쓰는 크기. 유튜브 PiP 를 작게 · 기본 · 크게 늘렸을 때에 맞춘다. */
        val PRESETS: List<Pair<String, Int>> = listOf("작게" to 100, "보통" to 130, "크게" to 180)

        fun clampHeight(heightDp: Int): Int = heightDp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
    }
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
    // PiP 자리. 화면 크기가 기기마다 달라서 백업 파일에는 넣지 않는다.
    val pipSpaceEnabled: Boolean = false,
    val pipSpacePosition: PipSpacePosition = PipSpacePosition.BOTTOM,
    val pipSpaceHeightDp: Int = PipSpace.DEFAULT_HEIGHT_DP,
    // Health Connect. 허용한 권한이 기기마다 달라서 백업 파일에는 넣지 않는다.
    /** 끝난 러닝 · 헬스 운동을 Health Connect 로 내보낸다. */
    val healthConnectEnabled: Boolean = false,
    /** Health Connect 의 최근 체중을 가져와 러닝 칼로리 계산에 쓴다. */
    val healthConnectImportWeight: Boolean = false,
    /** 마지막으로 맞추는 데 성공한 시각(epoch ms). 0 이면 아직 없다. */
    val healthConnectLastSyncAt: Long = 0,
    /** 마지막 시도가 실패했다면 그 이유. 성공하면 지운다. */
    val healthConnectLastError: String? = null,
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

    val pipSpace: PipSpace
        get() = PipSpace(pipSpaceEnabled, pipSpacePosition, PipSpace.clampHeight(pipSpaceHeightDp))
}