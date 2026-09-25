package com.windowhyun.health.core.util

import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.model.WeightUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** 초 -> "1:05:03" 또는 "5:03". */
fun formatDuration(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** 초 -> "1시간 12분" 형태의 짧은 한국어 표기. */
fun formatDurationKorean(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}시간 ${minutes}분"
        hours > 0 -> "${hours}시간"
        minutes > 0 -> "${minutes}분"
        else -> "${safe}초"
    }
}

/** 소수점이 필요 없으면 정수로, 필요하면 소수 첫째 자리까지. 60.0 -> "60", 62.5 -> "62.5" */
fun formatWeightValue(value: Double): String {
    val rounded = (value * 10).roundToLong() / 10.0
    return if (abs(rounded - rounded.roundToInt()) < 0.05) {
        rounded.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", rounded)
    }
}

/** kg 값을 사용자 단위로 변환해 "60kg" 형태로 표기. */
fun formatWeight(weightKg: Double, unit: WeightUnit): String =
    "${formatWeightValue(unit.fromKg(weightKg))}${unit.label}"

/** 볼륨 표기. 값이 커지므로 천 단위 구분 기호를 넣는다. 예) 12,340kg */
fun formatVolume(volumeKg: Double, unit: WeightUnit): String {
    val converted = unit.fromKg(volumeKg)
    val rounded = converted.roundToLong()
    return if (converted >= 1_000) {
        String.format(Locale.US, "%,d", rounded) + unit.label
    } else {
        "${formatWeightValue(converted)}${unit.label}"
    }
}

/** 미터 -> "5.42km". */
fun formatDistance(meters: Double, unit: DistanceUnit): String =
    String.format(Locale.US, "%.2f", unit.fromMeters(meters)) + unit.label

/** 초/킬로미터(또는 마일) 페이스 -> "5'42\"". */
fun formatPace(secondsPerUnit: Double): String {
    if (secondsPerUnit <= 0 || secondsPerUnit.isNaN() || secondsPerUnit.isInfinite()) return "--'--\""
    val total = secondsPerUnit.roundToLong()
    return String.format(Locale.US, "%d'%02d\"", total / 60, total % 60)
}

/** 개인 기록 값을 종류에 맞게 표기한다. */
fun formatPersonalRecordValue(
    type: com.windowhyun.health.core.model.PersonalRecordType,
    value: Double,
    unit: WeightUnit,
): String = when (type) {
    com.windowhyun.health.core.model.PersonalRecordType.MAX_WEIGHT,
    com.windowhyun.health.core.model.PersonalRecordType.MAX_ESTIMATED_ONE_RM,
    -> formatWeight(value, unit)

    com.windowhyun.health.core.model.PersonalRecordType.MAX_VOLUME -> formatVolume(value, unit)
    com.windowhyun.health.core.model.PersonalRecordType.MAX_REPS -> "${value.toInt()}회"
    com.windowhyun.health.core.model.PersonalRecordType.MAX_DURATION ->
        formatDuration(value.toLong())
}
