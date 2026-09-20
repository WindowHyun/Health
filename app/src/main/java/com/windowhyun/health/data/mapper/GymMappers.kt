package com.windowhyun.health.data.mapper

import com.windowhyun.health.data.local.entity.ExerciseEntity
import com.windowhyun.health.data.local.entity.RoutineEntity
import com.windowhyun.health.data.local.entity.RoutineExerciseEntity
import com.windowhyun.health.data.local.entity.WorkoutEntity
import com.windowhyun.health.data.local.entity.WorkoutExerciseEntity
import com.windowhyun.health.data.local.entity.WorkoutSetEntity
import com.windowhyun.health.data.local.relation.RoutineExerciseWithExercise
import com.windowhyun.health.data.local.relation.RoutineWithItems
import com.windowhyun.health.data.local.relation.WorkoutExerciseWithSets
import com.windowhyun.health.data.local.relation.WorkoutWithDetail
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.model.RoutineItem
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.model.WorkoutExerciseRecord
import com.windowhyun.health.domain.model.WorkoutSet
import java.time.DayOfWeek
import java.time.LocalDate

// ---------- 요일 비트마스크 ----------

/** 월=bit0 ... 일=bit6 */
fun Set<DayOfWeek>.toDayMask(): Int = fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }

fun Int.toDayOfWeekSet(): Set<DayOfWeek> =
    DayOfWeek.entries.filter { (this shr (it.value - 1)) and 1 == 1 }.toSet()

/** 단일 요일 비트. DAO 의 observeRoutinesForDay 에 넘긴다. */
fun DayOfWeek.toDayBit(): Int = 1 shl (value - 1)

// ---------- Exercise ----------

fun ExerciseEntity.toDomain(): Exercise = Exercise(
    id = id,
    name = name,
    category = category,
    bodyPart = bodyPart,
    isBuiltIn = isBuiltIn,
    defaultRestSeconds = defaultRestSeconds,
)

fun Exercise.toEntity(): ExerciseEntity = ExerciseEntity(
    id = id,
    name = name,
    category = category,
    bodyPart = bodyPart,
    isBuiltIn = isBuiltIn,
    defaultRestSeconds = defaultRestSeconds,
)

// ---------- Routine ----------

fun RoutineExerciseWithExercise.toDomain(): RoutineItem = RoutineItem(
    id = routineExercise.id,
    exercise = exercise.toDomain(),
    orderIndex = routineExercise.orderIndex,
    defaultSets = routineExercise.defaultSets,
    restSeconds = routineExercise.restSeconds,
)

fun RoutineWithItems.toDomain(): Routine = Routine(
    id = routine.id,
    name = routine.name,
    scheduledDays = routine.scheduledDayMask.toDayOfWeekSet(),
    items = items.sortedBy { it.routineExercise.orderIndex }.map { it.toDomain() },
)

fun Routine.toEntity(createdAt: Long = System.currentTimeMillis()): RoutineEntity = RoutineEntity(
    id = id,
    name = name,
    scheduledDayMask = scheduledDays.toDayMask(),
    createdAt = createdAt,
)

fun RoutineItem.toEntity(routineId: Long): RoutineExerciseEntity = RoutineExerciseEntity(
    id = id,
    routineId = routineId,
    exerciseId = exercise.id,
    orderIndex = orderIndex,
    defaultSets = defaultSets,
    restSeconds = restSeconds,
)

// ---------- Workout ----------

fun WorkoutSetEntity.toDomain(): WorkoutSet = WorkoutSet(
    id = id,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    completed = completed,
)

fun WorkoutExerciseWithSets.toDomain(): WorkoutExerciseRecord = WorkoutExerciseRecord(
    id = workoutExercise.id,
    exercise = exercise.toDomain(),
    orderIndex = workoutExercise.orderIndex,
    restSeconds = workoutExercise.restSeconds,
    sets = sets.sortedBy { it.setNumber }.map { it.toDomain() },
)

fun WorkoutWithDetail.toDomain(): Workout = Workout(
    id = workout.id,
    routineId = workout.routineId,
    routineName = workout.routineName,
    date = LocalDate.ofEpochDay(workout.date),
    startTime = workout.startTime,
    endTime = workout.endTime,
    durationSeconds = workout.durationSeconds,
    memo = workout.memo,
    exercises = exercises.sortedBy { it.workoutExercise.orderIndex }.map { it.toDomain() },
)

fun WorkoutEntity.toDomain(): Workout = Workout(
    id = id,
    routineId = routineId,
    routineName = routineName,
    date = LocalDate.ofEpochDay(date),
    startTime = startTime,
    endTime = endTime,
    durationSeconds = durationSeconds,
    memo = memo,
)

fun WorkoutExerciseRecord.toEntity(workoutId: Long): WorkoutExerciseEntity = WorkoutExerciseEntity(
    id = id,
    workoutId = workoutId,
    exerciseId = exercise.id,
    orderIndex = orderIndex,
    restSeconds = restSeconds,
)

fun WorkoutSet.toEntity(workoutExerciseId: Long): WorkoutSetEntity = WorkoutSetEntity(
    id = id,
    workoutExerciseId = workoutExerciseId,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    completed = completed,
)
