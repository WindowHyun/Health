package com.windowhyun.health.wear

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WatchFormatTest {

    @Test
    fun `durations use minutes seconds and add hours only when needed`() {
        assertThat(WatchFormat.duration(0)).isEqualTo("0:00")
        assertThat(WatchFormat.duration(59)).isEqualTo("0:59")
        assertThat(WatchFormat.duration(65)).isEqualTo("1:05")
        assertThat(WatchFormat.duration(3_599)).isEqualTo("59:59")
        assertThat(WatchFormat.duration(3_600)).isEqualTo("1:00:00")
        assertThat(WatchFormat.duration(3_725)).isEqualTo("1:02:05")
    }

    @Test
    fun `a negative duration is shown as zero`() {
        assertThat(WatchFormat.duration(-5)).isEqualTo("0:00")
    }

    @Test
    fun `distance is kilometres or miles with two decimals`() {
        assertThat(WatchFormat.distanceNumber(8_420.0, useMiles = false)).isEqualTo("8.42")
        assertThat(WatchFormat.distanceNumber(0.0, useMiles = false)).isEqualTo("0.00")
        assertThat(WatchFormat.distanceNumber(1_609.344, useMiles = true)).isEqualTo("1.00")
        assertThat(WatchFormat.distanceNumber(5_000.0, useMiles = true)).isEqualTo("3.11")
        assertThat(WatchFormat.distanceUnit(false)).isEqualTo("km")
        assertThat(WatchFormat.distanceUnit(true)).isEqualTo("mi")
    }

    @Test
    fun `pace follows the unit and unknown pace is dashes`() {
        assertThat(WatchFormat.pace(330.0, useMiles = false)).isEqualTo("5'30\"")
        // 같은 5'00"/km 는 마일로 8'03" 이다. km 값을 그대로 쓰면 틀린다.
        assertThat(WatchFormat.pace(300.0, useMiles = true)).isEqualTo("8'03\"")
        assertThat(WatchFormat.pace(0.0, useMiles = false)).isEqualTo("--'--\"")
        assertThat(WatchFormat.pace(Double.NaN, useMiles = false)).isEqualTo("--'--\"")
        assertThat(WatchFormat.pace(Double.POSITIVE_INFINITY, useMiles = true)).isEqualTo("--'--\"")
    }

    @Test
    fun `rest clock reads like a countdown`() {
        assertThat(WatchFormat.restClock(90)).isEqualTo("1:30")
        assertThat(WatchFormat.restClock(0)).isEqualTo("0:00")
    }
}
