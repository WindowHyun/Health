package com.windowhyun.health.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.ThemeMode
import com.windowhyun.health.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Preferences DataStore 기반 설정 저장소. */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val DEFAULT_REST_SECONDS = intPreferencesKey("default_rest_seconds")
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val REST_AUTO_START = booleanPreferencesKey("rest_timer_auto_start")
        val AUTO_LAP_METERS = intPreferencesKey("auto_lap_meters")
        val BODY_WEIGHT_KG = doublePreferencesKey("body_weight_kg")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HEALTH_CONNECT = booleanPreferencesKey("health_connect_enabled")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }

    override val settings: Flow<AppSettings> = dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings(): AppSettings {
        val prefs = this
        val defaults = AppSettings()
        return AppSettings(
            defaultRestSeconds = prefs[Keys.DEFAULT_REST_SECONDS] ?: defaults.defaultRestSeconds,
            weightUnit = prefs[Keys.WEIGHT_UNIT]?.let { name ->
                runCatching { WeightUnit.valueOf(name) }.getOrNull()
            } ?: defaults.weightUnit,
            distanceUnit = prefs[Keys.DISTANCE_UNIT]?.let { name ->
                runCatching { DistanceUnit.valueOf(name) }.getOrNull()
            } ?: defaults.distanceUnit,
            vibrationEnabled = prefs[Keys.VIBRATION] ?: defaults.vibrationEnabled,
            restTimerAutoStart = prefs[Keys.REST_AUTO_START] ?: defaults.restTimerAutoStart,
            autoLapMeters = prefs[Keys.AUTO_LAP_METERS] ?: defaults.autoLapMeters,
            bodyWeightKg = prefs[Keys.BODY_WEIGHT_KG] ?: defaults.bodyWeightKg,
            themeMode = prefs[Keys.THEME_MODE]?.let { name ->
                runCatching { ThemeMode.valueOf(name) }.getOrNull()
            } ?: defaults.themeMode,
            healthConnectEnabled = prefs[Keys.HEALTH_CONNECT] ?: defaults.healthConnectEnabled,
            keepScreenOnDuringWorkout = prefs[Keys.KEEP_SCREEN_ON] ?: defaults.keepScreenOnDuringWorkout,
        )
    }

    override suspend fun current(): AppSettings = settings.first()

    /**
     * 읽기와 쓰기를 [DataStore.edit] 안에서 함께 처리한다.
     *
     * 밖에서 읽고 안에서 쓰면, 설정 두 개를 빠르게 연달아 바꿨을 때
     * 나중 것이 먼저 것을 덮어써서 변경이 사라진다(lost update).
     * edit 블록은 직렬화되므로 안에서 읽으면 항상 최신 값을 본다.
     */
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[Keys.DEFAULT_REST_SECONDS] = updated.defaultRestSeconds
            prefs[Keys.WEIGHT_UNIT] = updated.weightUnit.name
            prefs[Keys.DISTANCE_UNIT] = updated.distanceUnit.name
            prefs[Keys.VIBRATION] = updated.vibrationEnabled
            prefs[Keys.REST_AUTO_START] = updated.restTimerAutoStart
            prefs[Keys.AUTO_LAP_METERS] = updated.autoLapMeters
            prefs[Keys.BODY_WEIGHT_KG] = updated.bodyWeightKg
            prefs[Keys.THEME_MODE] = updated.themeMode.name
            prefs[Keys.HEALTH_CONNECT] = updated.healthConnectEnabled
            prefs[Keys.KEEP_SCREEN_ON] = updated.keepScreenOnDuringWorkout
        }
    }
}
