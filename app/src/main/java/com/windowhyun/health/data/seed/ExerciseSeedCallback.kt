package com.windowhyun.health.data.seed

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * DB 가 처음 만들어질 때 기본 운동 종목을 넣는다.
 *
 * 생성 트랜잭션 안에서 동기적으로 넣으므로, 첫 조회 시점에는
 * 이미 종목이 들어 있는 상태가 보장된다.
 */
object ExerciseSeedCallback : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        DefaultExercises.all.forEach { exercise ->
            db.execSQL(
                "INSERT INTO exercise " +
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
    }
}
