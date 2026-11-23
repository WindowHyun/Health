package com.windowhyun.health.wear

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.shared.CommandAck
import com.windowhyun.health.shared.RestSnapshot
import com.windowhyun.health.shared.RunSnapshot
import com.windowhyun.health.shared.WatchCommand
import com.windowhyun.health.shared.WatchRunStatus
import com.windowhyun.health.shared.WearCodec
import com.windowhyun.health.shared.WorkoutSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import androidx.lifecycle.viewModelScope

@OptIn(ExperimentalCoroutinesApi::class)
class WatchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeSource : WatchDataSource {
        val runFlow = MutableSharedFlow<RunSnapshot>(replay = 1)
        val restFlow = MutableSharedFlow<RestSnapshot>(replay = 1)
        val workoutFlow = MutableSharedFlow<WorkoutSnapshot>(replay = 1)
        val ackFlow = MutableSharedFlow<CommandAck>()
        val sent = mutableListOf<WatchCommand>()
        val payloads = mutableListOf<ByteArray>()
        var delivers = true

        override val run = runFlow
        override val rest = restFlow
        override val workout = workoutFlow
        override val acks = ackFlow
        override suspend fun send(command: WatchCommand, payload: ByteArray): Boolean {
            sent += command
            payloads += payload
            return delivers
        }
    }

    private class FakeHaptics : WatchHaptics {
        var count = 0
        var accepted = 0
        var rejected = 0
        override fun restFinished(endsAtMillis: Long) {
            count++
        }

        override fun commandAccepted() {
            accepted++
        }

        override fun commandRejected() {
            rejected++
        }
    }

    private val source = FakeSource()
    private val haptics = FakeHaptics()
    private var wall = 1_000_000L

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): WatchViewModel {
        val viewModel = WatchViewModel(source, haptics) { wall + testScheduler.currentTime }
        runCurrent()
        return viewModel
    }

    private suspend fun TestScope.withViewModel(block: suspend TestScope.(WatchViewModel) -> Unit) {
        val viewModel = viewModel()
        try {
            block(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    private fun tracking(sentAt: Long = wall) =
        RunSnapshot(status = WatchRunStatus.TRACKING, distanceMeters = 1_000.0, elapsedSeconds = 300, sentAtMillis = sentAt)

    private fun rest(endsAt: Long, total: Int = 60) =
        RestSnapshot(active = true, totalSeconds = total, endsAtMillis = endsAt, sentAtMillis = wall)

    @Test
    fun `starts empty and shows what the phone sends`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            assertThat(viewModel.state.value.screen).isEqualTo(WatchScreen.IDLE)

            source.runFlow.emit(tracking())
            runCurrent()
            assertThat(viewModel.state.value.screen).isEqualTo(WatchScreen.RUN)
            assertThat(viewModel.state.value.run.distanceMeters).isEqualTo(1_000.0)

            source.restFlow.emit(rest(endsAt = wall + 60_000))
            runCurrent()
            assertThat(viewModel.state.value.screen).isEqualTo(WatchScreen.REST)
        }
    }

    @Test
    fun `the clock ticks while the screen is open`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            source.runFlow.emit(tracking())
            runCurrent()
            val first = viewModel.state.value.runElapsedSeconds

            advanceTimeBy(3_000)

            assertThat(viewModel.state.value.runElapsedSeconds).isAtLeast(first + 2)
        }
    }

    // ----- 명령 -----

    @Test
    fun `a command is sent to the phone`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            viewModel.send(WatchCommand.RUN_PAUSE)
            runCurrent()

            assertThat(source.sent).containsExactly(WatchCommand.RUN_PAUSE)
            assertThat(viewModel.state.value.sendFailed).isFalse()
        }
    }

    @Test
    fun `a command that could not be delivered is reported and can be dismissed`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            source.delivers = false
            viewModel.send(WatchCommand.REST_SKIP)
            runCurrent()
            assertThat(viewModel.state.value.sendFailed).isTrue()

            source.delivers = true
            viewModel.send(WatchCommand.REST_SKIP)
            runCurrent()
            assertThat(viewModel.state.value.sendFailed).isFalse()

            source.delivers = false
            viewModel.send(WatchCommand.REST_ADD)
            runCurrent()
            viewModel.dismissSendFailure()
            assertThat(viewModel.state.value.sendFailed).isFalse()
        }
    }

    // ----- 종료 확인 -----

    @Test
    fun `stopping needs a second press`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            viewModel.requestStop()
            runCurrent()
            assertThat(viewModel.state.value.confirmingStop).isTrue()
            assertThat(source.sent).isEmpty()

            viewModel.confirmStop()
            runCurrent()
            assertThat(viewModel.state.value.confirmingStop).isFalse()
            assertThat(source.sent).containsExactly(WatchCommand.RUN_STOP)
        }
    }

    @Test
    fun `cancelling the stop sends nothing`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            viewModel.requestStop()
            viewModel.cancelStop()
            runCurrent()

            assertThat(viewModel.state.value.confirmingStop).isFalse()
            assertThat(source.sent).isEmpty()
        }
    }

    /** 손목이 스친 한 번으로 러닝이 끝나지 않고, 내버려 두면 확인 창도 저절로 닫힌다. */
    @Test
    fun `an unanswered stop question closes by itself and sends nothing`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            viewModel.requestStop()
            advanceTimeBy(WatchViewModel.STOP_CONFIRM_MILLIS - 100)
            assertThat(viewModel.state.value.confirmingStop).isTrue()

            advanceTimeBy(200)

            assertThat(viewModel.state.value.confirmingStop).isFalse()
            assertThat(source.sent).isEmpty()
        }
    }

    @Test
    fun `asking again restarts the countdown`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            viewModel.requestStop()
            advanceTimeBy(4_000)
            viewModel.requestStop()
            advanceTimeBy(4_000)

            assertThat(viewModel.state.value.confirmingStop).isTrue()
        }
    }

    // ----- 휴식 끝 진동 -----

    @Test
    fun `the wrist buzzes once when the rest ends`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            source.restFlow.emit(rest(endsAt = wall + 2_000, total = 2))
            runCurrent()
            advanceTimeBy(1_500)
            assertThat(haptics.count).isEqualTo(0)

            advanceTimeBy(1_000)
            assertThat(haptics.count).isEqualTo(1)

            advanceTimeBy(5_000) // 계속 0 을 보여 주고 있어도 다시 울리지 않는다
            assertThat(haptics.count).isEqualTo(1)
        }
    }

    @Test
    fun `adding time after the buzz makes it buzz again when the new end comes`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            source.restFlow.emit(rest(endsAt = wall + 1_000, total = 1))
            runCurrent()
            advanceTimeBy(1_500)
            assertThat(haptics.count).isEqualTo(1)

            source.restFlow.emit(rest(endsAt = wall + 4_000, total = 4)) // +15초를 누른 것처럼 새 끝 시각
            runCurrent()
            advanceTimeBy(1_000)
            assertThat(haptics.count).isEqualTo(1)

            advanceTimeBy(3_000)
            assertThat(haptics.count).isEqualTo(2)
        }
    }

    @Test
    fun `a paused rest never buzzes`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            source.restFlow.emit(
                RestSnapshot(active = true, totalSeconds = 60, paused = true, pausedRemainingMillis = 30_000, sentAtMillis = wall),
            )
            runCurrent()

            advanceTimeBy(120_000)

            assertThat(haptics.count).isEqualTo(0)
        }
    }

    @Test
    fun `no rest no buzz`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            source.restFlow.emit(RestSnapshot.None)
            runCurrent()
            advanceTimeBy(60_000)

            assertThat(haptics.count).isEqualTo(0)
        }
    }

    // ----- 헬스 세트 / 러닝 시작 -----

    private fun workout(setId: Long = 11, reps: Int = 8, sentAt: Long = wall) = WorkoutSnapshot(
        active = true, setId = setId, exerciseName = "벤치프레스", setNumber = 2, setCount = 4,
        weightKg = 62.5, reps = reps, sentAtMillis = sentAt,
    )

    @Test
    fun `the current set shows once the phone sends it`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            source.workoutFlow.emit(workout())
            runCurrent()

            assertThat(viewModel.state.value.screen).isEqualTo(WatchScreen.WORKOUT)
            assertThat(viewModel.state.value.workout.exerciseName).isEqualTo("벤치프레스")
        }
    }

    /** 어느 세트를 끝내는지 번호를 실어 보낸다. 폰이 그사이 끝낸 세트를 또 끝내지 않게 하려는 것이다. */
    @Test
    fun `completing a set sends which set it is`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            source.workoutFlow.emit(workout(setId = 11))
            runCurrent()

            viewModel.completeSet()
            runCurrent()

            assertThat(source.sent).containsExactly(WatchCommand.SET_COMPLETE)
            assertThat(WearCodec.decodeSetId(source.payloads.single())).isEqualTo(11L)
        }
    }

    @Test
    fun `an empty set is not sent`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            source.workoutFlow.emit(workout(reps = 0))
            runCurrent()

            viewModel.completeSet()
            runCurrent()

            assertThat(source.sent).isEmpty()
        }
    }

    @Test
    fun `completing with no set on screen sends nothing`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            viewModel.completeSet()
            runCurrent()

            assertThat(source.sent).isEmpty()
        }
    }

    @Test
    fun `a set that could not be sent is reported`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            source.workoutFlow.emit(workout())
            runCurrent()
            source.delivers = false

            viewModel.completeSet()
            runCurrent()

            assertThat(viewModel.state.value.sendFailed).isTrue()
        }
    }

    @Test
    fun `asking the phone to start a run is remembered for a moment`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            viewModel.startRun()
            runCurrent()

            assertThat(source.sent).containsExactly(WatchCommand.RUN_START)
            assertThat(viewModel.state.value.startPending).isTrue()

            advanceTimeBy(START_HINT_MILLIS + 1_000)
            assertThat(viewModel.state.value.startPending).isFalse()
        }
    }

    @Test
    fun `a start that never reached the phone shows no waiting hint`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            source.delivers = false

            viewModel.startRun()
            runCurrent()

            assertThat(viewModel.state.value.sendFailed).isTrue()
            assertThat(viewModel.state.value.startPending).isFalse()
        }
    }

    // ----- 폰의 응답 -----

    @Test
    fun `a refusal from the phone is shown and buzzes`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            source.ackFlow.emit(CommandAck(WatchCommand.SET_COMPLETE, accepted = false))
            runCurrent()

            assertThat(viewModel.state.value.problemText).isEqualTo(WatchFormat.rejection(WatchCommand.SET_COMPLETE))
            assertThat(haptics.rejected).isEqualTo(1)
            assertThat(haptics.accepted).isEqualTo(0)
        }
    }

    @Test
    fun `an accepted command gives a small tick and clears an old refusal`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            source.ackFlow.emit(CommandAck(WatchCommand.REST_SKIP, accepted = false))
            runCurrent()
            source.ackFlow.emit(CommandAck(WatchCommand.REST_SKIP, accepted = true))
            runCurrent()

            assertThat(haptics.accepted).isEqualTo(1)
            assertThat(viewModel.state.value.problemText).isNull()
        }
    }

    @Test
    fun `a refused run start drops the waiting hint`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            viewModel.startRun()
            runCurrent()
            assertThat(viewModel.state.value.startPending).isTrue()

            source.ackFlow.emit(CommandAck(WatchCommand.RUN_START, accepted = false))
            runCurrent()

            assertThat(viewModel.state.value.startPending).isFalse()
            assertThat(viewModel.state.value.problemText).contains("직접 시작")
        }
    }

    @Test
    fun `sending a new command forgets the earlier refusal`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            source.ackFlow.emit(CommandAck(WatchCommand.REST_ADD, accepted = false))
            runCurrent()

            viewModel.send(WatchCommand.REST_SKIP)
            runCurrent()

            assertThat(viewModel.state.value.rejectedCommand).isNull()
        }
    }

    // ----- 실패 안내는 오래 남지 않는다 -----

    @Test
    fun `a send failure fades after a few seconds`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            source.delivers = false
            viewModel.send(WatchCommand.REST_SKIP)
            runCurrent()
            assertThat(viewModel.state.value.problemText).isEqualTo("폰에 보내지 못했어요")

            advanceTimeBy(SEND_FAILED_MILLIS + 1_000)

            assertThat(viewModel.state.value.problemText).isNull()
        }
    }

    // ----- 한 번 실패해도 계속 듣는다 -----

    private class FlakySource : WatchDataSource {
        var attempts = 0
        override val run = kotlinx.coroutines.flow.flow<RunSnapshot> {
            attempts++
            if (attempts == 1) throw java.io.IOException("Play 서비스가 아직 준비되지 않았다")
            emit(RunSnapshot(status = WatchRunStatus.TRACKING, distanceMeters = 500.0, sentAtMillis = 1_000_000))
            kotlinx.coroutines.awaitCancellation()
        }
        override val rest = kotlinx.coroutines.flow.emptyFlow<RestSnapshot>()
        override val workout = kotlinx.coroutines.flow.emptyFlow<WorkoutSnapshot>()
        override suspend fun send(command: WatchCommand, payload: ByteArray) = true
    }

    @Test
    fun `a failed connection is retried instead of freezing the screen`() = runTest(dispatcher) {
        val flaky = FlakySource()
        val viewModel = WatchViewModel(flaky, haptics) { wall + testScheduler.currentTime }
        try {
            runCurrent()
            assertThat(flaky.attempts).isEqualTo(1)
            assertThat(viewModel.state.value.run.distanceMeters).isEqualTo(0.0)

            advanceTimeBy(WatchViewModel.retryDelayMillis(0) + 100)

            assertThat(flaky.attempts).isEqualTo(2)
            assertThat(viewModel.state.value.run.distanceMeters).isEqualTo(500.0)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `the wait before listening again grows but is capped`() {
        val delays = (0L..10L).map { WatchViewModel.retryDelayMillis(it) }
        assertThat(delays).isInOrder()
        assertThat(delays.first()).isEqualTo(1_000)
        assertThat(delays.last()).isAtMost(30_000)
    }

    // ----- 화면이 안 보일 때 -----

    @Test
    fun `the clock stops ticking while the screen is hidden and resumes when shown`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            source.runFlow.emit(tracking())
            runCurrent()

            viewModel.setScreenVisible(false)
            runCurrent()
            val frozen = viewModel.state.value.nowMillis
            advanceTimeBy(10_000)
            assertThat(viewModel.state.value.nowMillis).isEqualTo(frozen)

            viewModel.setScreenVisible(true)
            runCurrent()
            // 다시 보이는 즉시 지금 시각으로 맞춘다.
            assertThat(viewModel.state.value.nowMillis).isEqualTo(wall + testScheduler.currentTime)
        }
    }

    @Test
    fun `no buzz for a rest that ended long before the app was opened`() = runTest(dispatcher) {
        withViewModel { viewModel ->
            // 끝난 지 30초가 지난 휴식이 폰에서 지워지지 않은 채 남아 있다(알람은 그때 이미 울렸다).
            source.restFlow.emit(rest(endsAt = wall - 30_000))
            runCurrent()
            advanceTimeBy(2_000)

            assertThat(haptics.count).isEqualTo(0)
        }
    }
}
