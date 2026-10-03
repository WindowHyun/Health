package com.windowhyun.health.domain.usecase

/**
 * 슈퍼셋 묶음 계산. 운동 목록 순서대로 늘어놓은 그룹 번호([NONE] = 묶음 없음)만 다룬다.
 *
 * 규칙
 * - 같은 번호가 **연속**으로 2개 이상 있어야 슈퍼셋이다. 하나뿐이거나 떨어져 있으면 묶음이 아니다.
 * - 번호는 [normalize] 가 목록 순서대로 1, 2, 3 ... 으로 다시 매긴다. 그래서 A, B, C 라벨이 안정적이다.
 *
 * 운동을 지우거나 순서를 바꾸면 반드시 [normalize] 를 거친다.
 */
object SupersetGroups {
    const val NONE = 0

    /** 연속하지 않거나 혼자인 그룹을 풀고 번호를 1부터 다시 매긴다. */
    fun normalize(groups: List<Int>): List<Int> {
        val result = MutableList(groups.size) { NONE }
        var next = 1
        var start = 0
        while (start < groups.size) {
            val group = groups[start]
            var end = start
            while (end + 1 < groups.size && groups[end + 1] == group) end++
            if (group != NONE && end > start) {
                for (i in start..end) result[i] = next
                next++
            }
            start = end + 1
        }
        return result
    }

    /** [index] 번째 운동을 바로 앞 운동과 묶는다. 첫 번째 운동이거나 범위 밖이면 그대로. */
    fun linkWithPrevious(groups: List<Int>, index: Int): List<Int> {
        if (index <= 0 || index >= groups.size) return normalize(groups)
        val previous = groups[index - 1]
        val group = if (previous != NONE) previous else (groups.maxOrNull() ?: NONE) + 1
        val updated = groups.toMutableList().also {
            it[index - 1] = group
            it[index] = group
        }
        return normalize(updated)
    }

    /** [index] 번째 운동을 묶음에서 뺀다. 남은 운동이 혼자가 되면 그 묶음도 풀린다. */
    fun unlink(groups: List<Int>, index: Int): List<Int> {
        if (index !in groups.indices) return normalize(groups)
        val updated = groups.toMutableList().also { it[index] = NONE }
        return normalize(updated)
    }

    /** 바로 앞 운동과 묶여 있는가. */
    fun isLinkedWithPrevious(groups: List<Int>, index: Int): Boolean =
        index in 1 until groups.size && groups[index] != NONE && groups[index] == groups[index - 1]

    /** 슈퍼셋 라벨. 1번 묶음의 두 번째 운동이면 "A2", 묶음이 아니면 null. */
    fun label(groups: List<Int>, index: Int): String? {
        val normalized = normalize(groups)
        val group = normalized.getOrNull(index) ?: return null
        if (group == NONE) return null
        val position = (0..index).count { normalized[it] == group }
        return "${'A' + (group - 1) % 26}$position"
    }

    /** 이 운동의 세트를 마치면 쉬어야 하는가. 묶음 중간이면 쉬지 않고 바로 다음 운동으로 간다. */
    fun restAfter(groups: List<Int>, index: Int): Boolean {
        val normalized = normalize(groups)
        val group = normalized.getOrNull(index) ?: return true
        return group == NONE || normalized.getOrNull(index + 1) != group
    }

    /** 묶음 중간이면 다음 운동의 인덱스, 아니면 null. */
    fun nextInGroup(groups: List<Int>, index: Int): Int? =
        if (restAfter(groups, index)) null else index + 1
}
