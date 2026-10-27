package com.windowhyun.health.data.healthconnect

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.windowhyun.health.domain.model.HealthConnectLedger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 보낸 기록의 장부를 설정 저장소(DataStore)에 적는다. 항목은 "이름표|내용 값" 한 줄이다. 이름표에는 '|' 가 들어가지 않는다.
 *
 * 장부는 **이 기기의 Health Connect** 에 무엇을 보냈는지 적은 것이라 기기마다 다른 값이다. 그런데 설정 저장소는
 * 자동 백업 · 기기 이전에 들어가서, 새 폰으로 옮기면 장부만 따라와 "이미 보냈다"고 믿는다(새 폰의 Health Connect 에는 없는데).
 * 그래서 장부에 **이 기기의 표식**을 함께 적고, 표식이 다르면(= 다른 기기에서 온 장부면) 없는 것으로 본다.
 * 표식 파일은 백업에서 빠지는 곳(noBackupFilesDir)에 둔다.
 */
@Singleton
class DataStoreHealthConnectLedger internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val deviceToken: () -> String,
) : HealthConnectLedger {

    @Inject
    constructor(
        dataStore: DataStore<Preferences>,
        @ApplicationContext context: Context,
    ) : this(dataStore, fileToken(File(context.noBackupFilesDir, TOKEN_FILE)))

    override suspend fun read(): Map<String, String> {
        val prefs = dataStore.data.first()
        if (prefs[OWNER_KEY] != deviceToken()) return emptyMap()
        return prefs[KEY].orEmpty()
            .mapNotNull { line ->
                val at = line.indexOf(SEPARATOR)
                if (at <= 0) null else line.substring(0, at) to line.substring(at + 1)
            }
            .toMap()
    }

    override suspend fun write(entries: Map<String, String>) {
        val token = deviceToken()
        dataStore.edit { prefs ->
            prefs[OWNER_KEY] = token
            prefs[KEY] = entries.mapTo(HashSet()) { (id, fingerprint) -> "$id$SEPARATOR$fingerprint" }
        }
    }

    internal companion object {
        val KEY = stringSetPreferencesKey("health_connect_ledger")
        val OWNER_KEY = stringPreferencesKey("health_connect_ledger_owner")
        const val SEPARATOR = '|'
        const val TOKEN_FILE = "health_connect_ledger_owner"

        /** 파일에 적힌 표식을 쓰고, 없으면 새로 만들어 적는다. */
        fun fileToken(file: File): () -> String {
            val token by lazy {
                runCatching { file.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()
                    ?: UUID.randomUUID().toString().also { created ->
                        runCatching {
                            file.parentFile?.mkdirs()
                            file.writeText(created)
                        }
                    }
            }
            return { token }
        }
    }
}
