package com.windowhyun.health.ui.components

import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.RunGoalType

/** 러닝 한 건의 제목. 러닝에는 이름이 없어서 목표 종류로 부른다. */
fun runTitle(run: Run): String = when (run.goalType) {
    RunGoalType.FREE -> "자유 달리기"
    RunGoalType.DISTANCE -> "거리 목표 달리기"
    RunGoalType.DURATION -> "시간 목표 달리기"
}
