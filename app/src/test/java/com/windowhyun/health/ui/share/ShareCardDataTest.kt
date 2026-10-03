package com.windowhyun.health.ui.share

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.model.PersonalRecord
import com.windowhyun.health.core.model.PersonalRecordType
import com.windowhyun.health.core.model.SetType
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.RunPoint
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.model.WorkoutExerciseRecord
import com.windowhyun.health.domain.model.WorkoutSet
import org.junit.Test
import java.time.LocalDate

/** 정리 카드에 무엇이 어떻게 적히는지. 화면 없이 값만 검증한다. */
class ShareCardDataTest {

    private fun set(
        weight: Double = 0.0,
        reps: Int = 0,
        seconds: Int = 0,
        completed: Boolean = true,
        type: SetType = SetType.NORMAL,
    ) = WorkoutSet(
        setNumber = 1,
        weightKg = weight,
        reps = reps,
        completed = completed,
        durationSeconds = seconds,
        setType = type,
    )

    private fun record(
        name: String,
        tracking: ExerciseTrackingType,
        vararg sets: WorkoutSet,
    ) = WorkoutExerciseRecord(
        exercise = Exercise(0, name, ExerciseCategory.BARBELL, BodyPart.CHEST, trackingType = tracking),
        orderIndex = 0,
        sets = sets.toList(),
    )

    private fun workout(vararg records: WorkoutExerciseRecord, name: String? = "가슴 데이") = Workout(
        routineName = name,
        date = LocalDate.of(2026, 10, 3),
        startTime = 0,
        endTime = 1,
        durationSeconds = 4_080,
        exercises = records.toList(),
    )

    private fun pr(n: Int) = (1..n).map {
        PersonalRecord(1, "벤치", PersonalRecordType.MAX_WEIGHT, 100.0, 90.0)
    }

    @Test
    fun `workout card carries the headline numbers`() {
        val data = WorkoutCardData.from(
            workout(record("벤치프레스", ExerciseTrackingType.WEIGHT_REPS, set(60.0, 10), set(80.0, 5))),
            pr(2),
            WeightUnit.KG,
        )

        assertThat(data.title).isEqualTo("가슴 데이")
        assertThat(data.date).isEqualTo("2026.10.03 토")
        assertThat(data.durationText).isEqualTo("1:08:00")
        assertThat(data.volumeText).isEqualTo("1,000kg")
        assertThat(data.totalSets).isEqualTo(2)
        assertThat(data.exerciseCount).isEqualTo(1)
        assertThat(data.personalRecordCount).isEqualTo(2)
    }

    @Test
    fun `a workout without a routine is called free workout`() {
        val data = WorkoutCardData.from(workout(name = null), emptyList(), WeightUnit.KG)
        assertThat(data.title).isEqualTo("자유 운동")
    }

    @Test
    fun `best set is the heaviest, then the most reps`() {
        val text = WorkoutCardData.bestSet(
            record(
                "벤치",
                ExerciseTrackingType.WEIGHT_REPS,
                set(60.0, 12),
                set(80.0, 3),
                set(80.0, 5),
                set(100.0, 1, completed = false), // 끝내지 못한 세트는 세지 않는다
                set(120.0, 5, type = SetType.WARMUP), // 워밍업도 세지 않는다
            ),
            WeightUnit.KG,
        )
        assertThat(text).isEqualTo("80kg × 5")
    }

    @Test
    fun `best set uses the user's weight unit`() {
        val text = WorkoutCardData.bestSet(
            record("벤치", ExerciseTrackingType.WEIGHT_REPS, set(100.0, 5)),
            WeightUnit.LB,
        )
        assertThat(text).isEqualTo("220.5lb × 5")
    }

    @Test
    fun `bodyweight shows the most reps and timed shows the longest hold`() {
        assertThat(
            WorkoutCardData.bestSet(
                record("푸시업", ExerciseTrackingType.REPS_ONLY, set(reps = 12), set(reps = 20), set(reps = 15)),
                WeightUnit.KG,
            ),
        ).isEqualTo("20회")
        assertThat(
            WorkoutCardData.bestSet(
                record("플랭크", ExerciseTrackingType.TIME, set(seconds = 45), set(seconds = 90)),
                WeightUnit.KG,
            ),
        ).isEqualTo("1:30")
    }

    /** 한 세트도 못 마친 운동이 카드에 "한 운동"으로 올라가면 거짓이다. */
    @Test
    fun `exercises with no finished set are left out`() {
        val data = WorkoutCardData.from(
            workout(
                record("벤치", ExerciseTrackingType.WEIGHT_REPS, set(60.0, 10)),
                record("스쿼트", ExerciseTrackingType.WEIGHT_REPS, set(100.0, 5, completed = false)),
                record("워밍업만", ExerciseTrackingType.WEIGHT_REPS, set(20.0, 10, type = SetType.WARMUP)),
            ),
            emptyList(),
            WeightUnit.KG,
        )

        assertThat(data.exerciseCount).isEqualTo(1)
        assertThat(data.exercises.map { it.name }).containsExactly("벤치")
    }

    @Test
    fun `a long workout shows the first four and counts the rest`() {
        val records = (1..7).map { record("운동$it", ExerciseTrackingType.WEIGHT_REPS, set(10.0 * it, 5)) }

        val data = WorkoutCardData.from(workout(*records.toTypedArray()), emptyList(), WeightUnit.KG)

        assertThat(data.exercises.map { it.name }).containsExactly("운동1", "운동2", "운동3", "운동4").inOrder()
        assertThat(data.hiddenExerciseCount).isEqualTo(3)
        assertThat(data.exerciseCount).isEqualTo(7)
    }

    @Test
    fun `no hidden count when everything fits`() {
        val records = (1..4).map { record("운동$it", ExerciseTrackingType.WEIGHT_REPS, set(10.0, 5)) }
        val data = WorkoutCardData.from(workout(*records.toTypedArray()), emptyList(), WeightUnit.KG)
        assertThat(data.hiddenExerciseCount).isEqualTo(0)
    }

    // ----- 러닝 -----

    private fun point(lat: Double, lon: Double, segmentStart: Boolean = false) =
        RunPoint(latitude = lat, longitude = lon, timestamp = 0, isSegmentStart = segmentStart)

    private fun run(route: List<RunPoint> = emptyList()) = Run(
        date = LocalDate.of(2026, 10, 3),
        startTime = 0,
        endTime = 1,
        durationSeconds = 2_580,
        distanceMeters = 8_200.0,
        averagePaceSecPerKm = 315.0,
        calories = 492,
        route = route,
    )

    @Test
    fun `run card carries distance time pace and calories`() {
        val data = RunCardData.from(run(), DistanceUnit.KM, isPersonalBest = true)

        assertThat(data.distanceNumber).isEqualTo("8.20")
        assertThat(data.distanceUnit).isEqualTo("km")
        assertThat(data.durationText).isEqualTo("43:00")
        assertThat(data.paceText).isEqualTo("5'15\"")
        assertThat(data.calories).isEqualTo(492)
        assertThat(data.isPersonalBest).isTrue()
        assertThat(data.route).isNull()
    }

    @Test
    fun `run card follows the distance unit`() {
        val data = RunCardData.from(run(), DistanceUnit.MILE, isPersonalBest = false)

        assertThat(data.distanceUnit).isEqualTo(DistanceUnit.MILE.label)
        assertThat(data.distanceNumber).isEqualTo("5.10")
        // 같은 5'15"/km 가 마일 기준으로 바뀌어야 한다.
        assertThat(data.paceText).isEqualTo("8'27\"")
    }

    @Test
    fun `a route needs at least two distinct points`() {
        assertThat(RouteShape.from(emptyList())).isNull()
        assertThat(RouteShape.from(listOf(point(37.0, 127.0)))).isNull()
        // 제자리 — 점은 많아도 움직이지 않았다
        assertThat(RouteShape.from(List(10) { point(37.0, 127.0) })).isNull()
    }

    @Test
    fun `route points are scaled into the unit square keeping the real aspect`() {
        // 위도 0.002 x 경도 0.004 인 직사각형 길. 적도 근처라 cos 보정이 거의 없다.
        val shape = RouteShape.from(
            listOf(point(0.000, 0.000), point(0.000, 0.004), point(0.002, 0.004), point(0.002, 0.000)),
        )!!

        val points = shape.segments.single()
        assertThat(points.map { it.first }.min()).isWithin(1e-4f).of(0f)
        assertThat(points.map { it.first }.max()).isWithin(1e-4f).of(1f)
        assertThat(points.map { it.second }.min()).isWithin(1e-4f).of(0f)
        assertThat(points.map { it.second }.max()).isWithin(1e-4f).of(1f)
        assertThat(shape.aspect).isWithin(0.01f).of(2f)
        // 화면 좌표는 아래로 커진다. 북쪽(위도 큰 쪽)이 위(0)에 와야 한다.
        assertThat(points[2].second).isWithin(1e-4f).of(0f)
        assertThat(points[0].second).isWithin(1e-4f).of(1f)
    }

    /** 위도가 높으면 경도 1도의 실제 길이가 짧다. 보정하지 않으면 길이 옆으로 퍼져 보인다. */
    @Test
    fun `longitude is shortened at high latitudes`() {
        val equator = RouteShape.from(listOf(point(0.0, 0.0), point(0.001, 0.001)))!!
        val north = RouteShape.from(listOf(point(60.0, 0.0), point(60.001, 0.001)))!!

        assertThat(equator.aspect).isWithin(0.01f).of(1f)
        assertThat(north.aspect).isWithin(0.02f).of(0.5f)
    }

    /** 일시정지 뒤 다시 시작한 점을 앞 점과 잇지 않는다. 이으면 건너뛴 구간이 직선으로 그려진다. */
    @Test
    fun `a resumed segment is not connected to the previous one`() {
        val shape = RouteShape.from(
            listOf(
                point(37.000, 127.000), point(37.001, 127.000),
                point(37.010, 127.010, segmentStart = true), point(37.011, 127.010),
            ),
        )!!

        assertThat(shape.segments).hasSize(2)
        assertThat(shape.segments.all { it.size == 2 }).isTrue()
    }

    @Test
    fun `segments with a single point are dropped and a route with none is null`() {
        val shape = RouteShape.from(
            listOf(point(37.0, 127.0), point(37.001, 127.001), point(37.01, 127.01, segmentStart = true)),
        )!!
        assertThat(shape.segments).hasSize(1)

        assertThat(
            RouteShape.from(listOf(point(37.0, 127.0, segmentStart = true), point(37.5, 127.5, segmentStart = true))),
        ).isNull()
    }

    @Test
    fun `a straight line is still drawable`() {
        // 남북으로 곧은 길과 동서로 곧은 길 모두. 한쪽 폭이 0 이어도 나눗셈이 깨지면 안 된다.
        listOf(
            listOf(point(37.0, 127.0), point(37.01, 127.0)),
            listOf(point(37.0, 127.0), point(37.0, 127.01)),
        ).forEach { line ->
            val shape = RouteShape.from(line)!!

            assertThat(shape.aspect).isGreaterThan(0f)
            assertThat(shape.aspect.isNaN()).isFalse()
            assertThat(shape.aspect.isInfinite()).isFalse()
            assertThat(shape.segments.single().all { !it.first.isNaN() && !it.second.isNaN() }).isTrue()
        }
    }
}
