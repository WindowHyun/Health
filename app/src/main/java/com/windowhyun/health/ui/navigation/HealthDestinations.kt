package com.windowhyun.health.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

/** 앱 화면 경로. 문자열을 한 곳에서만 관리한다. */
object Routes {
    const val HOME = "home"
    const val GYM = "gym"
    const val RUNNING = "running"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    const val ROUTINE_EDIT = "routine_edit"
    const val ARG_ROUTINE_ID = "routineId"
    fun routineEdit(routineId: Long = 0L) = "$ROUTINE_EDIT/$routineId"

    const val WORKOUT_SESSION = "workout_session"
    const val ARG_WORKOUT_ID = "workoutId"
    fun workoutSession(workoutId: Long) = "$WORKOUT_SESSION/$workoutId"

    const val WORKOUT_SUMMARY = "workout_summary"
    fun workoutSummary(workoutId: Long) = "$WORKOUT_SUMMARY/$workoutId"

    const val WORKOUT_DETAIL = "workout_detail"
    fun workoutDetail(workoutId: Long) = "$WORKOUT_DETAIL/$workoutId"

    /** 러닝 진행 화면. 상태는 RunTracker 가 들고 있어 인자가 필요 없다. */
    const val RUN_ACTIVE = "run_active"

    /** 러닝 결과 화면. */
    const val RUN_SUMMARY = "run_summary"
}

/** 하단 네비게이션 탭. 설정은 홈 우측 상단 버튼으로만 접근한다. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME(Routes.HOME, "홈", Icons.Filled.Home),
    GYM(Routes.GYM, "헬스", Icons.Filled.FitnessCenter),
    RUNNING(Routes.RUNNING, "러닝", Icons.AutoMirrored.Filled.DirectionsRun),
    HISTORY(Routes.HISTORY, "기록", Icons.Filled.CalendarMonth),
}
