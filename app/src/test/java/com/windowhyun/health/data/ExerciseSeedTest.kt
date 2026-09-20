package com.windowhyun.health.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
            .build()
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
}
