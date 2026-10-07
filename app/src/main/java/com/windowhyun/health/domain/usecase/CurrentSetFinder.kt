package com.windowhyun.health.domain.usecase

import com.windowhyun.health.domain.model.WorkoutExerciseRecord
import com.windowhyun.health.domain.model.WorkoutSet

/** 지금 할 세트와 그 세트가 속한 종목. */
data class CurrentSet(
    val record: WorkoutExerciseRecord,
    val set: WorkoutSet,
)

/**
 * 운동 중 "지금 할 세트"를 고른다. 시계에서 완료를 누를 수 있는 세트가 이것이다.
 *
 * - 아직 안 끝낸 세트가 남은 첫 종목을 고른다.
 * - 그 종목이 슈퍼셋 묶음이면 묶음 안에서 번갈아 한다: 끝낸 세트가 가장 적은 종목 차례다(같으면 앞 종목).
 * - 고른 종목에서는 처음으로 안 끝낸 세트다.
 *
 * 폰 화면에서 ± 버튼이 붙는 세트(종목마다 처음 안 끝낸 세트)와 같은 규칙이다.
 */
object CurrentSetFinder {
    fun find(exercises: List<WorkoutExerciseRecord>): CurrentSet? {
        val first = exercises.indexOfFirst { record -> record.sets.any { !it.completed } }
        if (first < 0) return null

        val groups = SupersetGroups.normalize(exercises.map { it.supersetGroup })
        val group = groups[first]
        val candidates = if (group == SupersetGroups.NONE) {
            listOf(first)
        } else {
            exercises.indices.filter { groups[it] == group && exercises[it].sets.any { set -> !set.completed } }
        }
        val chosen = candidates.minByOrNull { exercises[it].completedSets.size } ?: first
        val record = exercises[chosen]
        return CurrentSet(record, record.sets.first { !it.completed })
    }
}
