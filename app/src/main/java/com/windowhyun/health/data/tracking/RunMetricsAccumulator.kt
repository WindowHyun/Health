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

    /** 아직 DB 에 저장하지 않은 경로 포인트. 저장하면 비워진다. */
    private val pendingPoints = mutableListOf<RunPoint>()

    /** 화면에 지도를 그리기 위한 전체 경로. 저장 여부와 무관하게 계속 쌓인다. */
    private val _routePoints = mutableListOf<RunPoint>()
    val routePoints: List<RunPoint> get() = _routePoints

    private var lastLapDistance = 0.0
    private var lastLapElapsed = 0L

    /**
     * 걸음 센서가 주는 마지막 누적값.
     *
     * 센서는 부팅 이후 누적값을 주므로 직전 값과의 차이만 더한다.
     * 일시정지하면 null 로 비워서, 재개 후 첫 값은 기준점만 새로 잡고
     * 정지한 동안 걸은 수가 더해지지 않게 한다.
     */
    private var lastRawStepCount: Long? = null

    var steps: Long = 0
        private set

    val averagePaceSecPerKm: Double get() = paceSecPerKm(distanceMeters, elapsedSeconds)

    /** 평균 케이던스(분당 걸음 수). */
    val cadenceStepsPerMinute: Int
        get() = if (elapsedSeconds <= 0 || steps <= 0) {
            0
        } else {
            (steps * 60.0 / elapsedSeconds).toInt()
        }

    /** 평균 보폭(m). 걸음 수가 없으면 0. */
    val strideMeters: Double
        get() = if (steps <= 0) 0.0 else distanceMeters / steps

    /**
     * 일시정지 후 재개. 다음 위치는 새 구간의 시작으로 표시되고
     * 정지한 사이에 이동한 거리는 누적하지 않는다.
     */
    fun breakSegment() {
        lastAccepted = null
        jumpStreak = 0
        jumpAt = null
        segmentBreakPending = true
        paceWindow.clear()
        lastRawStepCount = null
    }

    /**
     * 걸음 센서 값 1건. [rawCumulative] 는 부팅 이후 누적값이다.
     *
     * 기기를 재부팅하면 누적값이 0 으로 돌아가는데, 그때는 값이 줄어드는 것으로
     * 보이므로 기준점만 새로 잡고 더하지 않는다.
     */
    fun onStepCount(rawCumulative: Long) {
        val last = lastRawStepCount
        if (last != null && rawCumulative >= last) {
            steps += rawCumulative - last
        }
        lastRawStepCount = rawCumulative
    }

    /** 1초 타이머가 호출한다. 일시정지 중에는 호출하지 않는다. */
    fun advanceTime(seconds: Long = 1) {
        elapsedSeconds += seconds
        updateCurrentPace()
    }

    /**
     * GPS 샘플 1건 처리.
     *
     * @return 이번 호출로 새로 만들어진 Lap 목록 (없으면 빈 목록)
     */
    fun onLocation(sample: LocationSample): List<RunLap> {
        when (judge(sample)) {
            Verdict.REJECT -> return emptyList()
            Verdict.JUMP -> {
                reanchorIfFirstFixWasWrong(sample)
                return emptyList()
            }
            Verdict.ACCEPT -> jumpStreak = 0
        }

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

        val point = RunPoint(
            latitude = sample.latitude,
            longitude = sample.longitude,
            altitude = sample.altitude,
            timestamp = sample.timestamp,
            isSegmentStart = isSegmentStart,
        )
        pendingPoints += point
        _routePoints += point

        paceWindow.addLast(elapsedSeconds to distanceMeters)
        trimPaceWindow()
        updateCurrentPace()

        return checkLaps()
    }

    private enum class Verdict { ACCEPT, REJECT, JUMP }

    /** 사람이 낼 수 없는 속도로 튄 샘플이 이어서 몇 개나 나왔는지, 그리고 그 마지막 위치. */
    private var jumpStreak = 0
    private var jumpAt: LocationSample? = null

    /**
     * 노이즈 걸러내기.
     * - 정확도가 나쁘거나 알 수 없는 샘플, 좌표가 숫자가 아닌 샘플은 버린다
     * - 직전 점과 너무 가까우면(GPS 흔들림) 버린다
     * - 사람이 낼 수 없는 속도로 튀면 버린다([Verdict.JUMP]). 시각이 같거나 거꾸로인 샘플이 크게 튀어도 마찬가지다
     */
    private fun judge(sample: LocationSample): Verdict {
        if (!sample.latitude.isFinite() || !sample.longitude.isFinite()) return Verdict.REJECT
        val accuracy = sample.accuracyMeters ?: return Verdict.REJECT
        if (accuracy > MAX_ACCURACY_METERS) return Verdict.REJECT
        val previous = lastAccepted ?: return Verdict.ACCEPT

        val meters = haversineMeters(
            previous.latitude,
            previous.longitude,
            sample.latitude,
            sample.longitude,
        )
        if (meters < MIN_DISPLACEMENT_METERS) return Verdict.REJECT

        val deltaSeconds = (sample.timestamp - previous.timestamp) / 1000.0
        if (deltaSeconds <= 0) {
            // 시간이 흐르지 않았는데 크게 옮겨 갔다면 속도를 따질 수 없으니 튄 값으로 본다.
            return if (meters > SAME_TIME_JUMP_METERS) Verdict.JUMP else Verdict.ACCEPT
        }
        if (meters / deltaSeconds > MAX_SPEED_MPS) return Verdict.JUMP

        return Verdict.ACCEPT
    }

    /**
     * 첫 위치가 엉뚱한 곳(예: 몇 km 떨어진 값)이었다면 그 뒤의 진짜 위치가 모두 "튄 값"으로 버려져 러닝이 시작되지 않는다.
     * 아직 거의 달리지 않았는데, 서로 가까운 위치가 연달아 튄 값으로 판정되면 그쪽이 진짜라고 보고 기준점을 옮긴다.
     */
    private fun reanchorIfFirstFixWasWrong(sample: LocationSample) {
        val previousJump = jumpAt
        jumpStreak = if (previousJump != null &&
            haversineMeters(previousJump.latitude, previousJump.longitude, sample.latitude, sample.longitude) <= JUMP_CLUSTER_METERS
        ) {
            jumpStreak + 1
        } else {
            1
        }
        jumpAt = sample
        if (jumpStreak >= REANCHOR_AFTER_JUMPS && distanceMeters < REANCHOR_MAX_DISTANCE_METERS) {
            lastAccepted = sample
            // 새 기준점은 새 구간의 시작이다(잘못된 첫 점과 이어 그리지 않는다).
            segmentBreakPending = true
            jumpStreak = 0
            jumpAt = null
        }
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

    /**
     * 자동 Lap 경계를 넘었으면 Lap 을 만든다.
     *
     * 신호가 오래 끊겼다가 큰 점프가 들어오면 경계를 여러 번 넘을 수 있으므로
     * 한 번에 여러 Lap 을 만든다. 한 개만 만들면 그 Lap 하나가 몇 km 짜리가 된다.
     * 점프 구간의 시간은 거리에 비례해 나눈다.
     */
    private fun checkLaps(): List<RunLap> {
        if (autoLapMeters <= 0) return emptyList()
        if (distanceMeters - lastLapDistance < autoLapMeters) return emptyList()

        val pendingDistance = distanceMeters - lastLapDistance
        val pendingDuration = elapsedSeconds - lastLapElapsed
        val created = mutableListOf<RunLap>()

        while (distanceMeters - lastLapDistance >= autoLapMeters) {
            val lapDistance = autoLapMeters.toDouble()
            // 이번 호출에서 늘어난 거리에 비례해 시간을 나눈다.
            val lapDuration = if (pendingDistance > 0) {
                (pendingDuration * (lapDistance / pendingDistance)).toLong()
            } else {
                pendingDuration
            }
            val lap = RunLap(
                lapNumber = _laps.size + 1,
                distanceMeters = lapDistance,
                durationSeconds = lapDuration,
                paceSecPerKm = paceSecPerKm(lapDistance, lapDuration),
            )
            _laps += lap
            created += lap
            lastLapDistance += lapDistance
            lastLapElapsed += lapDuration
        }
        return created
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

    /** 저장에 실패해 돌려받은 경로 포인트를 다시 저장 대기열 맨 앞에 둔다(다음 저장 때 함께 저장한다). */
    fun restorePendingPoints(points: List<RunPoint>) {
        if (points.isNotEmpty()) pendingPoints.addAll(0, points)
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

        /** 시각이 같거나 거꾸로인데 이만큼 넘게 옮겨 간 샘플은 튄 값으로 본다. */
        const val SAME_TIME_JUMP_METERS = 50.0

        /** 튄 값이 이만큼 가까이 연달아 나오면 "같은 곳에 있다"고 본다. */
        const val JUMP_CLUSTER_METERS = 100.0

        /** 튄 값이 이만큼 연달아 나오고 아직 거의 달리지 않았으면 기준점을 옮긴다. */
        const val REANCHOR_AFTER_JUMPS = 3
        const val REANCHOR_MAX_DISTANCE_METERS = 100.0

        /** 현재 페이스를 계산할 최근 구간 길이. */
        const val PACE_WINDOW_SECONDS = 30L

        /** 최고 페이스로 인정할 최소 구간 거리. */
        const val MIN_BEST_PACE_WINDOW_METERS = 100.0

        /** 마지막 자투리 Lap 을 남길 최소 거리. */
        const val MIN_PARTIAL_LAP_METERS = 50.0
    }
}
