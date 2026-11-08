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

    /**
     * 지금 기록을 보관한다. 보관했으면 true, 기록이 비어 있어 건너뛰었으면 false. 쓰다가 실패하면 예외를 던진다
     * (그러면 복원도 하지 않는다 — 되돌릴 방법 없이 지우지 않는다).
     */
    suspend fun save(backup: BackupRepository): Boolean = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val temp = File(directory, "$FILE_NAME.tmp")
        try {
            val summary = temp.outputStream().buffered().use { backup.exportBackup(it) }
            if (summary.isEmpty) {
                temp.delete()
                return@withContext false
            }
            if (!temp.renameTo(file)) {
                file.delete()
                check(temp.renameTo(file)) { "복원 전 기록을 보관하지 못했습니다." }
            }
            file.setLastModified(clock())
            true
        } finally {
            temp.delete()
        }
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
