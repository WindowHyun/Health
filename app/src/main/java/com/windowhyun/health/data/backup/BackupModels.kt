package com.windowhyun.health.data.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

/**
 * 백업 파일 형식.
 *
 * 개인 앱이라 서버가 없으므로, 기기를 바꾸거나 앱을 지웠을 때 기록을 되찾는
 * 수단은 이 파일뿐이다. 그래서 다음 두 가지를 지킨다.
 *
 * 1. id 를 그대로 담는다. 세트가 어떤 운동에, 그 운동이 어떤 기록에 붙어
 *    있는지가 id 로 이어져 있어서, 새로 번호를 매기면 연결이 끊어진다.
 * 2. 모든 필드에 기본값을 둔다. 옛 버전에서 만든 파일에 새 필드가 없어도
 *    읽을 수 있어야 한다.
 *
 * 단, [formatVersion] 과 [createdAt] 은 파일에 **반드시** 있어야 한다([Required]).
 * 기본값만으로 읽히게 두면 `{}` 나 다른 앱의 JSON 도 "빈 백업"으로 읽혀서,
 * 잘못 고른 파일 하나로 기록 전체가 지워진다.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupFile(
    /** 형식 버전. 읽을 수 없는 미래 버전을 만나면 거절한다. */
    @Required
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    /** 만든 시각(epoch millis). 복원 화면에서 "언제 백업" 인지 보여 준다. */
    @Required
    val createdAt: Long = 0,
    /** 만든 앱 버전. 문제가 생겼을 때 어디서 나온 파일인지 알 수 있다. */
    val appVersion: String = "",
    val settings: BackupSettings = BackupSettings(),
    val exercises: List<BackupExercise> = emptyList(),
    val routines: List<BackupRoutine> = emptyList(),
    val routineExercises: List<BackupRoutineExercise> = emptyList(),
    val workouts: List<BackupWorkout> = emptyList(),
    val workoutExercises: List<BackupWorkoutExercise> = emptyList(),
    val workoutSets: List<BackupWorkoutSet> = emptyList(),
    val personalRecords: List<BackupPersonalRecord> = emptyList(),
    val runs: List<BackupRun> = emptyList(),
    val runLaps: List<BackupRunLap> = emptyList(),
    val runLocations: List<BackupRunLocation> = emptyList(),
) {
    companion object {
        /** 지금 쓰는 형식 버전. 필드를 지우거나 뜻을 바꿀 때만 올린다. */
        const val CURRENT_FORMAT_VERSION = 1
    }
}

@Serializable
data class BackupSettings(
    val defaultRestSeconds: Int = 60,
    val weightUnit: String = "KG",
    val distanceUnit: String = "KM",
    val vibrationEnabled: Boolean = true,
    val restTimerAutoStart: Boolean = true,
    val autoLapMeters: Int = 1000,
    val bodyWeightKg: Double = 70.0,
    val themeMode: String = "SYSTEM",
    val healthConnectEnabled: Boolean = false,
    val keepScreenOnDuringWorkout: Boolean = true,
)

@Serializable
data class BackupExercise(
    val id: Long = 0,
    val name: String = "",
    val category: String = "",
    val bodyPart: String = "",
    val isBuiltIn: Boolean = false,
    val defaultRestSeconds: Int? = null,
    val trackingType: String = "WEIGHT_REPS",
)

@Serializable
data class BackupRoutine(
    val id: Long = 0,
    val name: String = "",
    val scheduledDayMask: Int = 0,
    val createdAt: Long = 0,
    val sortOrder: Int = 0,
)

@Serializable
data class BackupRoutineExercise(
    val id: Long = 0,
    val routineId: Long = 0,
    val exerciseId: Long = 0,
    val orderIndex: Int = 0,
    val defaultSets: Int = 3,
    val restSeconds: Int? = null,
)

@Serializable
data class BackupWorkout(
    val id: Long = 0,
    val routineId: Long? = null,
    val routineName: String? = null,
    val date: Long = 0,
    val startTime: Long = 0,
    val endTime: Long? = null,
    val durationSeconds: Long = 0,
    val memo: String? = null,
)

@Serializable
data class BackupWorkoutExercise(
    val id: Long = 0,
    val workoutId: Long = 0,
    val exerciseId: Long = 0,
    val orderIndex: Int = 0,
    val restSeconds: Int? = null,
)

@Serializable
data class BackupWorkoutSet(
    val id: Long = 0,
    val workoutExerciseId: Long = 0,
    val setNumber: Int = 0,
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val durationSeconds: Int = 0,
    val setType: String = "NORMAL",
)

@Serializable
data class BackupPersonalRecord(
    val id: Long = 0,
    val workoutId: Long = 0,
    val exerciseId: Long = 0,
    val type: String = "",
    val value: Double = 0.0,
    val previousValue: Double? = null,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val achievedAt: Long = 0,
)

@Serializable
data class BackupRun(
    val id: Long = 0,
    val date: Long = 0,
    val startTime: Long = 0,
    val endTime: Long? = null,
    val durationSeconds: Long = 0,
    val distanceMeters: Double = 0.0,
    val averagePaceSecPerKm: Double = 0.0,
    val bestPaceSecPerKm: Double = 0.0,
    val calories: Int = 0,
    val steps: Int = 0,
    val goalType: String = "FREE",
    val goalValue: Double = 0.0,
    val memo: String? = null,
)

@Serializable
data class BackupRunLap(
    val id: Long = 0,
    val runId: Long = 0,
    val lapNumber: Int = 0,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Long = 0,
    val paceSecPerKm: Double = 0.0,
)

@Serializable
data class BackupRunLocation(
    val id: Long = 0,
    val runId: Long = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val timestamp: Long = 0,
    val isSegmentStart: Boolean = false,
)
