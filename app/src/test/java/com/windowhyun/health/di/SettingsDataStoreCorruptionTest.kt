package com.windowhyun.health.di

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 설정 파일이 깨져 있어도 앱이 켜져야 한다. 이 파일은 자동 백업으로 다른 폰에 복원될 수 있어,
 * 깨진 채 들어오면 예전에는 설정을 읽는 순간 예외가 나 앱을 켤 때마다 죽었다.
 */
class SettingsDataStoreCorruptionTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun settingsFile() = File(folder.root, "settings.preferences_pb")

    @Test
    fun `a corrupted settings file starts from the defaults and keeps a copy`() = runBlocking<Unit> {
        settingsFile().writeBytes(byteArrayOf(0x7F, 0x00, 0x13, 0x42, 0x55, 0x1A, 0x6B, 0x01, 0x02, 0x03))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = SettingsRepositoryImpl(createSettingsDataStore(scope) { settingsFile() })

            val settings = repository.current() // 예전에는 여기서 CorruptionException

            assertThat(settings.defaultRestSeconds).isEqualTo(com.windowhyun.health.domain.model.AppSettings().defaultRestSeconds)
            assertThat(File(settingsFile().path + ".corrupt").exists()).isTrue()

            // 이어서 평소처럼 저장하고 다시 읽을 수 있다.
            repository.update { it.copy(defaultRestSeconds = 95) }
            assertThat(repository.current().defaultRestSeconds).isEqualTo(95)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `an intact settings file is left alone`() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = SettingsRepositoryImpl(createSettingsDataStore(scope) { settingsFile() })
            repository.update { it.copy(defaultRestSeconds = 77) }

            assertThat(repository.current().defaultRestSeconds).isEqualTo(77)
            assertThat(File(settingsFile().path + ".corrupt").exists()).isFalse()
        } finally {
            scope.cancel()
        }
    }
}
