package com.windowhyun.health.ui.session

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.core.notification.RestTimerNotifier
import com.windowhyun.health.ui.navigation.Routes
import com.windowhyun.health.ui.screenshot.ScreenshotFixture
import com.windowhyun.health.ui.screenshot.waitFor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 운동 진행 화면이 손을 따라가는지: 종목의 마지막 세트를 끝내면 다음 종목이 보이고,
 * 종료 창에서는 안전한 "종료"가 위에 있으며 하지 않은 세트를 저장하자고 하지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h600dp-xxhdpi")
class SessionFollowsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fixture: ScreenshotFixture
    private lateinit var viewModel: WorkoutSessionViewModel
    private var workoutId = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fixture = ScreenshotFixture(context)
        fixture.seed()
        // 오늘 루틴: 벤치프레스 4세트 → 바벨 로우 4세트. 화면 높이가 낮아 둘째 종목은 처음에 보이지 않는다.
        workoutId = runBlocking { fixture.workouts.startWorkout(fixture.todayRoutineId) }
        viewModel = WorkoutSessionViewModel(
            fixture.workouts, fixture.exercises, fixture.settings, RestTimerNotifier(context),
            SavedStateHandle(mapOf(Routes.ARG_WORKOUT_ID to workoutId)),
            com.windowhyun.health.wear.WatchLink(),
        )
        compose.setContent {
            HealthTheme { WorkoutSessionScreen(onFinished = {}, onDiscarded = {}, viewModel = viewModel) }
        }
        waitFor(timeoutMillis = 20_000) { viewModel.uiState.value.workout != null }
        compose.waitForIdle()
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        fixture.close()
    }

    /** 그 글자가 화면에 보이는가(없거나 화면 밖이면 false). */
    private fun shown(text: String, substring: Boolean = false) =
        runCatching { compose.onNodeWithText(text, substring = substring).assertIsDisplayed() }.isSuccess

    private fun completeSet(exerciseIndex: Int, setIndex: Int) = runBlocking {
        val record = fixture.workouts.getWorkout(workoutId)!!.exercises[exerciseIndex]
        val set = record.sets[setIndex]
        viewModel.toggleSetCompleted(set, 80.0, 8, 0, record.restSeconds)
        waitFor(timeoutMillis = 20_000) { runBlocking { fixture.workouts.getWorkout(workoutId)!!.exercises[exerciseIndex].sets[setIndex].completed } }
    }

    @Test
    fun `finishing the last set of an exercise brings the next exercise into view`() {
        // 둘째 종목은 아직 화면 밖이다.
        val before = runCatching { compose.onNodeWithText("바벨 로우").assertIsDisplayed() }
        assertThat(before.isFailure).isTrue()

        repeat(4) { completeSet(exerciseIndex = 0, setIndex = it) }
        val secondId = runBlocking { fixture.workouts.getWorkout(workoutId)!!.exercises[1].id }
        // 화면이 새 상태를 받고 옮겨지는 데는 시간이 걸린다. 보일 때까지 화면을 돌리며 기다린다(고정된 시간을 믿지 않는다).
        compose.waitUntil(timeoutMillis = 20_000) { shown("바벨 로우") }
    }

    @Test
    fun `finishing a set inside an exercise does not move the screen`() {
        completeSet(exerciseIndex = 0, setIndex = 0)
        completeSet(exerciseIndex = 0, setIndex = 1)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        compose.onNodeWithText("벤치프레스").assertIsDisplayed() // 아직 첫 종목을 하는 중이라 그 자리에 있다
    }

    /** 3세트만 하고 종료를 눌렀다. 지난번 값이 미리 채워진 안 한 세트를 기록하자는 선택지는 나오지 않는다. */
    @Test
    fun `the finish dialog does not offer to save sets that were never done`() {
        repeat(3) { completeSet(exerciseIndex = 0, setIndex = it) }
        compose.waitForIdle()

        compose.onNodeWithText("운동 종료").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("운동을 종료할까요?").assertIsDisplayed()
        val offered = runCatching { compose.onNodeWithText("완료로 저장하고 종료", substring = true).assertIsDisplayed() }
        assertThat(offered.isFailure).isTrue()
    }

    @Test
    fun `the set type hint shows on a fresh workout and goes away after the first set`() {
        compose.onNodeWithText("세트 번호를 누르면 워밍업", substring = true).assertIsDisplayed()

        completeSet(exerciseIndex = 0, setIndex = 0)

        compose.waitUntil(timeoutMillis = 20_000) { !shown("세트 번호를 누르면 워밍업", substring = true) }
    }
}
