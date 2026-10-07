package com.windowhyun.health.wear

import java.util.Locale
import kotlin.math.roundToLong

/** 시계 화면에 적는 글자 모양. 폰 앱의 표기와 같게 맞춘다(거리 소수 둘째 자리, 페이스 5'30"). */
internal object WatchFormat {
    private const val METERS_PER_MILE = 1_609.344

    /** 초 → "5:03" 또는 "1:05:03". */
    fun duration(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val hours = safe / 3_600
        val minutes = (safe % 3_600) / 60
        val seconds = safe % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /** 거리 숫자만. 예) 8420.0m -> "8.42". */
    fun distanceNumber(meters: Double, useMiles: Boolean): String {
        val value = if (useMiles) meters / METERS_PER_MILE else meters / 1_000.0
        return String.format(Locale.US, "%.2f", value.coerceAtLeast(0.0))
    }

    fun distanceUnit(useMiles: Boolean): String = if (useMiles) "mi" else "km"

    /**
     * km 당 초로 온 페이스를 사용자의 단위로 바꿔 적는다. 계산할 수 없으면 "--'--\"".
     * 마일을 쓰는 사람에게 km 페이스를 그대로 보여 주면 3.11mi 옆에 틀린 페이스가 붙는다.
     */
    fun pace(secPerKm: Double, useMiles: Boolean): String {
        if (secPerKm <= 0 || secPerKm.isNaN() || secPerKm.isInfinite()) return "--'--\""
        val perUnit = if (useMiles) secPerKm * METERS_PER_MILE / 1_000.0 else secPerKm
        val total = perUnit.roundToLong()
        return String.format(Locale.US, "%d'%02d\"", total / 60, total % 60)
    }

    /** 휴식 남은 초 → "1:05". */
    fun restClock(seconds: Int): String = duration(seconds.toLong())

    /** 세트 무게. 예) 62.5kg -> "62.5kg", 파운드면 "138lb". 정수면 소수점을 뗀다. */
    fun weight(kg: Double, useLb: Boolean): String {
        val value = if (useLb) kg * 2.2046226218 else kg
        val rounded = (value * 10).roundToLong() / 10.0
        val text = if (rounded == Math.floor(rounded)) rounded.toLong().toString() else String.format(Locale.US, "%.1f", rounded)
        return text + if (useLb) "lb" else "kg"
    }

    /** 세트 값을 한 줄로. 무게 운동 "62.5kg × 8", 횟수만 "8회", 시간 "0:45". */
    fun setValue(workout: com.windowhyun.health.shared.WorkoutSnapshot): String = when (workout.kind) {
        com.windowhyun.health.shared.WatchSetKind.WEIGHT_REPS -> "${weight(workout.weightKg, workout.useLb)} × ${workout.reps}"
        com.windowhyun.health.shared.WatchSetKind.REPS_ONLY -> "${workout.reps}회"
        com.windowhyun.health.shared.WatchSetKind.TIME -> duration(workout.durationSeconds.toLong())
    }
}
