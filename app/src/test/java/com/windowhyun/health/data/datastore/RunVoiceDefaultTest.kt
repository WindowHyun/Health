package com.windowhyun.health.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** 러닝 음성 안내의 기본값: 새로 설치하면 켜지만, 이 설정이 생기기 전부터 쓰던 사람에게는 소리가 갑자기 나지 않는다. */
@RunWith(RobolectricTestRunner::class)
class RunVoiceDefaultTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun dataStore() = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) {
        File(context.cacheDir, "voice-default-${System.nanoTime()}.preferences_pb")
    }

    @Test
    fun `a fresh install has the voice cues on`() = runBlocking<Unit> {
        assertThat(SettingsRepositoryImpl(dataStore()).current().runVoiceCues).isTrue()
    }

    @Test
    fun `someone who already had settings but no voice choice does not get sound after updating`() = runBlocking<Unit> {
        val store = dataStore()
        store.edit { it[intPreferencesKey("default_rest_seconds")] = 90 }

        assertThat(SettingsRepositoryImpl(store).current().runVoiceCues).isFalse()
    }

    @Test
    fun `an explicit choice is always kept`() = runBlocking<Unit> {
        val on = dataStore().also { it.edit { p -> p[booleanPreferencesKey("run_voice_cues")] = true; p[intPreferencesKey("default_rest_seconds")] = 90 } }
        assertThat(SettingsRepositoryImpl(on).current().runVoiceCues).isTrue()
    }
}
