package com.windowhyun.health.data.catalog

import android.content.Context
import com.windowhyun.health.di.IoDispatcher
import com.windowhyun.health.ui.gym.RoutineTemplates
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

/** 사용자가 가져온 템플릿이 한도를 넘어 저장할 수 없을 때. 메시지는 그대로 사용자에게 보여 준다. */
class TemplateStorageFullException(message: String) : IllegalStateException(message) {
    companion object {
        /** 개수 한도를 넘을 때의 안내. 저장소 · 가져오기 검사 · 테스트가 같은 문구를 쓴다. */
        val TOO_MANY = "가져온 템플릿은 ${UserTemplateRepository.MAX_TEMPLATES}개까지 저장할 수 있습니다. 안 쓰는 템플릿을 지우고 다시 시도해 주세요."

        /** 크기 한도를 넘을 때의 안내. */
        const val TOO_LARGE = "가져온 템플릿이 너무 커서 저장할 수 없습니다. 안 쓰는 템플릿을 지우고 다시 시도해 주세요."
    }
}

/**
 * 사용자가 파일 · 붙여넣기로 가져온 루틴 템플릿의 저장소.
 *
 * 앱에 포함된 템플릿([RoutineTemplates.all])과 따로 둔다. 같은 id 로 다시 저장하면 새 내용으로 바뀐다.
 */
interface UserTemplateRepository {

    /** 가져온 템플릿 목록(저장한 순서). 바뀔 때마다 새로 나온다. */
    val templates: Flow<List<RoutineTemplates.Template>>

    suspend fun current(): List<RoutineTemplates.Template>

    /** 같은 id 는 새 내용으로 바꾸고, 새 id 는 뒤에 붙인다. 한도를 넘으면 [TemplateStorageFullException]. */
    suspend fun save(templates: List<RoutineTemplates.Template>)

    suspend fun delete(id: String)

    /** 백업 복원처럼 통째로 바꿀 때. */
    suspend fun replaceAll(templates: List<RoutineTemplates.Template>)

    companion object {
        const val MAX_TEMPLATES = 100
        const val MAX_BYTES = 512 * 1024

        /** [existing] 에 [incoming] 을 합친 목록: 같은 id 는 그 자리에서 바꾸고 새 id 는 뒤에 붙인다. */
        fun merge(
            existing: List<RoutineTemplates.Template>,
            incoming: List<RoutineTemplates.Template>,
        ): List<RoutineTemplates.Template> {
            val replacements = incoming.associateBy { it.id }
            val kept = existing.map { replacements[it.id] ?: it }
            val existingIds = existing.mapTo(HashSet()) { it.id }
            return kept + incoming.filter { it.id !in existingIds }
        }
    }
}

/**
 * 파일 하나(`files/user_templates.json`)에 [CatalogWriter] 형식으로 저장한다.
 *
 * 쓸 때는 임시 파일에 쓴 뒤 한 번에 바꿔 치워서, 쓰다가 앱이 꺼져도 예전 파일이 남는다.
 * 읽을 수 없는 파일은 지우지 않고 `.broken` 으로 옆에 남겨 두고 빈 목록으로 시작한다.
 */
@Singleton
class FileUserTemplateRepository internal constructor(
    private val file: File,
    private val io: CoroutineDispatcher,
) : UserTemplateRepository {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this(File(context.filesDir, FILE_NAME), io)

    /** null 이면 아직 파일을 읽지 않았다. */
    private val loaded = MutableStateFlow<List<RoutineTemplates.Template>?>(null)
    private val lock = Mutex()

    override val templates: Flow<List<RoutineTemplates.Template>> =
        loaded.filterNotNull().onStart { current() }

    override suspend fun current(): List<RoutineTemplates.Template> =
        loaded.value ?: lock.withLock { loadLocked() }

    override suspend fun save(templates: List<RoutineTemplates.Template>) {
        lock.withLock { write(UserTemplateRepository.merge(loadLocked(), templates)) }
    }

    override suspend fun delete(id: String) {
        lock.withLock {
            val list = loadLocked()
            if (list.any { it.id == id }) write(list.filterNot { it.id == id })
        }
    }

    override suspend fun replaceAll(templates: List<RoutineTemplates.Template>) {
        lock.withLock {
            loadLocked()
            write(templates)
        }
    }

    private suspend fun loadLocked(): List<RoutineTemplates.Template> {
        loaded.value?.let { return it }
        val list = withContext(io) { readFile() }
        loaded.value = list
        return list
    }

    private fun readFile(): List<RoutineTemplates.Template> {
        if (!file.exists()) return emptyList()
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            return emptyList()
        }
        // 이미 받아들여 저장한 글이라, 가져올 때의 까다로운 규칙(템플릿 사이의 이름 겹침 · 글자 규칙)은 다시 따지지 않는다.
        // 그러지 않으면 옛 버전이나 경고만 내고 받아들인 템플릿 때문에 저장해 둔 템플릿이 통째로 읽히지 않는다.
        val report = CatalogReader.read(text, ImportContext.STORED)
        if (report.isValid) return report.templates
        keepBrokenCopy(text)
        return emptyList()
    }

    private suspend fun write(list: List<RoutineTemplates.Template>) {
        if (list.size > UserTemplateRepository.MAX_TEMPLATES) {
            throw TemplateStorageFullException(TemplateStorageFullException.TOO_MANY)
        }
        // 글을 만드는 일(최대 512KB)도 입출력 스레드에서 한다.
        withContext(io) {
            if (list.isEmpty()) {
                // 내용이 없는 파일은 읽을 수 없는 파일로 보이므로, 파일 자체를 지운다.
                Files.deleteIfExists(file.toPath())
            } else {
                val text = CatalogWriter.write(list)
                if (text.toByteArray(Charsets.UTF_8).size > UserTemplateRepository.MAX_BYTES) {
                    throw TemplateStorageFullException(TemplateStorageFullException.TOO_LARGE)
                }
                file.parentFile?.mkdirs()
                val temp = File(file.path + TEMP_SUFFIX)
                temp.writeText(text, Charsets.UTF_8)
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        loaded.value = list
    }

    /**
     * 읽을 수 없는 파일을 다음에 쓸 때 덮어쓰면 되살릴 길이 없어지므로 옆에 남겨 둔다.
     * 같은 내용은 한 번만 남기고(실행할 때마다 늘어나지 않게), 먼저 남긴 것은 덮어쓰지 않으며, 다섯 개까지만 둔다.
     */
    private fun keepBrokenCopy(text: String) {
        runCatching {
            val dir = file.absoluteFile.parentFile ?: return
            val prefix = file.name + BROKEN_SUFFIX
            val existing = dir.listFiles { _, name -> name.startsWith(prefix) }.orEmpty()
            if (existing.size >= MAX_BROKEN_COPIES) return
            if (existing.any { runCatching { it.readText(Charsets.UTF_8) == text }.getOrDefault(false) }) return
            // .broken, .broken.1, .broken.2 … 처럼 비어 있는 첫 이름에 남긴다(시계에 기대지 않아 같은 순간에도 겹치지 않는다).
            var number = 0
            var target: File
            do {
                target = File(dir, if (number == 0) prefix else "$prefix.$number")
                number++
            } while (target.exists())
            target.writeText(text, Charsets.UTF_8)
        }
    }

    companion object {
        const val FILE_NAME = "user_templates.json"
        private const val TEMP_SUFFIX = ".tmp"
        const val BROKEN_SUFFIX = ".broken"
        private const val MAX_BROKEN_COPIES = 5
    }
}
