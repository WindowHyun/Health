package com.windowhyun.health.ui.session

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.core.util.formatWeightValue
import org.junit.Test

/**
 * 중량 입력칸의 글자 -> kg.
 *
 * 입력칸은 저장값을 소수 1자리로 반올림해 보여 준다. 손대지 않은 값이 그 반올림 때문에
 * 바뀌면 안 된다(완료만 눌러도 중량이 올라가 가짜 최고 기록이 생긴다).
 */
class WeightInputTest {

    private fun shown(kg: Double, unit: WeightUnit) = formatWeightValue(unit.fromKg(kg))

    @Test
    fun `an untouched kg value with two decimals stays as stored`() {
        assertThat(shown(61.25, WeightUnit.KG)).isEqualTo("61.3")
        assertThat(weightKgOf("61.3", storedKg = 61.25, unit = WeightUnit.KG)).isEqualTo(61.25)
    }

    @Test
    fun `an untouched value shown in pounds stays as stored`() {
        assertThat(shown(100.0, WeightUnit.LB)).isEqualTo("220.5")
        assertThat(weightKgOf("220.5", storedKg = 100.0, unit = WeightUnit.LB)).isEqualTo(100.0)
    }

    @Test
    fun `a typed value replaces the stored one`() {
        assertThat(weightKgOf("62.5", storedKg = 61.25, unit = WeightUnit.KG)).isEqualTo(62.5)
        assertThat(weightKgOf("225", storedKg = 100.0, unit = WeightUnit.LB))
            .isWithin(1e-9).of(225 / WeightUnit.LB.perKg)
    }

    @Test
    fun `empty or unreadable input is zero`() {
        assertThat(weightKgOf("", storedKg = 60.0, unit = WeightUnit.KG)).isEqualTo(0.0)
        assertThat(weightKgOf(".", storedKg = 60.0, unit = WeightUnit.KG)).isEqualTo(0.0)
    }

    @Test
    fun `a new set with no weight stays zero`() {
        assertThat(shown(0.0, WeightUnit.KG)).isEqualTo("0")
        assertThat(weightKgOf("0", storedKg = 0.0, unit = WeightUnit.KG)).isEqualTo(0.0)
    }
}
