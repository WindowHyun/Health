package com.windowhyun.health.data.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.data.catalog.InMemoryUserTemplateRepository
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.domain.model.Exercise
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

/** 복원 직전 보관본: 잘못 복원해도 한 단계 되돌릴 수 있고, 빈 기록이 좋은 보관본을 밀어내지 않는다. */
@RunWith(RobolectricTestRunner::class)
class PreRestoreSnapshotTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: HealthDatabase
    private lateinit var backup: BackupRepositoryImpl
    private lateinit var workouts: WorkoutRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var dir: File
    private var now = 1_000L
    private lateinit var snapshot: PreRestoreSnapshot

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).allowMainThreadQueries()
            .build().also { it.openHelper.writableDatabase }
        val settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = TestScope(dispatcher)) { File(context.cacheDir, "prs-${System.nanoTime()}.preferences_pb") },
        )
        backup = BackupRepositoryImpl(db, db.backupDao(), settings, InMemoryUserTemplateRepository(), dispatcher)
        workouts = WorkoutRepositoryImpl(db.workoutDao(), db.routineDao(), db.exerciseDao(), db.personalRecordDao())
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        dir = File(context.cacheDir, "snap-${System.nanoTime()}")
        snapshot = PreRestoreSnapshot(dir) { now }
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private suspend fun addWorkout(name: String, kg: Double) {
        val id = exercises.addExercise(Exercise(id = 0, name = name, category = ExerciseCategory.BARBELL, bodyPart = BodyPart.CHEST))
        val wid = workouts.startWorkout(null)
        val we = workouts.addExerciseToWorkout(wid, id)
        workouts.setCompleted(db.workoutDao().getSets(we).first().id, kg, 5, true)
        workouts.finishWorkout(wid)
    }

    private suspend fun export(): ByteArray = ByteArrayOutputStream().also { backup.exportBackup(it) }.toByteArray()

    @Test
    fun `restoring from a file can be undone with the snapshot`() = runTest(dispatcher) {
        addWorkout("A 종목", 50.0)
        val oldFile = export() // 오래된 파일
        addWorkout("B 종목", 100.0) // 그 뒤에 쌓인 기록
        assertThat(snapshot.savedAt()).isNull()

        assertThat(snapshot.save(backup)).isTrue()
        backup.restoreBackup(ByteArrayInputStream(oldFile)) // 잘못 고른 오래된 파일로 복원
        assertThat(db.backupDao().allWorkouts()).hasSize(1)

        snapshot.restore(backup)
        assertThat(db.backupDao().allWorkouts()).hasSize(2) // B 기록이 돌아왔다
        assertThat(snapshot.savedAt()).isNotNull() // 보관본은 그대로 남는다
    }

    @Test
    fun `an empty database does not replace a good snapshot`() = runTest(dispatcher) {
        addWorkout("A 종목", 50.0)
        assertThat(snapshot.save(backup)).isTrue()
        val good = File(dir, "before-restore.json").readBytes()

        db.clearAllTables() // 새 기기처럼 아무 기록이 없다
        assertThat(snapshot.save(backup)).isFalse()
        assertThat(File(dir, "before-restore.json").readBytes()).isEqualTo(good)
    }

    @Test
    fun `no leftover temp file after saving`() = runTest(dispatcher) {
        addWorkout("A 종목", 50.0)
        snapshot.save(backup)
        assertThat(dir.list()!!.toList()).containsExactly("before-restore.json")
    }

    @Test
    fun `restoring without a snapshot fails clearly`() = runTest(dispatcher) {
        val e = runCatching { snapshot.restore(backup) }.exceptionOrNull()
        assertThat(e).isInstanceOf(IllegalStateException::class.java)
    }

    // ----- CSV 수식 주입 -----

    @Test
    fun `csv values that look like formulas are neutralised and numbers are left alone`() {
        assertThat("=SUM(A1:A9)".csvEscaped()).isEqualTo("'=SUM(A1:A9)")
        assertThat("+cmd".csvEscaped()).isEqualTo("'+cmd")
        assertThat("@HYPERLINK(\"x\")".csvEscaped()).isEqualTo("\"'@HYPERLINK(\"\"x\"\")\"")
        assertThat("-1+2".csvEscaped()).isEqualTo("'-1+2")
        assertThat("-3.5".csvEscaped()).isEqualTo("-3.5")
        assertThat("12".csvEscaped()).isEqualTo("12")
        assertThat("벤치프레스".csvEscaped()).isEqualTo("벤치프레스")
        assertThat("a,b".csvEscaped()).isEqualTo("\"a,b\"")
        assertThat("".csvEscaped()).isEqualTo("")
    }
}
