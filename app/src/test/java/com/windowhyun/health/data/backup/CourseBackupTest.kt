package com.windowhyun.health.data.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.data.catalog.InMemoryUserTemplateRepository
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.CourseRepositoryImpl
import com.windowhyun.health.data.repository.RunRepositoryImpl
import com.windowhyun.health.domain.model.Course
import com.windowhyun.health.domain.model.CoursePoint
import com.windowhyun.health.domain.model.RunGoalType
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** 코스와 "따라간 코스 이름" 이 백업 · 복원에서 살아남는지, 옛 백업을 복원해도 코스가 지워지지 않는지. */
@RunWith(RobolectricTestRunner::class)
class CourseBackupTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: HealthDatabase
    private lateinit var backup: BackupRepositoryImpl
    private lateinit var courses: CourseRepositoryImpl
    private lateinit var runs: RunRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).allowMainThreadQueries()
            .build().also { it.openHelper.writableDatabase }
        val settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = TestScope(dispatcher)) { File(context.cacheDir, "cb-${System.nanoTime()}.preferences_pb") },
        )
        backup = BackupRepositoryImpl(db, db.backupDao(), settings, InMemoryUserTemplateRepository(), dispatcher)
        courses = CourseRepositoryImpl(db.courseDao())
        runs = RunRepositoryImpl(db.runDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed() {
        courses.save(Course(name = "한강", points = listOf(CoursePoint(37.5, 127.0), CoursePoint(37.51, 127.01), CoursePoint(37.52, 127.0))))
        val id = runs.startRun(RunGoalType.FREE, 0.0, "한강")
        val run = runs.getRun(id)!!
        runs.saveRun(run.copy(endTime = run.startTime + 1000, durationSeconds = 600, distanceMeters = 2000.0))
    }

    private suspend fun export(): ByteArray = ByteArrayOutputStream().also { backup.exportBackup(it) }.toByteArray()

    @Test
    fun `courses and the followed course name round trip`() = runTest(dispatcher) {
        seed()
        val bytes = export()
        // 지우고 복원
        db.courseDao().delete(1)
        db.runDao().deleteRun(1)
        val summary = backup.restoreBackup(ByteArrayInputStream(bytes))
        assertThat(summary.courseCount).isEqualTo(1)
        assertThat(courses.get(1)!!.points).hasSize(3)
        assertThat(runs.getRun(1)!!.courseName).isEqualTo("한강")
    }

    @Test
    fun `restoring replaces the current courses`() = runTest(dispatcher) {
        seed()
        val bytes = export()
        courses.save(Course(name = "나중에 만든 것", points = listOf(CoursePoint(1.0, 1.0), CoursePoint(1.1, 1.1))))
        backup.restoreBackup(ByteArrayInputStream(bytes))
        assertThat(db.courseDao().get(2)).isNull()
        assertThat(courses.get(1)!!.name).isEqualTo("한강")
    }

    @Test
    fun `an older backup without a course section keeps the current courses`() = runTest(dispatcher) {
        seed()
        val text = String(export())
        // 코스 항목이 생기기 전 백업을 흉내 낸다.
        val old = text.replace("\"hasCourses\":true", "\"hasCourses\":false").replace(Regex("\"courses\":\\[.*?\\]"), "\"courses\":[]")
        backup.restoreBackup(ByteArrayInputStream(old.toByteArray()))
        assertThat(courses.get(1)).isNotNull()
    }

    @Test
    fun `a corrupt course in the file is skipped without failing the restore`() = runTest(dispatcher) {
        seed()
        val text = String(export()).replace(Regex("\"points\":\"[^\"]*\""), "\"points\":\"garbage\"")
        val summary = backup.restoreBackup(ByteArrayInputStream(text.toByteArray()))
        assertThat(summary.courseCount).isEqualTo(0)
        assertThat(courses.get(1)).isNull()
        assertThat(runs.getRun(1)).isNotNull()
    }

    @Test
    fun `same course stats count runs and find the best time`() = runTest(dispatcher) {
        suspend fun finished(id: Long, start: Long, seconds: Long) = runBlockingSave(id, start, seconds)
        val a = runs.startRun(RunGoalType.FREE, 0.0, "한강"); finished(a, 1_000, 1800)
        val b = runs.startRun(RunGoalType.FREE, 0.0, "한강"); finished(b, 2_000, 1700)
        val c = runs.startRun(RunGoalType.FREE, 0.0, "한강"); finished(c, 3_000, 1750)
        runs.startRun(RunGoalType.FREE, 0.0, null).also { finished(it, 4_000, 100) }
        val stats = runs.courseStats(runs.getRun(c)!!)!!
        assertThat(stats.count).isEqualTo(3)
        assertThat(stats.bestDurationSeconds).isEqualTo(1700)
        assertThat(runs.courseStats(runs.getRun(runs.getRun(c)!!.id + 1)!!)).isNull()
    }

    private suspend fun runBlockingSave(id: Long, start: Long, seconds: Long) {
        val run = runs.getRun(id)!!
        runs.saveRun(run.copy(startTime = start, endTime = start + seconds * 1000, durationSeconds = seconds))
    }
}
