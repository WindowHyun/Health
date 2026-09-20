package com.windowhyun.health.data.tracking

import com.windowhyun.health.core.util.estimateRunCalories
import com.windowhyun.health.di.ApplicationScope
import com.windowhyun.health.domain.model.LocationSample
import com.windowhyun.health.domain.model.RunGoal
import com.windowhyun.health.domain.model.RunStatus
import com.windowhyun.health.domain.model.RunTrackingState
import com.windowhyun.health.domain.repository.RunRepository
import com.windowhyun.health.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 진행 중인 러닝의 단일 진실 공급원.
 *
 * Foreground Service 가 GPS 샘플과 1초 타이머를 넣어 주고,
 * ViewModel 은 [state] 만 구독한다. 서비스에 바인딩할 필요가 없으므로
 * 화면 회전이나 화면 꺼짐에 영향을 받지 않는다.
 *
 * 누적값은 주기적으로 DB 에 기록되므로 기록 도중 앱이 죽어도 남는다.
 */
@Singleton
class RunTracker @Inject constructor(
    private val runRepository: RunRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(RunTrackingState())
    val state: StateFlow<RunTrackingState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var accumulator: RunMetricsAccumulator? = null
    private var bodyWeightKg: Double = DEFAULT_BODY_WEIGHT_KG
    private var ticksSinceFlush = 0

    /** 러닝을 시작한다. 이미 진행 중이면 아무것도 하지 않는다. */
    suspend fun start(goal: RunGoal) {
        mutex.withLock {
            if (_state.value.isActive) return
            val settings = settingsRepository.current()
            bodyWeightKg = settings.bodyWeightKg
            accumulator = RunMetricsAccumulator(autoLapMeters = settings.autoLapMeters)
            ticksSinceFlush = 0

            val runId = runRepository.startRun(goal.type, goal.value)
            _state.value = RunTrackingState(
                status = RunStatus.TRACKING,
                runId = runId,
                goal = goal,
                startTime = System.currentTimeMillis(),
            )
        }
    }

    /** GPS 샘플 1건. 일시정지 중에는 무시한다. */
    suspend fun onLocation(sample: LocationSample) {
        mutex.withLock {
            val accumulator = accumulator ?: return
            if (_state.value.status != RunStatus.TRACKING) {
                // 일시정지 중에도 정확도는 보여 준다(재개 직후 바로 잡히도록).
                _state.update { it.copy(lastAccuracyMeters = sample.accuracyMeters) }
                return
            }

            val newLap = accumulator.onLocation(sample)
            if (newLap != null) {
                runRepository.appendLap(_state.value.runId, newLap)
            }
            publish(accumulator, lastAccuracy = sample.accuracyMeters)
        }
    }

    /** 1초마다 호출된다. */
    suspend fun tick() {
        mutex.withLock {
            val accumulator = accumulator ?: return
            if (_state.value.status != RunStatus.TRACKING) return

            accumulator.advanceTime()
            publish(accumulator, lastAccuracy = _state.value.lastAccuracyMeters)

            if (++ticksSinceFlush >= FLUSH_INTERVAL_SECONDS) {
                ticksSinceFlush = 0
                flush(accumulator)
            }
        }
    }

    suspend fun pause() {
        mutex.withLock {
            if (_state.value.status != RunStatus.TRACKING) return
            val accumulator = accumulator ?: return
            // 정지한 사이의 이동은 거리에 넣지 않는다.
            accumulator.breakSegment()
            flush(accumulator)
            _state.update { it.copy(status = RunStatus.PAUSED, currentPaceSecPerKm = 0.0) }
        }
    }

    suspend fun resume() {
        mutex.withLock {
            if (_state.value.status != RunStatus.PAUSED) return
            _state.update { it.copy(status = RunStatus.TRACKING) }
        }
    }

    /** 러닝을 끝낸다. 결과 화면에서 저장/삭제를 선택할 때까지 상태는 유지된다. */
    suspend fun finish() {
        mutex.withLock {
            val accumulator = accumulator ?: return
            if (!_state.value.isActive) return

            accumulator.finalizePartialLap()?.let { lap ->
                runRepository.appendLap(_state.value.runId, lap)
            }
            flush(accumulator)

            val runId = _state.value.runId
            runRepository.finishRun(
                runId = runId,
                endTime = System.currentTimeMillis(),
                distanceMeters = accumulator.distanceMeters,
                durationSeconds = accumulator.elapsedSeconds,
                averagePaceSecPerKm = accumulator.averagePaceSecPerKm,
                bestPaceSecPerKm = accumulator.bestPaceSecPerKm,
                calories = estimateRunCalories(bodyWeightKg, accumulator.distanceMeters),
            )
            publish(accumulator, lastAccuracy = _state.value.lastAccuracyMeters)
            _state.update { it.copy(status = RunStatus.FINISHED, currentPaceSecPerKm = 0.0) }
        }
    }

    /** 결과 화면에서 "삭제"를 고른 경우. */
    suspend fun discard() {
        mutex.withLock {
            val runId = _state.value.runId
            if (runId != 0L) runRepository.deleteRun(runId)
            accumulator = null
            _state.value = RunTrackingState()
        }
    }

    /** 결과 화면을 닫을 때 상태를 비운다(기록은 DB 에 남는다). */
    fun reset() {
        scope.launch {
            mutex.withLock {
                accumulator = null
                _state.value = RunTrackingState()
            }
        }
    }

    private fun publish(accumulator: RunMetricsAccumulator, lastAccuracy: Float?) {
        _state.update { current ->
            current.copy(
                distanceMeters = accumulator.distanceMeters,
                durationSeconds = accumulator.elapsedSeconds,
                currentPaceSecPerKm = accumulator.currentPaceSecPerKm,
                averagePaceSecPerKm = accumulator.averagePaceSecPerKm,
                bestPaceSecPerKm = accumulator.bestPaceSecPerKm,
                calories = estimateRunCalories(bodyWeightKg, accumulator.distanceMeters),
                laps = accumulator.laps.toList(),
                lastAccuracyMeters = lastAccuracy,
            )
        }
    }

    /** 경로 포인트와 누적값을 DB 에 반영한다. */
    private suspend fun flush(accumulator: RunMetricsAccumulator) {
        val runId = _state.value.runId
        if (runId == 0L) return
        runRepository.appendRoutePoints(runId, accumulator.drainPendingPoints())
        runRepository.updateProgress(
            runId = runId,
            distanceMeters = accumulator.distanceMeters,
            durationSeconds = accumulator.elapsedSeconds,
            averagePaceSecPerKm = accumulator.averagePaceSecPerKm,
            bestPaceSecPerKm = accumulator.bestPaceSecPerKm,
            calories = estimateRunCalories(bodyWeightKg, accumulator.distanceMeters),
        )
    }

    private companion object {
        /** 몇 초마다 DB 에 반영할지. */
        const val FLUSH_INTERVAL_SECONDS = 5

        const val DEFAULT_BODY_WEIGHT_KG = 70.0
    }
}
