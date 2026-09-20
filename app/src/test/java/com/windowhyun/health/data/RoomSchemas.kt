package com.windowhyun.health.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import java.io.File

/**
 * Room 이 `app/schemas` 로 내보낸 스키마 JSON 을 읽어, 그 버전의 빈 DB 를 만들어 준다.
 *
 * `MigrationTestHelper` 는 스키마를 **에셋**에서 찾는데, Robolectric 단위 테스트는
 * main 소스셋의 머지된 에셋만 보기 때문에 테스트용 스키마를 APK 에 넣어야 한다.
 * 실행할 때 필요 없는 파일을 앱에 넣고 싶지 않아서, 파일을 직접 읽는 쪽을 택했다.
 *
 * 이렇게 만든 옛 버전 DB 를 실제 Room 으로 열면 Room 이 직접 마이그레이션을 실행하고
 * 결과 스키마까지 검증하므로, 앱이 업데이트될 때와 같은 경로를 그대로 확인할 수 있다.
 */
object RoomSchemas {

    private const val DATABASE_NAME = "com.windowhyun.health.data.local.HealthDatabase"

    /** 테스트 작업 디렉터리가 모듈 루트일 수도, 저장소 루트일 수도 있어 둘 다 본다. */
    private fun schemaFile(version: Int): File {
        val candidates = listOf(
            File("schemas/$DATABASE_NAME/$version.json"),
            File("app/schemas/$DATABASE_NAME/$version.json"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error(
                "스키마 파일을 찾지 못했습니다: $version.json\n" +
                    "확인한 경로: ${candidates.joinToString { it.absolutePath }}",
            )
    }

    private fun database(version: Int): JSONObject =
        JSONObject(schemaFile(version).readText()).getJSONObject("database")

    /** 해당 버전의 테이블/인덱스를 만들고, Room 이 인식할 수 있도록 버전과 해시까지 기록한다. */
    fun createVersion(db: SupportSQLiteDatabase, version: Int) {
        val database = database(version)

        val entities = database.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))

            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                db.execSQL(
                    indices.getJSONObject(j).getString("createSql")
                        .replace("\${TABLE_NAME}", tableName),
                )
            }
        }

        // Room 은 이 테이블의 해시로 스키마가 맞는지 확인한다. 없으면 열 때 거부한다.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS room_master_table " +
                "(id INTEGER PRIMARY KEY, identity_hash TEXT)",
        )
        db.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
            arrayOf(database.getString("identityHash")),
        )
        db.execSQL("PRAGMA user_version = $version")
    }
}
