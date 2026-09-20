package com.windowhyun.health.core.util

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.model.WeightUnit
import org.junit.Test

class FormattersTest {

    @Test
    /** 한 시간 미만은 분:초 */
    fun `formats duration with hours only when needed`() {
        assertThat(formatDuration(65)).isEqualTo("1:05")
        assertThat(formatDuration(3_725)).isEqualTo("1:02:05")
        assertThat(formatDuration(-5)).isEqualTo("0:00")
    }

    @Test
    /** 60 -> "60", 62.5 -> "62.5" */
    fun `keeps weight integral unless a decimal is needed`() {
        assertThat(formatWeightValue(60.0)).isEqualTo("60")
        assertThat(formatWeightValue(62.5)).isEqualTo("62.5")
    }

    @Test
    /** kg 저장값을 lb 로 표시 */
    fun `converts weight to the display unit`() {
        // 100kg = 220.46lb
        assertThat(formatWeight(100.0, WeightUnit.LB)).isEqualTo("220.5lb")
        assertThat(formatWeight(100.0, WeightUnit.KG)).isEqualTo("100kg")
    }

    @Test
    /** 미터 저장값을 km/mile 로 표시 */
    fun `converts distance to the display unit`() {
        assertThat(formatDistance(5_420.0, DistanceUnit.KM)).isEqualTo("5.42km")
        assertThat(formatDistance(1_609.34, DistanceUnit.MILE)).isEqualTo("1.00mile")
    }

    @Test
    /** 페이스 표기 */
    fun `formats pace as minutes and seconds`() {
        assertThat(formatPace(352.0)).isEqualTo("5'52\"")
        assertThat(formatPace(0.0)).isEqualTo("--'--\"")
    }

    @Test
    /** 단위 왕복 변환은 원래 값 유지 */
    fun `round trips unit conversion`() {
        val kg = 82.5
        assertThat(WeightUnit.LB.toKg(WeightUnit.LB.fromKg(kg))).isWithin(1e-9).of(kg)
        val meters = 5_000.0
        assertThat(DistanceUnit.MILE.toMeters(DistanceUnit.MILE.fromMeters(meters)))
            .isWithin(1e-6).of(meters)
    }
}
