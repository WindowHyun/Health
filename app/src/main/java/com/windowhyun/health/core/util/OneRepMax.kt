package com.windowhyun.health.core.util

/**
 * Epley 공식 기반 예상 1RM.
 *
 * 1RM = weight x (1 + reps / 30)
 *
 * reps 가 1 이면 중량 그대로가 1RM 이다.
 */
fun estimateOneRepMax(weightKg: Double, reps: Int): Double {
    if (weightKg <= 0.0 || reps <= 0) return 0.0
    if (reps == 1) return weightKg
    return weightKg * (1.0 + reps / 30.0)
}

/** 세트 볼륨 = 중량 x 반복 횟수. */
fun setVolume(weightKg: Double, reps: Int): Double {
    if (weightKg <= 0.0 || reps <= 0) return 0.0
    return weightKg * reps
}
