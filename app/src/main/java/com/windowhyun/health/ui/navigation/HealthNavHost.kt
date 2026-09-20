package com.windowhyun.health.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.windowhyun.health.ui.gym.RoutineEditScreen
import com.windowhyun.health.ui.gym.RoutineListScreen
import com.windowhyun.health.ui.history.HistoryScreen
import com.windowhyun.health.ui.history.WorkoutDetailScreen
import com.windowhyun.health.ui.home.HomeScreen
import com.windowhyun.health.ui.running.RunningScreen
import com.windowhyun.health.ui.session.WorkoutSessionScreen
import com.windowhyun.health.ui.session.WorkoutSummaryScreen
import com.windowhyun.health.ui.settings.SettingsScreen

/**
 * 모든 화면 연결. 운동 시작/종료 흐름은 백스택을 정리해
 * 뒤로가기로 진행 중이던 화면에 되돌아가지 않도록 한다.
 */
@Composable
fun HealthNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onStartWorkout = { workoutId ->
                    navController.navigate(Routes.workoutSession(workoutId))
                },
                onOpenGym = { navController.navigate(Routes.GYM) },
                onOpenRunning = { navController.navigate(Routes.RUNNING) },
                onOpenWorkout = { workoutId -> navController.navigate(Routes.workoutDetail(workoutId)) },
            )
        }

        composable(Routes.GYM) {
            RoutineListScreen(
                onCreateRoutine = { navController.navigate(Routes.routineEdit()) },
                onEditRoutine = { routineId -> navController.navigate(Routes.routineEdit(routineId)) },
                onStartWorkout = { workoutId -> navController.navigate(Routes.workoutSession(workoutId)) },
            )
        }

        composable(Routes.RUNNING) { RunningScreen() }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onOpenWorkout = { workoutId -> navController.navigate(Routes.workoutDetail(workoutId)) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "${Routes.ROUTINE_EDIT}/{${Routes.ARG_ROUTINE_ID}}",
            arguments = listOf(navArgument(Routes.ARG_ROUTINE_ID) { type = NavType.LongType }),
        ) {
            RoutineEditScreen(onDone = { navController.popBackStack() })
        }

        composable(
            route = "${Routes.WORKOUT_SESSION}/{${Routes.ARG_WORKOUT_ID}}",
            arguments = listOf(navArgument(Routes.ARG_WORKOUT_ID) { type = NavType.LongType }),
        ) {
            WorkoutSessionScreen(
                onFinished = { workoutId ->
                    navController.navigate(Routes.workoutSummary(workoutId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onDiscarded = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }

        composable(
            route = "${Routes.WORKOUT_SUMMARY}/{${Routes.ARG_WORKOUT_ID}}",
            arguments = listOf(navArgument(Routes.ARG_WORKOUT_ID) { type = NavType.LongType }),
        ) {
            WorkoutSummaryScreen(
                onClose = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }

        composable(
            route = "${Routes.WORKOUT_DETAIL}/{${Routes.ARG_WORKOUT_ID}}",
            arguments = listOf(navArgument(Routes.ARG_WORKOUT_ID) { type = NavType.LongType }),
        ) {
            WorkoutDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
