package com.windowhyun.health.data.mapper

import com.windowhyun.health.data.local.entity.RunEntity
import com.windowhyun.health.data.local.entity.RunLapEntity
import com.windowhyun.health.data.local.entity.RunLocationEntity
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.RunLap
import com.windowhyun.health.domain.model.RunPoint
import java.time.LocalDate

fun RunLapEntity.toDomain(): RunLap = RunLap(
    id = id,
    lapNumber = lapNumber,
    distanceMeters = distanceMeters,
    durationSeconds = durationSeconds,
    paceSecPerKm = paceSecPerKm,
)

fun RunLocationEntity.toDomain(): RunPoint = RunPoint(
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    timestamp = timestamp,
    isSegmentStart = isSegmentStart,
)

fun RunEntity.toDomain(
    laps: List<RunLapEntity> = emptyList(),
    locations: List<RunLocationEntity> = emptyList(),
): Run = Run(
    id = id,
    date = LocalDate.ofEpochDay(date),
    startTime = startTime,
    endTime = endTime,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    averagePaceSecPerKm = averagePaceSecPerKm,
    bestPaceSecPerKm = bestPaceSecPerKm,
    calories = calories,
    goalType = runCatching { RunGoalType.valueOf(goalType) }.getOrDefault(RunGoalType.FREE),
    goalValue = goalValue,
    memo = memo,
    laps = laps.map { it.toDomain() },
    route = locations.map { it.toDomain() },
)
