package com.windowhyun.health.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.domain.repository.BackupFormatException
import com.windowhyun.health.domain.repository.BackupRepository
import com.windowhyun.health.domain.repository.BackupSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class BackupUiState(
    /** 파일을 읽고 쓰는 중. 버튼을 잠가 두 번 눌리지 않게 한다. */
    val working: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * 백업 / 복원 / CSV 내보내기.
 *
 * 저장 위치는 안드로이드 파일 선택기(SAF)로 사용자가 고른다. 그래야 저장소
 * 권한을 요구하지 않고도 구글 드라이브 같은 곳에 바로 넣을 수 있다.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    /** 파일 선택기에 미리 채워 넣을 이름. */
    fun backupFileName(): String = "health-backup-${LocalDate.now()}.json"

    fun workoutCsvFileName(): String = "health-workouts-${LocalDate.now()}.csv"

    fun runCsvFileName(): String = "health-runs-${LocalDate.now()}.csv"

    fun exportBackup(uri: Uri) = run("백업") {
        val summary = context.contentResolver.openOutputStream(uri)?.use {
            backupRepository.exportBackup(it)
        } ?: error("파일을 만들 수 없습니다.")
        "백업 완료 · ${summary.describe()}"
    }

    fun restoreBackup(uri: Uri) = run("복원") {
        val summary = context.contentResolver.openInputStream(uri)?.use {
            backupRepository.restoreBackup(it)
        } ?: error("파일을 열 수 없습니다.")
        buildString {
            append("복원 완료 · ${summary.describe()}")
            if (summary.droppedRows > 0) {
                append("\n연결이 끊어진 ${summary.droppedRows}개 항목은 건너뛰었습니다.")
            }
        }
    }

    fun exportWorkoutCsv(uri: Uri) = run("내보내기") {
        val rows = context.contentResolver.openOutputStream(uri)?.use {
            backupRepository.exportWorkoutCsv(it)
        } ?: error("파일을 만들 수 없습니다.")
        "헬스 기록 ${rows}줄을 내보냈습니다."
    }

    fun exportRunCsv(uri: Uri) = run("내보내기") {
        val rows = context.contentResolver.openOutputStream(uri)?.use {
            backupRepository.exportRunCsv(it)
        } ?: error("파일을 만들 수 없습니다.")
        "러닝 기록 ${rows}줄을 내보냈습니다."
    }

    fun clearMessage() = _uiState.update { it.copy(message = null, error = null) }

    /** 공통 실행 틀. 진행 중 표시와 오류 문구를 한곳에서 처리한다. */
    private fun run(action: String, block: suspend () -> String) {
        if (_uiState.value.working) return
        _uiState.update { it.copy(working = true, message = null, error = null) }
        viewModelScope.launch {
            try {
                val message = block()
                _uiState.update { it.copy(working = false, message = message) }
            } catch (e: BackupFormatException) {
                _uiState.update { it.copy(working = false, error = e.message) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        working = false,
                        error = "$action 에 실패했습니다: ${e.message ?: e::class.simpleName}",
                    )
                }
            }
        }
    }
}

private fun BackupSummary.describe(): String =
    "운동 ${workoutCount}회 · 세트 ${setCount}개 · 러닝 ${runCount}회 · 루틴 ${routineCount}개"
