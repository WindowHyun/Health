package com.windowhyun.health.data.location

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 위치를 못 받을 때 화면에 보여 주는 원인 문장: 무엇을 고치면 되는지 알려 준다. */
class LocationErrorsTest {

    private fun api(code: Int) = ApiException(Status(code))

    @Test
    fun `a permission problem points to the location permission settings`() {
        val text = describeLocationError(SecurityException("denied"))

        assertThat(text).contains("위치 권한")
        assertThat(text).contains("정확한 위치")
    }

    @Test
    fun `play services that cannot be reached tell to update them`() {
        val text = describeLocationError(api(17))

        assertThat(text).contains("Google Play 서비스")
        assertThat(text).contains("17")
    }

    @Test
    fun `location settings problems say to turn gps on`() {
        listOf(6, 8502).forEach { code ->
            assertThat(describeLocationError(api(code))).contains("위치(GPS)를 켜")
        }
    }

    @Test
    fun `an unknown play services code is still shown`() {
        assertThat(describeLocationError(api(13))).contains("코드 13")
    }

    @Test
    fun `any other error keeps its message`() {
        assertThat(describeLocationError(IllegalStateException("뭔가 이상함"))).contains("뭔가 이상함")
        assertThat(describeLocationError(IllegalStateException())).contains("IllegalStateException")
    }
}
