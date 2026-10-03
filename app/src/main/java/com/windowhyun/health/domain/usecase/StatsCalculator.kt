package com.windowhyun.health.domain.usecase

import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.Workout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 한 주(월~일)의 요약. 기록이 없는 주도 0 으로 채워 그래프가 끊기지 않게 한다. */
data class WeekStat(
    val weekStart: LocalDate,
    val workoutCount: Int,
    val sets: Int,
    val volumeKg: Double,
    val runCount: Int,
    val runDistanceMeters: Double,
)

/** 부위별 합계. 완료한 본세트만 센다(워밍업 제외). */
data class BodyPartStat(
    val bodyPart: BodyPart,
    val sets: Int,
    val volumeKg: Double,
)

data class TrainingStats(
    /** 오래된 주 -> 이번 주 순서. */
    val weeks: List<WeekStat>,
    /** 세트 수가 많은 부위부터. 세트가 없는 부위는 뺀다. */
    val bodyParts: List<BodyPartStat>,
) {
    val workoutCount: Int get() = weeks.sumOf { it.workoutCount }
    val totalSets: Int get() = weeks.sumOf { it.sets }
    val totalVolumeKg: Double get() = weeks.sumOf { it.volumeKg }
    val runCount: Int get() = weeks.sumOf { it.runCount }
    val runDistanceMeters: Double get() = weeks.sumOf { it.runDistanceMeters }
    val isEmpty: Boolean get() = workoutCount == 0 && runCount == 0

    /** 기간 전체 주 수로 나눈 주당 평균 운동 횟수(헬스 + 러닝). 쉰 주도 주 수에 들어간다. */
    val workoutsPerWeek: Double
        get() = if (weeks.isEmpty()) 0.0 else (workoutCount + runCount).toDouble() / weeks.size
}

/**
 * 헬스·러닝 기록을 주 단위와 부위별로 묶는다. 화면과 분리해 JVM 에서 바로 검증한다.
 *
 * 기간은 이번 주를 마지막으로 하는 [weekCount] 주다. 그 밖의 기록은 무시한다.
 */
object StatsCalculator {

    fun weekStartOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** [weekCount] 주 범위의 첫날(월요일). */
    fun rangeStart(today: LocalDate, weekCount: Int): LocalDate =
        weekStartOf(today).minusWeeks((weekCount - 1).toLong())

    fun compute(
        workouts: List<Workout>,
        runs: List<Run>,
        today: LocalDate,
        weekCount: Int,
    ): TrainingStats {
        require(weekCount >= 1) { "weekCount must be positive" }
        val start = rangeStart(today, weekCount)
        val end = weekStartOf(today).plusDays(6)
        val inRange = { date: LocalDate -> !date.isBefore(start) && !date.isAfter(end) }

        val workoutsByWeek = workouts.filter { inRange(it.date) }.groupBy { weekStartOf(it.date) }
        // 러닝은 아래에서 범위 안의 주만 꺼내 쓰므로 따로 걸러낼 필요가 없다.
        val runsByWeek = runs.groupBy { weekStartOf(it.date) }

        val weeks = (0 until weekCount).map { offset ->
            val weekStart = start.plusWeeks(offset.toLong())
            val weekWorkouts = workoutsByWeek[weekStart].orEmpty()
            val weekRuns = runsByWeek[weekStart].orEmpty()
            WeekStat(
                weekStart = weekStart,
                workoutCount = weekWorkouts.size,
                sets = weekWorkouts.sumOf { it.totalCompletedSets },
                volumeKg = weekWorkouts.sumOf { it.totalVolume },
                runCount = weekRuns.size,
                runDistanceMeters = weekRuns.sumOf { it.distanceMeters },
            )
        }

        val parts = workoutsByWeek.values.flatten()
            .flatMap { it.exercises }
            .groupBy { it.exercise.bodyPart }
            .map { (part, records) ->
                BodyPartStat(
                    bodyPart = part,
                    sets = records.sumOf { it.countedSets.size },
                    volumeKg = records.sumOf { it.totalVolume },
                )
            }
            .filter { it.sets > 0 }
            .sortedWith(compareByDescending<BodyPartStat> { it.sets }.thenBy { it.bodyPart.ordinal })

        return TrainingStats(weeks = weeks, bodyParts = parts)
    }
}
