package com.windowhyun.health.wear

import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.di.ApplicationScope
import com.windowhyun.health.domain.model.RunTrackingState
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.shared.RestSnapshot
import com.windowhyun.health.shared.RunSnapshot
import com.windowhyun.health.shared.WearCodec
import com.windowhyun.health.shared.WearProtocol
import com.windowhyun.health.shared.WorkoutSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 러닝이 어느 정도 바뀌었을 때만 시계로 다시 보내는 규칙.
 *
 * 추적기는 1초마다 상태를 내보내지만 그대로 다 보내면 폰과 시계 배터리가 빨리 닳는다.
 * 시계는 경과 시간을 스스로 세므로, 상태가 바뀌었을 때와 거리가 눈에 띄게 늘었을 때만 보낸다.
 */
internal object RunPublishPolicy {
    /** 같은 상태라도 거리와 페이스를 새로 고치는 최소 간격. */
    const val REFRESH_MILLIS = 3_000L

    /** 이만큼은 늘어야 새로 보낸다(m). */
    const val MIN_DISTANCE_DELTA_METERS = 5.0

    fun shouldSend(last: RunSnapshot?, next: RunSnapshot): Boolean {
        if (last == null) return true
        // 상태가 바뀐 것은 곧바로. 사용자가 누른 버튼이 시계에 바로 반영되어야 한다.
        if (last.status != next.status ||
            last.autoPaused != next.autoPaused ||
            last.signalLost != next.signalLost ||
            last.useMiles != next.useMiles
        ) {
            return true
        }
        if (!next.isActive) return false

        val elapsedSinceSend = next.sentAtMillis - last.sentAtMillis
        // 변화가 없어도 가끔은 보낸다. 시계가 폰과 끊겼는지 알아챌 수 있게.
        if (elapsedSinceSend >= WearProtocol.HEARTBEAT_MILLIS) return true
        return elapsedSinceSend >= REFRESH_MILLIS &&
            abs(next.distanceMeters - last.distanceMeters) >= MIN_DISTANCE_DELTA_METERS
    }
}

/**
 * 폰의 러닝 상태와 휴식 타이머를 시계로 계속 올려 보낸다.
 *
 * 앱이 켜질 때 한 번 [start] 하면 앱이 살아 있는 동안 지켜본다. 시계가 없거나 연결이 안 돼도
 * 보내기 실패를 삼키고 다음 변화 때 다시 시도하므로 폰 쪽 기능에는 영향이 없다.
 */
@Singleton
class WatchStatePublisher internal constructor(
    private val runState: Flow<RunTrackingState>,
    private val useMiles: Flow<Boolean>,
    private val rest: Flow<RestSnapshot>,
    private val transport: WatchTransport,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val heartbeat: Flow<Unit>,
    private val workout: Flow<WorkoutSnapshot> = emptyFlow(),
) {
    @Inject
    constructor(
        runTracker: RunTracker,
        settingsRepository: SettingsRepository,
        link: WatchLink,
        transport: WatchTransport,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        runState = runTracker.state,
        useMiles = settingsRepository.settings.map { it.distanceUnit == DistanceUnit.MILE }.distinctUntilChanged(),
        rest = link.rest,
        transport = transport,
        scope = scope,
        clock = System::currentTimeMillis,
        heartbeat = ticker(HEARTBEAT_CHECK_MILLIS),
        workout = link.workout,
    )

    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch { publishRun() }
        scope.launch { publishRest() }
        scope.launch { publishWorkout() }
    }

    private suspend fun publishRun() {
        var last: RunSnapshot? = null
        // 폰이 가만히 있어도(일시정지 중 등) 주기적으로 깨어나 하트비트를 보낼 수 있게 시계 틱을 섞는다.
        combine(runState, useMiles, heartbeat.onStart { emit(Unit) }) { state, miles, _ ->
            state.toSnapshot(miles, clock())
        }.collect { next ->
            if (RunPublishPolicy.shouldSend(last, next) && send(WearProtocol.PATH_RUN_STATE, WearCodec.encode(next))) {
                last = next
            }
        }
    }

    private suspend fun publishRest() {
        var last: RestSnapshot? = null
        rest.collect { next ->
            // 보낸 시각만 다른 것은 같은 상태다.
            if (last?.copy(sentAtMillis = 0) == next.copy(sentAtMillis = 0)) return@collect
            if (send(WearProtocol.PATH_REST_STATE, WearCodec.encode(next))) last = next
        }
    }

    /**
     * 지금 할 세트를 올린다. 값이 바뀔 때 보내고, 진행 중이면 가만히 있어도 주기적으로 다시 보낸다.
     * 시계는 이 소식이 한동안 끊기면(폰 앱이 죽었다) 지나간 세트를 치우기 때문이다.
     */
    private suspend fun publishWorkout() {
        var last: WorkoutSnapshot? = null
        var lastSentAt = 0L
        combine(workout, heartbeat.onStart { emit(Unit) }) { snapshot, _ -> snapshot }.collect { next ->
            val now = clock()
            val changed = last?.copy(sentAtMillis = 0) != next.copy(sentAtMillis = 0)
            val heartbeatDue = next.active && now - lastSentAt >= WearProtocol.HEARTBEAT_MILLIS
            if (!changed && !heartbeatDue) return@collect
            if (send(WearProtocol.PATH_WORKOUT_STATE, WearCodec.encode(next.copy(sentAtMillis = now)))) {
                last = next
                lastSentAt = now
            }
        }
    }

    /** 보내지 못해도 폰 기능에는 영향이 없다. 실패하면 false 를 돌려줘 다음 변화 때 다시 시도하게 한다. */
    private suspend fun send(path: String, bytes: ByteArray): Boolean = try {
        transport.put(path, bytes)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val HEARTBEAT_CHECK_MILLIS = 5_000L

        fun ticker(periodMillis: Long): Flow<Unit> = flow {
            while (true) {
                delay(periodMillis)
                emit(Unit)
            }
        }
    }
}
