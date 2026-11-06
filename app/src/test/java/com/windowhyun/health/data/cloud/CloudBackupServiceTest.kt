package com.windowhyun.health.data.cloud

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.domain.cloud.CloudAccount
import com.windowhyun.health.domain.cloud.CloudBackupInfo
import com.windowhyun.health.domain.cloud.CloudBackupStore
import com.windowhyun.health.domain.cloud.CloudException
import com.windowhyun.health.domain.cloud.CloudUser
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.repository.BackupRepository
import com.windowhyun.health.domain.repository.BackupSummary
import com.windowhyun.health.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream

/** 클라우드 백업의 안전 규칙: 올리는 순서, 정리, 빈 백업 금지, 복원 전 백업, 해시 검증, 자동 백업 주기. */
class CloudBackupServiceTest {

    private val full = BackupSummary(appVersion = "0.14.0", workoutCount = 3, setCount = 9, runCount = 2, routineCount = 1)
    private var now = 1_000_000_000_000L

    private fun service(
        store: FakeStore = FakeStore(),
        backup: FakeBackup = FakeBackup(full),
        settings: FakeSettings = FakeSettings(),
        account: CloudAccount = FakeAccount(),
    ) = CloudBackupService(account, store, backup, settings) { now }

    @Test
    fun `backup uploads and records last time`() = runTest {
        val store = FakeStore(); val settings = FakeSettings()
        val info = service(store, settings = settings).backupNow()
        assertThat(store.infos).containsKey(info.id)
        assertThat(info.workoutCount).isEqualTo(3)
        assertThat(settings.current().lastCloudBackupAt).isEqualTo(now)
        assertThat(settings.current().lastCloudBackupError).isNull()
    }

    @Test
    fun `interrupted upload leaves no half backup and records error`() = runTest {
        val store = FakeStore().apply { failPut = true }; val settings = FakeSettings()
        assertThrows(CloudException::class.java) { kotlinx.coroutines.runBlocking { service(store, settings = settings).backupNow() } }
        assertThat(store.list("u1")).isEmpty()
        assertThat(settings.current().lastCloudBackupError).isNotNull()
        assertThat(settings.current().lastCloudBackupAt).isEqualTo(0)
    }

    @Test
    fun `empty data is not backed up`() = runTest {
        val store = FakeStore()
        val s = service(store, FakeBackup(BackupSummary()))
        assertThrows(CloudException::class.java) { kotlinx.coroutines.runBlocking { s.backupNow() } }
        assertThat(store.infos).isEmpty()
    }

    @Test
    fun `keeps only latest five`() = runTest {
        val store = FakeStore(); val s = service(store)
        repeat(7) { now += 1000; s.backupNow() }
        assertThat(store.infos).hasSize(CloudBackupService.KEEP_COUNT)
        assertThat(store.infos.keys.min()).isEqualTo(CloudBackupService.backupId(1_000_000_003_000L))
    }

    @Test
    fun `prune failure does not fail backup`() = runTest {
        val store = FakeStore(); val s = service(store)
        repeat(5) { now += 1000; s.backupNow() }
        store.failDelete = true
        now += 1000
        s.backupNow()
        assertThat(store.infos).hasSize(6)
    }

    @Test
    fun `restore backs up current data first then replaces`() = runTest {
        val store = FakeStore(); val backup = FakeBackup(full, "원본".toByteArray())
        val s = service(store, backup)
        val first = s.backupNow()
        backup.payload = "지금기록".toByteArray()
        now += 5000
        s.restore(first.id)
        assertThat(String(backup.restored!!)).isEqualTo("원본")
        // 복원 직전 기록("지금기록")이 새 백업으로 올라가 있다.
        assertThat(store.infos).hasSize(2)
    }

    @Test
    fun `hash mismatch blocks restore`() = runTest {
        val store = FakeStore(); val backup = FakeBackup(full)
        val s = service(store, backup)
        val info = s.backupNow()
        store.chunks[info.id] = listOf(ByteArray(10) { 7 })
        assertThrows(CloudException::class.java) { kotlinx.coroutines.runBlocking { s.restore(info.id) } }
        assertThat(backup.restored).isNull()
    }

    @Test
    fun `failed pre-restore backup aborts restore`() = runTest {
        val store = FakeStore(); val backup = FakeBackup(full)
        val s = service(store, backup)
        val info = s.backupNow()
        store.failPut = true
        now += 5000
        assertThrows(CloudException::class.java) { kotlinx.coroutines.runBlocking { s.restore(info.id) } }
        assertThat(backup.restored).isNull()
    }

    @Test
    fun `restore works on empty device`() = runTest {
        val store = FakeStore(); val backup = FakeBackup(full)
        val s = service(store, backup)
        val info = s.backupNow()
        backup.summary = BackupSummary() // 새 기기: 복원 전 기록이 없다
        now += 5000
        s.restore(info.id)
        assertThat(backup.restored).isNotNull()
        assertThat(store.infos).hasSize(1)
    }

    @Test
    fun `unknown backup id errors`() = runTest {
        assertThrows(CloudException::class.java) { kotlinx.coroutines.runBlocking { service().restore("nope") } }
    }

    @Test
    fun `signed out blocks everything`() = runTest {
        val s = service(account = FakeAccount(signedIn = false))
        assertThrows(CloudException::class.java) { kotlinx.coroutines.runBlocking { s.backupNow() } }
        assertThrows(CloudException::class.java) { kotlinx.coroutines.runBlocking { s.list() } }
        assertThat(s.runIfDue()).isFalse()
    }

    @Test
    fun `auto backup only when enabled and a day passed`() = runTest {
        val store = FakeStore()
        val settings = FakeSettings(AppSettings(cloudAutoBackup = false))
        val s = service(store, settings = settings)
        assertThat(s.runIfDue()).isFalse()
        settings.update { it.copy(cloudAutoBackup = true) }
        assertThat(s.runIfDue()).isTrue()
        now += 3_600_000
        assertThat(s.runIfDue()).isFalse()
        now += 86_400_000
        assertThat(s.runIfDue()).isTrue()
        assertThat(store.infos).hasSize(2)
    }

    @Test
    fun `auto backup failure is recorded not thrown`() = runTest {
        val store = FakeStore().apply { failPut = true }
        val settings = FakeSettings(AppSettings(cloudAutoBackup = true))
        assertThat(service(store, settings = settings).runIfDue()).isFalse()
        assertThat(settings.current().lastCloudBackupError).isNotNull()
    }

    @Test
    fun `auto backup skips empty data`() = runTest {
        val store = FakeStore()
        val settings = FakeSettings(AppSettings(cloudAutoBackup = true))
        val s = service(store, FakeBackup(BackupSummary()), settings)
        assertThat(s.runIfDue()).isFalse()
        assertThat(store.infos).isEmpty()
    }

    @Test
    fun `delete all clears list and last time`() = runTest {
        val store = FakeStore(); val settings = FakeSettings(); val s = service(store, settings = settings)
        s.backupNow(); now += 1000; s.backupNow()
        s.deleteAll()
        assertThat(store.infos).isEmpty()
        assertThat(settings.current().lastCloudBackupAt).isEqualTo(0)
    }

    @Test
    fun `codec round trip`() {
        val data = ByteArray(1_500_000) { (it % 251).toByte() }
        val chunks = CloudBackupCodec.split(data)
        assertThat(chunks).hasSize(3)
        assertThat(CloudBackupCodec.join(chunks)).isEqualTo(data)
        val (zipped, _) = CloudBackupCodec.compress { it.write(data) }
        assertThat(CloudBackupCodec.decompressed(zipped).readBytes()).isEqualTo(data)
        assertThat(CloudBackupCodec.sha256Hex(zipped)).hasLength(64)
    }

    @Test
    fun `due logic`() {
        assertThat(CloudBackupService.isDue(0, 100)).isTrue()
        assertThat(CloudBackupService.isDue(100, 100 + 3_600_000)).isFalse()
        assertThat(CloudBackupService.isDue(100, 100 + 86_400_000)).isTrue()
        assertThat(CloudBackupService.isDue(500, 100)).isTrue() // 시계가 뒤로
    }
}
