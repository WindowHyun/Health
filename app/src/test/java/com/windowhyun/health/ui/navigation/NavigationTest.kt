package com.windowhyun.health.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 종목 상세 ↔ 운동 기록을 오가도 화면이 끝없이 쌓이지 않는다. */
@RunWith(RobolectricTestRunner::class)
class NavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController

    private fun setUp() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Routes.HISTORY) {
                composable(Routes.HISTORY) { Text("history") }
                composable(
                    EXERCISE_DETAIL_PATTERN,
                    arguments = listOf(navArgument(Routes.ARG_EXERCISE_ID) { type = NavType.LongType }),
                ) { Text("exercise") }
                composable(
                    WORKOUT_DETAIL_PATTERN,
                    arguments = listOf(navArgument(Routes.ARG_WORKOUT_ID) { type = NavType.LongType }),
                ) { Text("workout") }
            }
        }
        compose.waitForIdle()
    }

    private fun openExercise(id: Long) = compose.runOnUiThread {
        nav.navigateOrReturn(EXERCISE_DETAIL_PATTERN, Routes.ARG_EXERCISE_ID, id, Routes.exerciseDetail(id))
    }

    private fun openWorkout(id: Long) = compose.runOnUiThread {
        nav.navigateOrReturn(WORKOUT_DETAIL_PATTERN, Routes.ARG_WORKOUT_ID, id, Routes.workoutDetail(id))
    }

    private fun depth(): Int = nav.currentBackStack.value.count { it.destination.route != null }

    @Test
    fun `going back and forth does not pile up screens`() {
        setUp()
        openExercise(7)
        val base = depth()

        repeat(10) {
            openWorkout(1)
            openExercise(7)
        }
        compose.waitForIdle()

        // 종목 상세 → 운동 기록 → (같은 종목 상세로 돌아옴) 을 반복해도 처음과 같다.
        assertThat(depth()).isEqualTo(base)
        assertThat(nav.currentBackStackEntry?.arguments?.getLong(Routes.ARG_EXERCISE_ID)).isEqualTo(7L)
    }

    @Test
    fun `a different target still opens a new screen`() {
        setUp()
        openExercise(7)
        openWorkout(1)
        val before = depth()

        openExercise(8) // 다른 종목은 새로 연다
        compose.waitForIdle()

        assertThat(depth()).isEqualTo(before + 1)
        assertThat(nav.currentBackStackEntry?.arguments?.getLong(Routes.ARG_EXERCISE_ID)).isEqualTo(8L)
    }
}
