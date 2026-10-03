package com.windowhyun.health.data.backup

import com.windowhyun.health.domain.repository.BackupFolder
import com.windowhyun.health.domain.repository.BackupRepository
import com.windowhyun.health.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AutoBackupResult {
    /** 폴더를 고르지 않았다. */
    data object NotConfigured : AutoBackupResult

    /** 아직 다음 백업 시기가 아니다. */
    data object NotDue : AutoBackupResult

    data class Done(val fileName: String) : AutoBackupResult

    data class Failed(val message: String) : AutoBackupResult
}

/**
 * 정해 둔 폴더에 백업 파일을 정기적으로 만든다.
 *
 * 백그라운드 작업(WorkManager)은 쓰지 않고, 앱을 켤 때 "백업할 때가 됐는가"만 본다.
 * 개인용 앱은 거의 매일 열기 때문에 충분하고, 백그라운드 권한도 필요 없다.
 * 대신 앱을 한동안 열지 않으면 그동안은 백업이 만들어지지 않는다.
 *
 * - 새 파일을 다 쓴 뒤에만 옛 파일을 지운다. 실패한 시도가 좋은 백업을 밀어내면 안 된다.
 * - 이 앱이 만든 파일(`health-auto-*.json`)만 지우고, 같은 폴더의 다른 파일은 건드리지 않는다.
 */
@Singleton
class AutoBackupManager internal constructor(
    private val backupRepository: BackupRepository,
    private val settingsRepository: SettingsRepository,
    private val folder: BackupFolder,
    private val clock: () -> Long,
    private val zone: ZoneId,
) {
    @Inject
    constructor(
        backupRepository: BackupRepository,
        settingsRepository: SettingsRepository,
        folder: BackupFolder,
    ) : this(backupRepository, settingsRepository, folder, System::currentTimeMillis, ZoneId.systemDefault())

    private val mutex = Mutex()

    /** 앱을 켤 때 부른다. 백업할 때가 아니면 아무것도 하지 않는다. */
    suspend fun runIfDue(): AutoBackupResult = run(force = false)

    /** 사용자가 "지금 백업"을 눌렀을 때. 간격과 상관없이 바로 만든다. */
    suspend fun runNow(): AutoBackupResult = run(force = true)

    private suspend fun run(force: Boolean): AutoBackupResult = mutex.withLock {
        val settings = settingsRepository.current()
        val folderUri = settings.autoBackupFolderUri ?: return AutoBackupResult.NotConfigured
        val now = clock()
        if (!force && !isDue(settings.lastAutoBackupAt, settings.autoBackupEveryDays, now)) {
            return AutoBackupResult.NotDue
        }

        val fileName = fileNameAt(now)
        try {
            folder.write(folderUri, fileName) { out -> backupRepository.exportBackup(out) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e::class.simpleName ?: "알 수 없는 오류"
            // 그 사이 폴더를 바꿨다면 옛 폴더의 실패를 새 설정에 남기지 않는다.
            settingsRepository.update {
                if (it.autoBackupFolderUri == folderUri) it.copy(lastAutoBackupError = message) else it
            }
            return AutoBackupResult.Failed(message)
        }

        settingsRepository.update {
            if (it.autoBackupFolderUri == folderUri) {
                it.copy(lastAutoBackupAt = now, lastAutoBackupError = null)
            } else {
                it
            }
        }
        prune(folderUri)
        AutoBackupResult.Done(fileName)
    }

    /** 오래된 자동 백업을 지우고 최근 [KEEP_COUNT]개만 남긴다. 지우기에 실패해도 백업 자체는 성공이다. */
    private suspend fun prune(folderUri: String) {
        try {
            folder.listNames(folderUri)
                .filter { it.startsWith(FILE_PREFIX) && it.endsWith(FILE_SUFFIX) }
                .sortedDescending() // 이름에 날짜·시각이 들어 있어 사전순이 곧 시간순이다
                .drop(KEEP_COUNT)
                .forEach { folder.delete(folderUri, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 다음 백업 때 다시 정리된다.
        }
    }

    private fun fileNameAt(now: Long): String =
        FILE_PREFIX + NAME_TIME.format(Instant.ofEpochMilli(now).atZone(zone)) + FILE_SUFFIX

    companion object {
        const val FILE_PREFIX = "health-auto-"
        const val FILE_SUFFIX = ".json"
        const val KEEP_COUNT = 5
        private const val DAY_MILLIS = 86_400_000L
        private val NAME_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

        /** 마지막 백업이 없거나, 간격이 지났거나, 시계가 뒤로 돌아갔으면 백업할 때다. */
        internal fun isDue(lastAt: Long, everyDays: Int, now: Long): Boolean =
            lastAt <= 0 || lastAt > now || now - lastAt >= everyDays.coerceAtLeast(1) * DAY_MILLIS
    }
}
