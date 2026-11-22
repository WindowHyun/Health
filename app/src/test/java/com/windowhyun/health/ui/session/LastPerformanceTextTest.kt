package com.windowhyun.health.ui.session

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.domain.model.WorkoutSet
import org.junit.Test

/**
 * 운동 중 화면의 "지난 운동" 한 줄.
 *
 * 시간·횟수만 재는 종목은 중량과 횟수가 0 으로 저장된다. 기록 방식대로 적지 않으면
 * 플랭크가 "0kg×0" 으로 나온다.
 */
class LastPerformanceTextTest {

    private fun set(weightKg: Double = 0.0, reps: Int = 0, seconds: Int = 0) =
        WorkoutSet(setNumber = 1, weightKg = weightKg, reps = reps, completed = true, durationSeconds = seconds)

    @Test
    fun `weight exercises show weight and reps`() {
        val text = lastPerformanceText(
            listOf(set(weightKg = 60.0, reps = 10), set(weightKg = 62.5, reps = 8)),
            ExerciseTrackingType.WEIGHT_REPS,
            WeightUnit.KG,
        )
        assertThat(text).isEqualTo("지난 운동  60kg×10 · 62.5kg×8")
    }

    @Test
    fun `time exercises show the duration instead of zero weight`() {
        val text = lastPerformanceText(
            listOf(set(seconds = 60), set(seconds = 45)),
            ExerciseTrackingType.TIME,
            WeightUnit.KG,
        )
        assertThat(text).isEqualTo("지난 운동  1:00 · 0:45")
    }

    @Test
    fun `reps only exercises show just the reps`() {
        val text = lastPerformanceText(
            listOf(set(reps = 12), set(reps = 10)),
            ExerciseTrackingType.REPS_ONLY,
            WeightUnit.KG,
        )
        assertThat(text).isEqualTo("지난 운동  12회 · 10회")
    }

    @Test
    fun `no history says so`() {
        val text = lastPerformanceText(emptyList(), ExerciseTrackingType.TIME, WeightUnit.KG)
        assertThat(text).contains("지난 기록 없음")
    }
}
