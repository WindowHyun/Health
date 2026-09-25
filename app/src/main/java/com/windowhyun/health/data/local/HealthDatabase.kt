package com.windowhyun.health.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.windowhyun.health.data.local.dao.BackupDao
import com.windowhyun.health.data.local.dao.ExerciseDao
import com.windowhyun.health.data.local.dao.PersonalRecordDao
import com.windowhyun.health.data.local.dao.RoutineDao
import com.windowhyun.health.data.local.dao.RunDao
import com.windowhyun.health.data.local.dao.WorkoutDao
import com.windowhyun.health.data.local.entity.ExerciseEntity
import com.windowhyun.health.data.local.entity.PersonalRecordEntity
import com.windowhyun.health.data.local.entity.RoutineEntity
import com.windowhyun.health.data.local.entity.RoutineExerciseEntity
import com.windowhyun.health.data.local.entity.RunEntity
import com.windowhyun.health.data.local.entity.RunLapEntity
import com.windowhyun.health.data.local.entity.RunLocationEntity
import com.windowhyun.health.data.local.entity.WorkoutEntity
import com.windowhyun.health.data.local.entity.WorkoutExerciseEntity
import com.windowhyun.health.data.local.entity.WorkoutSetEntity

/**
 * 앱 전체 로컬 DB. 서버가 없으므로 이 DB 가 단일 진실 공급원이다.
 *
 * 러닝 관련 테이블은 Phase 2 에서 사용하지만 스키마를 v1 에 함께 넣어
 * 불필요한 마이그레이션을 피한다.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
        PersonalRecordEntity::class,
        RunEntity::class,
        RunLapEntity::class,
        RunLocationEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun runDao(): RunDao
    abstract fun personalRecordDao(): PersonalRecordDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "health.db"

        /**
         * v2: 러닝에 걸음 수 컬럼 추가.
         *
         * 이미 저장된 러닝은 걸음 수를 알 수 없으므로 0 으로 둔다.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE run ADD COLUMN steps INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v3: 세트 종류(워밍업 등)와 시간 기록, 운동의 기록 방식 추가.
         *
         * 기존 세트는 전부 본세트로 본다. 기본 제공 종목 중 시간·횟수로 재는 것들은
         * 이름으로 찾아 기록 방식을 맞춰 준다(시드는 최초 1회만 돌기 때문에
         * 이미 설치된 기기에는 적용되지 않는다).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE workout_set ADD COLUMN durationSeconds INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE workout_set ADD COLUMN setType TEXT NOT NULL DEFAULT 'NORMAL'",
                )
                db.execSQL(
                    "ALTER TABLE exercise ADD COLUMN trackingType TEXT NOT NULL DEFAULT 'WEIGHT_REPS'",
                )
                db.execSQL(
                    "UPDATE exercise SET trackingType = 'TIME' WHERE isBuiltIn = 1 AND name IN ('플랭크')",
                )
                db.execSQL(
                    "UPDATE exercise SET trackingType = 'REPS_ONLY' WHERE isBuiltIn = 1 AND name IN " +
                        "('풀업', '딥스', '푸시업', '크런치', '행잉 레그레이즈', '버피')",
                )
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
