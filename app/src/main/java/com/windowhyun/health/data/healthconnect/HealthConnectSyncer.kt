package com.windowhyun.health.data.healthconnect

import android.util.Log
import com.windowhyun.health.di.ApplicationScope
import com.windowhyun.health.domain.model.HealthConnectAvailability
import com.windowhyun.health.domain.model.HealthConnectGateway
import com.windowhyun.health.domain.model.HealthConnectLedger
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.domain.repository.RunRepository
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.domain.repository.WorkoutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface HealthSyncResult {
    /** 연동을 켜지 않았다. */
    data object Disabled : HealthSyncResult

    data object Unavailable : HealthSyncResult

    /** 권한이 모자란다. */
    data object NoPermission : HealthSyncResult

    data class Done(val written: Int, val deleted: Int) : HealthSyncResult

    data class Failed(val message: String) : HealthSyncResult
}

/**
 * 끝난 러닝과 헬스 운동을 Health Connect 로 내보내는 일.
 *
 * 쓰는 이름표(clientId)가 기록마다 고정이라 같은 기록을 다시 보내도 중복되지 않는다. 그래도 매번
 * 전부 다시 쓰지 않도록, 보낸 기록과 그때의 내용 값을 장부([HealthConnectLedger])에 적어 두고
 * **새로 생기거나 바뀐 것만** 보낸다. 앱에서 지운 기록은 장부에 남은 이름표로 Health Connect 에서도 지운다.
 *
 * 기록이 바뀌는 때마다(저장 · 수정 · 삭제) 잠깐 기다렸다가 한 번에 맞춘다.
 * 켜져 있지 않거나 권한이 없으면 아무것도 하지 않고, 실패해도 앱 기능에는 영향이 없다.
 */
@Singleton
class HealthConnectSyncer internal constructor(
    private val gateway: HealthConnectGateway,
    private val ledger: HealthConnectLedger,
    private val settingsRepository: SettingsRepository,
    private val runRepository: RunRepository,
    private val workoutRepository: WorkoutRepository,
    private val scope: CoroutineScope,
    /** 한 번에 읽는 기록 수. 이만큼 읽혔다면 목록이 잘렸을 수 있어 지우는 일은 건너뛴다. */
    private val historyLimit: Int,
) {
    @Inject
    constructor(
        gateway: HealthConnectGateway,
        ledger: HealthConnectLedger,
        settingsRepository: SettingsRepository,
        runRepository: RunRepository,
        workoutRepository: WorkoutRepository,
        @ApplicationScope scope: CoroutineScope,
    ) : this(gateway, ledger, settingsRepository, runRepository, workoutRepository, scope, HISTORY_LIMIT)

    private val mutex = Mutex()
    private var started = false

    /**
     * 앱을 켤 때 한 번 부른다. 이후 **보낼 기록이 바뀔 때마다** 알아서 맞춘다.
     *
     * 연동이 꺼져 있으면 기록을 지켜보지 않는다(켜 두지 않은 사람이 기록 저장마다 최근 기록을 통째로 다시 읽지 않도록).
     * 달리는 중 5초마다 바뀌는 기록처럼 **내보낼 내용이 그대로인 변화**는 무시한다.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            settingsRepository.settings
                .map { it.healthConnectEnabled to it.healthConnectImportWeight }
                .distinctUntilChanged()
                .flatMapLatest { (enabled, importWeight) ->
                    if (!enabled) {
                        emptyFlow()
                    } else {
                        combine(
                            runRepository.observeRecentRuns(historyLimit),
                            workoutRepository.observeRecentWorkouts(historyLimit),
                        ) { runs, workouts -> ChangeKey.of(importWeight, runs, workouts, historyLimit) }
                    }
                }
                .distinctUntilChanged()
                .debounce(DEBOUNCE_MILLIS)
                .collect { sync(manual = false) }
        }
    }

    /** 지금 맞춘다. 설정 화면의 "지금 동기화"도 이것을 쓴다. */
    suspend fun sync(): HealthSyncResult = sync(manual = true)

    /**
     * [manual] 이 아니면(기록이 바뀌어 저절로 도는 경우) 바뀐 것이 없을 때 마지막 동기화 시각을 다시 쓰지 않는다.
     * 설정 파일에는 장부도 들어 있어, 쓸 때마다 통째로 다시 쓰이기 때문이다.
     */
    private suspend fun sync(manual: Boolean): HealthSyncResult = mutex.withLock {
        // 읽기 · 권한 확인도 Health Connect 와 통신하다 실패할 수 있다(업데이트 중 등). 앱이 죽지 않게 모두 안에서 받는다.
        try {
            val settings = settingsRepository.current()
            if (!settings.healthConnectEnabled) return@withLock HealthSyncResult.Disabled
            if (gateway.availability() != HealthConnectAvailability.AVAILABLE) return@withLock HealthSyncResult.Unavailable
            val permissions = gateway.permissionState()
            if (!permissions.canWrite) return@withLock HealthSyncResult.NoPermission

            val runs = runRepository.observeRecentRuns(historyLimit).first()
            val workouts = workoutRepository.observeRecentWorkouts(historyLimit).first()
            val sessions = runs.mapNotNull { it.toHealthSession() } + workouts.mapNotNull { it.toHealthSession() }

            var sent = ledger.read()
            val toWrite = sessions.filter { sent[it.clientId] != it.fingerprint }
            // 기록이 너무 많아 목록이 잘렸으면 "없다"가 "지웠다"가 아니다. 그때는 지우지 않는다.
            val listComplete = runs.size < historyLimit && workouts.size < historyLimit
            val currentIds = sessions.mapTo(HashSet()) { it.clientId }
            val toDelete = if (listComplete) sent.keys.filter { it !in currentIds } else emptyList()

            // 한 번에 보내는 양에는 한도가 있어 나눠 보내고, 장부는 한 덩이를 반영한 뒤마다 고친다.
            // 중간에 실패해도 이미 반영한 덩이는 다음에 다시 보내지 않는다.
            for (batch in toWrite.chunked(BATCH_SIZE)) {
                gateway.upsert(batch)
                sent = sent + batch.associate { it.clientId to it.fingerprint }
                ledger.write(sent)
            }
            for (batch in toDelete.chunked(BATCH_SIZE)) {
                gateway.delete(batch)
                sent = sent - batch.toSet()
                ledger.write(sent)
            }

            // 체중은 덤이다. 백그라운드에서 읽기가 막혀 실패해도 이미 끝낸 내보내기를 실패로 만들지 않는다.
            if (settings.healthConnectImportWeight && permissions.canReadWeight) {
                try {
                    importWeight()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "체중을 가져오지 못했습니다", e)
                }
            }

            val changed = toWrite.isNotEmpty() || toDelete.isNotEmpty()
            if (manual || changed || settings.healthConnectLastError != null) {
                settingsRepository.update {
                    it.copy(healthConnectLastSyncAt = System.currentTimeMillis(), healthConnectLastError = null)
                }
            }
            HealthSyncResult.Done(written = toWrite.size, deleted = toDelete.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e::class.simpleName ?: "알 수 없는 오류"
            runCatching { settingsRepository.update { it.copy(healthConnectLastError = message) } }
            HealthSyncResult.Failed(message)
        }
    }

    /** 내보낼 내용이 같으면 같은 값. 이 값이 바뀔 때만 다시 맞춘다. */
    private data class ChangeKey(
        val importWeight: Boolean,
        val fingerprints: Map<String, String>,
        /** 기록이 한도만큼 읽혔는지. 지워도 되는지가 이것으로 갈린다. */
        val truncated: Boolean,
    ) {
        companion object {
            fun of(importWeight: Boolean, runs: List<Run>, workouts: List<Workout>, limit: Int) = ChangeKey(
                importWeight = importWeight,
                fingerprints = (runs.mapNotNull { it.toHealthSession() } + workouts.mapNotNull { it.toHealthSession() })
                    .associate { it.clientId to it.fingerprint },
                truncated = runs.size >= limit || workouts.size >= limit,
            )
        }
    }

    /** 최근 체중을 가져와 설정의 체중에 반영한다(러닝 칼로리 계산에 쓴다). 달라진 것이 없으면 건드리지 않는다. */
    private suspend fun importWeight() {
        val kg = gateway.latestWeightKg(WEIGHT_LOOKBACK_DAYS) ?: return
        val clamped = kg.coerceIn(MIN_WEIGHT_KG, MAX_WEIGHT_KG)
        settingsRepository.update {
            if (kotlin.math.abs(it.bodyWeightKg - clamped) < WEIGHT_EPSILON_KG) it else it.copy(bodyWeightKg = clamped)
        }
    }

    companion object {
        /** 기록이 바뀐 뒤 이만큼 가만히 있어야 맞춘다(운동을 끝내며 여러 번 바뀐다). */
        const val DEBOUNCE_MILLIS = 2_000L
        const val HISTORY_LIMIT = 5_000

        /** 한 번에 보내는 운동 수. 운동 하나가 기록 서너 개라, Health Connect 의 한 번 요청 한도(약 1000개)에 못 미치게 잡는다. */
        const val BATCH_SIZE = 100
        private const val TAG = "HealthConnectSyncer"
        const val WEIGHT_LOOKBACK_DAYS = 30
        const val MIN_WEIGHT_KG = 20.0
        const val MAX_WEIGHT_KG = 250.0
        const val WEIGHT_EPSILON_KG = 0.05
    }
}
