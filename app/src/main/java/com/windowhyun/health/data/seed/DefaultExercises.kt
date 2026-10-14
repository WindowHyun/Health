package com.windowhyun.health.data.seed

import com.windowhyun.health.data.local.entity.ExerciseEntity

/**
 * 기본 운동 종목. 내용은 코드가 아니라 `app/src/main/resources/health/exercises.json` 에 있다
 * (형식은 [ExerciseCatalog], 추가 방법은 docs/DATA.md).
 *
 * 처음 실행할 때 넣고, 앱을 업데이트해 목록이 늘면 빠진 것만 채운다([ExerciseSeedCallback]).
 * 사용자는 앱에서 자유롭게 종목을 추가할 수 있다.
 *
 * 이름은 루틴 템플릿(`routine_templates.json`)과 이미 설치된 기기의 기록이 가리키므로,
 * 있는 종목의 이름을 바꾸거나 지우지 않는다. 종목을 늘리는 것만 한다.
 * 기록 방식은 중량×횟수가 기본이고, 맨몸 횟수 운동은 횟수만, 버티는 운동과 유산소는 시간만 쓴다.
 */
object DefaultExercises {

    /** 앱에 포함된 기본 종목. 처음 쓸 때 한 번 읽는다. */
    val all: List<ExerciseEntity> by lazy { ExerciseCatalog.loadBundled() }
}
