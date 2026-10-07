package com.windowhyun.health.domain.usecase

/**
 * 이번 주 목표 한 가지의 진행 상황. 목표를 안 정했으면(0 이하) 만들어지지 않는다.
 */
data class GoalProgress(
    val current: Double,
    val goal: Double,
) {
    /** 0..1. 목표를 넘겨도 1 에서 멈춘다(막대가 넘치지 않게). */
    val fraction: Float get() = (current / goal).toFloat().coerceIn(0f, 1f)

    val achieved: Boolean get() = current >= goal

    /** 남은 양. 달성했으면 0. */
    val remaining: Double get() = (goal - current).coerceAtLeast(0.0)

    companion object {
        /** [goal] 이 0 이하면 "목표 없음"이라 null. */
        fun of(current: Double, goal: Double): GoalProgress? =
            if (goal > 0) GoalProgress(current.coerceAtLeast(0.0), goal) else null
    }
}
