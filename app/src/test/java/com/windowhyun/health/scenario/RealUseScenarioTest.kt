package com.windowhyun.health.scenario

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.notification.RestTimerNotifier
import com.windowhyun.health.data.backup.AutoBackupManager
import com.windowhyun.health.data.backup.BackupRepositoryImpl
import com.windowhyun.health.data.backup.FakeBackupFolder
import com.windowhyun.health.data.backup.PreRestoreSnapshot
import com.windowhyun.health.data.catalog.InMemoryUserTemplateRepository
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.CourseRepositoryImpl
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RoutineRepositoryImpl
import com.windowhyun.health.data.repository.RunRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.data.seed.ExerciseSeedCallback
import com.windowhyun.health.data.tracking.ActiveCourse
import com.windowhyun.health.domain.model.RunCue
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.course.CourseGeometry
import com.windowhyun.health.domain.model.Course
import com.windowhyun.health.domain.model.CoursePoint
import com.windowhyun.health.domain.model.LocationSample
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.RunStatus
import com.windowhyun.health.domain.repository.LocationTracker
import com.windowhyun.health.domain.repository.StepCounter
import com.windowhyun.health.service.RunServiceController
import com.windowhyun.health.service.RunTrackingService
import com.windowhyun.health.ui.gym.RoutineTemplateViewModel
import com.windowhyun.health.ui.gym.RoutineTemplates
import com.windowhyun.health.ui.navigation.Routes
import com.windowhyun.health.ui.running.RunSetupViewModel
import com.windowhyun.health.ui.session.WorkoutSessionEvent
import com.windowhyun.health.ui.session.WorkoutSessionViewModel
import com.windowhyun.health.ui.settings.BackupViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * 실제로 쓰는 순서를 그대로 따라가는 시나리오 테스트.
 *
 * 화면 대신 화면이 부르는 ViewModel 을 누르고, 저장은 진짜 DB(Room)에 한다. 기기에서 손으로 해 보는 것을 대신하지는 못한다
 * (터치 · 실제 GPS · 알림 · 시계는 여기서 볼 수 없다). 대신 "이 순서로 쓰면 기록이 이렇게 남는다" 를 고정해,
 * 한 기능을 고치다가 흐름의 다른 곳이 깨지는 것을 잡는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RealUseScenarioTest {

    private val dispatcher = StandardTestDispatcher()
    private val io = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cleanups = mutableListOf<() -> Unit>()

    private lateinit var context: Context
    private lateinit var db: HealthDatabase
    private lateinit var workouts: WorkoutRepositoryImpl
    private lateinit var routines: RoutineRepositoryImpl
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var runs: RunRepositoryImpl
    private lateinit var courses: CourseRepositoryImpl
    private lateinit var settings: SettingsRepositoryImpl
    private lateinit var backup: BackupRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        // 실제 앱처럼 기본 종목이 들어 있는 DB.
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .addCallback(ExerciseSeedCallback)
            .allowMainThreadQueries()
            .build().also { it.openHelper.writableDatabase }
        workouts = WorkoutRepositoryImpl(db.workoutDao(), db.routineDao(), db.exerciseDao(), db.personalRecordDao())
        routines = RoutineRepositoryImpl(db.routineDao())
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        runs = RunRepositoryImpl(db.runDao(), db.courseDao())
        courses = CourseRepositoryImpl(db.courseDao())
        settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = io) { File(context.cacheDir, "scenario-${System.nanoTime()}.preferences_pb") },
        )
        backup = BackupRepositoryImpl(db, db.backupDao(), settings, InMemoryUserTemplateRepository(), Dispatchers.IO)
    }

    @After
    fun tearDown() {
        cleanups.forEach { runCatching(it) }
        io.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    /** 화면 쪽 코루틴(테스트 시계)과 저장 쪽 스레드(진짜 시간)를 함께 굴리며 조건을 기다린다. */
    private fun await(what: String, timeoutMillis: Long = 8_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) {
            dispatcher.scheduler.advanceTimeBy(50)
            dispatcher.scheduler.runCurrent()
            Thread.sleep(10)
        }
        check(condition()) { "기다리던 일이 일어나지 않았어요: $what" }
    }

    /** 일어나면 안 되는 일을 볼 때: 일어날 시간을 충분히 준다. */
    private fun settle(millis: Long = 600) {
        val end = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < end) {
            dispatcher.scheduler.advanceTimeBy(50)
            dispatcher.scheduler.runCurrent()
            Thread.sleep(15)
        }
    }

    // =====================================================================================
    // 1. 헬스 하루: 템플릿 추가 → 운동 → 실수 고치기 → 종료 → 다음 운동
    // =====================================================================================

    private fun sessionViewModel(workoutId: Long) = WorkoutSessionViewModel(
        workoutRepository = workouts,
        exerciseRepository = exercises,
        settingsRepository = settings,
        restTimerNotifier = RestTimerNotifier(context),
        savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_WORKOUT_ID to workoutId)),
        clock = { dispatcher.scheduler.currentTime + 1_000_000L },
    ).also { vm -> cleanups += { vm.viewModelScope.cancel() } }

    private fun workout(id: Long) = runBlocking { workouts.getWorkout(id)!! }

    @Test
    fun `gym day - add a template, train, undo mistakes, finish, then start the next session`() {
        // ----- 템플릿을 내 루틴에 추가한다 -----
        val template = RoutineTemplates.all.first { t -> t.days.any { it.supersets.isNotEmpty() } }
        // 첫 종목이 슈퍼셋 중간이 아닌 날(세트를 마치면 바로 쉬는 날)을 고른다.
        val day = template.days.first { d -> d.exercises.size >= 3 && d.supersets.none { d.exercises.first().first in it } }
        val templateVm = RoutineTemplateViewModel(routines, exercises).also { vm -> cleanups += { vm.viewModelScope.cancel() } }
        templateVm.applyTemplate(template)
        await("템플릿의 루틴이 모두 만들어짐") { runBlocking { routines.observeRoutines().first() }.size == template.days.size }

        val routine = runBlocking { routines.observeRoutines().first() }.first { it.name == day.routineName }
        assertThat(routine.items.map { it.exercise.name }).containsExactlyElementsIn(day.exercises.map { it.first }).inOrder()
        assertThat(routine.scheduledDays).isEqualTo(day.scheduledDays)

        // ----- 그 루틴으로 운동을 시작한다 -----
        val workoutId = runBlocking { workouts.startWorkout(routine.id) }
        val vm = sessionViewModel(workoutId)
        val events = mutableListOf<WorkoutSessionEvent>()
        val collector = CoroutineScope(dispatcher).launch { vm.events.collect { events += it } }
        cleanups += { collector.cancel() }
        settle(200)

        val firstName = day.exercises.first().first
        val first = workout(workoutId).exercises[0]
        assertThat(first.exercise.name).isEqualTo(firstName)
        assertThat(first.sets).hasSize(day.exercises.first().second)
        // 템플릿이 정한 휴식(없으면 종목의 기본 휴식)이 운동까지 따라온다.
        val expectedRest = day.restSeconds[firstName] ?: first.exercise.defaultRestSeconds
        assertThat(first.restSeconds).isEqualTo(expectedRest)
        while (workout(workoutId).exercises[0].sets.size < 4) {
            val before = workout(workoutId).exercises[0].sets.size
            vm.addSet(first.id)
            await("세트 추가") { workout(workoutId).exercises[0].sets.size == before + 1 }
        }

        // ----- 1세트: 무게를 넣고 완료를 누른다. 휴식 타이머가 정해진 시간으로 돈다 -----
        fun firstSets() = workout(workoutId).exercises[0].sets
        vm.updateSetValues(firstSets()[0], 80.0, 8, 0)
        await("1세트 값 저장") { firstSets()[0].let { it.weightKg == 80.0 && it.reps == 8 } }
        // 뒤 세트는 무게만 따라온다(횟수는 아직 없다).
        await("뒤 세트 무게 따라오기") { firstSets().drop(1).all { it.weightKg == 80.0 } }
        assertThat(firstSets().drop(1).map { it.reps }.distinct()).containsExactly(0)

        vm.toggleSetCompleted(firstSets()[0], 80.0, 8, 0, first.restSeconds)
        await("휴식 타이머 시작") { vm.restTimer.value.visible }
        assertThat(vm.restTimer.value.totalSeconds).isEqualTo(expectedRest ?: runBlocking { settings.current() }.defaultRestSeconds)
        vm.stopRestTimer()

        // ----- 2세트 완료, 3세트는 값만 넣고 체크를 깜빡, 4세트는 하지 않음(무게만 채워져 있다) -----
        vm.updateSetValues(firstSets()[1], 80.0, 7, 0)
        await("2세트 값 저장") { firstSets()[1].reps == 7 }
        vm.toggleSetCompleted(firstSets()[1], 80.0, 7, 0, first.restSeconds)
        await("2세트 완료") { firstSets()[1].completed }
        vm.stopRestTimer()
        vm.updateSetValues(firstSets()[2], 80.0, 6, 0)
        await("3세트 값 저장") { firstSets()[2].reps == 6 }
        assertThat(firstSets()[3].let { it.weightKg to it.reps }).isEqualTo(80.0 to 0)

        // ----- 실수 1: 셋째 종목의 세트를 잘못 지웠다가 되돌린다 -----
        val third = workout(workoutId).exercises[2]
        vm.removeSet(third.sets.first().id)
        await("세트 삭제") { workout(workoutId).exercises[2].sets.size == third.sets.size - 1 }
        // "실행 취소" 는 삭제 안내가 뜬 뒤에야 누를 수 있다.
        await("삭제 안내") { events.count { it == WorkoutSessionEvent.SetRemoved } == 1 }
        vm.undoRemoveSet()
        await("세트 되살리기") { workout(workoutId).exercises[2].sets.size == third.sets.size }

        // ----- 실수 2: 둘째 종목의 세트를 지우고, 그 종목을 통째로 뺀 뒤, 떠 있던 "실행 취소"를 누른다 -----
        val second = workout(workoutId).exercises[1]
        vm.removeSet(second.sets.first().id)
        await("둘째 종목 세트 삭제") { workout(workoutId).exercises[1].sets.size == second.sets.size - 1 }
        await("삭제 안내") { events.count { it == WorkoutSessionEvent.SetRemoved } == 2 }
        vm.removeExercise(second.id)
        await("둘째 종목 빼기") { workout(workoutId).exercises.none { it.id == second.id } }
        vm.undoRemoveSet() // 예전에는 여기서 앱이 죽었다
        settle()
        assertThat(workout(workoutId).exercises.map { it.exercise.name }).doesNotContain(second.exercise.name)
        assertThat(events.count { it == WorkoutSessionEvent.SetRemoved }).isEqualTo(2)

        // ----- 종료: "체크 안 한 세트도 완료로 저장" 을 고른다 -----
        // 종료 창이 세는 수: 3세트(80kg x 6)만. 무게만 있는 4세트와 손대지 않은 다른 종목의 빈 세트는 세지 않는다.
        assertThat(workout(workoutId).exercises.sumOf { it.uncheckedCompletableSets.size }).isEqualTo(1)
        vm.finishWorkout(completeUnchecked = true)
        await("운동 종료") { events.any { it is WorkoutSessionEvent.Finished } }

        val saved = workout(workoutId)
        assertThat(saved.endTime).isNotNull()
        val savedFirst = saved.exercises.first { it.exercise.name == firstName }
        assertThat(savedFirst.sets.map { Triple(it.weightKg, it.reps, it.completed) })
            .containsExactly(Triple(80.0, 8, true), Triple(80.0, 7, true), Triple(80.0, 6, true)).inOrder()
        // 한 적 없는 세트는 어디에도 완료로 남지 않는다.
        assertThat(saved.exercises.sumOf { it.completedSets.size }).isEqualTo(3)
        assertThat(runBlocking { workouts.observeRecentWorkouts(5).first() }.map { it.id }).contains(workoutId)

        // ----- 다음 운동: 같은 루틴을 다시 시작하면 지난 기록이 기본값으로 채워져 있다 -----
        val nextId = runBlocking { workouts.startWorkout(routine.id) }
        val nextFirst = workout(nextId).exercises[0]
        assertThat(nextFirst.sets.take(3).map { it.weightKg to it.reps }).containsExactly(80.0 to 8, 80.0 to 7, 80.0 to 6).inOrder()
        assertThat(nextFirst.sets.none { it.completed }).isTrue()
        val last = runBlocking { workouts.getLastPerformance(nextFirst.exercise.id, nextId) }
        assertThat(last.map { it.weightKg to it.reps }).containsExactly(80.0 to 8, 80.0 to 7, 80.0 to 6).inOrder()
    }

    // =====================================================================================
    // 2. 러닝: 순환 코스를 만들고 → 골라서 → 한 바퀴 달리고 → 기록을 본다
    // =====================================================================================

    private var now = 1_700_000_000_000L

    private val noSteps = object : StepCounter {
        override fun isAvailable() = false
        override fun hasPermission() = false
        override fun cumulativeSteps() = emptyFlow<Long>()
    }
    private val gpsReady = object : LocationTracker {
        override fun isLocationAvailable() = true
        override fun hasLocationPermission() = true
        override fun locationUpdates(intervalMillis: Long) = emptyFlow<LocationSample>()
    }

    /** 집 앞에서 출발해 한 변 300m 정사각형을 돌아 집 앞으로 돌아오는 코스(약 1.2km). */
    private val loop: List<CoursePoint> = run {
        val lat0 = 37.5
        val lon0 = 127.0
        val dLat = 300.0 / 111_195.0
        val dLon = 300.0 / (111_195.0 * Math.cos(Math.toRadians(lat0)))
        listOf(
            CoursePoint(lat0, lon0),
            CoursePoint(lat0, lon0 + dLon),
            CoursePoint(lat0 + dLat, lon0 + dLon),
            CoursePoint(lat0 + dLat, lon0),
            CoursePoint(lat0, lon0),
        )
    }
    private val loopMeters = CourseGeometry.length(loop)

    /** 코스 위에서 출발점부터 [meters] 만큼 간 곳. */
    private fun onLoop(meters: Double): CoursePoint {
        var left = meters
        for (i in 0 until loop.size - 1) {
            val length = CourseGeometry.distance(loop[i], loop[i + 1])
            if (left <= length || i == loop.size - 2) {
                val t = (left / length).coerceIn(0.0, 1.0)
                return CoursePoint(
                    loop[i].latitude + (loop[i + 1].latitude - loop[i].latitude) * t,
                    loop[i].longitude + (loop[i + 1].longitude - loop[i].longitude) * t,
                )
            }
            left -= length
        }
        return loop.last()
    }

    /** 3초에 10m(약 5분 페이스)로 코스를 따라 달린다. 서비스가 하듯 위치마다 시간도 흘린다. */
    private suspend fun RunTracker.runOnLoop(from: Double, to: Double) {
        var d = from
        while (d <= to) {
            now += 3_000
            val p = onLoop(d)
            onLocation(LocationSample(p.latitude, p.longitude, accuracyMeters = 5f, timestamp = now))
            tick()
            // 서비스의 규칙: 목표에 닿으면 러닝을 끝낸다. 코스를 골랐다는 이유로 이 규칙이 걸리면 안 된다.
            val state = state.value
            check(!state.goal.isReached(state.distanceMeters, state.durationSeconds)) {
                "코스 러닝이 ${state.distanceMeters.toInt()}m 에서 목표 도달로 자동 종료될 뻔했어요"
            }
            d += 10.0
        }
    }

    @Test
    fun `running - draw a loop course, pick it, run one lap, and compare with an aborted run`() = runBlocking {
        // ----- 코스를 만들어 저장한다 -----
        val courseId = courses.save(Course(name = "집 앞 한 바퀴", points = loop))
        val course = courses.get(courseId)!!

        // ----- 러닝 시작 화면에서 코스를 고른다(목표는 건드리지 않고 자유 러닝 그대로) -----
        val tracker = RunTracker(runs, settings, noSteps) { now }
        val handoff = ActiveCourse()
        val setup = RunSetupViewModel(gpsReady, noSteps, RunServiceController(context), tracker, settings, courses, handoff)
            .also { vm -> cleanups += { vm.viewModelScope.cancel() } }
        val watching = CoroutineScope(dispatcher).launch { setup.uiState.collect { } }
        cleanups += { watching.cancel() }
        await("코스 목록이 화면에 옴") { setup.uiState.value.courses.size == 1 }
        setup.selectCourse(courseId)
        await("코스 선택") { setup.uiState.value.selectedCourse?.id == courseId }
        assertThat(setup.uiState.value.goalType).isEqualTo(RunGoalType.FREE)
        assertThat(setup.uiState.value.canStart).isTrue()

        // ----- 시작: 화면은 서비스를 띄우고, 서비스는 고른 코스를 한 번 가져가 기록을 시작한다 -----
        setup.startRun()
        val startIntent = shadowOf(context as Application).nextStartedService
        assertThat(startIntent.action).isEqualTo(RunTrackingService.ACTION_START)
        val goal = setup.uiState.value.toGoal()
        val taken = handoff.take()
        assertThat(taken).isEqualTo(course)
        assertThat(handoff.take()).isNull() // 다음 러닝에 따라붙지 않는다

        val cues = mutableListOf<RunCue>()
        val listening = CoroutineScope(Dispatchers.Unconfined).launch { tracker.cues.collect { cues += it } }
        cleanups += { listening.cancel() }
        tracker.start(goal, null, taken)

        // ----- 출발선 조금 앞(마지막 구간 위)에서 첫 신호가 잡힌다. 도착이 아니라 출발이다 -----
        now += 3_000
        onLoop(loopMeters - 12.0).let { tracker.onLocation(LocationSample(it.latitude, it.longitude, accuracyMeters = 5f, timestamp = now)) }
        assertThat(tracker.state.value.courseGuidance!!.finished).isFalse()
        assertThat(cues.filterIsInstance<RunCue.CourseFinished>()).isEmpty()

        // ----- 반 바퀴: 진행률이 맞고, 러닝은 계속된다 -----
        tracker.runOnLoop(0.0, 600.0)
        tracker.state.value.let { state ->
            assertThat(state.status).isEqualTo(RunStatus.TRACKING)
            assertThat(state.courseGuidance!!.progressMeters).isWithin(20.0).of(600.0)
            assertThat(state.courseGuidance!!.remainingMeters).isWithin(20.0).of(loopMeters - 600.0)
            assertThat(state.courseGuidance!!.offCourse).isFalse()
        }

        // ----- 끝까지: 도착 안내는 한 번만, 러닝은 내가 끝낼 때까지 계속된다 -----
        tracker.runOnLoop(610.0, loopMeters)
        assertThat(tracker.state.value.courseGuidance!!.finished).isTrue()
        assertThat(cues.filterIsInstance<RunCue.CourseFinished>()).hasSize(1)
        assertThat(cues.filterIsInstance<RunCue.OffCourse>()).isEmpty()
        assertThat(tracker.state.value.status).isEqualTo(RunStatus.TRACKING)
        assertThat(tracker.state.value.distanceMeters).isWithin(40.0).of(loopMeters + 12.0)

        // ----- 종료하고 기록을 본다 -----
        val runId = tracker.state.value.runId
        tracker.finish()
        val saved = runs.getRun(runId)!!
        assertThat(saved.courseName).isEqualTo("집 앞 한 바퀴")
        assertThat(saved.endTime).isNotNull()
        val stats = runs.courseStats(saved)!!
        assertThat(stats.count).isEqualTo(1)
        assertThat(stats.bestDurationSeconds).isEqualTo(saved.durationSeconds)

        // ----- 다음 날: 같은 코스를 100m 뛰다가 그만둔다. 그 러닝은 순위도 "최고 기록" 도 되지 않는다 -----
        now += 86_400_000L
        val second = RunTracker(runs, settings, noSteps) { now }
        second.start(goal, null, course)
        second.runOnLoop(0.0, 100.0)
        val abortedId = second.state.value.runId
        second.finish()
        val aborted = runs.getRun(abortedId)!!
        assertThat(aborted.durationSeconds).isLessThan(saved.durationSeconds)
        assertThat(runs.courseStats(aborted)).isNull()
        assertThat(runs.courseStats(runs.getRun(runId)!!)!!.bestDurationSeconds).isEqualTo(saved.durationSeconds)
    }

    // =====================================================================================
    // 3. 백업: 잘못 복원 → 다른 파일 거절 → 진행 중이라 거절 → 그래도 되돌릴 수 있다
    // =====================================================================================

    private fun finishedWorkout(exerciseName: String, kg: Double) = runBlocking {
        val exercise = exercises.observeExercises().first().first { it.name == exerciseName }
        val id = workouts.startWorkout(null)
        val workoutExerciseId = workouts.addExerciseToWorkout(id, exercise.id)
        workouts.setCompleted(db.workoutDao().getSets(workoutExerciseId).first().id, kg, 5, true)
        workouts.finishWorkout(id)
        id
    }

    private fun workoutCount() = runBlocking { db.backupDao().allWorkouts().size }

    @Test
    fun `backup - a wrong restore can still be undone after other restores were refused`() {
        val folder = File(context.cacheDir, "scenario-files-${System.nanoTime()}").apply { mkdirs() }
        val snapshotDir = File(context.cacheDir, "scenario-snap-${System.nanoTime()}")
        cleanups += { folder.deleteRecursively(); snapshotDir.deleteRecursively() }
        val vm = BackupViewModel(
            context,
            backup,
            settings,
            AutoBackupManager(backup, settings, FakeBackupFolder()),
            PreRestoreSnapshot(snapshotDir) { 1_000L },
        ).also { cleanups += { it.viewModelScope.cancel() } }
        fun done() = vm.uiState.value.let { !it.working && (it.message != null || it.error != null) }

        // ----- 운동 한 번 하고 파일로 백업해 둔다. 그 뒤 운동을 한 번 더 한다(진짜 기록 = 2회) -----
        finishedWorkout("벤치프레스", 60.0)
        val oldFile = File(folder, "old-backup.json")
        runBlocking { oldFile.outputStream().use { backup.exportBackup(it) } }
        finishedWorkout("스쿼트", 100.0)
        assertThat(workoutCount()).isEqualTo(2)
        assertThat(vm.uiState.value.snapshotAt).isNull()

        // ----- 실수: 오래된 파일로 복원해 버린다. 최근 운동이 사라졌지만, 복원 전 기록은 앱 안에 보관됐다 -----
        vm.restoreBackup(Uri.fromFile(oldFile))
        await("오래된 파일로 복원") { done() }
        assertThat(vm.uiState.value.error).isNull()
        assertThat(vm.uiState.value.message).contains("복원 완료")
        assertThat(workoutCount()).isEqualTo(1)
        assertThat(vm.uiState.value.snapshotAt).isNotNull()
        val kept = File(snapshotDir, "before-restore.json").readBytes()

        // ----- 다른 파일을 골라 봤는데 백업 파일이 아니다. 거절되고, 보관본은 그대로다 -----
        val notABackup = File(folder, "photo.jpg").apply { writeText("백업이 아닌 파일") }
        vm.restoreBackup(Uri.fromFile(notABackup))
        await("백업이 아닌 파일 거절") { done() }
        assertThat(vm.uiState.value.error).isNotNull()
        assertThat(workoutCount()).isEqualTo(1)
        assertThat(File(snapshotDir, "before-restore.json").readBytes()).isEqualTo(kept)

        // ----- 운동을 시작해 둔 채로 다시 복원을 시도한다. 진행 중이라 거절되고, 보관본은 그대로다 -----
        val inProgress = runBlocking { workouts.startWorkout(null) }
        vm.restoreBackup(Uri.fromFile(oldFile))
        await("진행 중이라 거절") { done() }
        assertThat(vm.uiState.value.error).contains("진행 중")
        assertThat(File(snapshotDir, "before-restore.json").readBytes()).isEqualTo(kept)
        assertThat(snapshotDir.list()!!.toList()).containsExactly("before-restore.json")
        runBlocking { workouts.discardWorkout(inProgress) }

        // ----- "복원 전으로 되돌리기": 진짜 기록(운동 2회)이 돌아온다 -----
        vm.restoreSnapshot()
        await("되돌리기") { done() }
        assertThat(vm.uiState.value.error).isNull()
        assertThat(workoutCount()).isEqualTo(2)
    }
}
