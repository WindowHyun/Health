package com.windowhyun.health.data.backup

import androidx.room.withTransaction
import com.windowhyun.health.BuildConfig
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.model.SetType
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.local.dao.BackupDao
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
import com.windowhyun.health.di.IoDispatcher
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.ThemeMode
import com.windowhyun.health.domain.repository.BackupFormatException
import com.windowhyun.health.domain.repository.BackupRepository
import com.windowhyun.health.domain.repository.BackupSummary
import com.windowhyun.health.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JSON 백업과 CSV 내보내기.
 *
 * 복원은 "전부 대체"다. 합치기는 같은 기록이 두 벌로 늘어나기 쉽고, 어느 쪽이
 * 맞는지 사용자가 판단할 방법이 없어서 택하지 않았다. 대신 되돌릴 수 없다는
 * 점을 화면에서 분명히 확인받는다.
 */
@OptIn(ExperimentalSerializationApi::class)
@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val database: HealthDatabase,
    private val backupDao: BackupDao,
    private val settingsRepository: SettingsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BackupRepository {

    private val json = Json {
        // 옛 버전 앱이 만든 파일에 모르는 필드가 있어도 읽을 수 있게 한다.
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    override suspend fun exportBackup(output: OutputStream): BackupSummary =
        withContext(ioDispatcher) {
            val settings = settingsRepository.settings.first()
            val file = BackupFile(
                createdAt = System.currentTimeMillis(),
                appVersion = BuildConfig.VERSION_NAME,
                settings = settings.toBackup(),
                exercises = backupDao.allExercises().map { it.toBackup() },
                routines = backupDao.allRoutines().map { it.toBackup() },
                routineExercises = backupDao.allRoutineExercises().map { it.toBackup() },
                workouts = backupDao.allWorkouts().map { it.toBackup() },
                workoutExercises = backupDao.allWorkoutExercises().map { it.toBackup() },
                workoutSets = backupDao.allWorkoutSets().map { it.toBackup() },
                personalRecords = backupDao.allPersonalRecords().map { it.toBackup() },
                runs = backupDao.allRuns().map { it.toBackup() },
                runLaps = backupDao.allRunLaps().map { it.toBackup() },
                runLocations = backupDao.allRunLocations().map { it.toBackup() },
            )
            output.use { json.encodeToStream(file, it) }
            file.summarize()
        }

    override suspend fun restoreBackup(input: InputStream): BackupSummary =
        withContext(ioDispatcher) {
            val file = try {
                input.use { json.decodeFromStream<BackupFile>(it) }
            } catch (e: Exception) {
                throw BackupFormatException("백업 파일을 읽을 수 없습니다. 이 앱에서 만든 파일인지 확인해 주세요.")
            }

            if (file.formatVersion > BackupFile.CURRENT_FORMAT_VERSION) {
                throw BackupFormatException(
                    "더 새로운 버전에서 만든 백업입니다(형식 v${file.formatVersion}). " +
                        "앱을 업데이트한 뒤 다시 시도해 주세요.",
                )
            }

            val clean = file.dropOrphans()

            database.withTransaction {
                // 자식부터 지운다. 중간에 실패하면 트랜잭션이 통째로 되돌아간다.
                backupDao.deleteAllRunLocations()
                backupDao.deleteAllRunLaps()
                backupDao.deleteAllRuns()
                backupDao.deleteAllPersonalRecords()
                backupDao.deleteAllWorkoutSets()
                backupDao.deleteAllWorkoutExercises()
                backupDao.deleteAllWorkouts()
                backupDao.deleteAllRoutineExercises()
                backupDao.deleteAllRoutines()
                backupDao.deleteAllExercises()

                // 넣을 때는 부모부터.
                backupDao.insertExercises(clean.exercises.map { it.toEntity() })
                backupDao.insertRoutines(clean.routines.map { it.toEntity() })
                backupDao.insertRoutineExercises(clean.routineExercises.map { it.toEntity() })
                backupDao.insertWorkouts(clean.workouts.map { it.toEntity() })
                backupDao.insertWorkoutExercises(clean.workoutExercises.map { it.toEntity() })
                backupDao.insertWorkoutSets(clean.workoutSets.map { it.toEntity() })
                backupDao.insertPersonalRecords(clean.personalRecords.map { it.toEntity() })
                backupDao.insertRuns(clean.runs.map { it.toEntity() })
                backupDao.insertRunLaps(clean.runLaps.map { it.toEntity() })
                backupDao.insertRunLocations(clean.runLocations.map { it.toEntity() })
            }

            settingsRepository.update { file.settings.toSettings(it) }

            clean.summarize().copy(
                droppedRows = file.rowCount() - clean.rowCount(),
            )
        }

    override suspend fun exportWorkoutCsv(output: OutputStream): Int =
        withContext(ioDispatcher) {
            val exercises = backupDao.allExercises().associateBy { it.id }
            val workouts = backupDao.allWorkouts().associateBy { it.id }
            val workoutExercises = backupDao.allWorkoutExercises().associateBy { it.id }
            val sets = backupDao.allWorkoutSets()

            var rows = 0
            output.csvWriter { write ->
                write(
                    listOf(
                        "날짜", "루틴", "운동", "부위", "세트", "세트종류",
                        "중량(kg)", "횟수", "시간(초)", "볼륨(kg)", "완료",
                    ),
                )
                sets.sortedWith(compareBy({ it.workoutExerciseId }, { it.setNumber }))
                    .forEach { set ->
                        val we = workoutExercises[set.workoutExerciseId] ?: return@forEach
                        val workout = workouts[we.workoutId] ?: return@forEach
                        val exercise = exercises[we.exerciseId]
                        write(
                            listOf(
                                LocalDate.ofEpochDay(workout.date).toString(),
                                workout.routineName.orEmpty(),
                                exercise?.name.orEmpty(),
                                exercise?.bodyPart?.name.orEmpty(),
                                set.setNumber.toString(),
                                set.setType.name,
                                set.weightKg.toString(),
                                set.reps.toString(),
                                set.durationSeconds.toString(),
                                (set.weightKg * set.reps).toString(),
                                if (set.completed) "Y" else "N",
                            ),
                        )
                        rows++
                    }
            }
            rows
        }

    override suspend fun exportRunCsv(output: OutputStream): Int =
        withContext(ioDispatcher) {
            val runs = backupDao.allRuns()
            var rows = 0
            output.csvWriter { write ->
                write(
                    listOf(
                        "날짜", "시작시각", "거리(m)", "시간(초)", "평균페이스(초/km)",
                        "최고페이스(초/km)", "칼로리", "걸음", "목표", "메모",
                    ),
                )
                runs.sortedBy { it.startTime }.forEach { run ->
                    write(
                        listOf(
                            LocalDate.ofEpochDay(run.date).toString(),
                            run.startTime.toLocalTimeText(),
                            run.distanceMeters.toString(),
                            run.durationSeconds.toString(),
                            run.averagePaceSecPerKm.toString(),
                            run.bestPaceSecPerKm.toString(),
                            run.calories.toString(),
                            run.steps.toString(),
                            run.goalType,
                            run.memo.orEmpty(),
                        ),
                    )
                    rows++
                }
            }
            rows
        }
}

/** 엑셀은 BOM 이 없으면 CSV 를 UTF-8 로 열지 않아 한글이 깨진다. */
private const val BOM = "\uFEFF"

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun Long.toLocalTimeText(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(timeFormatter)

/**
 * CSV 를 쓴다.
 *
 * 앞에 BOM 을 붙인다. 엑셀은 BOM 이 없으면 UTF-8 로 열지 않아 한글이 깨진다.
 */
private inline fun OutputStream.csvWriter(body: ((List<String>) -> Unit) -> Unit) {
    bufferedWriter(Charsets.UTF_8).use { writer ->
        writer.write(BOM)
        body { fields ->
            writer.write(fields.joinToString(",") { it.csvEscaped() })
            writer.write("\r\n")
        }
    }
}

/** 쉼표·따옴표·줄바꿈이 들어간 값은 따옴표로 감싸고, 안의 따옴표는 두 번 쓴다. */
private fun String.csvEscaped(): String =
    if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + replace("\"", "\"\"") + "\""
    } else {
        this
    }

// ----- 엔티티 <-> 백업 모델 -----

private fun AppSettings.toBackup() = BackupSettings(
    defaultRestSeconds = defaultRestSeconds,
    weightUnit = weightUnit.name,
    distanceUnit = distanceUnit.name,
    vibrationEnabled = vibrationEnabled,
    restTimerAutoStart = restTimerAutoStart,
    autoLapMeters = autoLapMeters,
    bodyWeightKg = bodyWeightKg,
    themeMode = themeMode.name,
    healthConnectEnabled = healthConnectEnabled,
    keepScreenOnDuringWorkout = keepScreenOnDuringWorkout,
)

/**
 * 백업의 설정을 현재 설정 위에 얹는다.
 * 알 수 없는 값(옛 파일의 지워진 항목 등)은 지금 값을 그대로 둔다.
 */
private fun BackupSettings.toSettings(current: AppSettings) = current.copy(
    defaultRestSeconds = defaultRestSeconds,
    weightUnit = enumOrNull<WeightUnit>(weightUnit) ?: current.weightUnit,
    distanceUnit = enumOrNull<DistanceUnit>(distanceUnit) ?: current.distanceUnit,
    vibrationEnabled = vibrationEnabled,
    restTimerAutoStart = restTimerAutoStart,
    autoLapMeters = autoLapMeters,
    bodyWeightKg = bodyWeightKg,
    themeMode = enumOrNull<ThemeMode>(themeMode) ?: current.themeMode,
    healthConnectEnabled = healthConnectEnabled,
    keepScreenOnDuringWorkout = keepScreenOnDuringWorkout,
)

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }

private fun ExerciseEntity.toBackup() = BackupExercise(
    id, name, category.name, bodyPart.name, isBuiltIn, defaultRestSeconds, trackingType.name,
)

private fun BackupExercise.toEntity() = ExerciseEntity(
    id = id,
    name = name,
    category = enumOrNull<ExerciseCategory>(category) ?: ExerciseCategory.entries.first(),
    bodyPart = enumOrNull<BodyPart>(bodyPart) ?: BodyPart.entries.first(),
    isBuiltIn = isBuiltIn,
    defaultRestSeconds = defaultRestSeconds,
    trackingType = enumOrNull<ExerciseTrackingType>(trackingType)
        ?: ExerciseTrackingType.WEIGHT_REPS,
)

private fun RoutineEntity.toBackup() =
    BackupRoutine(id, name, scheduledDayMask, createdAt, sortOrder)

private fun BackupRoutine.toEntity() =
    RoutineEntity(id, name, scheduledDayMask, createdAt, sortOrder)

private fun RoutineExerciseEntity.toBackup() =
    BackupRoutineExercise(id, routineId, exerciseId, orderIndex, defaultSets, restSeconds)

private fun BackupRoutineExercise.toEntity() =
    RoutineExerciseEntity(id, routineId, exerciseId, orderIndex, defaultSets, restSeconds)

private fun WorkoutEntity.toBackup() =
    BackupWorkout(id, routineId, routineName, date, startTime, endTime, durationSeconds, memo)

private fun BackupWorkout.toEntity() =
    WorkoutEntity(id, routineId, routineName, date, startTime, endTime, durationSeconds, memo)

private fun WorkoutExerciseEntity.toBackup() =
    BackupWorkoutExercise(id, workoutId, exerciseId, orderIndex, restSeconds)

private fun BackupWorkoutExercise.toEntity() =
    WorkoutExerciseEntity(id, workoutId, exerciseId, orderIndex, restSeconds)

private fun WorkoutSetEntity.toBackup() = BackupWorkoutSet(
    id, workoutExerciseId, setNumber, weightKg, reps, completed, completedAt,
    durationSeconds, setType.name,
)

private fun BackupWorkoutSet.toEntity() = WorkoutSetEntity(
    id = id,
    workoutExerciseId = workoutExerciseId,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    completed = completed,
    completedAt = completedAt,
    durationSeconds = durationSeconds,
    setType = enumOrNull<SetType>(setType) ?: SetType.NORMAL,
)

private fun PersonalRecordEntity.toBackup() = BackupPersonalRecord(
    id, workoutId, exerciseId, type, value, previousValue, reps, weightKg, achievedAt,
)

private fun BackupPersonalRecord.toEntity() = PersonalRecordEntity(
    id, workoutId, exerciseId, type, value, previousValue, reps, weightKg, achievedAt,
)

private fun RunEntity.toBackup() = BackupRun(
    id, date, startTime, endTime, durationSeconds, distanceMeters, averagePaceSecPerKm,
    bestPaceSecPerKm, calories, steps, goalType, goalValue, memo,
)

private fun BackupRun.toEntity() = RunEntity(
    id, date, startTime, endTime, durationSeconds, distanceMeters, averagePaceSecPerKm,
    bestPaceSecPerKm, calories, steps, goalType, goalValue, memo,
)

private fun RunLapEntity.toBackup() =
    BackupRunLap(id, runId, lapNumber, distanceMeters, durationSeconds, paceSecPerKm)

private fun BackupRunLap.toEntity() =
    RunLapEntity(id, runId, lapNumber, distanceMeters, durationSeconds, paceSecPerKm)

private fun RunLocationEntity.toBackup() =
    BackupRunLocation(id, runId, latitude, longitude, altitude, timestamp, isSegmentStart)

private fun BackupRunLocation.toEntity() =
    RunLocationEntity(id, runId, latitude, longitude, altitude, timestamp, isSegmentStart)

// ----- 파일 검사 -----

/**
 * 부모가 없는 행을 버린다.
 *
 * 파일이 손상되었거나 손으로 고친 경우 외래키 제약에 걸려 복원 전체가 실패한다.
 * 그러면 멀쩡한 기록까지 못 살리므로, 끊어진 행만 빼고 나머지를 살린다.
 */
internal fun BackupFile.dropOrphans(): BackupFile {
    val exerciseIds = exercises.map { it.id }.toSet()
    val routineIds = routines.map { it.id }.toSet()
    val workoutIds = workouts.map { it.id }.toSet()
    val runIds = runs.map { it.id }.toSet()

    val keptRoutineExercises = routineExercises.filter {
        it.routineId in routineIds && it.exerciseId in exerciseIds
    }
    val keptWorkoutExercises = workoutExercises.filter {
        it.workoutId in workoutIds && it.exerciseId in exerciseIds
    }
    val workoutExerciseIds = keptWorkoutExercises.map { it.id }.toSet()

    return copy(
        routineExercises = keptRoutineExercises,
        workoutExercises = keptWorkoutExercises,
        workoutSets = workoutSets.filter { it.workoutExerciseId in workoutExerciseIds },
        personalRecords = personalRecords.filter {
            it.workoutId in workoutIds && it.exerciseId in exerciseIds
        },
        runLaps = runLaps.filter { it.runId in runIds },
        runLocations = runLocations.filter { it.runId in runIds },
    )
}

internal fun BackupFile.rowCount(): Int =
    exercises.size + routines.size + routineExercises.size + workouts.size +
        workoutExercises.size + workoutSets.size + personalRecords.size +
        runs.size + runLaps.size + runLocations.size

internal fun BackupFile.summarize() = BackupSummary(
    createdAt = createdAt,
    appVersion = appVersion,
    routineCount = routines.size,
    customExerciseCount = exercises.count { !it.isBuiltIn },
    workoutCount = workouts.size,
    setCount = workoutSets.size,
    runCount = runs.size,
)
