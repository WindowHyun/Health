package com.windowhyun.health.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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
 * 백업·복원 전용 DAO.
 *
 * 화면에서 쓰는 DAO 는 필요한 만큼만 읽어 오지만, 백업은 테이블을 통째로
 * 읽고 쓴다. 성격이 다르므로 섞지 않고 따로 뒀다.
 *
 * id 를 그대로 살려서 넣기 때문에 테이블 사이의 연결(어떤 세트가 어떤 운동의
 * 것인지)이 복원 후에도 유지된다.
 */
@Dao
interface BackupDao {

    @Query("SELECT * FROM exercise ORDER BY id")
    suspend fun allExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM routine ORDER BY id")
    suspend fun allRoutines(): List<RoutineEntity>

    @Query("SELECT * FROM routine_exercise ORDER BY id")
    suspend fun allRoutineExercises(): List<RoutineExerciseEntity>

    @Query("SELECT * FROM workout ORDER BY id")
    suspend fun allWorkouts(): List<WorkoutEntity>

    @Query("SELECT * FROM workout_exercise ORDER BY id")
    suspend fun allWorkoutExercises(): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_set ORDER BY id")
    suspend fun allWorkoutSets(): List<WorkoutSetEntity>

    @Query("SELECT * FROM personal_record ORDER BY id")
    suspend fun allPersonalRecords(): List<PersonalRecordEntity>

    @Query("SELECT * FROM run ORDER BY id")
    suspend fun allRuns(): List<RunEntity>

    @Query("SELECT * FROM run_lap ORDER BY id")
    suspend fun allRunLaps(): List<RunLapEntity>

    @Query("SELECT * FROM run_location ORDER BY id")
    suspend fun allRunLocations(): List<RunLocationEntity>

    @Insert
    suspend fun insertExercises(rows: List<ExerciseEntity>)

    @Insert
    suspend fun insertRoutines(rows: List<RoutineEntity>)

    @Insert
    suspend fun insertRoutineExercises(rows: List<RoutineExerciseEntity>)

    @Insert
    suspend fun insertWorkouts(rows: List<WorkoutEntity>)

    @Insert
    suspend fun insertWorkoutExercises(rows: List<WorkoutExerciseEntity>)

    @Insert
    suspend fun insertWorkoutSets(rows: List<WorkoutSetEntity>)

    @Insert
    suspend fun insertPersonalRecords(rows: List<PersonalRecordEntity>)

    @Insert
    suspend fun insertRuns(rows: List<RunEntity>)

    @Insert
    suspend fun insertRunLaps(rows: List<RunLapEntity>)

    @Insert
    suspend fun insertRunLocations(rows: List<RunLocationEntity>)

    // 지우는 순서는 자식 -> 부모. CASCADE 가 걸려 있어도 순서를 지켜 두면
    // 외래키 설정이 바뀌어도 안전하다.

    @Query("DELETE FROM run_location")
    suspend fun deleteAllRunLocations()

    @Query("DELETE FROM run_lap")
    suspend fun deleteAllRunLaps()

    @Query("DELETE FROM run")
    suspend fun deleteAllRuns()

    @Query("DELETE FROM personal_record")
    suspend fun deleteAllPersonalRecords()

    @Query("DELETE FROM workout_set")
    suspend fun deleteAllWorkoutSets()

    @Query("DELETE FROM workout_exercise")
    suspend fun deleteAllWorkoutExercises()

    @Query("DELETE FROM workout")
    suspend fun deleteAllWorkouts()

    @Query("DELETE FROM routine_exercise")
    suspend fun deleteAllRoutineExercises()

    @Query("DELETE FROM routine")
    suspend fun deleteAllRoutines()

    @Query("DELETE FROM exercise")
    suspend fun deleteAllExercises()
}
