package com.windowhyun.health.data.healthconnect

import com.windowhyun.health.di.ApplicationScope
import com.windowhyun.health.domain.model.HealthConnectAvailability
import com.windowhyun.health.domain.model.HealthConnectGateway
import com.windowhyun.health.domain.model.HealthConnectLedger
import com.windowhyun.health.domain.repository.RunRepository
import com.windowhyun.health.domain.repository.SettingsRepository
import com.windowhyun.health.domain.repository.WorkoutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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

    /** 앱을 켤 때 한 번 부른다. 이후 기록이 바뀔 때마다 알아서 맞춘다. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(
                settingsRepository.settings.map { it.healthConnectEnabled to it.healthConnectImportWeight }
                    .distinctUntilChanged(),
                runRepository.observeRecentRuns(historyLimit),
                workoutRepository.observeRecentWorkouts(historyLimit),
            ) { _, _, _ -> Unit }
                .debounce(DEBOUNCE_MILLIS)
                .collect { sync() }
        }
    }

    /** 지금 맞춘다. 설정 화면의 "지금 동기화"도 이것을 쓴다. */
    suspend fun sync(): HealthSyncResult = mutex.withLock {
        val settings = settingsRepository.current()
        if (!settings.healthConnectEnabled) return HealthSyncResult.Disabled
        if (gateway.availability() != HealthConnectAvailability.AVAILABLE) return HealthSyncResult.Unavailable
        val permissions = gateway.permissionState()
        if (!permissions.canWrite) return HealthSyncResult.NoPermission

        try {
            val runs = runRepository.observeRecentRuns(historyLimit).first()
            val workouts = workoutRepository.observeRecentWorkouts(historyLimit).first()
            val sessions = runs.mapNotNull { it.toHealthSession() } + workouts.mapNotNull { it.toHealthSession() }

            val sent = ledger.read()
            val toWrite = sessions.filter { sent[it.clientId] != it.fingerprint }
            // 기록이 너무 많아 목록이 잘렸으면 "없다"가 "지웠다"가 아니다. 그때는 지우지 않는다.
            val listComplete = runs.size < historyLimit && workouts.size < historyLimit
            val currentIds = sessions.mapTo(HashSet()) { it.clientId }
            val toDelete = if (listComplete) sent.keys.filter { it !in currentIds } else emptyList()

            if (toWrite.isNotEmpty()) gateway.upsert(toWrite)
            if (toDelete.isNotEmpty()) gateway.delete(toDelete)
            // 장부는 Health Connect 에 반영한 뒤에만 고친다. 중간에 실패하면 다음에 다시 시도한다.
            if (toWrite.isNotEmpty() || toDelete.isNotEmpty()) {
                ledger.write((sent - toDelete.toSet()) + toWrite.associate { it.clientId to it.fingerprint })
            }

            if (settings.healthConnectImportWeight && permissions.canReadWeight) importWeight()

            settingsRepository.update {
                it.copy(healthConnectLastSyncAt = System.currentTimeMillis(), healthConnectLastError = null)
            }
            HealthSyncResult.Done(written = toWrite.size, deleted = toDelete.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e::class.simpleName ?: "알 수 없는 오류"
            settingsRepository.update { it.copy(healthConnectLastError = message) }
            HealthSyncResult.Failed(message)
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
        const val WEIGHT_LOOKBACK_DAYS = 30
        const val MIN_WEIGHT_KG = 20.0
        const val MAX_WEIGHT_KG = 250.0
        const val WEIGHT_EPSILON_KG = 0.05
    }
}
