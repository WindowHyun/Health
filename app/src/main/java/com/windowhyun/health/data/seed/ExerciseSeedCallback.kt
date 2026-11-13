package com.windowhyun.health.data.seed

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 빠진 기본 종목을 채우는 일. DB 를 열 때와 백업을 복원한 직후에 같은 것을 쓴다.
 *
 * 복원은 종목 표를 백업 내용으로 통째로 바꾸는데, [ExerciseSeedCallback] 은 DB 를 열 때만 돈다.
 * 옛 버전에서 만든 백업을 복원하면 그 뒤에 추가된 기본 종목이 앱을 다시 켤 때까지 빠져 있게 된다.
 */
fun interface BuiltInExerciseSeeder {
    fun seed(db: SupportSQLiteDatabase)

    companion object {
        /** 아무것도 채우지 않는다. 기본 종목 없이 DB 를 만드는 테스트에서 쓴다. */
        val None = BuiltInExerciseSeeder { }
    }
}

/**
 * DB 를 열 때마다 기본 운동 종목 중 **빠진 것만** 채운다.
 *
 * 처음 만들 때는 전부 들어가고, 앱을 업데이트해 [DefaultExercises] 가 늘면 이미 쓰던 기기에도
 * 새 종목만 더해진다. 있는 행은 건드리지 않으므로 기록이 가리키는 id 도 그대로다.
 * 사용자가 같은 이름의 종목을 직접 만들어 두었다면 그 종목을 그대로 두고 건너뛴다(이름은 유일해야 한다).
 *
 * 열리는 동안 동기적으로 실행되어, 첫 조회 시점에는 종목이 이미 들어 있는 상태가 보장된다.
 */
object ExerciseSeedCallback : RoomDatabase.Callback() {

    override fun onOpen(db: SupportSQLiteDatabase) {
        insertMissing(db)
    }

    internal fun insertMissing(db: SupportSQLiteDatabase) {
        val existing = db.query("SELECT name FROM exercise").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        val missing = DefaultExercises.all.filter { it.name !in existing }
        if (missing.isEmpty()) return

        db.beginTransaction()
        try {
            missing.forEach { exercise ->
                db.execSQL(
                    "INSERT OR IGNORE INTO exercise " +
                        "(name, category, bodyPart, isBuiltIn, defaultRestSeconds, trackingType) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf(
                        exercise.name,
                        exercise.category.name,
                        exercise.bodyPart.name,
                        if (exercise.isBuiltIn) 1 else 0,
                        exercise.defaultRestSeconds,
                        exercise.trackingType.name,
                    ),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
