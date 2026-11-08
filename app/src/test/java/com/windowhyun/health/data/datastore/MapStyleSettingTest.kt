package com.windowhyun.health.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.domain.model.MapStyleMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** 러닝 지도 방식 설정: 기본은 예전 지도이고, 고른 값은 저장되며, 알 수 없는 값은 기본으로 돌아간다. */
@RunWith(RobolectricTestRunner::class)
class MapStyleSettingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun dataStore() = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) {
        File(context.cacheDir, "map-style-${System.nanoTime()}.preferences_pb")
    }

    @Test
    fun `a fresh install keeps the classic map`() = runBlocking<Unit> {
        assertThat(SettingsRepositoryImpl(dataStore()).current().mapStyle).isEqualTo(MapStyleMode.CLASSIC)
    }

    @Test
    fun `the chosen map is saved and read back`() = runBlocking<Unit> {
        val repo = SettingsRepositoryImpl(dataStore())
        repo.update { it.copy(mapStyle = MapStyleMode.MODERN) }
        assertThat(repo.current().mapStyle).isEqualTo(MapStyleMode.MODERN)
        repo.update { it.copy(mapStyle = MapStyleMode.CLASSIC) }
        assertThat(repo.current().mapStyle).isEqualTo(MapStyleMode.CLASSIC)
    }

    @Test
    fun `an unknown stored value falls back to the classic map`() = runBlocking<Unit> {
        val store = dataStore()
        store.edit { it[stringPreferencesKey("map_style")] = "FUTURE_STYLE" }
        assertThat(SettingsRepositoryImpl(store).current().mapStyle).isEqualTo(MapStyleMode.CLASSIC)
    }
}
