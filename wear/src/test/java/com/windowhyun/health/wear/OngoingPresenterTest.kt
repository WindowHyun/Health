package com.windowhyun.health.wear

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.shared.RestSnapshot
import com.windowhyun.health.shared.RunSnapshot
import com.windowhyun.health.shared.WatchRunStatus
import com.windowhyun.health.shared.WearProtocol
import com.windowhyun.health.shared.WorkoutSnapshot
import org.junit.Test

/** 워치페이스 칩과 타일에 무엇을 띄울지. 폰 소식이 끊기면 지나간 운동을 치운다. */
class OngoingPresenterTest {

    private val sentAt = 1_000_000L

    private fun run(
        status: WatchRunStatus = WatchRunStatus.TRACKING,
        elapsed: Long = 600,
        miles: Boolean = false,
    ) = RunSnapshot(
        status = status, distanceMeters = 2_000.0, elapsedSeconds = elapsed, currentPaceSecPerKm = 300.0,
        useMiles = miles, sentAtMillis = sentAt,
    )

    private fun rest(remaining: Long = 45_000, paused: Boolean = false) = RestSnapshot(
        active = true, totalSeconds = 90,
        endsAtMillis = if (paused) 0 else sentAt + remaining,
        paused = paused, pausedRemainingMillis = if (paused) remaining else 0, sentAtMillis = sentAt,
    )

    private val set = WorkoutSnapshot(
        active = true, setId = 5, exerciseName = "벤치프레스", setNumber = 2, setCount = 4, weightKg = 62.5, reps = 8,
        sentAtMillis = sentAt,
    )

    private fun describe(
        run: RunSnapshot = RunSnapshot(),
        rest: RestSnapshot = RestSnapshot.None,
        workout: WorkoutSnapshot = WorkoutSnapshot.None,
        now: Long = sentAt,
    ) = OngoingPresenter.describe(run, rest, workout, now)

    @Test
    fun `nothing going on shows nothing`() {
        assertThat(describe()).isNull()
        assertThat(describe(run = run(WatchRunStatus.FINISHED))).isNull()
        assertThat(describe(run = run(WatchRunStatus.IDLE))).isNull()
    }

    @Test
    fun `a run counts up from when it started`() {
        val content = describe(run = run(elapsed = 600))!!

        assertThat(content.kind).isEqualTo(OngoingContent.Kind.RUN)
        assertThat(content.title).isEqualTo("러닝")
        assertThat(content.text).isEqualTo("2.00km")
        // 600초 지난 시점에 보냈으니 0 은 그보다 600초 전이다.
        assertThat(content.clock).isEqualTo(OngoingContent.Clock.Stopwatch(sentAt - 600_000))
        assertThat(content.tileLines).containsExactly("러닝", "2.00km", "10:00 · 5'00\"").inOrder()
    }

    @Test
    fun `a paused run is frozen and says so`() {
        val content = describe(run = run(WatchRunStatus.PAUSED, elapsed = 600), now = sentAt + 30_000)!!

        assertThat(content.title).isEqualTo("러닝 · 일시정지")
        assertThat(content.clock).isEqualTo(OngoingContent.Clock.Frozen("10:00"))
    }

    @Test
    fun `miles follow the users unit`() {
        val content = describe(run = run(miles = true))!!

        assertThat(content.text).isEqualTo("1.24mi")
        assertThat(content.tileLines[2]).contains("8'03\"")
    }

    @Test
    fun `a rest counts down to when it ends`() {
        val content = describe(rest = rest(remaining = 45_000))!!

        assertThat(content.kind).isEqualTo(OngoingContent.Kind.REST)
        assertThat(content.clock).isEqualTo(OngoingContent.Clock.Countdown(sentAt + 45_000))
        assertThat(content.tileLines).containsExactly("휴식", "0:45").inOrder()
    }

    @Test
    fun `a paused rest is frozen`() {
        val content = describe(rest = rest(remaining = 45_000, paused = true))!!

        assertThat(content.clock).isEqualTo(OngoingContent.Clock.Frozen("0:45"))
        assertThat(content.tileLines.first()).isEqualTo("휴식 · 멈춤")
    }

    /** 휴식이 끝나면 칩이 사라진다. 0:00 을 붙들고 있으면 안 된다. */
    @Test
    fun `a rest that ended is not shown`() {
        assertThat(describe(rest = rest(remaining = 45_000), now = sentAt + 45_000)).isNull()
    }

    @Test
    fun `a rest comes before a run and a run before a set`() {
        assertThat(describe(run = run(), rest = rest(), workout = set)!!.kind).isEqualTo(OngoingContent.Kind.REST)
        assertThat(describe(run = run(), workout = set)!!.kind).isEqualTo(OngoingContent.Kind.RUN)
        assertThat(describe(workout = set)!!.kind).isEqualTo(OngoingContent.Kind.WORKOUT)
    }

    @Test
    fun `a set shows its exercise and position`() {
        val content = describe(workout = set)!!

        assertThat(content.title).isEqualTo("벤치프레스")
        assertThat(content.text).isEqualTo("2/4세트")
        assertThat(content.clock).isNull()
        assertThat(content.tileLines).containsExactly("벤치프레스", "2/4세트", "62.5kg × 8").inOrder()
    }

    @Test
    fun `a finished workout shows nothing`() {
        assertThat(describe(workout = set.copy(allDone = true))).isNull()
    }

    // ----- 폰 소식이 끊겼을 때 -----

    @Test
    fun `a run from a phone that went quiet is taken down`() {
        assertThat(describe(run = run(), now = sentAt + WearProtocol.STALE_MILLIS)).isNotNull()
        assertThat(describe(run = run(), now = sentAt + WearProtocol.STALE_MILLIS + 1)).isNull()
    }

    @Test
    fun `a set from a phone that went quiet is taken down`() {
        assertThat(describe(workout = set, now = sentAt + WearProtocol.STALE_MILLIS)).isNotNull()
        assertThat(describe(workout = set, now = sentAt + WearProtocol.STALE_MILLIS + 1)).isNull()
    }

    /** 폰이 죽어 "휴식 끝"을 못 보냈어도 칩이 몇 분씩 남지 않는다. */
    @Test
    fun `a running rest from a phone that died is taken down`() {
        // 90초짜리 휴식. 끝나는 시각은 한참 뒤로 보내졌지만 소식이 끊긴 지 2분이 넘었다.
        val stuck = rest(remaining = 3_600_000)

        assertThat(describe(rest = stuck, now = sentAt + 60_000)).isNotNull()
        assertThat(describe(rest = stuck, now = sentAt + 90_000 + 60_000 + 1)).isNull()
    }

    @Test
    fun `the tile has a hint when nothing is going on`() {
        assertThat(OngoingPresenter.tileLines(null)).containsExactly("Health", "폰에서 운동을 시작하세요").inOrder()
        assertThat(OngoingPresenter.tileLines(describe(run = run()))).hasSize(3)
    }
}
