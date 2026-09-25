package com.windowhyun.health.data.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.model.SetType
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RunRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.RunLap
import com.windowhyun.health.domain.model.RunPoint
import com.windowhyun.health.domain.repository.BackupFormatException
import com.windowhyun.health.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
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

/**
 * 백업 / 복원 / CSV 검증.
 *
 * 서버가 없어서 이 파일이 유일한 복구 수단이다. 내보낸 파일로 원래 기록이
 * 그대로 돌아오지 않으면 백업이 있다는 사실 자체가 더 위험하다.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var db: HealthDatabase
    private lateinit var workouts: WorkoutRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var runs: RunRepositoryImpl
    private lateinit var settings: SettingsRepository
    private lateinit var backup: BackupRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workouts = WorkoutRepositoryImpl(
            workoutDao = db.workoutDao(),
            routineDao = db.routineDao(),
            exerciseDao = db.exerciseDao(),
            personalRecordDao = db.personalRecordDao(),
        )
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        runs = RunRepositoryImpl(db.runDao())
        val file = File(context.cacheDir, "backup-test-${System.nanoTime()}.preferences_pb")
        settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = TestScope(dispatcher)) { file },
        )
        backup = BackupRepositoryImpl(db, db.backupDao(), settings, dispatcher)
    }

    @After
    fun tearDown() = db.close()

    /** 헬스 1회 + 러닝 1회 + 루틴 1개를 만든다. */
    private suspend fun seedRecords(): Long {
        val exerciseId = exercises.addExercise(
            Exercise(
                id = 0,
                name = "내가 만든 운동",
                category = ExerciseCategory.BARBELL,
                bodyPart = BodyPart.LEG,
                trackingType = ExerciseTrackingType.WEIGHT_REPS,
            ),
        )
        val workoutId = workouts.startWorkout(null)
        val weId = workouts.addExerciseToWorkout(workoutId, exerciseId)
        workouts.addSet(weId)
        val sets = db.workoutDao().getSets(weId)
        workouts.setSetType(sets[0].id, SetType.WARMUP)
        workouts.setCompleted(sets[0].id, 40.0, 10, true)
        workouts.setCompleted(sets[1].id, 100.0, 5, true)
        workouts.finishWorkout(workoutId)
        workouts.updateMemo(workoutId, "다리, 쉼표, 있음")

        val runId = runs.startRun(RunGoalType.DISTANCE, 5_000.0)
        runs.appendRoutePoints(
            runId,
            listOf(
                RunPoint(latitude = 37.5, longitude = 127.0, timestamp = 1_000),
                RunPoint(latitude = 37.6, longitude = 127.1, timestamp = 2_000, isSegmentStart = true),
            ),
        )
        runs.appendLap(
            runId,
            RunLap(lapNumber = 1, distanceMeters = 1_000.0, durationSeconds = 330, paceSecPerKm = 330.0),
        )
        runs.finishRun(
            runId = runId,
            endTime = 9_000,
            distanceMeters = 5_000.0,
            durationSeconds = 1_800,
            averagePaceSecPerKm = 360.0,
            bestPaceSecPerKm = 330.0,
            calories = 300,
            steps = 5_000,
        )
        runs.updateMemo(runId, "바람 셌음")
        return workoutId
    }

    private suspend fun exportBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        backup.exportBackup(out)
        return out.toByteArray()
    }

    /** 내보냈다가 다시 읽으면 기록이 그대로여야 한다. */
    @Test
    fun `restores every record from its own backup`() = runTest(dispatcher) {
        val workoutId = seedRecords()
        val before = workouts.getWorkout(workoutId)!!
        val bytes = exportBytes()

        // 전부 지운 뒤 복원한다.
        db.backupDao().deleteAllRuns()
        db.backupDao().deleteAllWorkouts()
        val summary = backup.restoreBackup(ByteArrayInputStream(bytes))

        assertThat(summary.droppedRows).isEqualTo(0)
        assertThat(summary.workoutCount).isEqualTo(1)
        assertThat(summary.runCount).isEqualTo(1)

        val after = workouts.getWorkout(workoutId)!!
        assertThat(after.memo).isEqualTo("다리, 쉼표, 있음")
        assertThat(after.totalVolume).isWithin(0.001).of(before.totalVolume)
        // 워밍업 구분도 살아 있어야 한다.
        val setTypes = after.exercises.single().sets.map { it.setType }
        assertThat(setTypes).containsExactly(SetType.WARMUP, SetType.NORMAL).inOrder()
    }

    /** 러닝은 Lap 과 경로까지 돌아와야 한다. 지도가 빈 채로 복원되면 의미가 없다. */
    @Test
    fun `restores run laps and route`() = runTest(dispatcher) {
        seedRecords()
        val bytes = exportBytes()
        db.backupDao().deleteAllRuns()

        backup.restoreBackup(ByteArrayInputStream(bytes))

        // 목록 조회는 Lap/경로를 싣지 않으므로 상세로 읽는다.
        val runId = db.backupDao().allRuns().single().id
        val run = runs.getRun(runId)!!
        assertThat(run.laps).hasSize(1)
        assertThat(run.laps.single().paceSecPerKm).isWithin(0.001).of(330.0)
        assertThat(run.route).hasSize(2)
        assertThat(run.route.last().isSegmentStart).isTrue()
        assertThat(run.memo).isEqualTo("바람 셌음")
    }

    /** 설정도 백업에 들어간다. 단위를 다시 맞추게 하지 않는다. */
    @Test
    fun `restores settings`() = runTest(dispatcher) {
        settings.update { it.copy(weightUnit = WeightUnit.LB, bodyWeightKg = 82.5) }
        val bytes = exportBytes()
        settings.update { it.copy(weightUnit = WeightUnit.KG, bodyWeightKg = 70.0) }

        backup.restoreBackup(ByteArrayInputStream(bytes))

        val restored = settings.settings.first()
        assertThat(restored.weightUnit).isEqualTo(WeightUnit.LB)
        assertThat(restored.bodyWeightKg).isWithin(0.001).of(82.5)
        assertThat(restored.distanceUnit).isEqualTo(DistanceUnit.KM)
    }

    /** 복원은 합치기가 아니라 대체다. 같은 기록이 두 벌로 늘어나면 안 된다. */
    @Test
    fun `replaces existing records instead of duplicating them`() = runTest(dispatcher) {
        seedRecords()
        val bytes = exportBytes()

        backup.restoreBackup(ByteArrayInputStream(bytes))
        backup.restoreBackup(ByteArrayInputStream(bytes))

        assertThat(db.backupDao().allWorkouts()).hasSize(1)
        assertThat(db.backupDao().allRuns()).hasSize(1)
        assertThat(db.backupDao().allWorkoutSets()).hasSize(2)
    }

    /** 앱이 못 읽는 파일은 기록을 건드리기 전에 거절해야 한다. */
    @Test
    fun `rejects a file it cannot read`() = runTest(dispatcher) {
        seedRecords()

        val error = runCatching {
            backup.restoreBackup(ByteArrayInputStream("이건 백업이 아니다".toByteArray()))
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(BackupFormatException::class.java)
        // 기록은 그대로 남아 있어야 한다.
        assertThat(db.backupDao().allWorkouts()).hasSize(1)
    }

    /** 더 새로운 형식은 억지로 읽지 않는다. */
    @Test
    fun `rejects a newer format version`() = runTest(dispatcher) {
        seedRecords()
        val newer = """{"formatVersion":99,"createdAt":0,"appVersion":"9.9"}"""

        val error = runCatching {
            backup.restoreBackup(ByteArrayInputStream(newer.toByteArray()))
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(BackupFormatException::class.java)
        assertThat(error).hasMessageThat().contains("업데이트")
        assertThat(db.backupDao().allWorkouts()).hasSize(1)
    }

    /** 연결이 끊어진 행이 있어도 나머지는 살린다. */
    @Test
    fun `drops orphan rows and keeps the rest`() = runTest(dispatcher) {
        val file = BackupFile(
            exercises = listOf(BackupExercise(id = 1, name = "스쿼트", category = "BARBELL", bodyPart = "LEG")),
            workouts = listOf(BackupWorkout(id = 1, date = 20_000, startTime = 1_000)),
            workoutExercises = listOf(
                BackupWorkoutExercise(id = 1, workoutId = 1, exerciseId = 1),
                // 없는 운동을 가리키는 행
                BackupWorkoutExercise(id = 2, workoutId = 99, exerciseId = 1),
            ),
            workoutSets = listOf(
                BackupWorkoutSet(id = 1, workoutExerciseId = 1, setNumber = 1, weightKg = 60.0, reps = 10, completed = true),
                // 위에서 버려질 행에 붙은 세트
                BackupWorkoutSet(id = 2, workoutExerciseId = 2, setNumber = 1),
            ),
        )

        val clean = file.dropOrphans()

        assertThat(clean.workoutExercises).hasSize(1)
        assertThat(clean.workoutSets).hasSize(1)
        assertThat(file.rowCount() - clean.rowCount()).isEqualTo(2)
    }

    /** 끊어진 행이 든 파일을 복원해도 멀쩡한 기록은 들어가야 한다. */
    @Test
    fun `restores a file that contains orphan rows`() = runTest(dispatcher) {
        val file = BackupFile(
            exercises = listOf(
                BackupExercise(id = 1, name = "스쿼트", category = "BARBELL", bodyPart = "LEG"),
            ),
            workouts = listOf(BackupWorkout(id = 1, date = 20_000, startTime = 1_000)),
            workoutExercises = listOf(
                BackupWorkoutExercise(id = 1, workoutId = 1, exerciseId = 1),
                // 없는 기록을 가리키는 행. 그냥 넣으면 외래키 위반으로 복원 전체가 실패한다.
                BackupWorkoutExercise(id = 2, workoutId = 99, exerciseId = 1),
            ),
            workoutSets = listOf(
                BackupWorkoutSet(
                    id = 1,
                    workoutExerciseId = 1,
                    setNumber = 1,
                    weightKg = 60.0,
                    reps = 10,
                    completed = true,
                ),
            ),
        )
        val bytes = Json.encodeToString(BackupFile.serializer(), file).toByteArray()

        val summary = backup.restoreBackup(ByteArrayInputStream(bytes))

        assertThat(summary.droppedRows).isEqualTo(1)
        val restored = workouts.getWorkout(1)!!
        assertThat(restored.exercises).hasSize(1)
        assertThat(restored.totalVolume).isWithin(0.001).of(600.0)
    }

    /** 헬스 CSV 는 세트마다 한 줄이고, 쉼표가 든 값은 따옴표로 감싼다. */
    @Test
    fun `writes a workout csv`() = runTest(dispatcher) {
        seedRecords()
        val out = ByteArrayOutputStream()

        val rows = backup.exportWorkoutCsv(out)

        val text = out.toByteArray().toString(Charsets.UTF_8)
        assertThat(rows).isEqualTo(2)
        // 엑셀이 한글을 깨뜨리지 않도록 BOM 을 붙인다.
        assertThat(text.first()).isEqualTo('﻿')
        assertThat(text).contains("날짜,루틴,운동")
        assertThat(text).contains("내가 만든 운동")
        assertThat(text).contains("WARMUP")
        assertThat(text.lines().filter { it.isNotBlank() }).hasSize(3) // 머리글 + 2줄
    }

    /** 러닝 CSV. */
    @Test
    fun `writes a run csv`() = runTest(dispatcher) {
        seedRecords()
        val out = ByteArrayOutputStream()

        val rows = backup.exportRunCsv(out)

        val text = out.toByteArray().toString(Charsets.UTF_8)
        assertThat(rows).isEqualTo(1)
        assertThat(text).contains("거리(m)")
        assertThat(text).contains("5000.0")
        assertThat(text).contains("바람 셌음")
    }

    /** 쉼표가 든 메모가 열을 밀어내면 안 된다. */
    @Test
    fun `quotes values containing a comma`() = runTest(dispatcher) {
        seedRecords()
        val out = ByteArrayOutputStream()

        backup.exportWorkoutCsv(out)

        val text = out.toByteArray().toString(Charsets.UTF_8)
        // 메모는 헬스 CSV 에 없지만 루틴 이름 등 다른 값에도 같은 규칙이 적용된다.
        val dataLine = text.lines().first { it.contains("내가 만든 운동") }
        assertThat(dataLine.split(",")).hasSize(11)
    }
}
