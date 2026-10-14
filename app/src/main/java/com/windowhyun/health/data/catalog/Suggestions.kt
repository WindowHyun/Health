package com.windowhyun.health.data.catalog

/** 오타일 때 "혹시 이거?" 를 찾는다. */
object Suggestions {

    /**
     * [candidates] 중 [input] 과 가장 비슷한 것. 공백과 대소문자는 무시하고(레그컬 → 레그 컬),
     * 글자 두세 개짜리 이름은 한 글자까지만 다른 것으로 본다. 비슷한 것이 없으면 null.
     */
    fun closest(input: String, candidates: Collection<String>): String? {
        val key = squash(input)
        if (key.isEmpty()) return null
        val allowed = if (key.length <= 4) 1 else 2
        var best: String? = null
        var bestDistance = allowed + 1
        for (candidate in candidates) {
            val distance = distance(key, squash(candidate), bestDistance - 1)
            if (distance < bestDistance) {
                best = candidate
                bestDistance = distance
                if (distance == 0) break
            }
        }
        return best
    }

    private fun squash(text: String): String = text.filterNot { it.isWhitespace() }.lowercase()

    /** 편집 거리. [limit] 를 넘을 것이 분명하면 서둘러 limit + 1 을 돌려준다. */
    internal fun distance(a: String, b: String, limit: Int): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                if (current[j] < rowMin) rowMin = current[j]
            }
            if (rowMin > limit) return limit + 1
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
