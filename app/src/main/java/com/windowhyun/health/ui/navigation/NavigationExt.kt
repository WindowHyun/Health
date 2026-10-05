package com.windowhyun.health.ui.navigation

import androidx.navigation.NavController

/**
 * 바로 앞 화면이 같은 대상이면 새로 쌓지 않고 그 화면으로 돌아간다.
 *
 * 종목 상세 -> 운동 기록 -> 같은 종목 상세 처럼 오가면 화면이 계속 쌓여, 기록 탭으로
 * 돌아가려면 뒤로 가기를 수십 번 눌러야 한다. 방금 보던 화면으로 가는 것이면 되돌아간다.
 *
 * @param routePattern NavHost 에 등록한 경로 모양(예: "exercise_detail/{exerciseId}")
 * @param argName 경로의 id 인자 이름
 */
fun NavController.navigateOrReturn(routePattern: String, argName: String, id: Long, route: String) {
    val previous = previousBackStackEntry
    if (previous?.destination?.route == routePattern && previous.arguments?.getLong(argName) == id) {
        popBackStack()
    } else {
        navigate(route)
    }
}
