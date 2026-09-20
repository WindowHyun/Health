package com.windowhyun.health.data.tracking

import com.windowhyun.health.core.util.haversineMeters
import com.windowhyun.health.core.util.paceSecPerKm
import com.windowhyun.health.domain.model.LocationSample
import com.windowhyun.health.domain.model.RunLap
import com.windowhyun.health.domain.model.RunPoint

/**
 * GPS 샘플을 받아 거리 / 페이스 / Lap 을 누적하는 순수 계산기.
 *
 * android 타입에 의존하지 않으므로 JVM 단위 테스트로 전체 로직을 검증할 수 있다.
 * 시간은 바깥(서비스의 1초 타이머)에서 [advanceTime] 으로 넣어 준다.
 */
class RunMetricsAccumulator(
    private val autoLapMeters: Int = 1_000,
) {

    private var lastAccepted: LocationSample? = null
    private var segmentBreakPending = true

    /** 현재 구간 페이스 계산용 (경과초, 누적거리) 표본. */
    private val paceWindow = ArrayDeque<Pair<Long, Double>>()

    var distanceMeters: Double = 0.0
        private set

    var elapsedSeconds: Long = 0
        private set

    var currentPaceSecPerKm: Double = 0.0
        private set

    var bestPaceSecPerKm: Double = 0.0
        private set

    val laps: List<RunLap> get() = _laps
    private val _laps = mutableListOf<RunLap>()

    /** 아직 DB 에 저장하지 않은 경로 포인트. */
    private val pendingPoints = mutableListOf<RunPoint>()

    private var lastLapDistance = 0.0
    private var lastLapElapsed = 0L

    val averagePaceSecPerKm: Double get() = paceSecPerKm(distanceMeters, elapsedSeconds)

    /**
     * 일시정지 후 재개. 다음 위치는 새 구간의 시작으로 표시되고
     * 정지한 사이에 이동한 거리는 누적하지 않는다.
     */
    fun breakSegment() {
        lastAccepted = null
        segmentBreakPending = true
        paceWindow.clear()
    }

    /** 1초 타이머가 호출한다. 일시정지 중에는 호출하지 않는다. */
    fun advanceTime(seconds: Long = 1) {
        elapsedSeconds += seconds
        updateCurrentPace()
    }

    /**
     * GPS 샘플 1건 처리.
     *
     * @return 이번 호출로 새로 만들어진 Lap (없으면 null)
     */
    fun onLocation(sample: LocationSample): RunLap? {
        if (!isAcceptable(sample)) return null

        val previous = lastAccepted
        val isSegmentStart = segmentBreakPending
        if (previous != null) {
            distanceMeters += haversineMeters(
                previous.latitude,
                previous.longitude,
                sample.latitude,
                sample.longitude,
            )
        }
        lastAccepted = sample
        segmentBreakPending = false

        pendingPoints += RunPoint(
            latitude = sample.latitude,
            longitude = sample.longitude,
            altitude = sample.altitude,
            timestamp = sample.timestamp,
            isSegmentStart = isSegmentStart,
        )

        paceWindow.addLast(elapsedSeconds to distanceMeters)
        trimPaceWindow()
        updateCurrentPace()

        return checkLap()
    }

    /**
     * 노이즈 걸러내기.
     * - 정확도가 나쁜 샘플은 버린다
     * - 직전 점과 너무 가까우면(GPS 흔들림) 버린다
     * - 사람이 낼 수 없는 속도로 튀면 버린다
     */
    private fun isAcceptable(sample: LocationSample): Boolean {
        if (sample.accuracyMeters > MAX_ACCURACY_METERS) return false
        val previous = lastAccepted ?: return true

        val meters = haversineMeters(
            previous.latitude,
            previous.longitude,
            sample.latitude,
            sample.longitude,
        )
        if (meters < MIN_DISPLACEMENT_METERS) return false

        val deltaSeconds = (sample.timestamp - previous.timestamp) / 1000.0
        if (deltaSeconds > 0 && meters / deltaSeconds > MAX_SPEED_MPS) return false

        return true
    }

    /** 최근 [PACE_WINDOW_SECONDS] 초만 남긴다. */
    private fun trimPaceWindow() {
        while (paceWindow.size > 2 &&
            elapsedSeconds - paceWindow.first().first > PACE_WINDOW_SECONDS
        ) {
            paceWindow.removeFirst()
        }
    }

    private fun updateCurrentPace() {
        trimPaceWindow()
        val oldest = paceWindow.firstOrNull()
        if (oldest == null) {
            currentPaceSecPerKm = 0.0
            return
        }
        val windowSeconds = elapsedSeconds - oldest.first
        val windowMeters = distanceMeters - oldest.second
        if (windowSeconds <= 0 || windowMeters <= 0) {
            currentPaceSecPerKm = 0.0
            return
        }
        currentPaceSecPerKm = paceSecPerKm(windowMeters, windowSeconds)

        // 표본 구간이 충분히 길 때만 최고 페이스 후보로 본다(짧은 구간은 튄다).
        if (windowMeters >= MIN_BEST_PACE_WINDOW_METERS) {
            if (bestPaceSecPerKm <= 0.0 || currentPaceSecPerKm < bestPaceSecPerKm) {
                bestPaceSecPerKm = currentPaceSecPerKm
            }
        }
    }

    /** 자동 Lap 경계를 넘었으면 Lap 을 만든다. */
    private fun checkLap(): RunLap? {
        if (autoLapMeters <= 0) return null
        if (distanceMeters - lastLapDistance < autoLapMeters) return null

        val lapDistance = distanceMeters - lastLapDistance
        val lapDuration = elapsedSeconds - lastLapElapsed
        val lap = RunLap(
            lapNumber = _laps.size + 1,
            distanceMeters = lapDistance,
            durationSeconds = lapDuration,
            paceSecPerKm = paceSecPerKm(lapDistance, lapDuration),
        )
        _laps += lap
        lastLapDistance = distanceMeters
        lastLapElapsed = elapsedSeconds
        return lap
    }

    /**
     * 종료 시 남은 거리로 마지막 Lap 을 만든다.
     * 1km 를 못 채운 구간도 기록에 남기기 위해서다.
     */
    fun finalizePartialLap(): RunLap? {
        val lapDistance = distanceMeters - lastLapDistance
        if (lapDistance < MIN_PARTIAL_LAP_METERS) return null
        val lapDuration = elapsedSeconds - lastLapElapsed
        val lap = RunLap(
            lapNumber = _laps.size + 1,
            distanceMeters = lapDistance,
            durationSeconds = lapDuration,
            paceSecPerKm = paceSecPerKm(lapDistance, lapDuration),
        )
        _laps += lap
        lastLapDistance = distanceMeters
        lastLapElapsed = elapsedSeconds
        return lap
    }

    /** 아직 저장하지 않은 경로 포인트를 꺼내 간다(꺼내면 비워진다). */
    fun drainPendingPoints(): List<RunPoint> {
        if (pendingPoints.isEmpty()) return emptyList()
        val drained = pendingPoints.toList()
        pendingPoints.clear()
        return drained
    }

    companion object {
        /** 이보다 정확도가 나쁜 샘플은 무시한다. */
        const val MAX_ACCURACY_METERS = 30f

        /** GPS 흔들림으로 거리가 늘어나는 것을 막는 최소 이동 거리. */
        const val MIN_DISPLACEMENT_METERS = 3.0

        /** 사람이 달릴 수 없는 속도(약 43km/h)면 튄 값으로 본다. */
        const val MAX_SPEED_MPS = 12.0

        /** 현재 페이스를 계산할 최근 구간 길이. */
        const val PACE_WINDOW_SECONDS = 30L

        /** 최고 페이스로 인정할 최소 구간 거리. */
        const val MIN_BEST_PACE_WINDOW_METERS = 100.0

        /** 마지막 자투리 Lap 을 남길 최소 거리. */
        const val MIN_PARTIAL_LAP_METERS = 50.0
    }
}
