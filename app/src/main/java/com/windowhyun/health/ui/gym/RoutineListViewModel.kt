package com.windowhyun.health.ui.gym

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.repository.RoutineRepository
import com.windowhyun.health.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoutineListUiState(
    val routines: List<Routine> = emptyList(),
    val activeWorkout: Workout? = null,
    val loading: Boolean = true,
)

@HiltViewModel
class RoutineListViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    private val _startedWorkoutId = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val startedWorkoutId = _startedWorkoutId.asSharedFlow()

    val uiState: StateFlow<RoutineListUiState> = combine(
        routineRepository.observeRoutines(),
        workoutRepository.observeActiveWorkout(),
    ) { routines, active ->
        RoutineListUiState(routines = routines, activeWorkout = active, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineListUiState())

    fun startWorkout(routineId: Long?) {
        viewModelScope.launch {
            val activeId = uiState.value.activeWorkout?.id
            val workoutId = activeId ?: workoutRepository.startWorkout(routineId)
            _startedWorkoutId.emit(workoutId)
        }
    }

    fun deleteRoutine(routineId: Long) {
        viewModelScope.launch { routineRepository.deleteRoutine(routineId) }
    }
}
