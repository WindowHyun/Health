package com.windowhyun.health.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.data.cloud.CloudBackupService
import com.windowhyun.health.domain.cloud.CloudAccount
import com.windowhyun.health.domain.cloud.CloudBackupInfo
import com.windowhyun.health.domain.cloud.CloudException
import com.windowhyun.health.domain.cloud.CloudUser
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CloudBackupUiState(
    val working: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    /** 불러온 백업 목록(최근 것부터). null 이면 아직 불러오지 않음. */
    val backups: List<CloudBackupInfo>? = null,
)

/** 구글 로그인 + 클라우드 백업 · 복원. 화면은 이 상태만 그린다. */
@HiltViewModel
class CloudBackupViewModel @Inject constructor(
    private val account: CloudAccount,
    private val service: CloudBackupService,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val isAvailable: Boolean get() = account.isAvailable

    val user: StateFlow<CloudUser?> = account.user

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _uiState = MutableStateFlow(CloudBackupUiState())
    val uiState: StateFlow<CloudBackupUiState> = _uiState.asStateFlow()

    fun signIn(activityContext: Context) = run {
        if (account.signIn(activityContext)) "로그인했어요." else null
    }

    fun signOut() {
        account.signOut()
        _uiState.value = CloudBackupUiState()
    }

    fun backupNow() = run {
        val info = service.backupNow()
        refreshList()
        "백업했어요 · 운동 ${info.workoutCount}회 · 러닝 ${info.runCount}회 · 루틴 ${info.routineCount}개"
    }

    fun loadList() = run {
        refreshList()
        null
    }

    fun restore(id: String) = run {
        val summary = service.restore(id)
        refreshList()
        buildString {
            append("복원했어요 · ${summary.describe()}")
            if (summary.droppedRows > 0) append("\n연결이 끊어진 ${summary.droppedRows}개 항목은 건너뛰었어요.")
            append("\n복원 직전 기록은 클라우드에 따로 백업해 뒀어요.")
        }
    }

    fun deleteAll() = run {
        service.deleteAll()
        _uiState.update { it.copy(backups = emptyList()) }
        "클라우드 백업을 모두 지웠어요."
    }

    fun setAutoBackup(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.update { it.copy(cloudAutoBackup = enabled) } }
    }

    private suspend fun refreshList() {
        val list = service.list()
        _uiState.update { it.copy(backups = list) }
    }

    /** 작업 하나를 돌린다. 성공 메시지가 null 이면 메시지를 바꾸지 않는다(취소한 로그인 등). */
    private fun run(block: suspend () -> String?) {
        if (_uiState.value.working) return
        _uiState.update { it.copy(working = true, message = null, error = null) }
        viewModelScope.launch {
            try {
                val message = block()
                _uiState.update { it.copy(message = message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: CloudException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "작업에 실패했어요.") }
            } finally {
                _uiState.update { it.copy(working = false) }
            }
        }
    }
}
