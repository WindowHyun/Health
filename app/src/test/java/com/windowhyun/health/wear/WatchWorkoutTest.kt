package com.windowhyun.health.wear

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.RunStatus
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.model.WorkoutExerciseRecord
import com.windowhyun.health.domain.model.WorkoutSet
import com.windowhyun.health.domain.usecase.CurrentSetFinder
import com.windowhyun.health.service.RunControl
import com.windowhyun.health.shared.WatchCommand
import com.windowhyun.health.shared.WatchSetKind
import com.windowhyun.health.shared.WearCodec
import com.windowhyun.health.shared.WearProtocol
import com.windowhyun.health.shared.WorkoutSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

/** 시계에서 헬스 세트를 완료하고 러닝을 시작하는 규칙. 안드로이드 없이 JVM 에서 검증한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchWorkoutTest {

    private var nextId = 1L

    private fun set(
        number: Int,
        completed: Boolean = false,
        kg: Double = 60.0,
        reps: Int = 8,
        seconds: Int = 0,
    ) = WorkoutSet(id = nextId++, setNumber = number, weightKg = kg, reps = reps, completed = completed, durationSeconds = seconds)

    private fun record(
        name: String,
        sets: List<WorkoutSet>,
        group: Int = 0,
        type: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_REPS,
    ) = WorkoutExerciseRecord(
        id = nextId++,
        exercise = Exercise(nextId++, name, ExerciseCategory.BARBELL, BodyPart.CHEST, trackingType = type),
        orderIndex = 0,
        supersetGroup = group,
        sets = sets,
    )

    private fun workout(vararg records: WorkoutExerciseRecord, endTime: Long? = null) = Workout(
        id = 1, date = LocalDate.of(2026, 10, 7), startTime = 0, endTime = endTime, exercises = records.toList(),
    )

    // ----- 지금 할 세트 -----

    @Test
    fun `the current set is the first unfinished one`() {
        val bench = record("벤치", listOf(set(1, completed = true), set(2), set(3)))
        val squat = record("스쿼트", listOf(set(1), set(2)))

        val current = CurrentSetFinder.find(listOf(bench, squat))!!

        assertThat(current.record.exercise.name).isEqualTo("벤치")
        assertThat(current.set.setNumber).isEqualTo(2)
    }

    @Test
    fun `finished exercises are skipped and nothing is left when all are done`() {
        val bench = record("벤치", listOf(set(1, completed = true), set(2, completed = true)))
        val squat = record("스쿼트", listOf(set(1)))

        assertThat(CurrentSetFinder.find(listOf(bench, squat))!!.record.exercise.name).isEqualTo("스쿼트")
        assertThat(CurrentSetFinder.find(listOf(bench))).isNull()
        assertThat(CurrentSetFinder.find(emptyList())).isNull()
    }

    /** 슈퍼셋은 A, B, A, B 로 번갈아 한다. 끝낸 세트가 적은 쪽 차례다. */
    @Test
    fun `a superset alternates between its exercises`() {
        fun pair(aDone: Int, bDone: Int): String {
            val a = record("A", List(3) { set(it + 1, completed = it < aDone) }, group = 1)
            val b = record("B", List(3) { set(it + 1, completed = it < bDone) }, group = 1)
            return CurrentSetFinder.find(listOf(a, b))!!.record.exercise.name
        }

        assertThat(pair(0, 0)).isEqualTo("A")
        assertThat(pair(1, 0)).isEqualTo("B")
        assertThat(pair(1, 1)).isEqualTo("A")
        assertThat(pair(3, 2)).isEqualTo("B")
    }

    /** 묶음 한쪽을 다 끝냈으면 남은 쪽만 한다. */
    @Test
    fun `a finished half of a superset does not block the other`() {
        val a = record("A", listOf(set(1, completed = true)), group = 1)
        val b = record("B", listOf(set(1), set(2)), group = 1)

        assertThat(CurrentSetFinder.find(listOf(a, b))!!.record.exercise.name).isEqualTo("B")
    }

    /** 묶음에서 이미 다 끝낸 종목이 끝낸 세트가 더 적다고 다시 뽑히면 안 된다(할 세트가 없다). */
    @Test
    fun `a finished member with fewer sets is never picked again`() {
        val short = record("짧은 것", listOf(set(1, completed = true)), group = 1)
        val long = record("긴 것", listOf(set(1, completed = true), set(2, completed = true), set(3)), group = 1)

        val current = CurrentSetFinder.find(listOf(short, long))!!

        assertThat(current.record.exercise.name).isEqualTo("긴 것")
        assertThat(current.set.setNumber).isEqualTo(3)
    }

    @Test
    fun `an exercise outside the group is not mixed in`() {
        val a = record("A", listOf(set(1)), group = 1)
        val b = record("B", listOf(set(1)), group = 1)
        val c = record("C", listOf(set(1)))

        assertThat(CurrentSetFinder.find(listOf(c, a, b))!!.record.exercise.name).isEqualTo("C")
    }

    // ----- 시계로 보낼 모양 -----

    @Test
    fun `a workout maps to the set the watch should show`() {
        val bench = record("벤치프레스", listOf(set(1, completed = true), set(2, kg = 62.5, reps = 6), set(3)))

        val snapshot = workout(bench).toSnapshot(useLb = true)

        assertThat(snapshot.active).isTrue()
        assertThat(snapshot.exerciseName).isEqualTo("벤치프레스")
        assertThat(snapshot.setNumber).isEqualTo(2)
        assertThat(snapshot.setCount).isEqualTo(3)
        assertThat(snapshot.weightKg).isEqualTo(62.5)
        assertThat(snapshot.reps).isEqualTo(6)
        assertThat(snapshot.useLb).isTrue()
        assertThat(snapshot.setId).isEqualTo(bench.sets[1].id)
        assertThat(snapshot.canComplete).isTrue()
    }

    @Test
    fun `tracking type becomes the watch set kind`() {
        fun kind(type: ExerciseTrackingType) =
            workout(record("x", listOf(set(1, seconds = 30)), type = type)).toSnapshot(false).kind

        assertThat(kind(ExerciseTrackingType.WEIGHT_REPS)).isEqualTo(WatchSetKind.WEIGHT_REPS)
        assertThat(kind(ExerciseTrackingType.REPS_ONLY)).isEqualTo(WatchSetKind.REPS_ONLY)
        assertThat(kind(ExerciseTrackingType.TIME)).isEqualTo(WatchSetKind.TIME)
    }

    @Test
    fun `a finished workout shows nothing and a done one says so`() {
        val done = record("벤치", listOf(set(1, completed = true)))

        assertThat(workout(done, endTime = 99).toSnapshot(false)).isEqualTo(WorkoutSnapshot.None)

        val allDone = workout(done).toSnapshot(false)
        assertThat(allDone.active).isTrue()
        assertThat(allDone.allDone).isTrue()
        assertThat(allDone.canComplete).isFalse()
    }

    @Test
    fun `an empty set cannot be completed from the watch`() {
        val empty = record("벤치", listOf(set(1, reps = 0)))

        assertThat(workout(empty).toSnapshot(false).canComplete).isFalse()
    }

    // ----- 다리(WatchLink) -----

    private fun activeSnapshot(setId: Long = 5) =
        WorkoutSnapshot(active = true, setId = setId, exerciseName = "벤치", setNumber = 1, setCount = 3, reps = 8)

    @Test
    fun `a completion for the set the watch is showing is passed on`() = runTest {
        val link = WatchLink { 1_000 }
        val seen = mutableListOf<Long>()
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            link.setCompletions.collect { seen += it }
        }
        link.publishWorkout(activeSnapshot(setId = 5))

        assertThat(link.emitSetCompletion(5)).isTrue()
        assertThat(seen).containsExactly(5L)
        job.cancel()
    }

    /** 그사이 폰에서 먼저 끝낸 세트를 시계가 또 누르면, 다음 세트가 엉뚱하게 끝나지 않는다. */
    @Test
    fun `a completion for another set is ignored`() {
        val link = WatchLink { 1_000 }
        link.publishWorkout(activeSnapshot(setId = 6))

        assertThat(link.emitSetCompletion(5)).isFalse()
    }

    @Test
    fun `nothing is passed on without a workout or for an empty set`() {
        val link = WatchLink { 1_000 }
        assertThat(link.emitSetCompletion(5)).isFalse()

        link.publishWorkout(activeSnapshot(setId = 5).copy(reps = 0))
        assertThat(link.emitSetCompletion(5)).isFalse()

        link.publishWorkout(activeSnapshot(setId = 5))
        link.clearWorkout()
        assertThat(link.emitSetCompletion(5)).isFalse()
    }

    @Test
    fun `publishing stamps the send time`() {
        val link = WatchLink { 7_777 }
        link.publishWorkout(activeSnapshot())

        assertThat(link.workout.value.sentAtMillis).isEqualTo(7_777)
    }

    // ----- 명령 처리 -----

    private class Control : RunControl {
        override fun pause() = Unit
        override fun resume() = Unit
        override fun stop() = Unit
    }

    private class Starter(var result: Boolean = true) : RunStarter {
        var requests = 0
        override fun requestStart(): Boolean {
            requests++
            return result
        }
    }

    private fun handler(status: RunStatus, starter: Starter = Starter(), link: WatchLink = WatchLink { 0 }) =
        WatchCommandHandler(Control(), { status }, link, starter)

    @Test
    fun `a run can be started only while idle`() {
        RunStatus.entries.forEach { status ->
            val starter = Starter()
            val handled = handler(status, starter).handle(WatchCommand.RUN_START)

            assertThat(handled).isEqualTo(status == RunStatus.IDLE)
            assertThat(starter.requests).isEqualTo(if (status == RunStatus.IDLE) 1 else 0)
        }
    }

    @Test
    fun `the watch is told no when the phone could not start`() {
        assertThat(handler(RunStatus.IDLE, Starter(result = false)).handle(WatchCommand.RUN_START)).isFalse()
    }

    @Test
    fun `set completion needs the set number in the message`() {
        val link = WatchLink { 1_000 }
        link.publishWorkout(activeSnapshot(setId = 5))
        val handler = handler(RunStatus.IDLE, link = link)

        assertThat(handler.handle(WatchCommand.SET_COMPLETE, null)).isFalse()
        assertThat(handler.handle(WatchCommand.SET_COMPLETE, ByteArray(0))).isFalse()
        assertThat(handler.handle(WatchCommand.SET_COMPLETE, "abc".toByteArray())).isFalse()
        assertThat(handler.handle(WatchCommand.SET_COMPLETE, WearCodec.encodeSetId(9))).isFalse()
        assertThat(handler.handle(WatchCommand.SET_COMPLETE, WearCodec.encodeSetId(5))).isTrue()
    }

    // ----- 전송 -----

    @Test
    fun `the publisher sends the current set and skips repeats`() = runTest {
        val sent = mutableListOf<Pair<String, ByteArray>>()
        val workout = MutableStateFlow(WorkoutSnapshot.None)
        val publisher = WatchStatePublisher(
            runState = MutableStateFlow(com.windowhyun.health.domain.model.RunTrackingState()),
            useMiles = MutableStateFlow(false),
            rest = MutableStateFlow(com.windowhyun.health.shared.RestSnapshot.None),
            transport = object : WatchTransport {
                override suspend fun put(path: String, bytes: ByteArray) {
                    sent += path to bytes
                }
            },
            scope = backgroundScope,
            clock = { 1_000 },
            heartbeat = kotlinx.coroutines.flow.MutableSharedFlow(),
            workout = workout,
        )
        publisher.start()
        runCurrent()

        workout.value = activeSnapshot(setId = 5)
        runCurrent()
        // 보낸 시각만 다른 같은 상태는 다시 보내지 않는다.
        workout.value = activeSnapshot(setId = 5).copy(sentAtMillis = 99)
        runCurrent()
        workout.value = activeSnapshot(setId = 6)
        runCurrent()

        val workouts = sent.filter { it.first == WearProtocol.PATH_WORKOUT_STATE }
            .map { WearCodec.decodeWorkout(it.second)!! }
        assertThat(workouts.map { it.setId }).containsExactly(0L, 5L, 6L).inOrder()
    }

    /** 세트가 바뀌지 않아도 진행 중이면 주기적으로 다시 보낸다. 시계가 폰 앱이 죽었는지 알 수 있게. */
    @Test
    fun `the publisher repeats an active set as a heartbeat but never an empty one`() = runTest {
        var now = 1_000_000L
        val sent = mutableListOf<Pair<String, ByteArray>>()
        val tick = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()
        val workout = MutableStateFlow(WorkoutSnapshot.None)
        val publisher = WatchStatePublisher(
            runState = MutableStateFlow(com.windowhyun.health.domain.model.RunTrackingState()),
            useMiles = MutableStateFlow(false),
            rest = MutableStateFlow(com.windowhyun.health.shared.RestSnapshot.None),
            transport = object : WatchTransport {
                override suspend fun put(path: String, bytes: ByteArray) {
                    sent += path to bytes
                }
            },
            scope = backgroundScope,
            clock = { now },
            heartbeat = tick,
            workout = workout,
        )
        publisher.start()
        runCurrent()
        fun sets() = sent.filter { it.first == WearProtocol.PATH_WORKOUT_STATE }.map { WearCodec.decodeWorkout(it.second)!! }

        // 세트가 없을 때는 시간이 지나도 다시 보내지 않는다.
        now += WearProtocol.HEARTBEAT_MILLIS * 3
        tick.emit(Unit)
        runCurrent()
        assertThat(sets()).hasSize(1)

        workout.value = activeSnapshot(setId = 5)
        runCurrent()
        assertThat(sets()).hasSize(2)

        // 같은 세트라도 너무 이르면 다시 보내지 않고, 간격이 지나면 보낸 시각을 새로 해서 보낸다.
        now += WearProtocol.HEARTBEAT_MILLIS - 1
        tick.emit(Unit)
        runCurrent()
        assertThat(sets()).hasSize(2)

        now += 1
        tick.emit(Unit)
        runCurrent()
        assertThat(sets()).hasSize(3)
        assertThat(sets().last().sentAtMillis).isEqualTo(now)
        assertThat(sets().last().setId).isEqualTo(5L)
    }
}
