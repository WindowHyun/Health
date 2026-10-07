package com.windowhyun.health.data.healthconnect

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.windowhyun.health.domain.model.HealthConnectLedger
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 보낸 기록의 장부를 설정 저장소(DataStore)에 적는다. 기기마다 다른 값이라 백업에는 넣지 않는다.
 * 항목은 "이름표|내용 값" 한 줄이다. 이름표에는 '|' 가 들어가지 않는다.
 */
@Singleton
class DataStoreHealthConnectLedger @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : HealthConnectLedger {

    override suspend fun read(): Map<String, String> =
        dataStore.data.first()[KEY].orEmpty()
            .mapNotNull { line ->
                val at = line.indexOf(SEPARATOR)
                if (at <= 0) null else line.substring(0, at) to line.substring(at + 1)
            }
            .toMap()

    override suspend fun write(entries: Map<String, String>) {
        dataStore.edit { prefs ->
            prefs[KEY] = entries.mapTo(HashSet()) { (id, fingerprint) -> "$id$SEPARATOR$fingerprint" }
        }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("health_connect_ledger")
        const val SEPARATOR = '|'
    }
}
