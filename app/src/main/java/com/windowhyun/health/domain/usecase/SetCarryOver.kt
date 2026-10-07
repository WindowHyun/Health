package com.windowhyun.health.domain.usecase

import com.windowhyun.health.core.model.SetType
import com.windowhyun.health.domain.model.WorkoutSet
import kotlin.math.abs

/**
 * 한 세트의 무게를 바꾸면, 아직 안 한 뒤 세트도 같은 무게로 따라가게 한다.
 *
 * 계획은 보통 "60kg 3세트"라서, 첫 세트를 62.5kg 으로 바꾸면 나머지 두 세트를 하나씩 고치지
 * 않아도 된다. 입력 횟수를 줄이는 것이 목적이라 조심해서 따라가게 한다.
 *
 * - 바꾼 세트가 **아직 안 한 본세트**일 때만.
 * - 뒤 세트 중 **아직 안 했고**, **본세트**이고, **바꾸기 전 무게와 같았던 것**만. 일부러 다르게 잡아 둔
 *   세트(드롭, 피라미드)는 건드리지 않는다.
 * - 이미 끝낸 세트의 기록은 절대 바뀌지 않는다.
 */
object SetCarryOver {
    fun followers(sets: List<WorkoutSet>, edited: WorkoutSet, newWeightKg: Double): List<WorkoutSet> {
        if (edited.completed || edited.setType != SetType.NORMAL) return emptyList()
        if (sameWeight(edited.weightKg, newWeightKg)) return emptyList()
        val index = sets.indexOfFirst { it.id == edited.id }
        if (index < 0) return emptyList()
        return sets.drop(index + 1).filter {
            !it.completed && it.setType == SetType.NORMAL && sameWeight(it.weightKg, edited.weightKg)
        }
    }

    private fun sameWeight(a: Double, b: Double) = abs(a - b) < 1e-6
}
