package com.windowhyun.health.ui.session

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.core.notification.RestTimerNotifier
import com.windowhyun.health.domain.model.ThemeMode
import com.windowhyun.health.ui.navigation.Routes
import com.windowhyun.health.ui.screenshot.ScreenshotFixture
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 운동 중 실수로 한 조작을 되돌리거나 미리 막는 장치와, 화면 꺼짐 방지.
 * (세트 삭제 실행 취소 · 종목 빼기 확인 · 종료 확인의 숫자 · 한 세트도 안 한 운동 버리기)
 */
@OptIn(DelicateCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h1000dp-xxhdpi")
class SessionSafetyUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fixture: ScreenshotFixture
    private lateinit var context: Context
    private lateinit var viewModel: WorkoutSessionViewModel
    private var workoutId = 0L
    private val events = mutableListOf<WorkoutSessionEvent>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fixture = ScreenshotFixture(context)
        fixture.seed()
        workoutId = runBlocking { fixture.workouts.startWorkout(fixture.todayRoutineId) }
        viewModel = WorkoutSessionViewModel(
            fixture.workouts, fixture.exercises, fixture.settings, RestTimerNotifier(context),
            SavedStateHandle(mapOf(Routes.ARG_WORKOUT_ID to workoutId)),
            com.windowhyun.health.wear.WatchLink(),
        )
        GlobalScope.launch(Dispatchers.Unconfined) { viewModel.events.collect { events += it } }
    }

    @After
    fun tearDown() {
        // 화면이 닫히기 전에 ViewModel 작업을 멈춘다. 안 그러면 닫힌 DB 에 쓰다 난 예외가 다음 테스트로 번진다.
        viewModel.viewModelScope.cancel()
        fixture.close()
    }

    /**
     * 조건이 될 때까지 기다린다. 저장은 다른 스레드에서 끝나고, ViewModel 의 후속 작업은 메인 루퍼가 돌아야 이어지므로
     * 기다리는 동안 계속 루퍼를 돌려 준다.
     */
    private fun settle(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(20)
        }
    }

    private fun detail() = runBlocking { fixture.db.workoutDao().getWorkoutDetail(workoutId)!! }

    /** 첫 종목의 세트를 번호 순으로(DB 관계는 넣은 순서로 오므로, 화면이 쓰는 순서로 맞춘다). */
    private fun firstSets() = detail().exercises.first().sets.sortedBy { it.setNumber }

    private fun completeFirstTwoSets() = runBlocking {
        val sets = detail().exercises.first().sets
        fixture.workouts.setCompleted(sets[0].id, 80.0, 8, true)
        fixture.workouts.setCompleted(sets[1].id, 80.0, 8, true)
    }

    private fun show() {
        compose.setContent {
            HealthTheme(themeMode = ThemeMode.LIGHT) {
                WorkoutSessionScreen(onFinished = {}, onDiscarded = {}, viewModel = viewModel)
            }
        }
        settle { viewModel.uiState.value.workout != null }
        compose.waitForIdle()
    }

    private fun waitUntilShown(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    // ----- 화면 꺼짐 방지 -----

    /** 화면 안의 Compose 뷰. `view.keepScreenOn` 은 이 뷰에 건다. */
    private fun composeView(): View {
        fun find(view: View): View? {
            if (view.javaClass.simpleName == "AndroidComposeView") return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        return find(compose.activity.findViewById<ViewGroup>(android.R.id.content))!!
    }

    /** 설정에 "운동 중 화면 켜 둠" 이 있는데 헬스 화면은 이를 읽지 않아 세트 사이에 화면이 꺼졌다. */
    @Test
    fun `the screen stays on during a workout and follows the setting`() {
        show()
        assertThat(composeView().keepScreenOn).isTrue()

        runBlocking { fixture.settings.update { it.copy(keepScreenOnDuringWorkout = false) } }
        settle { !composeView().keepScreenOn }
        compose.waitForIdle()

        assertThat(composeView().keepScreenOn).isFalse()
    }

    // ----- 세트 삭제 실행 취소 -----

    @Test
    fun `a deleted set can be brought back with undo`() {
        show()
        val before = firstSets()
        assertThat(before.size).isAtLeast(2)

        compose.onAllNodesWithContentDescription("세트 삭제")[0].performClick()
        waitUntilShown("세트를 삭제했어요")
        compose.onNodeWithText("세트를 삭제했어요").assertIsDisplayed()
        settle { detail().exercises.first().sets.size == before.size - 1 }
        assertThat(detail().exercises.first().sets).hasSize(before.size - 1)

        compose.onNodeWithText("실행 취소").performClick()
        settle { detail().exercises.first().sets.size == before.size }

        val after = firstSets()
        assertThat(after.map { it.setNumber }).isEqualTo(before.map { it.setNumber })
        assertThat(after.map { it.weightKg }).isEqualTo(before.map { it.weightKg })
    }

    // ----- 종목 빼기 -----

    @Test
    fun `removing an exercise with completed sets asks first and a plain one goes at once`() {
        completeFirstTwoSets()
        show()
        val exerciseCount = detail().exercises.size
        assertThat(exerciseCount).isAtLeast(2)

        // 완료한 세트가 있는 첫 종목: 먼저 묻는다. 취소하면 그대로다.
        compose.onAllNodesWithContentDescription("운동 빼기")[0].performClick()
        compose.onNodeWithText("종목을 뺄까요?").assertIsDisplayed()
        compose.onNodeWithText("취소").performClick()
        compose.waitForIdle()
        assertThat(detail().exercises).hasSize(exerciseCount)

        // 한 세트도 안 한 둘째 종목은 잃을 기록이 없어 바로 빠진다.
        compose.onAllNodesWithContentDescription("운동 빼기")[1].performClick()
        settle { detail().exercises.size == exerciseCount - 1 }
        assertThat(detail().exercises).hasSize(exerciseCount - 1)

        // 첫 종목은 확인하면 빠진다.
        compose.onAllNodesWithContentDescription("운동 빼기")[0].performClick()
        compose.onNodeWithText("종목을 뺄까요?").assertIsDisplayed()
        compose.onNodeWithText("빼기").performClick()
        settle { detail().exercises.size == exerciseCount - 2 }
        assertThat(detail().exercises).hasSize(exerciseCount - 2)
    }

    // ----- 종료 확인 -----

    @Test
    fun `the end dialog says how many sets are kept and how many are dropped`() {
        completeFirstTwoSets()
        show()
        val total = detail().exercises.sumOf { it.sets.size }

        compose.onNodeWithText("운동 종료").performClick()

        compose.onNodeWithText("완료한 2세트는 저장되고, 체크하지 않은 ${total - 2}세트는 저장되지 않습니다.").assertIsDisplayed()
    }

    @Test
    fun `the end dialog warns when nothing was done`() {
        show()

        compose.onNodeWithText("운동 종료").performClick()

        compose.onNodeWithText("기록할 세트가 없어요").assertIsDisplayed()
        compose.onNodeWithText("버리고 종료").assertIsDisplayed()
    }

    // ----- 한 세트도 안 한 운동 -----

    @Test
    fun `ending a workout with nothing done discards it instead of saving an empty record`() {
        show()

        viewModel.finishWorkout()
        settle { events.isNotEmpty() }

        assertThat(events).containsExactly(WorkoutSessionEvent.Discarded)
        assertThat(runBlocking { fixture.workouts.getWorkout(workoutId) }).isNull()
    }

    @Test
    fun `ending a workout with a completed set saves it`() {
        completeFirstTwoSets()
        show()

        viewModel.finishWorkout()
        settle { events.isNotEmpty() }

        assertThat(events).containsExactly(WorkoutSessionEvent.Finished(workoutId))
        assertThat(runBlocking { fixture.workouts.getWorkout(workoutId) }?.endTime).isNotNull()
    }
}
