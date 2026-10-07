package com.windowhyun.health.wear

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.domain.model.RunGoal
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.model.RunStatus
import com.windowhyun.health.domain.model.RunTrackingState
import com.windowhyun.health.service.RunControl
import com.windowhyun.health.shared.RestSnapshot
import com.windowhyun.health.shared.RunSnapshot
import com.windowhyun.health.shared.WatchCommand
import com.windowhyun.health.shared.WatchRunStatus
import com.windowhyun.health.shared.WearCodec
import com.windowhyun.health.shared.WearProtocol
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

/** 폰 → 시계 상태 전송과 시계 → 폰 명령 처리. 안드로이드 없이 JVM 에서 검증한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchBridgeTest {

    // ----- 스냅샷 변환 -----

    @Test
    fun `tracking state maps to a watch snapshot`() {
        val state = RunTrackingState(
            status = RunStatus.TRACKING,
            goal = RunGoal(RunGoalType.DISTANCE, 5_000.0),
            distanceMeters = 2_000.0,
            durationSeconds = 600,
            currentPaceSecPerKm = 300.0,
            averagePaceSecPerKm = 310.0,
            signalLost = true,
        )

        val snapshot = state.toSnapshot(useMiles = true, nowMillis = 42)

        assertThat(snapshot.status).isEqualTo(WatchRunStatus.TRACKING)
        assertThat(snapshot.distanceMeters).isEqualTo(2_000.0)
        assertThat(snapshot.elapsedSeconds).isEqualTo(600)
        assertThat(snapshot.currentPaceSecPerKm).isEqualTo(300.0)
        assertThat(snapshot.averagePaceSecPerKm).isEqualTo(310.0)
        assertThat(snapshot.signalLost).isTrue()
        assertThat(snapshot.useMiles).isTrue()
        assertThat(snapshot.goalProgress).isWithin(0.001f).of(0.4f)
        assertThat(snapshot.sentAtMillis).isEqualTo(42)
    }

    @Test
    fun `every run status has a watch status`() {
        val expected = mapOf(
            RunStatus.IDLE to WatchRunStatus.IDLE,
            RunStatus.TRACKING to WatchRunStatus.TRACKING,
            RunStatus.PAUSED to WatchRunStatus.PAUSED,
            RunStatus.FINISHED to WatchRunStatus.FINISHED,
        )
        RunStatus.entries.forEach { status ->
            assertThat(RunTrackingState(status = status).toSnapshot(false, 0).status).isEqualTo(expected[status])
        }
    }

    @Test
    fun `auto pause is passed along and a free run has no goal progress`() {
        val snapshot = RunTrackingState(status = RunStatus.PAUSED, autoPaused = true).toSnapshot(false, 0)

        assertThat(snapshot.autoPaused).isTrue()
        assertThat(snapshot.goalProgress).isNull()
    }

    // ----- 보내는 규칙 -----

    private fun snap(
        status: WatchRunStatus = WatchRunStatus.TRACKING,
        distance: Double = 0.0,
        at: Long = 0,
        autoPaused: Boolean = false,
        signalLost: Boolean = false,
        miles: Boolean = false,
    ) = RunSnapshot(
        status = status,
        distanceMeters = distance,
        autoPaused = autoPaused,
        signalLost = signalLost,
        useMiles = miles,
        sentAtMillis = at,
    )

    @Test
    fun `the first snapshot is always sent`() {
        assertThat(RunPublishPolicy.shouldSend(null, snap(WatchRunStatus.IDLE))).isTrue()
    }

    @Test
    fun `a change of state is sent right away`() {
        val last = snap(at = 0)

        assertThat(RunPublishPolicy.shouldSend(last, snap(WatchRunStatus.PAUSED, at = 100))).isTrue()
        assertThat(RunPublishPolicy.shouldSend(last, snap(autoPaused = true, at = 100))).isTrue()
        assertThat(RunPublishPolicy.shouldSend(last, snap(signalLost = true, at = 100))).isTrue()
        assertThat(RunPublishPolicy.shouldSend(last, snap(miles = true, at = 100))).isTrue()
    }

    @Test
    fun `small or quick changes are held back`() {
        val last = snap(distance = 100.0, at = 0)

        // 3초가 안 지났으면 거리가 늘어도 기다린다
        assertThat(RunPublishPolicy.shouldSend(last, snap(distance = 130.0, at = 2_999))).isFalse()
        // 3초가 지나도 5m 도 안 늘었으면 보낼 이유가 없다
        assertThat(RunPublishPolicy.shouldSend(last, snap(distance = 103.0, at = 3_500))).isFalse()
        // 3초가 지났고 5m 이상 늘었다
        assertThat(RunPublishPolicy.shouldSend(last, snap(distance = 105.0, at = 3_000))).isTrue()
    }

    @Test
    fun `a heartbeat is sent even when nothing changed`() {
        val last = snap(distance = 100.0, at = 0)

        assertThat(RunPublishPolicy.shouldSend(last, snap(distance = 100.0, at = WearProtocol.HEARTBEAT_MILLIS - 1))).isFalse()
        assertThat(RunPublishPolicy.shouldSend(last, snap(distance = 100.0, at = WearProtocol.HEARTBEAT_MILLIS))).isTrue()
    }

    @Test
    fun `idle and finished runs are not resent`() {
        assertThat(RunPublishPolicy.shouldSend(snap(WatchRunStatus.IDLE, at = 0), snap(WatchRunStatus.IDLE, at = 999_999))).isFalse()
        assertThat(RunPublishPolicy.shouldSend(snap(WatchRunStatus.FINISHED, at = 0), snap(WatchRunStatus.FINISHED, at = 999_999))).isFalse()
    }

    // ----- 전송 -----

    private class FakeTransport : WatchTransport {
        val sent = mutableListOf<Pair<String, ByteArray>>()
        var failing = false

        override suspend fun put(path: String, bytes: ByteArray) {
            if (failing) throw IOException("no watch")
            sent += path to bytes
        }

        fun runs() = sent.filter { it.first == WearProtocol.PATH_RUN_STATE }.map { WearCodec.decodeRun(it.second)!! }
        fun rests() = sent.filter { it.first == WearProtocol.PATH_REST_STATE }.map { WearCodec.decodeRest(it.second)!! }
    }

    private class Harness(scope: TestScope) {
        var now = 1_000_000L
        val run = MutableStateFlow(RunTrackingState())
        val miles = MutableStateFlow(false)
        val link = WatchLink { now }
        val transport = FakeTransport()
        val tick = MutableSharedFlow<Unit>()
        val publisher = WatchStatePublisher(
            runState = run,
            useMiles = miles,
            rest = link.rest,
            transport = transport,
            scope = scope.backgroundScope,
            clock = { now },
            heartbeat = tick,
        )
    }

    @Test
    fun `publisher sends the idle state first and then follows the run`() = runTest {
        val h = Harness(this)
        h.publisher.start()
        runCurrent()
        assertThat(h.transport.runs().map { it.status }).containsExactly(WatchRunStatus.IDLE)

        h.run.value = RunTrackingState(status = RunStatus.TRACKING, distanceMeters = 10.0, durationSeconds = 5)
        runCurrent()
        assertThat(h.transport.runs().last().status).isEqualTo(WatchRunStatus.TRACKING)

        h.run.value = h.run.value.copy(status = RunStatus.PAUSED)
        runCurrent()
        assertThat(h.transport.runs().last().status).isEqualTo(WatchRunStatus.PAUSED)
    }

    @Test
    fun `publisher does not flood the watch with every second`() = runTest {
        val h = Harness(this)
        h.publisher.start()
        h.run.value = RunTrackingState(status = RunStatus.TRACKING)
        runCurrent()
        val before = h.transport.runs().size

        // 1초마다 아주 조금씩 늘어난다(실제 러닝처럼). 3초 · 5m 규칙에 걸려 대부분 안 보낸다.
        repeat(10) { second ->
            h.now += 1_000
            h.run.value = h.run.value.copy(distanceMeters = 3.0 * (second + 1), durationSeconds = (second + 1).toLong())
            runCurrent()
        }

        val sentWhileRunning = h.transport.runs().size - before
        assertThat(sentWhileRunning).isAtMost(4)
        assertThat(sentWhileRunning).isAtLeast(1)
    }

    @Test
    fun `publisher sends a heartbeat while paused`() = runTest {
        val h = Harness(this)
        h.publisher.start()
        h.run.value = RunTrackingState(status = RunStatus.PAUSED, durationSeconds = 100)
        runCurrent()
        val before = h.transport.runs().size

        h.now += WearProtocol.HEARTBEAT_MILLIS
        h.tick.emit(Unit)
        runCurrent()

        assertThat(h.transport.runs().size).isEqualTo(before + 1)
        assertThat(h.transport.runs().last().sentAtMillis).isEqualTo(h.now)
    }

    @Test
    fun `publisher follows the distance unit setting`() = runTest {
        val h = Harness(this)
        h.publisher.start()
        h.run.value = RunTrackingState(status = RunStatus.TRACKING)
        runCurrent()

        h.miles.value = true
        runCurrent()

        assertThat(h.transport.runs().last().useMiles).isTrue()
    }

    /** 시계가 없거나 연결이 안 돼도 폰 쪽은 아무 일 없고, 연결되면 다음 변화 때 이어서 보낸다. */
    @Test
    fun `a failing transport never breaks the phone and is retried`() = runTest {
        val h = Harness(this)
        h.transport.failing = true
        h.publisher.start()
        h.run.value = RunTrackingState(status = RunStatus.TRACKING)
        runCurrent()
        assertThat(h.transport.sent).isEmpty()

        h.transport.failing = false
        h.run.value = h.run.value.copy(status = RunStatus.PAUSED)
        runCurrent()

        assertThat(h.transport.runs().last().status).isEqualTo(WatchRunStatus.PAUSED)
    }

    /** 못 보낸 상태는 "보낸 것"으로 치지 않는다. 연결이 돌아오면 같은 상태라도 바로 다시 보낸다. */
    @Test
    fun `a state that failed to send is sent again as soon as the link is back`() = runTest {
        val h = Harness(this)
        h.transport.failing = true
        h.publisher.start()
        h.run.value = RunTrackingState(status = RunStatus.TRACKING, distanceMeters = 100.0, durationSeconds = 10)
        runCurrent()
        assertThat(h.transport.runs()).isEmpty()

        h.transport.failing = false
        h.now += 1_000
        // 같은 상태, 거리도 거의 그대로. 보낸 적이 없다면 규칙에 걸리지 않고 곧바로 나가야 한다.
        h.run.value = h.run.value.copy(durationSeconds = 11)
        runCurrent()

        assertThat(h.transport.runs()).hasSize(1)
        assertThat(h.transport.runs().single().status).isEqualTo(WatchRunStatus.TRACKING)
    }

    @Test
    fun `starting twice does not double the traffic`() = runTest {
        val h = Harness(this)
        h.publisher.start()
        h.publisher.start()
        runCurrent()

        assertThat(h.transport.runs()).hasSize(1)
    }

    @Test
    fun `rest state is sent when it starts changes and is cleared`() = runTest {
        val h = Harness(this)
        h.publisher.start()
        runCurrent()
        val none = h.transport.rests().size

        h.link.publishRest(totalSeconds = 90, remainingMillis = 90_000, paused = false)
        runCurrent()
        assertThat(h.transport.rests().last().active).isTrue()
        assertThat(h.transport.rests().last().endsAtMillis).isEqualTo(h.now + 90_000)

        h.link.publishRest(totalSeconds = 90, remainingMillis = 45_000, paused = true)
        runCurrent()
        assertThat(h.transport.rests().last().paused).isTrue()
        assertThat(h.transport.rests().last().pausedRemainingMillis).isEqualTo(45_000)

        h.link.clearRest()
        runCurrent()
        assertThat(h.transport.rests().last().active).isFalse()
        assertThat(h.transport.rests().size).isGreaterThan(none)
    }

    @Test
    fun `an unchanged rest state is not resent just because the time moved`() = runTest {
        val h = Harness(this)
        h.publisher.start()
        h.link.publishRest(90, 90_000, paused = true)
        runCurrent()
        val before = h.transport.rests().size

        h.now += 5_000
        h.link.publishRest(90, 90_000, paused = true) // 멈춘 채라 같은 상태. 보낸 시각만 다르다
        runCurrent()

        assertThat(h.transport.rests().size).isEqualTo(before)
    }

    // ----- 휴식 연결 -----

    @Test
    fun `rest commands reach the session only while a rest is active`() = runTest {
        val link = WatchLink { 0 }
        val received = mutableListOf<WatchCommand>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            link.restCommands.collect { received += it }
        }

        assertThat(link.emitRestCommand(WatchCommand.REST_SKIP)).isFalse()
        assertThat(received).isEmpty()

        link.publishRest(60, 60_000, paused = false)
        assertThat(link.emitRestCommand(WatchCommand.REST_ADD)).isTrue()
        assertThat(received).containsExactly(WatchCommand.REST_ADD)
        job.cancel()
    }

    @Test
    fun `remaining time is turned into an absolute end time and never negative`() {
        var now = 5_000L
        val link = WatchLink { now }

        link.publishRest(60, 60_000, paused = false)
        assertThat(link.rest.value.endsAtMillis).isEqualTo(65_000)
        assertThat(link.rest.value.sentAtMillis).isEqualTo(5_000)

        link.publishRest(60, -500, paused = false)
        assertThat(link.rest.value.endsAtMillis).isEqualTo(5_000)

        link.clearRest()
        assertThat(link.rest.value).isEqualTo(RestSnapshot.None)
    }

    // ----- 명령 처리 -----

    private class FakeRunControl : RunControl {
        val calls = mutableListOf<String>()
        override fun pause() { calls += "pause" }
        override fun resume() { calls += "resume" }
        override fun stop() { calls += "stop" }
    }

    private fun handler(status: RunStatus, control: FakeRunControl = FakeRunControl(), link: WatchLink = WatchLink { 0 }) =
        WatchCommandHandler(control, { status }, link) to control

    @Test
    fun `pause works only while tracking`() {
        RunStatus.entries.forEach { status ->
            val (handler, control) = handler(status)
            val handled = handler.handle(WatchCommand.RUN_PAUSE)
            assertThat(handled).isEqualTo(status == RunStatus.TRACKING)
            assertThat(control.calls).isEqualTo(if (status == RunStatus.TRACKING) listOf("pause") else emptyList<String>())
        }
    }

    @Test
    fun `resume works only while paused`() {
        RunStatus.entries.forEach { status ->
            val (handler, control) = handler(status)
            assertThat(handler.handle(WatchCommand.RUN_RESUME)).isEqualTo(status == RunStatus.PAUSED)
            assertThat(control.calls).isEqualTo(if (status == RunStatus.PAUSED) listOf("resume") else emptyList<String>())
        }
    }

    @Test
    fun `stop works while tracking or paused but not after the run ended`() {
        RunStatus.entries.forEach { status ->
            val (handler, control) = handler(status)
            val active = status == RunStatus.TRACKING || status == RunStatus.PAUSED
            assertThat(handler.handle(WatchCommand.RUN_STOP)).isEqualTo(active)
            assertThat(control.calls).isEqualTo(if (active) listOf("stop") else emptyList<String>())
        }
    }

    @Test
    fun `rest commands are forwarded to the session link`() {
        val link = WatchLink { 0 }
        link.publishRest(60, 60_000, paused = false)
        val (handler, control) = handler(RunStatus.IDLE, link = link)

        assertThat(handler.handle(WatchCommand.REST_SKIP)).isTrue()
        assertThat(handler.handle(WatchCommand.REST_TOGGLE_PAUSE)).isTrue()
        assertThat(control.calls).isEmpty() // 러닝에는 손대지 않는다
    }

    @Test
    fun `rest commands without a rest are ignored`() {
        val (handler, _) = handler(RunStatus.IDLE)

        WatchCommand.entries.filter { it.key.startsWith("rest") }.forEach {
            assertThat(handler.handle(it)).isFalse()
        }
    }
}
