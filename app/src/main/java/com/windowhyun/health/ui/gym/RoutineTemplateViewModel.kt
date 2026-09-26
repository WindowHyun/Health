package com.windowhyun.health.ui.gym

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.model.RoutineItem
import com.windowhyun.health.domain.repository.ExerciseRepository
import com.windowhyun.health.domain.repository.RoutineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 템플릿을 적용한 결과. 화면에서 스낵바로 보여 준다. */
data class TemplateApplyResult(
    val template: RoutineTemplates.Template,
    val createdRoutineNames: List<String>,
    /** 사용자가 지우거나 이름을 바꿔서 이번엔 못 찾은 종목. 정상 파일이면 비어 있다. */
    val missingExerciseNames: Set<String>,
)

/**
 * 이름난 루틴 템플릿을 실제 루틴으로 만든다.
 *
 * 종목은 이름으로 찾는다. 기본 제공 종목을 지우거나 이름을 바꾼 사용자도 있을 수
 * 있어서, 못 찾은 종목은 건너뛰고 나머지로 루틴을 만든다. 하루치가 통째로
 * 비면 그 루틴은 만들지 않는다 — 빈 루틴은 시작 버튼이 눌리지 않아 쓸모가 없다.
 */
@HiltViewModel
class RoutineTemplateViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {

    private val _applied = MutableSharedFlow<TemplateApplyResult>(extraBufferCapacity = 1)
    val applied = _applied.asSharedFlow()

    /**
     * 적용 중인지. 시트는 결과가 나와야 닫히므로, 그 사이 카드를 한 번 더 누르면
     * 같은 루틴이 두 벌 만들어진다. 메인 스레드에서만 읽고 쓴다.
     */
    private var applying = false

    fun applyTemplate(template: RoutineTemplates.Template) {
        if (applying) return
        applying = true
        viewModelScope.launch {
            try {
                apply(template)
            } finally {
                applying = false
            }
        }
    }

    private suspend fun apply(template: RoutineTemplates.Template) {
        val byName = exerciseRepository.observeExercises().first().associateBy { it.name }
        val missing = mutableSetOf<String>()
        val createdNames = mutableListOf<String>()

        template.days.forEach { day ->
            val items = day.exercises.mapNotNull { (name, sets) ->
                val exercise = byName[name]
                if (exercise == null) {
                    missing += name
                    null
                } else {
                    exercise to sets
                }
            }.mapIndexed { index, (exercise, sets) ->
                RoutineItem(
                    exercise = exercise,
                    orderIndex = index,
                    defaultSets = sets,
                    restSeconds = exercise.defaultRestSeconds,
                )
            }

            if (items.isNotEmpty()) {
                routineRepository.saveRoutine(
                    Routine(id = 0, name = day.routineName, items = items),
                )
                createdNames += day.routineName
            }
        }

        _applied.emit(TemplateApplyResult(template, createdNames, missing))
    }
}
