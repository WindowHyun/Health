package com.windowhyun.health.wear

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.shared.RestSnapshot
import com.windowhyun.health.shared.RunSnapshot
import com.windowhyun.health.shared.WatchCommand
import com.windowhyun.health.shared.WatchRunStatus
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
        val sent = mutableListOf<WatchCommand>()
        var delivers = true

        override val run = runFlow
        override val rest = restFlow
        override suspend fun send(command: WatchCommand): Boolean {
            sent += command
            return delivers
        }
    }

    private class FakeHaptics : WatchHaptics {
        var count = 0
        override fun restFinished() {
            count++
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
}
