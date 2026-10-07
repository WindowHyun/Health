package com.windowhyun.health.wear

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.shared.RestSnapshot
import com.windowhyun.health.shared.RunSnapshot
import com.windowhyun.health.shared.WatchCommand
import com.windowhyun.health.shared.WatchRunStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 시계 화면. 둥근 192dp 화면 기준이고, 눈으로 볼 수 있게 build/screenshots 에 그림도 남긴다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w192dp-h192dp-round-xxhdpi")
class WatchScreensTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val sentAt = 1_000_000L

    private fun run(
        status: WatchRunStatus = WatchRunStatus.TRACKING,
        autoPaused: Boolean = false,
        signalLost: Boolean = false,
        miles: Boolean = false,
    ) = RunSnapshot(
        status = status,
        distanceMeters = 5_120.0,
        elapsedSeconds = 1_930,
        currentPaceSecPerKm = 330.0,
        averagePaceSecPerKm = 340.0,
        autoPaused = autoPaused,
        signalLost = signalLost,
        useMiles = miles,
        sentAtMillis = sentAt,
    )

    private fun rest(remainingMillis: Long = 83_000, paused: Boolean = false) = RestSnapshot(
        active = true,
        totalSeconds = 90,
        endsAtMillis = if (paused) 0 else sentAt + remainingMillis,
        paused = paused,
        pausedRemainingMillis = if (paused) remainingMillis else 0,
        sentAtMillis = sentAt,
    )

    private class Taps {
        val commands = mutableListOf<WatchCommand>()
        var requestStop = 0
        var confirmStop = 0
        var cancelStop = 0
    }

    private fun show(state: WatchUiState, taps: Taps = Taps(), shot: String? = null): Taps {
        compose.setContent {
            WatchApp(
                state = state,
                onCommand = { taps.commands += it },
                onRequestStop = { taps.requestStop++ },
                onConfirmStop = { taps.confirmStop++ },
                onCancelStop = { taps.cancelStop++ },
            )
        }
        compose.waitForIdle()
        if (shot != null) saveScreenshot(shot)
        return taps
    }

    private fun saveScreenshot(name: String) {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ----- 대기 -----

    @Test
    fun `idle tells the user what to do`() {
        show(WatchUiState(), shot = "watch_idle")

        compose.onNodeWithText("폰에서 운동이나 러닝을 시작하면 여기에 나타나요.").assertIsDisplayed()
    }

    @Test
    fun `a finished run points to the phone`() {
        show(WatchUiState(run = run(WatchRunStatus.FINISHED)))

        compose.onNodeWithText("러닝이 끝났어요. 결과는 폰에서 확인하세요.").assertIsDisplayed()
    }

    // ----- 러닝 -----

    @Test
    fun `the run screen shows distance time and pace`() {
        show(WatchUiState(run = run(), nowMillis = sentAt), shot = "watch_run")

        compose.onNodeWithText("5.12").assertIsDisplayed()
        compose.onNodeWithText("km").assertIsDisplayed()
        compose.onNodeWithText("32:10").assertIsDisplayed()
        compose.onNodeWithText("5'30\"").assertIsDisplayed()
        compose.onNodeWithText("일시정지").assertIsDisplayed()
    }

    @Test
    fun `the clock on the run screen keeps moving`() {
        show(WatchUiState(run = run(), nowMillis = sentAt + 65_000))

        compose.onNodeWithText("33:15").assertIsDisplayed()
    }

    @Test
    fun `miles use miles and a mile pace`() {
        show(WatchUiState(run = run(miles = true), nowMillis = sentAt))

        compose.onNodeWithText("3.18").assertIsDisplayed()
        compose.onNodeWithText("mi").assertIsDisplayed()
        compose.onNodeWithText("8'51\"").assertIsDisplayed()
    }

    @Test
    fun `pause sends the pause command`() {
        val taps = show(WatchUiState(run = run(), nowMillis = sentAt))

        compose.onNodeWithText("일시정지").performClick()

        assertThat(taps.commands).containsExactly(WatchCommand.RUN_PAUSE)
    }

    @Test
    fun `a paused run offers resume instead`() {
        val taps = show(WatchUiState(run = run(WatchRunStatus.PAUSED), nowMillis = sentAt), shot = "watch_run_paused")

        compose.onNodeWithText("러닝 · 일시정지").assertIsDisplayed()
        compose.onNodeWithText("일시정지").assertDoesNotExist()
        compose.onNodeWithText("계속").performClick()

        assertThat(taps.commands).containsExactly(WatchCommand.RUN_RESUME)
    }

    @Test
    fun `an auto paused run says why`() {
        show(WatchUiState(run = run(WatchRunStatus.PAUSED, autoPaused = true), nowMillis = sentAt))

        compose.onNodeWithText("멈춰서 자동 일시정지").assertIsDisplayed()
    }

    @Test
    fun `a lost signal is shown`() {
        show(WatchUiState(run = run(signalLost = true), nowMillis = sentAt))

        compose.onNodeWithText("러닝 · 위치 신호 없음").assertIsDisplayed()
    }

    /** 종료는 바로 폰에 가지 않고 확인부터 요청한다. */
    @Test
    fun `stop asks for confirmation first`() {
        val taps = show(WatchUiState(run = run(), nowMillis = sentAt))

        compose.onNodeWithText("종료").performClick()

        assertThat(taps.requestStop).isEqualTo(1)
        assertThat(taps.commands).isEmpty()
    }

    @Test
    fun `the confirmation can be confirmed or cancelled`() {
        val taps = show(
            WatchUiState(run = run(), nowMillis = sentAt, confirmingStop = true),
            shot = "watch_run_confirm",
        )

        compose.onNodeWithText("러닝을 끝낼까요?").assertIsDisplayed()
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("종료").performClick()

        assertThat(taps.cancelStop).isEqualTo(1)
        assertThat(taps.confirmStop).isEqualTo(1)
        assertThat(taps.requestStop).isEqualTo(0)
    }

    @Test
    fun `a silent phone is flagged on a run`() {
        show(WatchUiState(run = run(), nowMillis = sentAt + 5 * 60_000))

        compose.onNodeWithText("폰과 연결이 끊겼을 수 있어요").assertIsDisplayed()
    }

    // ----- 휴식 -----

    @Test
    fun `the rest screen counts down`() {
        show(WatchUiState(rest = rest(), nowMillis = sentAt), shot = "watch_rest")

        compose.onNodeWithText("휴식").assertIsDisplayed()
        compose.onNodeWithText("1:23").assertIsDisplayed()
        compose.onNodeWithText("건너뛰기").assertIsDisplayed()
    }

    @Test
    fun `rest buttons send their commands`() {
        val taps = show(WatchUiState(rest = rest(), nowMillis = sentAt))

        compose.onNodeWithText("+15").performClick()
        compose.onNodeWithText("-15").performClick()
        compose.onNodeWithText("멈춤").performClick()
        compose.onNodeWithText("건너뛰기").performClick()

        assertThat(taps.commands).containsExactly(
            WatchCommand.REST_ADD,
            WatchCommand.REST_SUB,
            WatchCommand.REST_TOGGLE_PAUSE,
            WatchCommand.REST_SKIP,
        ).inOrder()
    }

    @Test
    fun `a paused rest offers to continue`() {
        show(WatchUiState(rest = rest(paused = true, remainingMillis = 40_000), nowMillis = sentAt))

        compose.onNodeWithText("휴식 · 멈춤").assertIsDisplayed()
        compose.onNodeWithText("0:40").assertIsDisplayed()
        compose.onNodeWithText("계속").assertIsDisplayed()
    }

    @Test
    fun `a finished rest says so and has nothing left to pause`() {
        show(WatchUiState(rest = rest(), nowMillis = sentAt + 90_000), shot = "watch_rest_done")

        compose.onNodeWithText("휴식 끝").assertIsDisplayed()
        compose.onNodeWithText("0:00").assertIsDisplayed()
        compose.onNodeWithText("멈춤").assertDoesNotExist()
        compose.onNodeWithText("건너뛰기").assertIsDisplayed()
    }

    // ----- 둘 다 있을 때 -----

    /** 휴식이 있으면 휴식이 먼저다. 끝나면 러닝 화면으로 돌아온다. */
    @Test
    fun `a rest comes before a run and the run returns after it`() {
        show(WatchUiState(run = run(), rest = rest(), nowMillis = sentAt))
        compose.onNodeWithText("건너뛰기").assertIsDisplayed()
        compose.onNodeWithText("일시정지").assertDoesNotExist()
    }

    // ----- 보내기 실패 -----

    @Test
    fun `a failed send is explained`() {
        show(WatchUiState(run = run(), nowMillis = sentAt, sendFailed = true))

        compose.onNodeWithText("폰에 보내지 못했어요").assertIsDisplayed()
    }
}
