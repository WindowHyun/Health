package com.windowhyun.health.ui.running

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.repository.RunRepository
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RunDetailUiState(
    val run: Run? = null,
    val settings: AppSettings = AppSettings(),
    val memo: String = "",
    /** 저장하지 않은 메모가 있는지. 뒤로 가기 전에 자동 저장할지 판단한다. */
    val memoDirty: Boolean = false,
    val editingMemo: Boolean = false,
    val loading: Boolean = true,
    val deleted: Boolean = false,
)

/**
 * 저장된 러닝 1건.
 *
 * 러닝 결과 화면과 달리 여기서는 기록이 이미 확정된 상태라, 메모 수정과 삭제만 할 수 있다.
 */
@HiltViewModel
class RunDetailViewModel @Inject constructor(
    private val runRepository: RunRepository,
    settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val runId: Long = savedStateHandle[Routes.ARG_RUN_ID] ?: 0L

    private val local = MutableStateFlow(RunDetailUiState())

    val uiState: StateFlow<RunDetailUiState> = combine(
        local,
        settingsRepository.settings,
    ) { state, settings ->
        state.copy(settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RunDetailUiState())

    init {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val run = runRepository.getRun(runId)
        local.update {
            it.copy(
                run = run,
                memo = run?.memo.orEmpty(),
                memoDirty = false,
                loading = false,
                // 기록이 사라졌다면(다른 화면에서 삭제) 머무를 이유가 없다.
                deleted = run == null,
            )
        }
    }

    fun startEditingMemo() = local.update { it.copy(editingMemo = true) }

    fun setMemo(memo: String) = local.update { it.copy(memo = memo, memoDirty = true) }

    fun saveMemo() {
        val state = local.value
        val run = state.run ?: return
        if (!state.memoDirty) {
            local.update { it.copy(editingMemo = false) }
            return
        }
        viewModelScope.launch {
            val memo = state.memo.takeIf { it.isNotBlank() }
            runRepository.updateMemo(run.id, memo)
            local.update {
                it.copy(run = run.copy(memo = memo), memoDirty = false, editingMemo = false)
            }
        }
    }

    /** 화면을 벗어날 때 쓰던 메모를 잃지 않도록 조용히 저장한다. */
    fun saveMemoIfNeeded() {
        if (local.value.memoDirty) saveMemo()
    }

    fun delete() {
        val run = local.value.run ?: return
        viewModelScope.launch {
            runRepository.deleteRun(run.id)
            local.update { it.copy(deleted = true) }
        }
    }
}
