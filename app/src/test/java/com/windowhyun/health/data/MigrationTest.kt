package com.windowhyun.health.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.model.SetType
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.RunRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * 이미 기록이 들어 있는 DB 를 최신 스키마로 올릴 수 있는지 검증한다.
 *
 * 마이그레이션이 깨지면 앱을 업데이트한 순간 기존 기록이 전부 날아가므로,
 * 스키마를 바꿀 때마다 여기에 케이스를 추가한다.
 *
 * 흐름은 실제 업데이트와 같다.
 * 1. 옛 버전 스키마로 DB 를 만들고 기록을 넣는다
 * 2. 최신 Room 으로 그 파일을 연다 -> Room 이 마이그레이션을 실행하고 스키마를 검증한다
 * 3. 기록이 그대로 남아 있는지 확인한다
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath(TEST_DB).also {
            it.parentFile?.mkdirs()
            it.delete()
        }
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    /** 옛 버전 스키마로 DB 파일을 만들고 [seed] 로 기록을 채운다. */
    private fun createOldDatabase(version: Int, seed: (androidx.sqlite.db.SupportSQLiteDatabase) -> Unit) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build(),
        )
        helper.writableDatabase.use { db ->
            RoomSchemas.createVersion(db, version)
            seed(db)
        }
    }

    /** 최신 Room 으로 연다. 마이그레이션이 빠졌거나 틀리면 여기서 예외가 난다. */
    private fun openCurrentDatabase(): HealthDatabase =
        Room.databaseBuilder(context, HealthDatabase::class.java, TEST_DB)
            .addMigrations(*HealthDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    /** v1 에 저장된 러닝은 v2 로 올린 뒤에도 남아 있고, 걸음 수는 0 이 된다. */
    @Test
    fun `migrates a run from version 1 to 2`() = runTest {
        createOldDatabase(1) { db ->
            db.execSQL(
                """
                INSERT INTO run (date, startTime, endTime, durationSeconds, distanceMeters,
                                 averagePaceSecPerKm, bestPaceSecPerKm, calories, goalType,
                                 goalValue, memo)
                VALUES (20000, 1000, 2000, 1800, 5000.0, 360.0, 330.0, 360, 'FREE', 0.0, '테스트 러닝')
                """.trimIndent(),
            )
        }

        val db = openCurrentDatabase()
        try {
            val run = RunRepositoryImpl(db.runDao()).getRun(1)!!

            assertThat(run.distanceMeters).isWithin(0.001).of(5_000.0)
            assertThat(run.durationSeconds).isEqualTo(1_800)
            assertThat(run.memo).isEqualTo("테스트 러닝")
            // v1 에는 걸음 수가 없었으므로 0 으로 채워진다.
            assertThat(run.steps).isEqualTo(0)
        } finally {
            db.close()
        }
    }

    /** 헬스 기록도 마이그레이션 후 그대로 남아야 한다. */
    @Test
    fun `keeps gym records across the migration`() = runTest {
        createOldDatabase(1) { db ->
            db.execSQL(
                "INSERT INTO exercise (name, category, bodyPart, isBuiltIn, defaultRestSeconds) " +
                    "VALUES ('벤치프레스', 'BARBELL', 'CHEST', 1, NULL)",
            )
            db.execSQL(
                "INSERT INTO workout (routineId, routineName, date, startTime, endTime, " +
                    "durationSeconds, memo) VALUES (NULL, '상체', 20000, 1000, 5000, 4000, NULL)",
            )
            db.execSQL(
                "INSERT INTO workout_exercise (workoutId, exerciseId, orderIndex, restSeconds) " +
                    "VALUES (1, 1, 0, NULL)",
            )
            db.execSQL(
                "INSERT INTO workout_set (workoutExerciseId, setNumber, weightKg, reps, " +
                    "completed, completedAt) VALUES (1, 1, 60.0, 10, 1, 4000)",
            )
        }

        val db = openCurrentDatabase()
        try {
            val workout = WorkoutRepositoryImpl(
                workoutDao = db.workoutDao(),
                routineDao = db.routineDao(),
                exerciseDao = db.exerciseDao(),
                personalRecordDao = db.personalRecordDao(),
            ).getWorkout(1)!!

            assertThat(workout.routineName).isEqualTo("상체")
            assertThat(workout.exercises).hasSize(1)
            assertThat(workout.exercises.first().exercise.name).isEqualTo("벤치프레스")
            assertThat(workout.totalVolume).isWithin(0.001).of(600.0)
        } finally {
            db.close()
        }
    }

    /** 빈 v1 DB 도 문제없이 올라가야 한다. */
    @Test
    fun `migrates an empty database`() = runTest {
        createOldDatabase(1) { }

        val db = openCurrentDatabase()
        try {
            // 열리기만 하면 스키마 검증을 통과한 것이다.
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(3)
        } finally {
            db.close()
        }
    }

    /** v2 에 저장된 세트는 v3 에서 본세트로, 시간은 0 으로 채워진다. */
    @Test
    fun `migrates sets from version 2 to 3`() = runTest {
        createOldDatabase(2) { db ->
            db.execSQL(
                "INSERT INTO exercise (name, category, bodyPart, isBuiltIn, defaultRestSeconds) " +
                    "VALUES ('벤치프레스', 'BARBELL', 'CHEST', 1, NULL)",
            )
            db.execSQL(
                "INSERT INTO workout (routineId, routineName, date, startTime, endTime, " +
                    "durationSeconds, memo) VALUES (NULL, '상체', 20000, 1000, 5000, 4000, NULL)",
            )
            db.execSQL(
                "INSERT INTO workout_exercise (workoutId, exerciseId, orderIndex, restSeconds) " +
                    "VALUES (1, 1, 0, NULL)",
            )
            db.execSQL(
                "INSERT INTO workout_set (workoutExerciseId, setNumber, weightKg, reps, " +
                    "completed, completedAt) VALUES (1, 1, 60.0, 10, 1, 4000)",
            )
        }

        val db = openCurrentDatabase()
        try {
            val workout = WorkoutRepositoryImpl(
                workoutDao = db.workoutDao(),
                routineDao = db.routineDao(),
                exerciseDao = db.exerciseDao(),
                personalRecordDao = db.personalRecordDao(),
            ).getWorkout(1)!!

            val set = workout.exercises.single().sets.single()
            // 기존 세트는 모두 본세트로 남아야 볼륨이 그대로 유지된다.
            assertThat(set.setType).isEqualTo(SetType.NORMAL)
            assertThat(set.durationSeconds).isEqualTo(0)
            assertThat(workout.totalVolume).isWithin(0.001).of(600.0)
            assertThat(workout.exercises.single().exercise.trackingType)
                .isEqualTo(ExerciseTrackingType.WEIGHT_REPS)
        } finally {
            db.close()
        }
    }

    /** 이미 쓰고 있던 종목의 기록 방식은 이름을 보고 채워 넣는다. */
    @Test
    fun `backfills tracking types by exercise name`() = runTest {
        createOldDatabase(2) { db ->
            fun insert(name: String) = db.execSQL(
                "INSERT INTO exercise (name, category, bodyPart, isBuiltIn, defaultRestSeconds) " +
                    "VALUES ('$name', 'BODYWEIGHT', 'CORE', 1, NULL)",
            )
            insert("플랭크")
            insert("풀업")
            insert("벤치프레스")
            // 직접 만든 종목은 건드리지 않는다.
            insert("나만의 운동")
        }

        val db = openCurrentDatabase()
        try {
            val dao = db.exerciseDao()
            assertThat(dao.getByName("플랭크")!!.trackingType)
                .isEqualTo(ExerciseTrackingType.TIME)
            assertThat(dao.getByName("풀업")!!.trackingType)
                .isEqualTo(ExerciseTrackingType.REPS_ONLY)
            assertThat(dao.getByName("벤치프레스")!!.trackingType)
                .isEqualTo(ExerciseTrackingType.WEIGHT_REPS)
            assertThat(dao.getByName("나만의 운동")!!.trackingType)
                .isEqualTo(ExerciseTrackingType.WEIGHT_REPS)
        } finally {
            db.close()
        }
    }

    /** v1 에서 v3 까지 한 번에 올라가야 한다. 두 버전을 건너뛴 사용자가 있기 때문이다. */
    @Test
    fun `migrates all the way from version 1 to 3`() = runTest {
        createOldDatabase(1) { db ->
            db.execSQL(
                "INSERT INTO exercise (name, category, bodyPart, isBuiltIn, defaultRestSeconds) " +
                    "VALUES ('플랭크', 'BODYWEIGHT', 'CORE', 1, NULL)",
            )
        }

        val db = openCurrentDatabase()
        try {
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(3)
            assertThat(db.exerciseDao().getByName("플랭크")!!.trackingType)
                .isEqualTo(ExerciseTrackingType.TIME)
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
