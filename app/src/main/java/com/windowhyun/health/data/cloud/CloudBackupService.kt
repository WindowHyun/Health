package com.windowhyun.health.data.cloud

import com.windowhyun.health.domain.cloud.CloudAccount
import com.windowhyun.health.domain.cloud.CloudBackupInfo
import com.windowhyun.health.domain.cloud.CloudBackupStore
import com.windowhyun.health.domain.cloud.CloudException
import com.windowhyun.health.domain.repository.BackupRepository
import com.windowhyun.health.domain.repository.BackupSummary
import com.windowhyun.health.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 기존 백업 파일(운동 · 러닝(GPS 경로 포함) · 루틴 · 종목 · 템플릿 · 설정)을 압축해 클라우드에 올리고 내려받는다.
 *
 * **여러 기기 실시간 동기화가 아니라 "백업 · 복원" 이다.** 폰을 바꾸거나 앱을 지웠을 때 로그인해서 되돌리는 용도다.
 * 그래서 충돌 해결이 필요 없고, 로컬 DB 가 언제나 기준이다. 복원은 기존처럼 **현재 기록을 전부 대체**한다.
 *
 * 안전장치:
 * - 올리는 순서: 조각을 모두 쓴 뒤 정보를 쓴다. 중간에 끊겨도 반쪽짜리 백업이 목록에 뜨지 않는다.
 * - 새 백업이 올라간 뒤에만 옛 백업을 지운다(최근 [KEEP_COUNT]개 유지).
 * - **복원하기 전에 지금 기록을 먼저 백업**한다. 잘못 복원해도 한 단계 되돌릴 수 있다.
 * - 기록이 하나도 없는 상태(새로 설치한 직후)에서는 백업하지 않는다. 안 그러면 빈 백업이 좋은 백업을 밀어낸다.
 * - 내려받은 파일은 해시가 맞아야만 복원한다.
 */
@Singleton
class CloudBackupService internal constructor(
    private val account: CloudAccount,
    private val store: CloudBackupStore,
    private val backupRepository: BackupRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: () -> Long,
) {
    @Inject
    constructor(
        account: CloudAccount,
        store: CloudBackupStore,
        backupRepository: BackupRepository,
        settingsRepository: SettingsRepository,
    ) : this(account, store, backupRepository, settingsRepository, System::currentTimeMillis)

    private val mutex = Mutex()

    /** 지금 백업한다. 간격과 상관없이 올린다. 기록이 없으면 [CloudException]. */
    suspend fun backupNow(): CloudBackupInfo = mutex.withLock { backupLocked(auto = false)!! }

    /** 앱을 켤 때 부른다. 자동 백업이 켜져 있고 하루가 지났을 때만 올린다. 올렸으면 true. */
    suspend fun runIfDue(): Boolean = mutex.withLock {
        val settings = settingsRepository.current()
        if (!settings.cloudAutoBackup || account.user.value == null) return false
        if (!isDue(settings.lastCloudBackupAt, clock())) return false
        backupLocked(auto = true) != null
    }

    suspend fun list(): List<CloudBackupInfo> {
        val user = requireUser()
        return call("백업 목록을 가져오지 못했어요.") { store.list(user.uid) }
    }

    /**
     * [id] 백업으로 **현재 기록을 전부 대체**한다. 대체하기 전에 지금 기록을 먼저 올려 둔다(있을 때).
     * 내려받은 파일의 해시가 맞지 않으면 아무것도 바꾸지 않는다.
     */
    suspend fun restore(id: String): BackupSummary = mutex.withLock {
        val user = requireUser()
        val info = call("백업 목록을 가져오지 못했어요.") { store.list(user.uid) }.firstOrNull { it.id == id }
            ?: throw CloudException("선택한 백업을 찾을 수 없어요. 목록을 새로 불러와 주세요.")

        // 먼저 내려받아 확인한다(받다가 실패해도 지금 기록은 그대로다).
        val zipped = CloudBackupCodec.join(call("백업을 내려받지 못했어요.") { store.read(user.uid, info) })
        if (zipped.size.toLong() != info.sizeBytes || CloudBackupCodec.sha256Hex(zipped) != info.sha256) {
            throw CloudException("내려받은 백업이 온전하지 않아요(해시가 맞지 않음). 다시 시도해 주세요. 지금 기록은 바뀌지 않았어요.")
        }

        // 복원하면 지금 기록이 사라지므로, 있다면 먼저 한 번 올려 둔다. 이게 실패하면 복원도 하지 않는다.
        backupLocked(auto = false, allowEmpty = true)

        try {
            backupRepository.restoreBackup(CloudBackupCodec.decompressed(zipped))
        } catch (e: CancellationException) {
            throw e
        } catch (e: CloudException) {
            throw e
        } catch (e: Exception) {
            throw CloudException(e.message ?: "백업을 복원하지 못했어요.", e)
        }
    }

    /** 이 계정의 클라우드 백업을 모두 지운다. */
    suspend fun deleteAll() = mutex.withLock {
        val user = requireUser()
        call("백업을 지우지 못했어요.") { store.list(user.uid).forEach { store.delete(user.uid, it.id) } }
        settingsRepository.update { it.copy(lastCloudBackupAt = 0, lastCloudBackupError = null) }
    }

    // ----- 안쪽 -----

    private fun requireUser() = account.user.value ?: throw CloudException("먼저 구글 계정으로 로그인해 주세요.")

    /**
     * @param auto 자동 백업이면 실패를 설정에 남기고 예외 대신 null 을 돌려준다(앱을 켤 때 화면에 오류를 띄우지 않는다).
     * @param allowEmpty 기록이 없어도 올린다. 복원 직전 백업은 기록이 없으면 그냥 건너뛰므로 false 가 아니라 "건너뛰기" 로 쓴다.
     * 반환: 올렸으면 정보, 기록이 없어 건너뛰었으면 null(자동 백업 또는 [allowEmpty] 일 때).
     */
    private suspend fun backupLocked(auto: Boolean, allowEmpty: Boolean = false): CloudBackupInfo? {
        val user = requireUser()
        try {
            val (zipped, summary) = CloudBackupCodec.compress { out -> backupRepository.exportBackup(out) }

            if (summary.isEmpty) {
                if (auto || allowEmpty) return null
                throw CloudException("백업할 기록이 없어요. 운동이나 러닝을 기록한 뒤에 백업해 주세요.")
            }
            if (zipped.size > CloudBackupCodec.MAX_COMPRESSED_BYTES) {
                throw CloudException("기록이 너무 커서 클라우드에 올릴 수 없어요(압축 후 ${zipped.size / 1_048_576}MB).")
            }

            val now = clock()
            val info = CloudBackupInfo(
                id = backupId(now),
                createdAt = now,
                appVersion = summary.appVersion,
                workoutCount = summary.workoutCount,
                setCount = summary.setCount,
                runCount = summary.runCount,
                routineCount = summary.routineCount,
                customExerciseCount = summary.customExerciseCount,
                userTemplateCount = summary.userTemplateCount,
                sizeBytes = zipped.size.toLong(),
                chunkCount = CloudBackupCodec.split(zipped).size,
                sha256 = CloudBackupCodec.sha256Hex(zipped),
            )
            call("클라우드에 올리지 못했어요.") { store.put(user.uid, info, CloudBackupCodec.split(zipped)) }

            settingsRepository.update { it.copy(lastCloudBackupAt = now, lastCloudBackupError = null) }
            prune(user.uid)
            return info
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e::class.simpleName ?: "알 수 없는 오류"
            settingsRepository.update { it.copy(lastCloudBackupError = message) }
            if (auto) return null
            throw if (e is CloudException) e else CloudException(message, e)
        }
    }

    /** 새 백업이 올라간 뒤에만 오래된 것을 지운다. 지우기에 실패해도 백업 자체는 성공이다. */
    private suspend fun prune(uid: String) {
        try {
            store.list(uid).drop(KEEP_COUNT).forEach { store.delete(uid, it.id) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 다음 백업 때 다시 정리된다.
        }
    }

    /** 저장소 호출에서 난 오류를 사용자 문장으로 바꾼다(이미 [CloudException] 이면 그대로). */
    private suspend fun <T> call(fallback: String, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: CloudException) {
        throw e
    } catch (e: Exception) {
        throw CloudException(e.message?.let { "$fallback ($it)" } ?: fallback, e)
    }

    companion object {
        /** 클라우드에 남겨 두는 백업 수. 복원 직전 백업까지 합쳐 최근 몇 단계를 되돌릴 수 있다. */
        const val KEEP_COUNT = 5
        private const val DAY_MILLIS = 86_400_000L

        /** 이름에 시각이 들어 있어 사전순이 곧 시간순이다. */
        internal fun backupId(now: Long): String = "%013d".format(now)

        /** 마지막 백업이 없거나, 하루가 지났거나, 시계가 뒤로 돌아갔으면 백업할 때다. */
        internal fun isDue(lastAt: Long, now: Long): Boolean = lastAt <= 0 || lastAt > now || now - lastAt >= DAY_MILLIS
    }
}

/** 백업에 담긴 것이 하나도 없는가(새로 설치한 직후 등). */
private val BackupSummary.isEmpty: Boolean
    get() = workoutCount == 0 && runCount == 0 && routineCount == 0 && customExerciseCount == 0 && userTemplateCount == 0
