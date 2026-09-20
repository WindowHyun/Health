package com.windowhyun.health.core.util

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_008.8

/**
 * 두 좌표 사이의 대권 거리(미터). Haversine 공식.
 *
 * android.location.Location 에 의존하지 않으므로 JVM 단위 테스트에서 그대로 쓸 수 있다.
 */
fun haversineMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val radLat1 = Math.toRadians(lat1)
    val radLat2 = Math.toRadians(lat2)

    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(radLat1) * cos(radLat2) * sin(dLon / 2) * sin(dLon / 2)
    // asin 쪽이 atan2 보다 짧은 거리에서 수치적으로 안정적이다.
    return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
}

/**
 * 초/킬로미터 페이스.
 *
 * 거리가 0 이면 페이스를 정의할 수 없으므로 0 을 돌려준다(표시할 때 "--'--"" 로 처리).
 */
fun paceSecPerKm(distanceMeters: Double, durationSeconds: Long): Double {
    if (distanceMeters <= 0.0 || durationSeconds <= 0) return 0.0
    return durationSeconds / (distanceMeters / 1000.0)
}

/**
 * 러닝 소모 칼로리 추정.
 *
 * 달리기는 속도와 관계없이 "체중 x 거리" 에 거의 비례한다는 점을 이용한
 * 널리 쓰이는 근사식이다. kcal = 1.036 x 체중(kg) x 거리(km)
 */
fun estimateRunCalories(bodyWeightKg: Double, distanceMeters: Double): Int {
    if (bodyWeightKg <= 0.0 || distanceMeters <= 0.0) return 0
    return (1.036 * bodyWeightKg * (distanceMeters / 1000.0)).toInt()
}
