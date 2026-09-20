package com.windowhyun.health.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OneRepMaxTest {

    @Test
    /** 60kg x 10 -> 80kg */
    fun `estimates one rep max with the Epley formula`() {
        // 60kg x 10 -> 60 * (1 + 10/30) = 80
        assertThat(estimateOneRepMax(60.0, 10)).isWithin(0.001).of(80.0)
    }

    @Test
    /** 1회는 중량이 곧 1RM */
    fun `treats a single rep as the weight itself`() {
        assertThat(estimateOneRepMax(100.0, 1)).isWithin(0.001).of(100.0)
    }

    @Test
    /** 중량이나 횟수가 0 이면 0 */
    fun `returns zero for empty input`() {
        assertThat(estimateOneRepMax(0.0, 10)).isEqualTo(0.0)
        assertThat(estimateOneRepMax(60.0, 0)).isEqualTo(0.0)
    }

    @Test
    /** 세트 볼륨 = 중량 x 횟수 */
    fun `computes set volume as weight times reps`() {
        assertThat(setVolume(62.5, 8)).isWithin(0.001).of(500.0)
        assertThat(setVolume(0.0, 8)).isEqualTo(0.0)
    }
}
