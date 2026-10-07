package com.windowhyun.health.wear

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.shared.RestSnapshot
import com.windowhyun.health.shared.RunSnapshot
import com.windowhyun.health.shared.WatchRunStatus
import com.windowhyun.health.shared.WorkoutSnapshot
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

    // ----- 헬스 세트 -----

    private val setting = WorkoutSnapshot(
        active = true, setId = 3, exerciseName = "벤치", setNumber = 1, setCount = 3, reps = 8, sentAtMillis = 1_000,
    )

    @Test
    fun `a current set shows the workout screen`() {
        assertThat(WatchUiState(workout = setting, nowMillis = 1_000).screen).isEqualTo(WatchScreen.WORKOUT)
    }

    /** 휴식과 러닝이 세트보다 급하다. 휴식이 끝나면 다음 세트가 다시 나온다. */
    @Test
    fun `rest and run come before the current set`() {
        assertThat(WatchUiState(workout = setting, rest = resting, nowMillis = 1_000).screen).isEqualTo(WatchScreen.REST)
        assertThat(WatchUiState(workout = setting, run = running, nowMillis = 1_000).screen).isEqualTo(WatchScreen.RUN)
        assertThat(WatchUiState(workout = setting, rest = RestSnapshot.None, nowMillis = 1_000).screen)
            .isEqualTo(WatchScreen.WORKOUT)
    }

    /** 폰 앱이 죽어 소식이 끊기면 지나간 세트가 시계에 영원히 남지 않는다. */
    @Test
    fun `a set from a phone that went quiet disappears`() {
        assertThat(WatchUiState(workout = setting, nowMillis = 1_000 + 30_000).workoutVisible).isTrue()
        assertThat(WatchUiState(workout = setting, nowMillis = 1_000 + 120_000).workoutVisible).isFalse()
        assertThat(WatchUiState(workout = setting, nowMillis = 1_000 + 120_000).screen).isEqualTo(WatchScreen.IDLE)
    }

    @Test
    fun `no set means no workout screen`() {
        assertThat(WatchUiState(workout = WorkoutSnapshot.None).workoutVisible).isFalse()
    }

    // ----- 러닝 시작 요청 -----

    @Test
    fun `a start request is pending only for a few seconds`() {
        val asked = 5_000L
        assertThat(WatchUiState(startRequestedAtMillis = 0, nowMillis = 6_000).startPending).isFalse()
        assertThat(WatchUiState(startRequestedAtMillis = asked, nowMillis = asked + 1_000).startPending).isTrue()
        assertThat(WatchUiState(startRequestedAtMillis = asked, nowMillis = asked + START_HINT_MILLIS - 1).startPending).isTrue()
        assertThat(WatchUiState(startRequestedAtMillis = asked, nowMillis = asked + START_HINT_MILLIS).startPending).isFalse()
    }
}
