package com.windowhyun.health.data.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.ui.settings.autoBackupFolderLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 자동 백업: 언제 만들고, 실패하면 어떻게 되고, 무엇을 지우는지.
 *
 * 백업이 조용히 안 만들어지거나, 좋은 백업을 지우면 백업이 없는 것보다 위험하다.
 */
@RunWith(RobolectricTestRunner::class)
class AutoBackupTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("Asia/Seoul")
    private val day = 86_400_000L

    private lateinit var context: Context
    private lateinit var db: HealthDatabase
    private lateinit var settings: SettingsRepositoryImpl
    private lateinit var backup: BackupRepositoryImpl
    private lateinit var folder: FakeBackupFolder
    private var now = ZonedDateTime.of(2026, 10, 3, 14, 15, 30, 0, zone).toInstant().toEpochMilli()

    private val uri = "content://docs/tree/primary%3ABackups"

    private fun manager() = AutoBackupManager(backup, settings, folder, { now }, zone)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val file = File(context.cacheDir, "autobackup-${System.nanoTime()}.preferences_pb")
        settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) { file },
        )
        backup = BackupRepositoryImpl(db, db.backupDao(), settings, dispatcher)
        folder = FakeBackupFolder()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun enable(everyDays: Int = 7) = settings.update {
        it.copy(autoBackupFolderUri = uri, autoBackupEveryDays = everyDays)
    }

    @Test
    fun `does nothing until a folder is chosen`() = runTest(dispatcher) {
        assertThat(manager().runIfDue()).isEqualTo(AutoBackupResult.NotConfigured)
        assertThat(folder.folders).isEmpty()
    }

    @Test
    fun `first run writes a dated backup file and records the time`() = runTest(dispatcher) {
        enable()

        val result = manager().runIfDue()

        assertThat(result).isEqualTo(AutoBackupResult.Done("health-auto-2026-10-03-141530.json"))
        assertThat(folder.files(uri).keys).containsExactly("health-auto-2026-10-03-141530.json")
        val saved = settings.current()
        assertThat(saved.lastAutoBackupAt).isEqualTo(now)
        assertThat(saved.lastAutoBackupError).isNull()
    }

    /** 만들어진 파일은 실제로 복원할 수 있는 백업이어야 한다. */
    @Test
    fun `the written file restores`() = runTest(dispatcher) {
        ExerciseRepositoryImpl(db.exerciseDao()).addExercise(
            Exercise(id = 0, name = "내 운동", category = ExerciseCategory.BARBELL, bodyPart = BodyPart.CHEST),
        )
        enable()
        manager().runNow()

        val bytes = folder.files(uri).values.single()
        db.backupDao().deleteAllWorkouts()
        val summary = backup.restoreBackup(ByteArrayInputStream(bytes))

        assertThat(summary.customExerciseCount).isEqualTo(1)
    }

    @Test
    fun `does not run again before the interval is over`() = runTest(dispatcher) {
        enable(everyDays = 7)
        manager().runIfDue()

        now += 6 * day + 23 * 3_600_000L
        assertThat(manager().runIfDue()).isEqualTo(AutoBackupResult.NotDue)
        assertThat(folder.files(uri)).hasSize(1)

        now += 3_600_000L // 꼭 7일
        assertThat(manager().runIfDue()).isInstanceOf(AutoBackupResult.Done::class.java)
        assertThat(folder.files(uri)).hasSize(2)
    }

    @Test
    fun `daily interval backs up the next day`() = runTest(dispatcher) {
        enable(everyDays = 1)
        manager().runIfDue()

        now += day
        assertThat(manager().runIfDue()).isInstanceOf(AutoBackupResult.Done::class.java)
    }

    /** 기기 시계를 되돌렸다면 "마지막 백업이 미래"다. 영원히 안 만들어지면 안 된다. */
    @Test
    fun `backs up when the clock went backwards`() = runTest(dispatcher) {
        enable()
        settings.update { it.copy(lastAutoBackupAt = now + 30 * day) }

        assertThat(manager().runIfDue()).isInstanceOf(AutoBackupResult.Done::class.java)
    }

    @Test
    fun `run now ignores the interval`() = runTest(dispatcher) {
        enable()
        manager().runIfDue()
        now += 1_000

        assertThat(manager().runNow()).isInstanceOf(AutoBackupResult.Done::class.java)
        assertThat(folder.files(uri)).hasSize(2)
    }

    @Test
    fun `a failed write is recorded and retried next time`() = runTest(dispatcher) {
        enable()
        folder.failWrites = "폴더에 접근할 수 없습니다."

        val result = manager().runIfDue()

        assertThat(result).isEqualTo(AutoBackupResult.Failed("폴더에 접근할 수 없습니다."))
        val failed = settings.current()
        assertThat(failed.lastAutoBackupError).isEqualTo("폴더에 접근할 수 없습니다.")
        // 성공한 적이 없으므로 시각은 그대로. 그래서 다음에 켤 때 다시 시도한다.
        assertThat(failed.lastAutoBackupAt).isEqualTo(0)

        folder.failWrites = null
        assertThat(manager().runIfDue()).isInstanceOf(AutoBackupResult.Done::class.java)
        assertThat(settings.current().lastAutoBackupError).isNull()
    }

    @Test
    fun `keeps only the newest five and leaves other files alone`() = runTest(dispatcher) {
        enable()
        (1..6).forEach { folder.put(uri, "health-auto-2026-09-0$it-100000.json") }
        folder.put(uri, "내 사진.jpg")
        folder.put(uri, "health-backup-2026-01-01.json") // 사용자가 직접 만든 백업
        folder.put(uri, "health-auto-notes.txt")

        manager().runIfDue()

        val auto = folder.files(uri).keys.filter { it.startsWith("health-auto-") && it.endsWith(".json") }
        assertThat(auto).containsExactly(
            "health-auto-2026-10-03-141530.json",
            "health-auto-2026-09-06-100000.json",
            "health-auto-2026-09-05-100000.json",
            "health-auto-2026-09-04-100000.json",
            "health-auto-2026-09-03-100000.json",
        )
        assertThat(folder.files(uri).keys).containsAtLeast("내 사진.jpg", "health-backup-2026-01-01.json", "health-auto-notes.txt")
    }

    /** 새 백업이 실패한 날에는 옛 백업을 하나도 지우지 않는다. */
    @Test
    fun `a failed run deletes nothing`() = runTest(dispatcher) {
        enable()
        // 이미 다섯 개를 넘겨 둔 상태에서 새 백업이 실패해도, 아직 아무것도 지우지 않는다.
        (1..6).forEach { folder.put(uri, "health-auto-2026-09-0$it-100000.json") }
        folder.failWrites = "실패"

        manager().runIfDue()

        assertThat(folder.deleted).isEmpty()
        assertThat(folder.files(uri)).hasSize(6)
    }

    /** 옛 파일을 못 지워도 방금 만든 백업은 성공이다. */
    @Test
    fun `a pruning failure does not fail the backup`() = runTest(dispatcher) {
        enable()
        folder.failList = true

        val result = manager().runIfDue()

        assertThat(result).isInstanceOf(AutoBackupResult.Done::class.java)
        assertThat(settings.current().lastAutoBackupError).isNull()
        assertThat(settings.current().lastAutoBackupAt).isEqualTo(now)
    }

    /** 백업 도중 폴더를 바꿨다면, 옛 폴더에 쓴 결과가 새 설정을 건드리면 안 된다. */
    @Test
    fun `switching folders mid backup does not mark the new folder as done`() = runTest(dispatcher) {
        enable()
        folder.beforeWrite = {
            settings.update { it.copy(autoBackupFolderUri = "content://docs/tree/other", lastAutoBackupAt = 0) }
        }

        manager().runIfDue()

        val saved = settings.current()
        assertThat(saved.autoBackupFolderUri).isEqualTo("content://docs/tree/other")
        assertThat(saved.lastAutoBackupAt).isEqualTo(0)
    }

    /** 자동 백업 설정은 이 기기의 폴더 주소라서 백업 파일에 넣지도, 복원으로 덮지도 않는다. */
    @Test
    fun `device specific backup settings stay out of the backup file and survive a restore`() = runTest(dispatcher) {
        // 기록이 하나도 없는 백업은 복원을 거부하므로 종목 하나를 넣어 둔다.
        ExerciseRepositoryImpl(db.exerciseDao()).addExercise(
            Exercise(id = 0, name = "내 운동", category = ExerciseCategory.BARBELL, bodyPart = BodyPart.CHEST),
        )
        enable(everyDays = 1)
        manager().runNow()
        val text = String(folder.files(uri).values.single())
        assertThat(text).doesNotContain("autoBackup")
        assertThat(text).doesNotContain(uri)

        backup.restoreBackup(ByteArrayInputStream(text.toByteArray()))

        val after = settings.current()
        assertThat(after.autoBackupFolderUri).isEqualTo(uri)
        assertThat(after.autoBackupEveryDays).isEqualTo(1)
    }

    @Test
    fun `settings round trip the new fields and clear them`() = runTest(dispatcher) {
        settings.update {
            it.copy(autoBackupFolderUri = uri, autoBackupEveryDays = 1, lastAutoBackupAt = 123, lastAutoBackupError = "e")
        }
        val saved = settings.current()
        assertThat(saved.autoBackupFolderUri).isEqualTo(uri)
        assertThat(saved.autoBackupEveryDays).isEqualTo(1)
        assertThat(saved.lastAutoBackupAt).isEqualTo(123)
        assertThat(saved.lastAutoBackupError).isEqualTo("e")
        assertThat(saved.autoBackupEnabled).isTrue()

        settings.update { it.copy(autoBackupFolderUri = null, lastAutoBackupError = null) }
        val cleared = settings.current()
        assertThat(cleared.autoBackupFolderUri).isNull()
        assertThat(cleared.lastAutoBackupError).isNull()
        assertThat(cleared.autoBackupEnabled).isFalse()
    }

    @Test
    fun `folder label is readable`() {
        assertThat(
            autoBackupFolderLabel("content://com.android.externalstorage.documents/tree/primary%3ABackups%2Fhealth"),
        ).isEqualTo("primary:Backups/health")
    }
}
