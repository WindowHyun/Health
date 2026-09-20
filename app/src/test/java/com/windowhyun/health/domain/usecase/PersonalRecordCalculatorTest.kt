package com.windowhyun.health.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.PersonalRecordType
import com.windowhyun.health.data.local.relation.ExerciseSetHistory
import com.windowhyun.health.domain.model.WorkoutSet
import org.junit.Test

class PersonalRecordCalculatorTest {

    private fun history(weight: Double, reps: Int, workoutExerciseId: Long) =
        ExerciseSetHistory(
            workoutId = workoutExerciseId,
            workoutExerciseId = workoutExerciseId,
            weightKg = weight,
            reps = reps,
            startTime = workoutExerciseId,
        )

    private fun set(number: Int, weight: Double, reps: Int, completed: Boolean = true) =
        WorkoutSet(id = number.toLong(), setNumber = number, weightKg = weight, reps = reps, completed = completed)

    @Test
    /** 최고 볼륨 = 한 세션에서 그 종목으로 올린 합계 */
    fun `max volume is the per session total`() {
        val bests = PersonalRecordCalculator.fromHistory(
            listOf(
                // 세션 1: 60x10 + 60x10 = 1200
                history(60.0, 10, 1),
                history(60.0, 10, 1),
                // 세션 2: 70x5 = 350
                history(70.0, 5, 2),
            ),
        )
        assertThat(bests.maxSessionVolumeKg).isWithin(0.001).of(1_200.0)
        assertThat(bests.maxWeightKg).isWithin(0.001).of(70.0)
    }

    @Test
    /** 기록을 넘으면 PR */
    fun `reports a record when the session beats history`() {
        val previous = PersonalRecordCalculator.fromHistory(listOf(history(60.0, 10, 1)))
        val session = PersonalRecordCalculator.fromSession(listOf(set(1, 65.0, 10)))

        val records = PersonalRecordCalculator.newRecords(1L, "벤치프레스", previous, session)

        assertThat(records.map { it.type }).containsExactly(
            PersonalRecordType.MAX_WEIGHT,
            PersonalRecordType.MAX_VOLUME,
            PersonalRecordType.MAX_ESTIMATED_ONE_RM,
        )
        val maxWeight = records.first { it.type == PersonalRecordType.MAX_WEIGHT }
        assertThat(maxWeight.value).isWithin(0.001).of(65.0)
        assertThat(maxWeight.previousValue).isWithin(0.001).of(60.0)
    }

    @Test
    /** 같은 기록은 PR 이 아니다 */
    fun `does not report a tie as a record`() {
        val previous = PersonalRecordCalculator.fromHistory(listOf(history(60.0, 10, 1)))
        val session = PersonalRecordCalculator.fromSession(listOf(set(1, 60.0, 10)))

        assertThat(PersonalRecordCalculator.newRecords(1L, "벤치프레스", previous, session)).isEmpty()
    }

    @Test
    /** 이력이 없으면 첫 기록이 모두 PR */
    fun `reports every first record with no previous value`() {
        val session = PersonalRecordCalculator.fromSession(listOf(set(1, 40.0, 12)))

        val records = PersonalRecordCalculator.newRecords(
            exerciseId = 1L,
            exerciseName = "스쿼트",
            previous = PersonalRecordCalculator.fromHistory(emptyList()),
            session = session,
        )

        assertThat(records).hasSize(3)
        assertThat(records.all { it.previousValue == null }).isTrue()
    }

    @Test
    /** 완료하지 않은 세트는 제외 */
    fun `ignores incomplete sets`() {
        val session = PersonalRecordCalculator.fromSession(
            listOf(set(1, 60.0, 10), set(2, 100.0, 10, completed = false)),
        )
        assertThat(session.maxWeightKg).isWithin(0.001).of(60.0)
    }

    @Test
    /** 중량이 낮아도 반복이 많으면 1RM PR 가능 */
    fun `more reps at lower weight can still set a one rep max record`() {
        // 이전: 100kg x 1 -> 1RM 100
        val previous = PersonalRecordCalculator.fromHistory(listOf(history(100.0, 1, 1)))
        // 이번: 90kg x 5 -> 90 * (1 + 5/30) = 105
        val session = PersonalRecordCalculator.fromSession(listOf(set(1, 90.0, 5)))

        val records = PersonalRecordCalculator.newRecords(1L, "데드리프트", previous, session)

        assertThat(records.map { it.type }).contains(PersonalRecordType.MAX_ESTIMATED_ONE_RM)
        assertThat(records.map { it.type }).doesNotContain(PersonalRecordType.MAX_WEIGHT)
    }
}
