package com.windowhyun.health.data.backup

import com.windowhyun.health.domain.repository.BackupFolder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/** 메모리에 파일을 쌓아 두는 폴더. 실제 SAF 대신 자동 백업 규칙을 검증하는 데 쓴다. */
class FakeBackupFolder : BackupFolder {
    /** 폴더 주소 -> (파일 이름 -> 내용) */
    val folders = mutableMapOf<String, MutableMap<String, ByteArray>>()

    var failWrites: String? = null
    var failList: Boolean = false
    var beforeWrite: (suspend () -> Unit)? = null
    val deleted = mutableListOf<String>()

    fun files(folderUri: String): Map<String, ByteArray> = folders[folderUri].orEmpty()

    fun put(folderUri: String, name: String, content: String = "x") {
        folders.getOrPut(folderUri) { mutableMapOf() }[name] = content.toByteArray()
    }

    override suspend fun write(folderUri: String, fileName: String, writer: suspend (OutputStream) -> Unit) {
        beforeWrite?.invoke()
        failWrites?.let { throw IOException(it) }
        val out = ByteArrayOutputStream()
        writer(out)
        put(folderUri, fileName, "")
        folders.getValue(folderUri)[fileName] = out.toByteArray()
    }

    override suspend fun listNames(folderUri: String): List<String> {
        if (failList) throw IOException("목록을 읽을 수 없습니다")
        return files(folderUri).keys.toList()
    }

    override suspend fun delete(folderUri: String, fileName: String) {
        deleted += fileName
        folders[folderUri]?.remove(fileName)
    }
}
