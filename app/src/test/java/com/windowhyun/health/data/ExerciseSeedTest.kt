package com.windowhyun.health.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RoutineRepositoryImpl
import com.windowhyun.health.data.seed.DefaultExercises
import com.windowhyun.health.data.seed.ExerciseSeedCallback
import com.windowhyun.health.domain.model.Routine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.DayOfWeek

@RunWith(RobolectricTestRunner::class)
class ExerciseSeedTest {

    private lateinit var db: HealthDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .addCallback(ExerciseSeedCallback)
            .allowMainThreadQueries()
            .build().also { it.openHelper.writableDatabase } // 먼저 열어 둔다: 닫을 때 여는 중이면 서로 기다려 멈춘다
    }

    @After
    fun tearDown() = db.close()

    /** DB 를 처음 만들면 기본 종목이 이미 들어 있어야 한다. */
    @Test
    fun `seeds the default exercises on first creation`() = runTest {
        val stored = ExerciseRepositoryImpl(db.exerciseDao()).observeExercises().first()

        assertThat(stored).hasSize(DefaultExercises.all.size)
        assertThat(stored.map { it.name }).contains("벤치프레스")
        assertThat(stored.all { it.isBuiltIn }).isTrue()
    }

    /** 기본 종목 이름은 유니크 인덱스를 위해 중복이 없어야 한다. */
    @Test
    fun `default exercise names are unique`() {
        val names = DefaultExercises.all.map { it.name }
        assertThat(names).containsNoDuplicates()
    }
    /** 어느 부위든 고를 종목이 충분해야 한다. */
    @Test
    fun `every body part has several exercises`() {
        val perPart = DefaultExercises.all.groupBy { it.bodyPart }
        BodyPart.entries.forEach { part ->
            assertThat(perPart[part].orEmpty().size).isAtLeast(5)
        }
    }

    /** 유산소 기구는 중량이 없으니 시간만 기록한다. */
    @Test
    fun `cardio machines record time only`() {
        val cardio = DefaultExercises.all.filter { it.bodyPart == BodyPart.CARDIO }
        assertThat(cardio).isNotEmpty()
        assertThat(cardio.all { it.trackingType == ExerciseTrackingType.TIME }).isTrue()
    }

    // ----- 이미 쓰던 기기에서 앱을 업데이트한 경우 -----

    private fun openFile(file: File): HealthDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.databaseBuilder(context, HealthDatabase::class.java, file.absolutePath)
            .addMigrations(*HealthDatabase.MIGRATIONS)
            .addCallback(ExerciseSeedCallback)
            .allowMainThreadQueries()
            .build()
    }

    /**
     * 옛 버전은 종목이 적었다. 업데이트 후 열면 빠진 종목만 채워지고, 있던 종목(과 그 id)은 그대로다.
     * 같은 이름의 직접 만든 종목이 있으면 그것을 지우거나 덮어쓰지 않는다.
     */
    @Test
    fun `reopening adds only the missing defaults`() = runTest {
        val file = File.createTempFile("seed-upgrade", ".db")
        try {
            val first = openFile(file)
            val squatId = first.exerciseDao().getByName("스쿼트")!!.id
            val db0 = first.openHelper.writableDatabase
            // 옛 버전에는 없던 종목 둘을 지우고, 그중 하나는 사용자가 직접 같은 이름으로 만들어 둔 상황.
            db0.execSQL("DELETE FROM exercise WHERE name IN ('힙 쓰러스트', '러닝머신')")
            db0.execSQL(
                "INSERT INTO exercise (name, category, bodyPart, isBuiltIn, defaultRestSeconds, trackingType) " +
                    "VALUES ('러닝머신', 'OTHER', 'CORE', 0, NULL, 'WEIGHT_REPS')",
            )
            first.close()

            val reopened = openFile(file)
            try {
                val dao = reopened.exerciseDao()
                assertThat(dao.getByName("힙 쓰러스트")!!.isBuiltIn).isTrue()
                // 사용자의 종목은 그대로 사용자의 종목이다.
                val custom = dao.getByName("러닝머신")!!
                assertThat(custom.isBuiltIn).isFalse()
                assertThat(custom.bodyPart).isEqualTo(BodyPart.CORE)
                // 있던 종목은 같은 id 로 남는다. 기록이 이 id 를 가리킨다.
                assertThat(dao.getByName("스쿼트")!!.id).isEqualTo(squatId)
                assertThat(ExerciseRepositoryImpl(dao).observeExercises().first())
                    .hasSize(DefaultExercises.all.size)
            } finally {
                reopened.close()
            }
        } finally {
            file.delete()
        }
    }

    /** 몇 번을 다시 열어도 종목이 늘어나지 않는다. */
    @Test
    fun `reopening twice does not duplicate anything`() = runTest {
        val file = File.createTempFile("seed-idempotent", ".db")
        try {
            repeat(3) {
                val database = openFile(file)
                try {
                    assertThat(ExerciseRepositoryImpl(database.exerciseDao()).observeExercises().first())
                        .hasSize(DefaultExercises.all.size)
                } finally {
                    database.close()
                }
            }
        } finally {
            file.delete()
        }
    }
}
