package com.windowhyun.health.ui.running

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.repository.RunPersonalBests
import com.windowhyun.health.domain.repository.RunRepository
import com.windowhyun.health.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RunSummaryUiState(
    val run: Run? = null,
    val personalBests: RunPersonalBests = RunPersonalBests(),
    val settings: AppSettings = AppSettings(),
    val memo: String = "",
    val closed: Boolean = false,
    val loading: Boolean = true,
)

/**
 * 러닝 결과 화면.
 *
 * 기록은 이미 DB 에 들어 있다(기록 중 증분 저장). 여기서 "저장"은 메모를 붙여
 * 마무리하는 것이고, "삭제"를 고르면 행을 지운다.
 */
@HiltViewModel
class RunSummaryViewModel @Inject constructor(
    private val runRepository: RunRepository,
    private val runTracker: RunTracker,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val loaded = MutableStateFlow(RunSummaryUiState())

    val uiState: StateFlow<RunSummaryUiState> = combine(
        loaded,
        settingsRepository.settings,
    ) { state, settings ->
        state.copy(settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RunSummaryUiState())

    init {
        viewModelScope.launch {
            // 방금 끝낸 러닝의 id 는 추적기가 들고 있다.
            // 앱이 재시작된 경우를 대비해 미완료 러닝도 확인한다.
            val runId = runTracker.state.value.runId
            val run = if (runId != 0L) runRepository.getRun(runId) else runRepository.getActiveRun()
            if (run == null) {
                loaded.update { it.copy(loading = false, closed = true) }
                return@launch
            }
            loaded.update {
                it.copy(
                    run = run,
                    personalBests = runRepository.comparePersonalBests(run),
                    memo = run.memo.orEmpty(),
                    loading = false,
                )
            }
        }
    }

    fun setMemo(memo: String) = loaded.update { it.copy(memo = memo) }

    /** 메모를 붙여 저장을 마무리하고 추적 상태를 비운다. */
    fun save() {
        viewModelScope.launch {
            val run = loaded.value.run ?: return@launch
            runRepository.updateMemo(run.id, loaded.value.memo)
            runTracker.reset()
            loaded.update { it.copy(closed = true) }
        }
    }

    /** 기록을 버린다. */
    fun discard() {
        viewModelScope.launch {
            runTracker.discard()
            loaded.update { it.copy(closed = true) }
        }
    }
}
