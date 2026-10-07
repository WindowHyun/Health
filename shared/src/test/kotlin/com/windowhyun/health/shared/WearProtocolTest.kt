package com.windowhyun.health.shared

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WearProtocolTest {

    // ----- 러닝 -----

    private fun run(
        status: WatchRunStatus = WatchRunStatus.TRACKING,
        elapsed: Long = 100,
        sentAt: Long = 1_000_000,
    ) = RunSnapshot(status = status, elapsedSeconds = elapsed, sentAtMillis = sentAt)

    @Test
    fun `elapsed keeps counting while tracking`() {
        val snapshot = run(elapsed = 100, sentAt = 1_000_000)

        assertThat(snapshot.elapsedAt(1_000_000)).isEqualTo(100)
        assertThat(snapshot.elapsedAt(1_000_999)).isEqualTo(100)
        assertThat(snapshot.elapsedAt(1_001_000)).isEqualTo(101)
        assertThat(snapshot.elapsedAt(1_065_500)).isEqualTo(165)
    }

    @Test
    fun `elapsed is frozen while paused or finished`() {
        assertThat(run(WatchRunStatus.PAUSED).elapsedAt(9_999_999)).isEqualTo(100)
        assertThat(run(WatchRunStatus.FINISHED).elapsedAt(9_999_999)).isEqualTo(100)
        assertThat(run(WatchRunStatus.IDLE).elapsedAt(9_999_999)).isEqualTo(100)
    }

    /** 시계가 폰보다 조금 뒤처져도 시간이 거꾸로 가지 않는다. */
    @Test
    fun `a watch clock behind the phone never makes time go backwards`() {
        assertThat(run(elapsed = 100, sentAt = 1_000_000).elapsedAt(999_000)).isEqualTo(100)
    }

    @Test
    fun `active means tracking or paused`() {
        assertThat(run(WatchRunStatus.TRACKING).isActive).isTrue()
        assertThat(run(WatchRunStatus.PAUSED).isActive).isTrue()
        assertThat(run(WatchRunStatus.FINISHED).isActive).isFalse()
        assertThat(run(WatchRunStatus.IDLE).isActive).isFalse()
    }

    @Test
    fun `an active run goes stale only after a minute of silence`() {
        val snapshot = run(sentAt = 1_000_000)

        assertThat(snapshot.isStale(1_000_000 + WearProtocol.STALE_MILLIS)).isFalse()
        assertThat(snapshot.isStale(1_000_000 + WearProtocol.STALE_MILLIS + 1)).isTrue()
    }

    @Test
    fun `an idle or finished run is never stale`() {
        assertThat(run(WatchRunStatus.IDLE, sentAt = 0).isStale(9_999_999)).isFalse()
        assertThat(run(WatchRunStatus.FINISHED, sentAt = 0).isStale(9_999_999)).isFalse()
    }

    // ----- 휴식 -----

    private fun rest(
        endsAt: Long = 100_000,
        total: Int = 60,
        paused: Boolean = false,
        pausedLeft: Long = 0,
    ) = RestSnapshot(
        active = true,
        totalSeconds = total,
        endsAtMillis = endsAt,
        paused = paused,
        pausedRemainingMillis = pausedLeft,
    )

    @Test
    fun `remaining time follows the absolute end time`() {
        val snapshot = rest(endsAt = 100_000)

        assertThat(snapshot.remainingMillis(70_000)).isEqualTo(30_000)
        assertThat(snapshot.remainingSeconds(70_000)).isEqualTo(30)
    }

    /** 0.3 초 남았는데 "0초"로 보이면 끝난 줄 안다. 올림해서 1초로 적는다. */
    @Test
    fun `remaining seconds round up`() {
        val snapshot = rest(endsAt = 100_000)

        assertThat(snapshot.remainingSeconds(99_700)).isEqualTo(1)
        assertThat(snapshot.remainingSeconds(100_000)).isEqualTo(0)
        assertThat(snapshot.remainingSeconds(120_000)).isEqualTo(0)
    }

    @Test
    fun `paused rest keeps its remaining time whatever the clock says`() {
        val snapshot = rest(endsAt = 100_000, paused = true, pausedLeft = 25_000)

        assertThat(snapshot.remainingMillis(1)).isEqualTo(25_000)
        assertThat(snapshot.remainingMillis(9_999_999)).isEqualTo(25_000)
        assertThat(snapshot.isFinished(9_999_999)).isFalse()
    }

    @Test
    fun `a rest finishes at its end time`() {
        val snapshot = rest(endsAt = 100_000)

        assertThat(snapshot.isFinished(99_999)).isFalse()
        assertThat(snapshot.isFinished(100_000)).isTrue()
    }

    @Test
    fun `no rest has nothing left`() {
        assertThat(RestSnapshot.None.remainingMillis(1)).isEqualTo(0)
        assertThat(RestSnapshot.None.isFinished(1)).isFalse()
        assertThat(RestSnapshot.None.fraction(1)).isEqualTo(0f)
    }

    @Test
    fun `fraction goes from full to empty and stays in range`() {
        val snapshot = rest(endsAt = 100_000, total = 60)

        assertThat(snapshot.fraction(40_000)).isEqualTo(1f)
        assertThat(snapshot.fraction(70_000)).isWithin(0.001f).of(0.5f)
        assertThat(snapshot.fraction(100_000)).isEqualTo(0f)
        assertThat(snapshot.fraction(10_000)).isEqualTo(1f) // 시계가 어긋나 더 남아 보여도 1 을 넘지 않는다
    }

    // ----- 명령 -----

    @Test
    fun `every command round trips through its path`() {
        WatchCommand.entries.forEach { command ->
            assertThat(WatchCommand.fromPath(command.path)).isEqualTo(command)
        }
    }

    @Test
    fun `command paths are unique and under the command prefix`() {
        val paths = WatchCommand.entries.map { it.path }
        assertThat(paths).containsNoDuplicates()
        assertThat(paths.all { it.startsWith(WearProtocol.COMMAND_PREFIX) }).isTrue()
    }

    @Test
    fun `an unknown path is not a command`() {
        assertThat(WatchCommand.fromPath("/health/cmd/launch_missiles")).isNull()
        assertThat(WatchCommand.fromPath("/health/run_state")).isNull()
        assertThat(WatchCommand.fromPath("")).isNull()
    }

    // ----- 직렬화 -----

    @Test
    fun `a run snapshot survives encoding`() {
        val original = RunSnapshot(
            status = WatchRunStatus.PAUSED,
            distanceMeters = 5_123.4,
            elapsedSeconds = 1_800,
            currentPaceSecPerKm = 330.0,
            averagePaceSecPerKm = 345.5,
            autoPaused = true,
            signalLost = true,
            useMiles = true,
            goalProgress = 0.62f,
            sentAtMillis = 1_700_000_000_000,
        )

        assertThat(WearCodec.decodeRun(WearCodec.encode(original))).isEqualTo(original)
    }

    @Test
    fun `a rest snapshot survives encoding`() {
        val original = rest(endsAt = 1_700_000_090_000, total = 90, paused = true, pausedLeft = 31_000)
            .copy(sentAtMillis = 1_700_000_000_000)

        assertThat(WearCodec.decodeRest(WearCodec.encode(original))).isEqualTo(original)
    }

    /** 폰과 시계 앱 버전이 다르면 모르는 항목이 올 수 있다. 읽을 수 있는 것만 읽는다. */
    @Test
    fun `unknown fields from another version are ignored`() {
        val bytes = """{"status":"TRACKING","distanceMeters":10.0,"someFutureField":42}""".toByteArray()

        val decoded = WearCodec.decodeRun(bytes)

        assertThat(decoded?.status).isEqualTo(WatchRunStatus.TRACKING)
        assertThat(decoded?.distanceMeters).isEqualTo(10.0)
    }

    @Test
    fun `garbage never throws`() {
        assertThat(WearCodec.decodeRun(null)).isNull()
        assertThat(WearCodec.decodeRun(ByteArray(0))).isNull()
        assertThat(WearCodec.decodeRun("not json".toByteArray())).isNull()
        assertThat(WearCodec.decodeRun("""{"status":"FLYING"}""".toByteArray())).isNull()
        assertThat(WearCodec.decodeRest("[1,2,3]".toByteArray())).isNull()
    }
}
