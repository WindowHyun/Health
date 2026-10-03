package com.windowhyun.health.data.tracking

import android.os.SystemClock
import com.windowhyun.health.core.util.estimateRunCalories
import com.windowhyun.health.core.util.haversineMeters
import com.windowhyun.health.domain.model.LocationSample
import com.windowhyun.health.domain.model.RunCue
import com.windowhyun.health.domain.model.RunGoal
import com.windowhyun.health.domain.model.RunStatus
import com.windowhyun.health.domain.model.RunTrackingState
import com.windowhyun.health.domain.repository.RunRepository
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.domain.repository.StepCounter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
class RunTracker internal constructor(
    private val runRepository: RunRepository,
    private val settingsRepository: SettingsRepository,
    private val stepCounter: StepCounter,
    /**
     * 경과 시간을 재는 시계(밀리초). 기기가 잠든 시간도 세는 시계여야 한다.
     * 테스트에서는 직접 넣는다.
     */
    private val clock: () -> Long,
) {

    @Inject
    constructor(
        runRepository: RunRepository,
        settingsRepository: SettingsRepository,
        stepCounter: StepCounter,
    ) : this(runRepository, settingsRepository, stepCounter, SystemClock::elapsedRealtime)

    private val _state = MutableStateFlow(RunTrackingState())
    val state: StateFlow<RunTrackingState> = _state.asStateFlow()

    /** 진동으로 알릴 순간. 서비스가 받아 진동을 낸다. 받는 쪽이 없으면 버려진다. */
    private val _cues = MutableSharedFlow<RunCue>(extraBufferCapacity = 16)
    val cues: SharedFlow<RunCue> = _cues.asSharedFlow()

    private val mutex = Mutex()
    private var accumulator: RunMetricsAccumulator? = null
    private var bodyWeightKg: Double = DEFAULT_BODY_WEIGHT_KG
    private var ticksSinceFlush = 0

    // 1초 타이머가 올 때마다 1 을 더하면, 화면이 꺼져 기기가 잠든 사이 빠진 타이머만큼 시간이
    // 짧게 기록된다(거리는 GPS 로 그대로 쌓여 페이스만 빨라진다). 시계에서 지난 시간을 읽어 센다.
    private var lastClockAt = 0L
    private var carryMillis = 0L
    private var lastLocationAt = 0L

    // 자동 일시정지: 마지막으로 "움직였다"고 본 시각과 그 위치. 멈춘 자리에서 일정 거리 벗어나면
    // 다시 움직이는 것으로 본다. 속도가 아니라 이동 거리로 재서 GPS 흔들림에 덜 흔들린다.
    private var autoPauseEnabled = false
    private var movementAnchor: LocationSample? = null
    private var lastMovementAt = 0L
    private var pauseAnchor: LocationSample? = null
    private var goalCueSent = false

    /** 러닝을 시작한다. 이미 진행 중이면 아무것도 하지 않는다. */
    suspend fun start(goal: RunGoal) {
        mutex.withLock {
            if (_state.value.isActive) return
            val settings = settingsRepository.current()
            bodyWeightKg = settings.bodyWeightKg
            accumulator = RunMetricsAccumulator(autoLapMeters = settings.autoLapMeters)
            ticksSinceFlush = 0
            lastClockAt = clock()
            carryMillis = 0
            lastLocationAt = lastClockAt
            autoPauseEnabled = settings.autoPauseRun
            movementAnchor = null
            pauseAnchor = null
            lastMovementAt = lastClockAt
            goalCueSent = false

            val runId = runRepository.startRun(goal.type, goal.value)
            _state.value = RunTrackingState(
                status = RunStatus.TRACKING,
                runId = runId,
                goal = goal,
                startTime = System.currentTimeMillis(),
                stepCountAvailable = stepCounter.isAvailable() && stepCounter.hasPermission(),
            )
        }
    }

    /** GPS 샘플 1건. 일시정지 중에는 무시한다. */
    suspend fun onLocation(sample: LocationSample) {
        mutex.withLock {
            val accumulator = accumulator ?: return
            // 자동으로 멈춘 상태에서 멈춘 자리를 벗어났으면 이어 간다.
            if (_state.value.status == RunStatus.PAUSED && _state.value.autoPaused && movedAwayFromPause(sample)) {
                resumeLocked(auto = true)
            }
            if (_state.value.status != RunStatus.TRACKING) {
                // 일시정지 중에도 정확도는 보여 준다(재개 직후 바로 잡히도록).
                _state.update { it.copy(lastAccuracyMeters = sample.accuracyMeters) }
                return
            }

            // 위치가 들어온 시점의 경과 시간으로 페이스 · Lap 을 계산한다. 타이머가 밀려 있으면
            // 거리만 먼저 늘어 구간 시간이 모자라게 잡힌다.
            syncClock(accumulator)
            lastLocationAt = clock()
            noteMovement(sample)
            accumulator.onLocation(sample).forEach { lap ->
                runRepository.appendLap(_state.value.runId, lap)
                _cues.tryEmit(RunCue.LapCompleted(lap.lapNumber))
            }
            publish(accumulator, lastAccuracy = sample.accuracyMeters)
            _state.update { it.copy(signalLost = false) }
        }
    }

    /**
     * 걸음 센서 값 1건. 센서는 부팅 이후 누적값을 주므로 누적기가 차이만 더한다.
     * 일시정지 중에는 무시해서 정지한 동안의 걸음이 더해지지 않게 한다.
     */
    suspend fun onStepCount(rawCumulative: Long) {
        mutex.withLock {
            val accumulator = accumulator ?: return
            if (_state.value.status != RunStatus.TRACKING) return
            accumulator.onStepCount(rawCumulative)
            publish(accumulator, lastAccuracy = _state.value.lastAccuracyMeters)
        }
    }

    /** 1초마다 호출된다. */
    suspend fun tick() {
        mutex.withLock {
            val accumulator = accumulator ?: return
            if (_state.value.status != RunStatus.TRACKING) return

            syncClock(accumulator)
            val signalLost = clock() - lastLocationAt > SIGNAL_LOST_MILLIS
            _state.update { it.copy(signalLost = signalLost) }
            publish(accumulator, lastAccuracy = _state.value.lastAccuracyMeters)

            val current = _state.value
            if (!goalCueSent && current.goal.isReached(current.distanceMeters, current.durationSeconds)) {
                goalCueSent = true
                _cues.tryEmit(RunCue.GoalReached)
            }

            // 첫 위치를 받은 뒤에만 센다. 신호를 못 받는 중에는 멈춘 것인지 알 수 없다.
            if (autoPauseEnabled && current.lastAccuracyMeters != null && !signalLost &&
                clock() - lastMovementAt >= AUTO_PAUSE_MILLIS
            ) {
                pauseLocked(accumulator, auto = true)
                return
            }

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
            pauseLocked(accumulator, auto = false)
        }
    }

    suspend fun resume() {
        mutex.withLock {
            if (_state.value.status != RunStatus.PAUSED) return
            resumeLocked(auto = false)
        }
    }

    /** [mutex] 를 잡은 채로 부른다. [auto] 는 멈춘 것을 감지해 저절로 멈추는 경우. */
    private suspend fun pauseLocked(accumulator: RunMetricsAccumulator, auto: Boolean) {
        syncClock(accumulator)
        // 정지한 사이의 이동은 거리에 넣지 않는다.
        accumulator.breakSegment()
        flush(accumulator)
        pauseAnchor = movementAnchor
        _state.update {
            it.copy(status = RunStatus.PAUSED, currentPaceSecPerKm = 0.0, autoPaused = auto)
        }
        if (auto) _cues.tryEmit(RunCue.AutoPaused)
    }

    /** [mutex] 를 잡은 채로 부른다. */
    private fun resumeLocked(auto: Boolean) {
        // 멈춰 있던 시간은 세지 않는다. 위치도 새로 기다리므로 바로 "신호 없음"이 되지 않게 한다.
        lastClockAt = clock()
        lastLocationAt = lastClockAt
        // 이어 가는 순간부터 다시 "움직임"을 센다. 안 그러면 곧바로 또 멈춘 것으로 본다.
        lastMovementAt = lastClockAt
        movementAnchor = null
        pauseAnchor = null
        _state.update { it.copy(status = RunStatus.TRACKING, signalLost = false, autoPaused = false) }
        if (auto) _cues.tryEmit(RunCue.AutoResumed)
    }

    /** 위치가 [MOVEMENT_METERS] 이상 옮겨 갔으면 "움직였다"고 적는다. 정확도가 나쁜 위치는 믿지 않는다. */
    private fun noteMovement(sample: LocationSample) {
        val accuracy = sample.accuracyMeters ?: return
        if (accuracy > MOVEMENT_MAX_ACCURACY_METERS) return
        val anchor = movementAnchor
        if (anchor == null ||
            haversineMeters(anchor.latitude, anchor.longitude, sample.latitude, sample.longitude) >= MOVEMENT_METERS
        ) {
            movementAnchor = sample
            lastMovementAt = clock()
        }
    }

    /** 자동으로 멈춘 자리에서 [RESUME_DISTANCE_METERS] 이상 벗어났는가. */
    private fun movedAwayFromPause(sample: LocationSample): Boolean {
        val anchor = pauseAnchor ?: return false
        val accuracy = sample.accuracyMeters ?: return false
        if (accuracy > MOVEMENT_MAX_ACCURACY_METERS) return false
        return haversineMeters(anchor.latitude, anchor.longitude, sample.latitude, sample.longitude) >=
            RESUME_DISTANCE_METERS
    }

    /** 러닝을 끝낸다. 결과 화면에서 저장/삭제를 선택할 때까지 상태는 유지된다. */
    suspend fun finish() {
        mutex.withLock {
            val accumulator = accumulator ?: return
            if (!_state.value.isActive) return

            if (_state.value.status == RunStatus.TRACKING) syncClock(accumulator)
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
                steps = accumulator.steps.toInt(),
            )
            publish(accumulator, lastAccuracy = _state.value.lastAccuracyMeters)
            _state.update { it.copy(status = RunStatus.FINISHED, currentPaceSecPerKm = 0.0) }
        }
    }

    /**
     * 앱을 켤 때 부른다. 기록 중이 아닌데 끝나지 않은 러닝(앱이 강제로 종료된 흔적)을 마감한다.
     *
     * 러닝 시작과 같은 잠금 안에서 하므로, 방금 시작한 러닝을 마감해 버리는 일은 없다.
     */
    suspend fun recoverUnfinishedRuns(): Int = mutex.withLock {
        runRepository.closeUnfinishedRuns(excludeRunId = _state.value.runId)
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

    /**
     * 결과 화면을 닫을 때 상태를 비운다(기록은 DB 에 남는다).
     *
     * 예전에는 앱 스코프에 던져 두고 바로 반환했는데, 그러면 초기화가 실행되기 전에
     * 새 러닝이 시작될 경우 방금 시작한 상태를 지워 버릴 수 있었다.
     * discard() 와 같이 호출한 쪽이 완료를 기다리게 한다.
     */
    suspend fun reset() {
        mutex.withLock {
            accumulator = null
            _state.value = RunTrackingState()
        }
    }

    /** 마지막으로 센 뒤 지난 시간을 더한다. 호출이 늦어도 실제로 흐른 만큼 센다. */
    private fun syncClock(accumulator: RunMetricsAccumulator) {
        val now = clock()
        val total = carryMillis + (now - lastClockAt).coerceAtLeast(0)
        lastClockAt = now
        carryMillis = total % 1_000
        val seconds = total / 1_000
        if (seconds > 0) accumulator.advanceTime(seconds)
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
                steps = accumulator.steps,
                cadenceStepsPerMinute = accumulator.cadenceStepsPerMinute,
                laps = accumulator.laps.toList(),
                route = accumulator.routePoints.toList(),
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
            steps = accumulator.steps.toInt(),
        )
    }

    private companion object {
        /** 몇 초마다 DB 에 반영할지. */
        const val FLUSH_INTERVAL_SECONDS = 5

        /** 위치를 이만큼 못 받으면 신호가 끊긴 것으로 본다. 위치는 2초마다 온다. */
        const val SIGNAL_LOST_MILLIS = 15_000L

        /** 이만큼 못 움직였으면 멈춘 것으로 본다. 신호등 대기처럼 짧은 정지도 잡도록 짧게. */
        const val AUTO_PAUSE_MILLIS = 8_000L

        /** 직전 기준 위치에서 이만큼 옮겨 가야 "움직였다". 걷는 속도(약 1m/초)면 8초 안에 넘는다. */
        const val MOVEMENT_METERS = 8.0

        /** 자동으로 멈춘 자리에서 이만큼 벗어나면 다시 달리는 것으로 본다(GPS 흔들림보다 크게). */
        const val RESUME_DISTANCE_METERS = 12.0

        /** 움직임 판단에 쓰는 위치의 최대 오차. */
        const val MOVEMENT_MAX_ACCURACY_METERS = 20f

        const val DEFAULT_BODY_WEIGHT_KG = 70.0
    }
}
