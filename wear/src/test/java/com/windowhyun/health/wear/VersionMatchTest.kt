package com.windowhyun.health.wear

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * 폰 앱과 시계 앱의 버전은 늘 같아야 한다. 한쪽만 올리면 폰에서 시계로 가는 상태 모양이 어긋난 채로
 * 설치되기 쉽다. 두 빌드 파일의 값을 읽어 맞는지 확인한다.
 */
class VersionMatchTest {

    private fun gradleFile(module: String): File {
        val candidates = listOf(File("../$module/build.gradle.kts"), File("$module/build.gradle.kts"))
        return candidates.firstOrNull { it.exists() } ?: error("$module/build.gradle.kts 를 찾지 못했습니다: ${candidates.map { it.absolutePath }}")
    }

    private fun value(module: String, key: String): String {
        val text = gradleFile(module).readText()
        val regex = Regex("""$key\s*=\s*"?([^"\n]+?)"?\s*$""", RegexOption.MULTILINE)
        return regex.find(text)?.groupValues?.get(1) ?: error("$module 에서 $key 를 찾지 못했습니다")
    }

    @Test
    fun `the watch app has the same version as the phone app`() {
        assertThat(value("wear", "versionName")).isEqualTo(value("app", "versionName"))
        assertThat(value("wear", "versionCode")).isEqualTo(value("app", "versionCode"))
    }

    /** 같은 앱 ID 여야 Data Layer 가 두 앱을 한 짝으로 본다. */
    @Test
    fun `the watch app uses the phone app id`() {
        val phone = value("app", "applicationId")
        assertThat(value("wear", "applicationId")).isEqualTo(phone)
    }
}
