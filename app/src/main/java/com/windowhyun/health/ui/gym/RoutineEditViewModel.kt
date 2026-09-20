package com.windowhyun.health.ui.gym

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.model.RoutineItem
import com.windowhyun.health.domain.repository.ExerciseRepository
import com.windowhyun.health.domain.repository.RoutineRepository
import com.windowhyun.health.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

data class RoutineEditUiState(
    val routineId: Long = 0,
    val name: String = "",
    val scheduledDays: Set<DayOfWeek> = emptySet(),
    val items: List<RoutineItem> = emptyList(),
    val saved: Boolean = false,
) {
    val isNew: Boolean get() = routineId == 0L
    val canSave: Boolean get() = name.isNotBlank() && items.isNotEmpty()
}

/**
 * 루틴 생성/수정. 순서 변경은 위/아래 이동 버튼으로 처리한다
 * (드래그보다 오조작이 적고 한 손으로 쓰기 쉽다).
 */
@HiltViewModel
class RoutineEditViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val exerciseRepository: ExerciseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val routineId: Long = savedStateHandle[Routes.ARG_ROUTINE_ID] ?: 0L

    private val _uiState = MutableStateFlow(RoutineEditUiState(routineId = routineId))
    val uiState: StateFlow<RoutineEditUiState> = _uiState.asStateFlow()

    /** 운동 선택 시트에서 쓰는 전체 종목 목록. */
    val exercises: StateFlow<List<Exercise>> = exerciseRepository.observeExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (routineId != 0L) {
            viewModelScope.launch {
                routineRepository.getRoutine(routineId)?.let { routine ->
                    _uiState.update {
                        it.copy(
                            name = routine.name,
                            scheduledDays = routine.scheduledDays,
                            items = routine.items,
                        )
                    }
                }
            }
        }
    }

    fun setName(name: String) = _uiState.update { it.copy(name = name) }

    fun toggleDay(day: DayOfWeek) = _uiState.update { state ->
        val days = state.scheduledDays.toMutableSet()
        if (!days.add(day)) days.remove(day)
        state.copy(scheduledDays = days)
    }

    fun addExercise(exercise: Exercise) = _uiState.update { state ->
        val item = RoutineItem(
            exercise = exercise,
            orderIndex = state.items.size,
            defaultSets = DEFAULT_SETS,
            restSeconds = exercise.defaultRestSeconds,
        )
        state.copy(items = state.items + item)
    }

    /** 사전에 없는 종목을 즉석에서 만들어 바로 루틴에 넣는다. */
    fun createAndAddExercise(name: String, category: ExerciseCategory, bodyPart: BodyPart) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = exerciseRepository.addExercise(
                Exercise(id = 0, name = trimmed, category = category, bodyPart = bodyPart),
            )
            exerciseRepository.getExercise(id)?.let { addExercise(it) }
        }
    }

    fun removeItem(index: Int) = _uiState.update { state ->
        state.copy(items = state.items.filterIndexed { i, _ -> i != index }.reindex())
    }

    fun moveItem(from: Int, to: Int) = _uiState.update { state ->
        if (from !in state.items.indices || to !in state.items.indices) return@update state
        val list = state.items.toMutableList()
        list.add(to, list.removeAt(from))
        state.copy(items = list.reindex())
    }

    fun changeSets(index: Int, delta: Int) = _uiState.update { state ->
        state.copy(
            items = state.items.mapIndexed { i, item ->
                if (i == index) item.copy(defaultSets = (item.defaultSets + delta).coerceIn(1, 20)) else item
            },
        )
    }

    fun changeRestSeconds(index: Int, restSeconds: Int?) = _uiState.update { state ->
        state.copy(
            items = state.items.mapIndexed { i, item ->
                if (i == index) item.copy(restSeconds = restSeconds?.coerceIn(0, 600)) else item
            },
        )
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            routineRepository.saveRoutine(
                Routine(
                    id = state.routineId,
                    name = state.name.trim(),
                    scheduledDays = state.scheduledDays,
                    items = state.items,
                ),
            )
            _uiState.update { it.copy(saved = true) }
        }
    }

    private fun List<RoutineItem>.reindex(): List<RoutineItem> =
        mapIndexed { index, item -> item.copy(orderIndex = index) }

    companion object {
        private const val DEFAULT_SETS = 3
    }
}
