package com.windowhyun.health.ui.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RoutineRepositoryImpl
import com.windowhyun.health.data.repository.RunRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.data.sensor.SensorStepCounter
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.model.RoutineItem
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.RunPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate

/**
 * 화면 캡처용 가짜 사용 기록. 일주일쯤 쓴 앱처럼: 오늘 예정 루틴 하나, 최근 운동 셋, 최근 러닝 셋.
 * 눈으로 보는 용도라 검증은 하지 않는다.
 */
class ScreenshotFixture(context: Context) {
    val db: HealthDatabase = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val workouts = WorkoutRepositoryImpl(db.workoutDao(), db.routineDao(), db.exerciseDao(), db.personalRecordDao())
    val routines = RoutineRepositoryImpl(db.routineDao())
    val exercises = ExerciseRepositoryImpl(db.exerciseDao())
    val runs = RunRepositoryImpl(db.runDao())
    val settings = SettingsRepositoryImpl(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
            File(context.cacheDir, "shot-${System.nanoTime()}.preferences_pb")
        },
    )
    val tracker = RunTracker(runs, settings, SensorStepCounter(context))

    /** 오늘 요일에 예정된 루틴("가슴 · 삼두"). */
    var todayRoutineId = 0L
        private set

    fun close() = db.close()

    fun seed() = runBlocking {
        suspend fun exercise(name: String, part: BodyPart) = exercises.getExercise(
            exercises.addExercise(Exercise(id = 0, name = name, category = ExerciseCategory.BARBELL, bodyPart = part)),
        )!!
        val bench = exercise("벤치프레스", BodyPart.CHEST)
        val squat = exercise("스쿼트", BodyPart.LEG)
        val row = exercise("바벨 로우", BodyPart.BACK)
        suspend fun routine(name: String, vararg items: Exercise, today: Boolean = false) = routines.saveRoutine(
            Routine(
                name = name,
                scheduledDays = if (today) setOf(LocalDate.now().dayOfWeek) else emptySet(),
                items = items.mapIndexed { i, e -> RoutineItem(exercise = e, orderIndex = i, defaultSets = 4) },
            ),
        )
        val chest = routine("가슴 · 삼두", bench, row, today = true)
        val leg = routine("하체 데이", squat, row)
        val back = routine("등 · 이두", row, bench)
        todayRoutineId = chest

        suspend fun finishedWorkout(routineId: Long, daysAgo: Long, kg: Double, minutes: Long) {
            val id = workouts.startWorkout(routineId)
            db.workoutDao().getWorkoutDetail(id)!!.exercises.flatMap { it.sets }.forEach {
                workouts.setCompleted(it.id, kg, 8, true)
            }
            workouts.finishWorkout(id)
            val stored = db.workoutDao().getWorkout(id)!!
            val shift = daysAgo * 86_400_000L
            db.workoutDao().updateWorkout(
                stored.copy(
                    date = stored.date - daysAgo,
                    startTime = stored.startTime - shift,
                    endTime = stored.endTime?.minus(shift),
                    durationSeconds = minutes * 60,
                ),
            )
        }
        finishedWorkout(leg, daysAgo = 1, kg = 100.0, minutes = 68)
        finishedWorkout(back, daysAgo = 3, kg = 70.0, minutes = 55)
        finishedWorkout(chest, daysAgo = 9, kg = 80.0, minutes = 62)

        suspend fun finishedRun(daysAgo: Long, meters: Double, seconds: Long) {
            val id = runs.startRun(RunGoalType.FREE, 0.0)
            val pace = seconds / (meters / 1000)
            // 눈으로 볼 때 경로 카드가 비지 않도록, 구불구불한 고리 모양 길을 넣는다.
            runs.appendRoutePoints(
                id,
                (0..120).map { i ->
                    val angle = i / 120.0 * 2 * Math.PI
                    RunPoint(
                        latitude = 37.5665 + 0.006 * Math.sin(angle) + 0.0015 * Math.sin(angle * 5),
                        longitude = 126.9780 + 0.009 * Math.cos(angle) * (1 + 0.15 * Math.cos(angle * 3)),
                        timestamp = 1_000L * i,
                    )
                },
            )
            runs.finishRun(id, System.currentTimeMillis(), meters, seconds, pace, pace - 12, (meters * 0.06).toInt(), 0)
            val stored = db.runDao().getRun(id)!!
            val shift = daysAgo * 86_400_000L
            db.runDao().updateRun(
                stored.copy(date = stored.date - daysAgo, startTime = stored.startTime - shift, endTime = stored.endTime?.minus(shift)),
            )
        }
        finishedRun(daysAgo = 2, meters = 8_200.0, seconds = 2_580)
        finishedRun(daysAgo = 8, meters = 5_000.0, seconds = 1_590)
        finishedRun(daysAgo = 12, meters = 10_400.0, seconds = 3_420)
    }
}

/** 지금 화면을 PNG 로 남긴다. captureToImage 는 Robolectric 에서 멈추므로 뷰를 직접 그린다. */
fun AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>.saveScreenshot(name: String) {
    waitForIdle()
    val view = activity.window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    val dir = File("build/screenshots").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

/** 실제 시간으로 기다린다(DB 는 다른 스레드에서 읽는다). */
fun waitFor(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
}
