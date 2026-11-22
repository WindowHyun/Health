package com.windowhyun.health.data

import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 설정 저장소가 필요하지만 DataStore 까지는 필요 없는 테스트용. */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings = state.asStateFlow()

    override suspend fun current(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}
