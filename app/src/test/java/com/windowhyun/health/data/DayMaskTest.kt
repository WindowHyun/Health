package com.windowhyun.health.data

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.data.mapper.toDayBit
import com.windowhyun.health.data.mapper.toDayMask
import com.windowhyun.health.data.mapper.toDayOfWeekSet
import org.junit.Test
import java.time.DayOfWeek

class DayMaskTest {

    @Test
    /** 요일 집합 <-> 비트마스크 */
    fun `converts between day set and bit mask`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)
        assertThat(days.toDayMask().toDayOfWeekSet()).isEqualTo(days)
    }

    @Test
    /** 빈 집합은 0 */
    fun `maps an empty set to zero`() {
        assertThat(emptySet<DayOfWeek>().toDayMask()).isEqualTo(0)
        assertThat(0.toDayOfWeekSet()).isEmpty()
    }

    @Test
    /** 단일 요일 비트 매칭 */
    fun `matches a single day bit against the mask`() {
        val mask = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY).toDayMask()
        assertThat(mask and DayOfWeek.FRIDAY.toDayBit()).isNotEqualTo(0)
        assertThat(mask and DayOfWeek.TUESDAY.toDayBit()).isEqualTo(0)
    }
}
