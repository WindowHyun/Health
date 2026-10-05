package com.windowhyun.health.domain.model

/** 이 종목이 어디에 쓰이고 있는지. 지우거나 기록 방식을 바꿔도 되는지 가리는 데 쓴다. */
data class ExerciseUsage(
    /** 이 종목이 들어 있는 운동 기록(진행 중 포함) 수. */
    val workoutCount: Int,
    /** 이 종목이 들어 있는 루틴 수. */
    val routineCount: Int,
) {
    val hasHistory: Boolean get() = workoutCount > 0
}

sealed interface ExerciseEditResult {
    data object Saved : ExerciseEditResult

    /** 이름이 비어 있다. */
    data object NameBlank : ExerciseEditResult

    /** 같은 이름의 다른 종목이 있다. */
    data object NameTaken : ExerciseEditResult

    /** 기본 제공 종목이거나 이미 지워졌다. 기본 종목 이름은 루틴 템플릿이 찾는 데 쓴다. */
    data object NotEditable : ExerciseEditResult

    /** 기록이 있는 종목은 기록 방식(중량/횟수/시간)을 바꿀 수 없다. 기록의 뜻이 달라진다. */
    data object TrackingTypeLocked : ExerciseEditResult
}

sealed interface ExerciseDeleteResult {
    data object Deleted : ExerciseDeleteResult

    /** 기본 제공 종목이거나 이미 지워졌다. */
    data object NotDeletable : ExerciseDeleteResult

    /**
     * 운동 기록이 있어 지울 수 없다. 종목을 지우면 그 종목의 기록이 함께 지워지므로 막는다.
     */
    data class InUse(val workoutCount: Int) : ExerciseDeleteResult
}
