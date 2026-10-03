package com.windowhyun.health.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.SetType
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.model.WorkoutExerciseRecord
import com.windowhyun.health.domain.model.WorkoutSet
import org.junit.Test
import java.time.LocalDate

class StatsCalculatorTest {

    // 2026-10-07 은 수요일. 그 주의 월요일은 10-05.
    private val today = LocalDate.of(2026, 10, 7)
    private val thisMonday = LocalDate.of(2026, 10, 5)

    private fun set(weight: Double, reps: Int, completed: Boolean = true, type: SetType = SetType.NORMAL) =
        WorkoutSet(setNumber = 1, weightKg = weight, reps = reps, completed = completed, setType = type)

    private fun record(part: BodyPart, vararg sets: WorkoutSet) = WorkoutExerciseRecord(
        exercise = Exercise(0, "x", ExerciseCategory.BARBELL, part),
        orderIndex = 0,
        sets = sets.toList(),
    )

    private fun workout(date: LocalDate, vararg records: WorkoutExerciseRecord) =
        Workout(date = date, startTime = 0, endTime = 1, exercises = records.toList())

    private fun run(date: LocalDate, meters: Double) =
        Run(date = date, startTime = 0, endTime = 1, distanceMeters = meters)

    @Test
    fun `weeks run from the oldest Monday to this week and empty weeks are zero`() {
        val stats = StatsCalculator.compute(emptyList(), emptyList(), today, weekCount = 4)

        assertThat(stats.weeks.map { it.weekStart }).containsExactly(
            thisMonday.minusWeeks(3), thisMonday.minusWeeks(2), thisMonday.minusWeeks(1), thisMonday,
        ).inOrder()
        assertThat(stats.weeks.all { it.workoutCount == 0 && it.volumeKg == 0.0 }).isTrue()
        assertThat(stats.isEmpty).isTrue()
    }

    @Test
    fun `a Sunday belongs to the week that started on the Monday before`() {
        val sunday = LocalDate.of(2026, 10, 4)
        val monday = LocalDate.of(2026, 10, 5)
        val stats = StatsCalculator.compute(
            listOf(workout(sunday, record(BodyPart.CHEST, set(50.0, 10))), workout(monday, record(BodyPart.CHEST, set(50.0, 10)))),
            emptyList(), today, 2,
        )

        assertThat(stats.weeks.map { it.workoutCount }).containsExactly(1, 1).inOrder()
    }

    @Test
    fun `records outside the range are ignored`() {
        val tooOld = thisMonday.minusWeeks(4)
        val future = thisMonday.plusWeeks(1)
        val stats = StatsCalculator.compute(
            listOf(
                // 범위 밖 기록은 다른 부위로 만들어, 부위별 합계에 섞이면 바로 드러나게 한다.
                workout(tooOld, record(BodyPart.ARM, set(100.0, 5))),
                workout(future, record(BodyPart.CORE, set(100.0, 5))),
                workout(thisMonday.minusWeeks(3), record(BodyPart.LEG, set(100.0, 5))),
            ),
            listOf(run(tooOld, 5_000.0), run(thisMonday, 3_000.0)),
            today, 4,
        )

        assertThat(stats.workoutCount).isEqualTo(1)
        assertThat(stats.runCount).isEqualTo(1)
        assertThat(stats.runDistanceMeters).isWithin(0.001).of(3_000.0)
        assertThat(stats.bodyParts.map { it.bodyPart }).containsExactly(BodyPart.LEG)
    }

    @Test
    fun `volume and sets skip warmups and unfinished sets`() {
        val stats = StatsCalculator.compute(
            listOf(
                workout(
                    thisMonday,
                    record(
                        BodyPart.CHEST,
                        set(20.0, 10, type = SetType.WARMUP),
                        set(60.0, 10),
                        set(60.0, 8, completed = false),
                    ),
                ),
            ),
            emptyList(), today, 1,
        )

        val week = stats.weeks.single()
        assertThat(week.sets).isEqualTo(1)
        assertThat(week.volumeKg).isWithin(0.001).of(600.0)
        assertThat(stats.bodyParts.single().sets).isEqualTo(1)
    }

    @Test
    fun `body parts are sorted by sets and parts without sets are left out`() {
        val stats = StatsCalculator.compute(
            listOf(
                workout(
                    thisMonday,
                    record(BodyPart.BACK, set(40.0, 10), set(40.0, 10)),
                    record(BodyPart.CHEST, set(40.0, 10), set(40.0, 10), set(40.0, 10)),
                    record(BodyPart.ARM, set(10.0, 10, completed = false)),
                ),
                workout(thisMonday.minusWeeks(1), record(BodyPart.LEG, set(40.0, 10))),
            ),
            emptyList(), today, 2,
        )

        assertThat(stats.bodyParts.map { it.bodyPart to it.sets })
            .containsExactly(BodyPart.CHEST to 3, BodyPart.BACK to 2, BodyPart.LEG to 1).inOrder()
    }

    @Test
    fun `runs add up per week`() {
        val stats = StatsCalculator.compute(
            emptyList(),
            listOf(run(thisMonday, 5_000.0), run(today, 3_000.0), run(thisMonday.minusDays(1), 10_000.0)),
            today, 2,
        )

        assertThat(stats.weeks.map { it.runDistanceMeters }).containsExactly(10_000.0, 8_000.0).inOrder()
        assertThat(stats.weeks.map { it.runCount }).containsExactly(1, 2).inOrder()
    }

    @Test
    fun `workouts per week counts quiet weeks too`() {
        val stats = StatsCalculator.compute(
            listOf(workout(thisMonday, record(BodyPart.CHEST, set(1.0, 1)))),
            listOf(run(thisMonday, 1_000.0)),
            today, 4,
        )

        assertThat(stats.workoutsPerWeek).isWithin(0.0001).of(0.5)
    }
}
