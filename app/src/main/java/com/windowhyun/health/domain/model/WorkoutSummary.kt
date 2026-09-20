package com.windowhyun.health.domain.model

import com.windowhyun.health.core.model.PersonalRecord

/** 운동 종료 화면에 보여 줄 요약. */
data class WorkoutSummary(
    val workoutId: Long,
    val routineName: String,
    val durationSeconds: Long,
    val exerciseCount: Int,
    val totalSets: Int,
    val totalReps: Int,
    val totalVolumeKg: Double,
    val personalRecords: List<PersonalRecord> = emptyList(),
    val memo: String? = null,
)
