package com.windowhyun.health.wear

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.shared.RestSnapshot
import com.windowhyun.health.shared.RunSnapshot
import com.windowhyun.health.shared.WatchRunStatus
import org.junit.Test

class WatchStateTest {

    private val running = RunSnapshot(status = WatchRunStatus.TRACKING, elapsedSeconds = 100, sentAtMillis = 1_000)
    private val resting = RestSnapshot(active = true, totalSeconds = 60, endsAtMillis = 61_000, sentAtMillis = 1_000)

    @Test
    fun `nothing going on shows the idle screen`() {
        assertThat(WatchUiState().screen).isEqualTo(WatchScreen.IDLE)
        assertThat(WatchUiState(run = running.copy(status = WatchRunStatus.FINISHED)).screen).isEqualTo(WatchScreen.IDLE)
    }

    @Test
    fun `a run shows the run screen`() {
        assertThat(WatchUiState(run = running).screen).isEqualTo(WatchScreen.RUN)
        assertThat(WatchUiState(run = running.copy(status = WatchRunStatus.PAUSED)).screen).isEqualTo(WatchScreen.RUN)
    }

    @Test
    fun `a rest shows the rest screen`() {
        assertThat(WatchUiState(rest = resting).screen).isEqualTo(WatchScreen.REST)
    }

    /** 헬스 중 휴식은 곧 끝나므로 먼저 보여 준다. 끝나면 러닝 화면이 나온다. */
    @Test
    fun `a rest comes before a run`() {
        assertThat(WatchUiState(run = running, rest = resting).screen).isEqualTo(WatchScreen.REST)
        assertThat(WatchUiState(run = running, rest = RestSnapshot.None).screen).isEqualTo(WatchScreen.RUN)
    }

    @Test
    fun `elapsed and remaining time follow the clock`() {
        val state = WatchUiState(run = running, rest = resting, nowMillis = 31_000)

        assertThat(state.runElapsedSeconds).isEqualTo(130)
        assertThat(state.restRemainingSeconds).isEqualTo(30)
    }

    @Test
    fun `a run that stopped reporting is stale`() {
        assertThat(WatchUiState(run = running, nowMillis = 1_000 + 30_000).runStale).isFalse()
        assertThat(WatchUiState(run = running, nowMillis = 1_000 + 120_000).runStale).isTrue()
    }
}
