package com.windowhyun.health.ui.session

/** 운동 종료 확인창의 글. 무엇이 저장되고 무엇이 사라지는지 숫자로 알려 준다. */
internal data class FinishPrompt(val title: String, val message: String, val confirmLabel: String)

internal fun finishPrompt(completedSets: Int, incompleteSets: Int): FinishPrompt = when {
    completedSets == 0 -> FinishPrompt(
        title = "기록할 세트가 없어요",
        message = "완료한 세트가 없어서 이 운동은 저장되지 않고 사라집니다.",
        confirmLabel = "버리고 종료",
    )

    incompleteSets > 0 -> FinishPrompt(
        title = "운동을 종료할까요?",
        message = "완료한 ${completedSets}세트는 저장되고, 체크하지 않은 ${incompleteSets}세트는 저장되지 않습니다.",
        confirmLabel = "종료",
    )

    else -> FinishPrompt(
        title = "운동을 종료할까요?",
        message = "완료한 ${completedSets}세트를 기록하고 종료합니다.",
        confirmLabel = "종료",
    )
}

/** 종목을 뺄 때 확인이 필요한가. 완료한 세트가 있으면 함께 사라지므로 확인을 받는다. */
internal fun removeExercisePrompt(name: String, completedSets: Int): String =
    "'$name' 종목을 빼면 완료한 ${completedSets}세트도 함께 사라집니다."
