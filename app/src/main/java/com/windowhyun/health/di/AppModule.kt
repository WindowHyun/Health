package com.windowhyun.health.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val SETTINGS_DATASTORE = "health_settings"

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /** 앱 수명과 함께 사는 스코프. DB 시드 같은 백그라운드 작업에 사용한다. */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(@IoDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = createSettingsDataStore(scope) { context.preferencesDataStoreFile(SETTINGS_DATASTORE) }
}

/**
 * 설정 저장소. 파일이 깨져 있어도 앱이 죽지 않게, 읽다가 손상을 만나면 기본 설정으로 시작한다.
 *
 * 이 파일은 클라우드 백업 · 기기 이전에도 들어가서(자동 백업 규칙), 깨진 파일이 복원돼 올 수도 있다.
 * 손상 복구 핸들러가 없으면 설정을 읽는 순간 예외가 나 앱을 켤 때마다 죽는다.
 * 깨진 파일은 지우지 않고 옆에 `.corrupt` 로 남겨 둔다(원인을 볼 수 있게).
 */
internal fun createSettingsDataStore(scope: kotlinx.coroutines.CoroutineScope, produceFile: () -> java.io.File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler {
            runCatching {
                val file = produceFile()
                file.copyTo(java.io.File(file.path + ".corrupt"), overwrite = true)
            }
            androidx.datastore.preferences.core.emptyPreferences()
        },
        scope = scope,
        produceFile = produceFile,
    )
