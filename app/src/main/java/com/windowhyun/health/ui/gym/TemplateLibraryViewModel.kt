package com.windowhyun.health.ui.gym

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.data.catalog.ImportPreview
import com.windowhyun.health.data.catalog.RoutineExport
import com.windowhyun.health.data.catalog.TemplateImporter
import com.windowhyun.health.data.catalog.TemplateStorageFullException
import com.windowhyun.health.data.catalog.UserTemplateRepository
import com.windowhyun.health.di.IoDispatcher
import com.windowhyun.health.domain.repository.RoutineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

data class TemplateLibraryState(
    /** 사용자가 가져온 템플릿. */
    val userTemplates: List<RoutineTemplates.Template> = emptyList(),
    /** 파일을 읽거나 쓰는 중. 버튼을 잠가 두 번 눌리지 않게 한다. */
    val working: Boolean = false,
    /** 검사를 마치고 사용자의 확인을 기다리는 결과. */
    val preview: ImportPreview? = null,
    /** 마지막 작업의 결과 안내. */
    val message: String? = null,
)

/**
 * 템플릿 가져오기 · 내보내기와 가져온 템플릿 관리.
 *
 * 파일은 안드로이드 파일 선택기(SAF)로 사용자가 고른다. 저장소 권한이 필요 없고, 앱은 사용자가 고른
 * 그 파일 하나만 읽는다. 읽은 글은 [TemplateImporter] 가 검사해서 결과를 보여 주고, 사용자가 확인해야 저장한다.
 */
@HiltViewModel
class TemplateLibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: TemplateImporter,
    private val userTemplates: UserTemplateRepository,
    private val routineRepository: RoutineRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private data class Transient(
        val working: Boolean = false,
        val preview: ImportPreview? = null,
        val message: String? = null,
    )

    private val transient = MutableStateFlow(Transient())

    val state: StateFlow<TemplateLibraryState> =
        combine(userTemplates.templates, transient) { list, t ->
            TemplateLibraryState(list, t.working, t.preview, t.message)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplateLibraryState())

    /** 내보낼 파일이 준비됐다. 화면이 파일 선택기를 열 때 쓸 기본 이름을 담는다. */
    private val _exportReady = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val exportReady: SharedFlow<String> = _exportReady.asSharedFlow()

    private var pendingExport: RoutineExport.Result? = null

    /**
     * 시트를 닫을 때마다 올린다. 파일을 읽는 도중에 시트를 닫으면, 나중에 끝난 결과가 다음에 시트를 열 때 갑자기
     * 나타나지 않게 이 값이 달라졌는지로 가려낸다.
     */
    private var epoch = 0

    // ----- 가져오기 -----

    /** 사용자가 고른 파일을 읽어 검사한다. */
    fun importFromUri(uri: Uri) {
        val token = epoch
        work {
            val stream = try {
                withContext(io) { context.contentResolver.openInputStream(uri) }
            } catch (e: Exception) {
                null
            }
            if (stream == null) {
                say("파일을 열 수 없습니다.", token)
                return@work
            }
            try {
                showPreview(importer.analyzeStream(stream), token)
            } catch (e: IOException) {
                say("파일을 읽는 중 문제가 생겼습니다.", token)
            }
        }
    }

    /** 붙여넣은 글을 검사한다. */
    fun importFromText(text: String) {
        val token = epoch
        work { showPreview(importer.analyzeText(text), token) }
    }

    private fun showPreview(preview: ImportPreview, token: Int) {
        if (token != epoch) return
        transient.update { it.copy(preview = preview, message = null) }
    }

    /** 확인 대기 중인 결과를 기기에 넣는다. 오류가 있는 결과는 넣지 않는다. */
    fun confirmImport() {
        val preview = transient.value.preview ?: return
        if (!preview.canImport) return
        val token = epoch
        work {
            val message = try {
                val result = importer.commit(preview)
                buildString {
                    append("가져왔습니다")
                    val parts = buildList {
                        if (result.importedTemplates > 0) add("템플릿 ${result.importedTemplates}개(루틴 ${result.importedRoutines}개)")
                        if (result.addedExercises > 0) add("새 종목 ${result.addedExercises}개")
                    }
                    if (parts.isNotEmpty()) append(" · ${parts.joinToString(", ")}")
                    if (result.replacedTemplates > 0) append(" · ${result.replacedTemplates}개는 새 내용으로 바꿨습니다")
                }
            } catch (e: TemplateStorageFullException) {
                e.message.orEmpty()
            } catch (e: IllegalArgumentException) {
                "가져올 수 없는 내용입니다."
            }
            if (token == epoch) transient.update { it.copy(preview = null, message = message) }
        }
    }

    fun dismissPreview() = transient.update { it.copy(preview = null) }

    fun clearMessage() = transient.update { it.copy(message = null) }

    /** 시트를 닫았다. 확인을 기다리던 결과와 안내를 치우고, 아직 끝나지 않은 검사의 결과는 나중에 와도 버린다. */
    fun sheetClosed() {
        epoch++
        transient.update { it.copy(preview = null, message = null) }
    }

    fun deleteUserTemplate(id: String) {
        viewModelScope.launch {
            try {
                val title = userTemplates.current().firstOrNull { it.id == id }?.title ?: return@launch
                userTemplates.delete(id)
                say("'$title' 템플릿을 지웠습니다. 이미 만든 루틴은 그대로 남습니다.")
            } catch (e: IOException) {
                Log.w(TAG, "템플릿을 지우지 못했습니다", e)
                say(FAILED)
            }
        }
    }

    // ----- 내보내기 -----

    /** 내보낼 루틴을 정리한다. 내보낼 것이 있을 때만 파일 선택기를 열게 해서, 빈 파일이 남지 않게 한다. */
    fun prepareExport() = work {
        val routines = routineRepository.observeRoutines().first()
        val result = withContext(io) { RoutineExport.build(routines, LocalDate.now()) }
        if (result.exportedRoutines == 0) {
            say(
                if (routines.isEmpty()) "내보낼 루틴이 없습니다."
                else "내보낼 수 있는 루틴이 없습니다. (${result.skipped.joinToString(", ")} 은(는) 파일 형식에 맞지 않습니다)",
            )
            return@work
        }
        pendingExport = result
        _exportReady.emit("health-routines-${LocalDate.now()}.json")
    }

    fun writeExport(uri: Uri) = work {
        val result = pendingExport ?: return@work
        pendingExport = null
        val written = try {
            withContext(io) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(result.json.toByteArray(Charsets.UTF_8)) } != null
            }
        } catch (e: Exception) {
            false
        }
        if (!written) {
            say("파일을 만들 수 없습니다.")
            return@work
        }
        say(
            buildString {
                append("루틴 ${result.exportedRoutines}개를 내보냈습니다")
                if (result.customExercises > 0) append(" (직접 만든 종목 ${result.customExercises}개 포함)")
                if (result.skipped.isNotEmpty()) append(" · 파일 형식에 맞지 않아 뺀 루틴: ${result.skipped.joinToString(", ")}")
            },
        )
    }

    /** 내보내기를 취소했다(파일 선택기를 그냥 닫음). */
    fun cancelExport() {
        pendingExport = null
    }

    // ----- 공통 -----

    /** 안내를 띄운다. [token] 을 주면 그사이 시트가 닫혔을 때는 띄우지 않는다. */
    private fun say(text: String, token: Int = epoch) {
        if (token == epoch) transient.update { it.copy(message = text) }
    }

    /**
     * 같은 작업이 겹치지 않게 막고, 하는 동안 [TemplateLibraryState.working] 을 켠다.
     * 파일을 쓰다 저장 공간이 모자라는 것처럼 예상 밖의 실패는 앱을 끝내지 않고 안내로 바꾼다.
     */
    private fun work(block: suspend () -> Unit) {
        if (transient.value.working) return
        transient.update { it.copy(working = true) }
        // 시작할 때의 시트에 대한 일이다. 그사이 시트를 닫았다면 실패 안내도 띄우지 않는다.
        val token = epoch
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "템플릿 작업에 실패했습니다", e)
                say(FAILED, token)
            } finally {
                transient.update { it.copy(working = false) }
            }
        }
    }

    private companion object {
        const val TAG = "TemplateLibrary"
        const val FAILED = "처리하지 못했습니다. 저장 공간을 확인한 뒤 다시 시도해 주세요."
    }
}
