package com.windowhyun.health.data.backup

import android.content.Context
import com.windowhyun.health.domain.repository.BackupRepository
import com.windowhyun.health.domain.repository.BackupSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 파일 백업으로 복원하기 **직전의 기록**을 앱 안에 한 개 보관한다. 복원은 지금 기록을 전부 지우고 파일 내용으로 바꾸므로,
 * 오래된 파일을 잘못 고르면 지금 기록이 사라진다. 이 보관본으로 한 단계 되돌릴 수 있다(클라우드 복원의 "복원 전 자동 백업"과 같은 보호).
 *
 * - 쓰는 도중 끊겨도 이전 보관본이 깨지지 않게 임시 파일에 쓴 뒤 바꿔 넣는다.
 * - 지금 기록이 비어 있으면(새 기기에서 첫 복원) 보관하지 않는다. 안 그러면 좋은 보관본이 빈 것으로 바뀐다.
 * - 보관본은 앱 전용 폴더에 있고, 앱을 지우면 함께 지워진다.
 */
@Singleton
class PreRestoreSnapshot internal constructor(
    private val directory: File,
    private val clock: () -> Long,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "pre_restore"), System::currentTimeMillis)

    private val file get() = File(directory, FILE_NAME)

    /** 보관본이 있으면 만든 시각(epoch millis), 없으면 null. */
    fun savedAt(): Long? = file.takeIf { it.isFile && it.length() > 0 }?.lastModified()

    private val pending get() = File(directory, "$FILE_NAME.pending")

    /**
     * 지금 기록을 **임시로** 써 둔다. 써 두었으면 true, 기록이 비어 있어 건너뛰었으면 false. 쓰다가 실패하면 예외를 던진다
     * (그러면 복원도 하지 않는다 — 되돌릴 방법 없이 지우지 않는다).
     *
     * 이 단계에서는 이전 보관본을 건드리지 않는다. 복원이 실제로 기록을 바꾼 뒤에 [commit] 으로 바꿔 넣고,
     * 파일이 거절되어 기록이 그대로면 [discard] 로 버린다. 먼저 바꿔 넣으면, 잘못 복원한 뒤 다른 파일을 골랐다가 거절당하는
     * 것만으로 "진짜 기록"이 담긴 보관본이 지금의 잘못된 기록으로 덮여 되돌릴 길이 사라진다.
     */
    suspend fun prepare(backup: BackupRepository): Boolean = withContext(Dispatchers.IO) {
        directory.mkdirs()
        pending.delete()
        try {
            val summary = pending.outputStream().buffered().use { backup.exportBackup(it) }
            if (summary.isEmpty) {
                pending.delete()
                false
            } else {
                true
            }
        } catch (e: Exception) {
            pending.delete()
            throw e
        }
    }

    /** [prepare] 로 써 둔 기록을 보관본으로 바꿔 넣는다. 써 둔 것이 없으면 아무 일도 하지 않는다. */
    suspend fun commit() = withContext(Dispatchers.IO) {
        val temp = pending
        if (!temp.isFile) return@withContext
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "복원 전 기록을 보관하지 못했습니다." }
        }
        file.setLastModified(clock())
    }

    /** [prepare] 로 써 둔 기록을 버린다. 이전 보관본은 그대로 남는다. */
    suspend fun discard() = withContext(Dispatchers.IO) {
        pending.delete()
        Unit
    }

    /** 지금 기록을 바로 보관한다([prepare] + [commit]). 보관했으면 true, 기록이 비어 있어 건너뛰었으면 false. */
    suspend fun save(backup: BackupRepository): Boolean {
        val kept = prepare(backup)
        if (kept) commit()
        return kept
    }

    /** 보관해 둔 기록으로 되돌린다. 보관본은 그대로 남는다. */
    suspend fun restore(backup: BackupRepository): BackupSummary = withContext(Dispatchers.IO) {
        check(file.isFile) { "되돌릴 보관본이 없습니다." }
        file.inputStream().buffered().use { backup.restoreBackup(it) }
    }

    private companion object {
        const val FILE_NAME = "before-restore.json"
    }
}

private val BackupSummary.isEmpty: Boolean
    get() = workoutCount == 0 && runCount == 0 && routineCount == 0 && customExerciseCount == 0 &&
        userTemplateCount == 0 && courseCount == 0
