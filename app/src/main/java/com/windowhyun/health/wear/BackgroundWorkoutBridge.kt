package com.windowhyun.health.wear

import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.di.ApplicationScope
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.domain.repository.WorkoutRepository
import com.windowhyun.health.shared.WorkoutSnapshot
import com.windowhyun.health.domain.usecase.CurrentSetFinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 폰의 운동 화면이 없을 때(다른 탭으로 나갔거나 화면을 닫았을 때)도 시계가 지금 할 세트를 보고 완료할 수 있게 하는 다리.
 *
 * 운동 화면의 ViewModel 이 살아 있는 동안에는 그쪽이 시계와 이야기한다([WatchLink.sessionAttached]).
 * 화면이 없을 때만 이 다리가 진행 중인 운동을 지켜보며 세트를 시계로 보내고, 시계의 "완료"를 직접 저장한다.
 *
 * 한계: 휴식 타이머는 화면의 ViewModel 이 들고 있어서, 화면이 없는 동안 시계에서 완료한 세트는
 * 휴식 타이머를 시작하지 않는다(세트 기록만 저장된다).
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class BackgroundWorkoutBridge internal constructor(
    private val workouts: WorkoutRepository,
    private val useLb: Flow<Boolean>,
    private val link: WatchLink,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        workouts: WorkoutRepository,
        settings: SettingsRepository,
        link: WatchLink,
        @ApplicationScope scope: CoroutineScope,
    ) : this(workouts, settings.settings.map { it.weightUnit == WeightUnit.LB }.distinctUntilChanged(), link, scope)

    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            link.sessionAttached
                .flatMapLatest { attached -> if (attached) emptyFlow() else snapshots() }
                .collect { snapshot -> if (snapshot.active) link.publishWorkout(snapshot) else link.clearWorkout() }
        }
        scope.launch {
            link.setCompletions.collect { setId -> if (!link.sessionAttached.value) complete(setId) }
        }
    }

    private fun snapshots(): Flow<WorkoutSnapshot> =
        workouts.observeActiveWorkout().flatMapLatest { active ->
            if (active == null) {
                flowOf(WorkoutSnapshot.None)
            } else {
                combine(workouts.observeWorkoutDetail(active.id), useLb) { workout: Workout?, lb ->
                    workout?.toSnapshot(lb) ?: WorkoutSnapshot.None
                }.debounce(DEBOUNCE_MILLIS)
            }
        }

    /** 화면 없이 세트를 끝낸다. 시계가 보던 세트가 지금 할 세트가 아니거나 값이 비었으면 아무것도 하지 않는다. */
    private suspend fun complete(setId: Long) {
        val active = workouts.observeActiveWorkout().first() ?: return
        val workout = workouts.getWorkout(active.id) ?: return
        val current = CurrentSetFinder.find(workout.exercises) ?: return
        val set = current.set
        if (set.id != setId) return
        val hasValue = when (current.record.exercise.trackingType) {
            ExerciseTrackingType.TIME -> set.durationSeconds > 0
            else -> set.reps > 0
        }
        if (!hasValue) return
        workouts.setCompleted(set.id, set.weightKg, set.reps, true, set.durationSeconds)
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 300L
    }
}


