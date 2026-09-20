package com.windowhyun.health.core.model

/** PR 종류. */
enum class PersonalRecordType(val label: String) {
    MAX_WEIGHT("최고 중량"),
    MAX_VOLUME("최고 볼륨"),
    MAX_ESTIMATED_ONE_RM("예상 1RM"),
}

/**
 * 하나의 운동에서 새로 달성한 개인 기록.
 *
 * @param previousValue 직전 기록. 최초 달성이면 null.
 */
data class PersonalRecord(
    val exerciseId: Long,
    val exerciseName: String,
    val type: PersonalRecordType,
    val value: Double,
    val previousValue: Double?,
    val reps: Int? = null,
    val weightKg: Double? = null,
)
