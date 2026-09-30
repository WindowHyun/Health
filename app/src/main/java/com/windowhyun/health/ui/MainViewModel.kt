package com.windowhyun.health.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 테마처럼 앱 전체에 영향을 주는 설정과, 앱을 켤 때 한 번 할 일을 담당한다. */
@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    runTracker: RunTracker,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    init {
        // 러닝 중 앱이 강제로 종료됐으면 그 러닝이 어디에도 보이지 않는다. 켤 때 마감해 살린다.
        viewModelScope.launch { runTracker.recoverUnfinishedRuns() }
    }
}
