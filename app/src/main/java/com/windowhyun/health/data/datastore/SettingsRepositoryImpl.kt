package com.windowhyun.health.data.datastore

import androidx.datastore.core.DataStore
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

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
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

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(current())
        dataStore.edit { prefs ->
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
