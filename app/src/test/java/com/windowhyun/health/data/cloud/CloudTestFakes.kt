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

/** 클라우드 백업 테스트용 가짜 계정 · 저장소 · 백업 · 설정. */
internal class FakeAccount(signedIn: Boolean = true, override val isAvailable: Boolean = true) : CloudAccount {
    override val user: StateFlow<CloudUser?> = MutableStateFlow(if (signedIn) CloudUser("u1", "a@b.c", "에이") else null)
    override suspend fun signIn(activityContext: Context) = true
    override fun signOut() = Unit
}

internal class FakeStore : CloudBackupStore {
    val infos = linkedMapOf<String, CloudBackupInfo>()
    val chunks = mutableMapOf<String, List<ByteArray>>()
    var failPut = false
    var failDelete = false
    override suspend fun put(uid: String, info: CloudBackupInfo, chunks: List<ByteArray>) {
        if (failPut) {
            // 조각만 쓰다 끊긴 상황: 정보는 남지 않아야 한다.
            this.chunks[info.id] = chunks.take(1)
            throw java.io.IOException("끊김")
        }
        this.chunks[info.id] = chunks
        infos[info.id] = info
    }
    override suspend fun list(uid: String) = infos.values.sortedByDescending { it.id }
    override suspend fun read(uid: String, info: CloudBackupInfo) =
        chunks[info.id] ?: throw CloudException("없음")
    override suspend fun delete(uid: String, id: String) {
        if (failDelete) throw java.io.IOException("지우기 실패")
        infos.remove(id); chunks.remove(id)
    }
}

internal class FakeBackup(var summary: BackupSummary, var payload: ByteArray = "데이터".toByteArray()) : BackupRepository {
    var restored: ByteArray? = null
    override suspend fun exportBackup(output: OutputStream): BackupSummary { output.write(payload); return summary }
    override suspend fun restoreBackup(input: InputStream): BackupSummary { restored = input.readBytes(); return summary }
    override suspend fun exportWorkoutCsv(output: OutputStream) = 0
    override suspend fun exportRunCsv(output: OutputStream) = 0
}

internal class FakeSettings(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state.map { it }
    override suspend fun current() = state.value
    override suspend fun update(transform: (AppSettings) -> AppSettings) { state.value = transform(state.value) }
}

