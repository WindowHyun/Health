package com.windowhyun.health.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.SetType
import com.windowhyun.health.core.util.startOfWeek
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.model.WorkoutExerciseRecord
import com.windowhyun.health.domain.model.WorkoutSet
import org.junit.Test
import java.time.LocalDate

class StatsCalculatorTest {

    // 2026-10-07 은 수요일. 주는 홈 · 캘린더와 같이 일요일에 시작하므로 그 주의 첫날은 10-04.
    private val today = LocalDate.of(2026, 10, 7)
    private val thisWeekStart = LocalDate.of(2026, 10, 4)

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
    fun `weeks run from the oldest Sunday to this week and empty weeks are zero`() {
        val stats = StatsCalculator.compute(emptyList(), emptyList(), today, weekCount = 4)

        assertThat(stats.weeks.map { it.weekStart }).containsExactly(
            thisWeekStart.minusWeeks(3), thisWeekStart.minusWeeks(2), thisWeekStart.minusWeeks(1), thisWeekStart,
        ).inOrder()
        assertThat(stats.weeks.all { it.workoutCount == 0 && it.volumeKg == 0.0 }).isTrue()
        assertThat(stats.isEmpty).isTrue()
    }

    @Test
    fun `a Saturday belongs to the week that started on the Sunday before`() {
        val saturday = LocalDate.of(2026, 10, 3)
        val sunday = LocalDate.of(2026, 10, 4)
        val stats = StatsCalculator.compute(
            listOf(workout(saturday, record(BodyPart.CHEST, set(50.0, 10))), workout(sunday, record(BodyPart.CHEST, set(50.0, 10)))),
            emptyList(), today, 2,
        )

        assertThat(stats.weeks.map { it.workoutCount }).containsExactly(1, 1).inOrder()
    }

    /** 홈의 "이번 주"와 같은 범위여야 두 화면의 숫자가 맞는다. */
    @Test
    fun `this week is the same range as the home summary`() {
        val stats = StatsCalculator.compute(emptyList(), emptyList(), today, 1)

        assertThat(stats.weeks.single().weekStart).isEqualTo(today.startOfWeek())
        // 일요일에 한 운동은 그날 시작한 주에 들어간다.
        assertThat(StatsCalculator.weekStartOf(thisWeekStart)).isEqualTo(thisWeekStart)
        assertThat(StatsCalculator.weekStartOf(thisWeekStart.minusDays(1))).isEqualTo(thisWeekStart.minusWeeks(1))
    }

    @Test
    fun `records outside the range are ignored`() {
        val tooOld = thisWeekStart.minusWeeks(4)
        val future = thisWeekStart.plusWeeks(1)
        val stats = StatsCalculator.compute(
            listOf(
                // 범위 밖 기록은 다른 부위로 만들어, 부위별 합계에 섞이면 바로 드러나게 한다.
                workout(tooOld, record(BodyPart.ARM, set(100.0, 5))),
                workout(future, record(BodyPart.CORE, set(100.0, 5))),
                workout(thisWeekStart.minusWeeks(3), record(BodyPart.LEG, set(100.0, 5))),
            ),
            listOf(run(tooOld, 5_000.0), run(thisWeekStart, 3_000.0)),
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
                    thisWeekStart,
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
                    thisWeekStart,
                    record(BodyPart.BACK, set(40.0, 10), set(40.0, 10)),
                    record(BodyPart.CHEST, set(40.0, 10), set(40.0, 10), set(40.0, 10)),
                    record(BodyPart.ARM, set(10.0, 10, completed = false)),
                ),
                workout(thisWeekStart.minusWeeks(1), record(BodyPart.LEG, set(40.0, 10))),
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
            listOf(run(thisWeekStart, 5_000.0), run(today, 3_000.0), run(thisWeekStart.minusDays(1), 10_000.0)),
            today, 2,
        )

        assertThat(stats.weeks.map { it.runDistanceMeters }).containsExactly(10_000.0, 8_000.0).inOrder()
        assertThat(stats.weeks.map { it.runCount }).containsExactly(1, 2).inOrder()
    }

    @Test
    fun `workouts per week counts quiet weeks too`() {
        val stats = StatsCalculator.compute(
            listOf(workout(thisWeekStart, record(BodyPart.CHEST, set(1.0, 1)))),
            listOf(run(thisWeekStart, 1_000.0)),
            today, 4,
        )

        assertThat(stats.workoutsPerWeek).isWithin(0.0001).of(0.5)
    }
}
