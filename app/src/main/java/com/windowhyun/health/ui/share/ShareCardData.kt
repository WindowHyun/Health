package com.windowhyun.health.ui.share

import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.model.PersonalRecord
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.core.util.formatDistanceValue
import com.windowhyun.health.core.util.formatDuration
import com.windowhyun.health.core.util.formatPace
import com.windowhyun.health.core.util.formatVolume
import com.windowhyun.health.core.util.formatWeight
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.RunPoint
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.model.WorkoutExerciseRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos

/** 카드 맨 위에 적는 날짜. 예) 2026.10.03 토 */
internal fun LocalDate.cardDate(): String =
    DateTimeFormatter.ofPattern("yyyy.MM.dd EEE", Locale.KOREAN).format(this)

/** 카드에 한 줄로 보여 주는 운동 하나. */
data class CardExercise(
    val name: String,
    /** 가장 좋았던 세트. 예) "100kg × 5" */
    val best: String,
)

/**
 * 헬스 정리 카드에 들어가는 값. 화면과 분리해 두어 무엇이 어떻게 적히는지 JVM 에서 검증한다.
 *
 * 카드는 한 장에 담겨야 하므로 운동이 많으면 앞의 [MAX_EXERCISES]개만 적고 나머지는 개수로 알린다.
 */
data class WorkoutCardData(
    val title: String,
    val date: String,
    val durationText: String,
    val volumeText: String,
    val totalSets: Int,
    val exerciseCount: Int,
    val exercises: List<CardExercise>,
    val hiddenExerciseCount: Int,
    val personalRecordCount: Int,
) {
    companion object {
        const val MAX_EXERCISES = 4

        fun from(
            workout: Workout,
            personalRecords: List<PersonalRecord>,
            weightUnit: WeightUnit,
        ): WorkoutCardData {
            // 한 세트도 못 마친 운동은 "한 운동"이 아니므로 카드에 올리지 않는다.
            val performed = workout.exercises.filter { it.countedSets.isNotEmpty() }
            return WorkoutCardData(
                title = workout.displayName,
                date = workout.date.cardDate(),
                durationText = formatDuration(workout.durationSeconds),
                volumeText = formatVolume(workout.totalVolume, weightUnit),
                totalSets = workout.totalCompletedSets,
                exerciseCount = performed.size,
                exercises = performed.take(MAX_EXERCISES).map { CardExercise(it.exercise.name, bestSet(it, weightUnit)) },
                hiddenExerciseCount = (performed.size - MAX_EXERCISES).coerceAtLeast(0),
                personalRecordCount = personalRecords.size,
            )
        }

        /** 가장 무거운 세트(같으면 횟수가 많은 쪽). 맨몸은 최다 횟수, 시간 운동은 가장 긴 시간. */
        internal fun bestSet(record: WorkoutExerciseRecord, weightUnit: WeightUnit): String {
            val sets = record.countedSets
            return when (record.exercise.trackingType) {
                ExerciseTrackingType.WEIGHT_REPS ->
                    sets.maxWithOrNull(compareBy({ it.weightKg }, { it.reps }))
                        ?.let { "${formatWeight(it.weightKg, weightUnit)} × ${it.reps}" }
                ExerciseTrackingType.REPS_ONLY -> sets.maxOfOrNull { it.reps }?.let { "${it}회" }
                ExerciseTrackingType.TIME -> sets.maxOfOrNull { it.durationSeconds }?.let { formatDuration(it.toLong()) }
            } ?: "-"
        }
    }
}

/** 카드 속 경로를 그리기 위해 0..1 로 줄인 점. 한 번에 이어 그릴 구간 단위로 묶는다. */
data class RouteShape(
    /** 각 구간의 점들. 가로·세로를 각각 0..1 로 맞춘 값이고 y 는 아래쪽이 큰 화면 좌표다. */
    val segments: List<List<Pair<Float, Float>>>,
    /** 실제 길의 가로 / 세로 비율. 그릴 때 이 비율을 지켜 찌그러지지 않게 한다. */
    val aspect: Float,
) {
    companion object {
        /**
         * 위도·경도를 평면 좌표로 펴서(경도는 위도의 cos 로 줄인다) 가로세로 비율을 지킨 채 0..1 로 맞춘다.
         * 점이 둘보다 적거나 한 곳에 모여 있으면, 또 이어 그릴 구간이 하나도 없으면 그릴 것이 없으므로 null.
         */
        fun from(route: List<RunPoint>): RouteShape? {
            if (route.size < 2) return null
            val meanLat = Math.toRadians(route.sumOf { it.latitude } / route.size)
            val scale = cos(meanLat)
            val xs = route.map { it.longitude * scale }
            val ys = route.map { it.latitude }
            val minX = xs.min()
            val minY = ys.min()
            val rawWidth = xs.max() - minX
            val rawHeight = ys.max() - minY
            val span = maxOf(rawWidth, rawHeight)
            if (span < MIN_SPAN_DEGREES) return null
            // 일직선에 가까운 길도 보이도록 한쪽이 지나치게 얇아지지 않게 한다.
            val width = maxOf(rawWidth, span * MIN_ASPECT)
            val height = maxOf(rawHeight, span * MIN_ASPECT)

            val segments = mutableListOf<MutableList<Pair<Float, Float>>>()
            route.forEachIndexed { index, point ->
                // 일시정지 뒤 다시 시작한 점은 앞 점과 잇지 않는다(직선으로 건너뛰는 선이 생기므로).
                if (index == 0 || point.isSegmentStart) segments.add(mutableListOf())
                val x = ((xs[index] - minX) / width).toFloat()
                val y = (1.0 - (ys[index] - minY) / height).toFloat()
                segments.last().add(x to y)
            }
            val drawable = segments.filter { it.size >= 2 }
            if (drawable.isEmpty()) return null
            return RouteShape(segments = drawable, aspect = (width / height).toFloat())
        }

        /** 약 1m. 이보다 좁으면 제자리에서 뛴 것이라 그릴 모양이 없다. */
        private const val MIN_SPAN_DEGREES = 0.00001
        private const val MIN_ASPECT = 0.05f
    }
}

/** 러닝 정리 카드에 들어가는 값. */
data class RunCardData(
    val date: String,
    val distanceNumber: String,
    val distanceUnit: String,
    val durationText: String,
    val paceText: String,
    val calories: Int,
    val route: RouteShape?,
    /** 최장 거리·최고 페이스 갱신 여부. 있으면 카드에 신기록 표시를 한다. */
    val isPersonalBest: Boolean,
) {
    companion object {
        fun from(run: Run, distanceUnit: DistanceUnit, isPersonalBest: Boolean): RunCardData = RunCardData(
            date = run.date.cardDate(),
            distanceNumber = formatDistanceValue(run.distanceMeters, distanceUnit),
            distanceUnit = distanceUnit.label,
            durationText = formatDuration(run.durationSeconds),
            paceText = formatPace(run.averagePaceSecPerKm, distanceUnit),
            calories = run.calories,
            route = RouteShape.from(run.route),
            isPersonalBest = isPersonalBest,
        )
    }
}
