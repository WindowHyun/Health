package com.windowhyun.health.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.windowhyun.health.di.IoDispatcher
import com.windowhyun.health.domain.repository.BackupFolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** 파일 선택기에서 고른 폴더(SAF 트리)에 쓰는 [BackupFolder]. 저장소 권한이 필요 없다. */
@Singleton
class SafBackupFolder @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BackupFolder {

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun write(
        folderUri: String,
        fileName: String,
        writer: suspend (OutputStream) -> Unit,
    ) = withContext(ioDispatcher) {
        accessing {
            val tree = Uri.parse(folderUri)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val document = DocumentsContract.createDocument(resolver, parent, MIME_JSON, fileName)
                ?: throw IOException("백업 파일을 만들 수 없습니다. 폴더를 다시 골라 주세요.")
            try {
                val stream = resolver.openOutputStream(document, "wt")
                    ?: throw IOException("백업 파일을 열 수 없습니다.")
                stream.use { writer(it) }
            } catch (e: Throwable) {
                // 반쯤 쓴 파일이 남으면 그것도 백업인 줄 알고 좋은 백업을 밀어낼 수 있다.
                runCatching { DocumentsContract.deleteDocument(resolver, document) }
                throw e
            }
        }
    }

    override suspend fun listNames(folderUri: String): List<String> = withContext(ioDispatcher) {
        accessing { children(Uri.parse(folderUri)).map { it.second } }
    }

    override suspend fun delete(folderUri: String, fileName: String) = withContext(ioDispatcher) {
        accessing {
            val tree = Uri.parse(folderUri)
            children(tree).filter { it.second == fileName }.forEach { (documentId, _) ->
                DocumentsContract.deleteDocument(
                    resolver,
                    DocumentsContract.buildDocumentUriUsingTree(tree, documentId),
                )
            }
        }
    }

    /** (문서 id, 표시 이름) 목록. */
    private fun children(tree: Uri): List<Pair<String, String>> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        val result = mutableListOf<Pair<String, String>>()
        resolver.query(childrenUri, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) result += cursor.getString(0) to cursor.getString(1)
        }
        return result
    }

    /** 폴더 권한이 사라졌거나 폴더가 지워진 경우를 사용자가 알아볼 문장으로 바꾼다. */
    private inline fun <T> accessing(block: () -> T): T = try {
        block()
    } catch (e: SecurityException) {
        throw IOException("폴더에 접근할 수 없습니다. 폴더를 다시 골라 주세요.", e)
    } catch (e: IllegalArgumentException) {
        throw IOException("폴더를 찾을 수 없습니다. 폴더를 다시 골라 주세요.", e)
    }

    private companion object {
        const val MIME_JSON = "application/json"
    }
}
