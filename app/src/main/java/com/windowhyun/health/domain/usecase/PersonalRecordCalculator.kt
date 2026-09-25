package com.windowhyun.health.domain.usecase

import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.model.PersonalRecord
import com.windowhyun.health.core.model.PersonalRecordType
import com.windowhyun.health.core.util.estimateOneRepMax
import com.windowhyun.health.core.util.setVolume
import com.windowhyun.health.data.local.relation.ExerciseSetHistory
import com.windowhyun.health.domain.model.WorkoutSet

/**
 * 종목별 PR(최고 중량 / 최고 볼륨 / 예상 1RM) 계산.
 *
 * - 최고 중량: 완료한 세트 중 가장 무거운 중량
 * - 최고 볼륨: 한 세션에서 그 종목으로 올린 볼륨 합계(중량 x 횟수)의 최댓값
 * - 예상 1RM: Epley 공식 기준 최댓값
 *
 * SQL 이 아니라 Kotlin 에서 계산해 공식이 한 곳에만 존재하도록 한다.
 */
object PersonalRecordCalculator {

    /** 한 종목의 기록 묶음. */
    data class Bests(
        val maxWeightKg: Double = 0.0,
        val maxSessionVolumeKg: Double = 0.0,
        val maxOneRepMaxKg: Double = 0.0,
        /** 횟수로 재는 운동의 최고 횟수. */
        val maxReps: Int = 0,
        /** 시간으로 재는 운동의 최고 유지 시간(초). */
        val maxDurationSeconds: Int = 0,
        /** 최고 1RM 을 만든 세트 정보. */
        val bestWeightKg: Double? = null,
        val bestReps: Int? = null,
    ) {
        val isEmpty: Boolean
            get() = maxWeightKg <= 0.0 && maxSessionVolumeKg <= 0.0 &&
                maxReps <= 0 && maxDurationSeconds <= 0
    }

    /**
     * 과거 이력으로부터 기존 기록을 계산한다.
     *
     * 완료 표시만 남고 횟수가 0 으로 지워진 세트는 제외한다.
     * [fromSession] 과 기준이 달라지면 기준선이 오염되어 이후 PR 이 막힌다.
     */
    fun fromHistory(history: List<ExerciseSetHistory>): Bests {
        // 기록 방식과 무관한 지표(최고 횟수·최고 시간)는 별도로 센다.
        val maxReps = history.maxOfOrNull { it.reps } ?: 0
        val maxDuration = history.maxOfOrNull { it.durationSeconds } ?: 0

        val usable = history.filter { it.reps > 0 && it.weightKg > 0.0 }
        if (usable.isEmpty()) return Bests(maxReps = maxReps, maxDurationSeconds = maxDuration)
        val maxWeight = usable.maxOf { it.weightKg }
        // 한 세션에 같은 종목이 두 번 들어갈 수 있으므로 세션(workoutId) 단위로 합산한다.
        // 블록(workoutExerciseId) 단위로 묶으면 세션 합계보다 작게 나와 기준이 어긋난다.
        val sessionVolumes = usable
            .groupBy { it.workoutId }
            .mapValues { (_, sets) -> sets.sumOf { setVolume(it.weightKg, it.reps) } }
        val maxSessionVolume = sessionVolumes.values.maxOrNull() ?: 0.0
        val best = usable.maxByOrNull { estimateOneRepMax(it.weightKg, it.reps) }
        return Bests(
            maxWeightKg = maxWeight,
            maxSessionVolumeKg = maxSessionVolume,
            maxOneRepMaxKg = best?.let { estimateOneRepMax(it.weightKg, it.reps) } ?: 0.0,
            maxReps = maxReps,
            maxDurationSeconds = maxDuration,
            bestWeightKg = best?.weightKg,
            bestReps = best?.reps,
        )
    }

    /** 이번 세션에서 그 종목으로 세운 기록을 계산한다. */
    fun fromSession(sets: List<WorkoutSet>): Bests {
        // 워밍업은 집계에서 뺀다(WorkoutSet.counts).
        val counted = sets.filter { it.counts }
        val maxReps = counted.maxOfOrNull { it.reps } ?: 0
        val maxDuration = counted.maxOfOrNull { it.durationSeconds } ?: 0

        val weighted = counted.filter { it.reps > 0 && it.weightKg > 0.0 }
        if (weighted.isEmpty()) return Bests(maxReps = maxReps, maxDurationSeconds = maxDuration)
        val best = weighted.maxByOrNull { it.estimatedOneRepMax }
        return Bests(
            maxWeightKg = weighted.maxOf { it.weightKg },
            maxSessionVolumeKg = weighted.sumOf { it.volume },
            maxOneRepMaxKg = best?.estimatedOneRepMax ?: 0.0,
            maxReps = maxReps,
            maxDurationSeconds = maxDuration,
            bestWeightKg = best?.weightKg,
            bestReps = best?.reps,
        )
    }

    /**
     * 이번 세션 기록이 과거 기록을 넘었으면 새 PR 로 만든다.
     * 값이 같은 경우는 PR 로 보지 않는다(부동소수 오차 대비 아주 작은 여유를 둔다).
     */
    fun newRecords(
        exerciseId: Long,
        exerciseName: String,
        previous: Bests,
        session: Bests,
        trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_REPS,
    ): List<PersonalRecord> {
        if (session.isEmpty) return emptyList()
        val epsilon = 0.0001
        val records = mutableListOf<PersonalRecord>()

        // 시간으로 재는 운동은 최고 시간만, 횟수로 재는 운동은 최고 횟수만 의미가 있다.
        if (trackingType == ExerciseTrackingType.TIME) {
            if (session.maxDurationSeconds > previous.maxDurationSeconds) {
                records += PersonalRecord(
                    exerciseId = exerciseId,
                    exerciseName = exerciseName,
                    type = PersonalRecordType.MAX_DURATION,
                    value = session.maxDurationSeconds.toDouble(),
                    previousValue = previous.maxDurationSeconds.toDouble().takeIf { it > 0 },
                )
            }
            return records
        }

        if (trackingType == ExerciseTrackingType.REPS_ONLY) {
            if (session.maxReps > previous.maxReps) {
                records += PersonalRecord(
                    exerciseId = exerciseId,
                    exerciseName = exerciseName,
                    type = PersonalRecordType.MAX_REPS,
                    value = session.maxReps.toDouble(),
                    previousValue = previous.maxReps.toDouble().takeIf { it > 0 },
                    reps = session.maxReps,
                )
            }
            return records
        }

        if (session.maxWeightKg > previous.maxWeightKg + epsilon) {
            records += PersonalRecord(
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                type = PersonalRecordType.MAX_WEIGHT,
                value = session.maxWeightKg,
                previousValue = previous.maxWeightKg.takeIf { it > 0.0 },
            )
        }
        if (session.maxSessionVolumeKg > previous.maxSessionVolumeKg + epsilon) {
            records += PersonalRecord(
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                type = PersonalRecordType.MAX_VOLUME,
                value = session.maxSessionVolumeKg,
                previousValue = previous.maxSessionVolumeKg.takeIf { it > 0.0 },
            )
        }
        if (session.maxOneRepMaxKg > previous.maxOneRepMaxKg + epsilon) {
            records += PersonalRecord(
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                type = PersonalRecordType.MAX_ESTIMATED_ONE_RM,
                value = session.maxOneRepMaxKg,
                previousValue = previous.maxOneRepMaxKg.takeIf { it > 0.0 },
                reps = session.bestReps,
                weightKg = session.bestWeightKg,
            )
        }
        return records
    }

    /** 현재까지의 PR 을 목록 형태로 반환(기록 상세/통계용). */
    fun currentRecords(
        exerciseId: Long,
        exerciseName: String,
        bests: Bests,
    ): List<PersonalRecord> {
        if (bests.isEmpty) return emptyList()
        return listOf(
            PersonalRecord(exerciseId, exerciseName, PersonalRecordType.MAX_WEIGHT, bests.maxWeightKg, null),
            PersonalRecord(exerciseId, exerciseName, PersonalRecordType.MAX_VOLUME, bests.maxSessionVolumeKg, null),
            PersonalRecord(
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                type = PersonalRecordType.MAX_ESTIMATED_ONE_RM,
                value = bests.maxOneRepMaxKg,
                previousValue = null,
                reps = bests.bestReps,
                weightKg = bests.bestWeightKg,
            ),
        )
    }
}
