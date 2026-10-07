package com.windowhyun.health.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.data.healthconnect.HealthConnectPermissions
import com.windowhyun.health.data.healthconnect.HealthConnectSyncer
import com.windowhyun.health.data.healthconnect.HealthSyncResult
import com.windowhyun.health.domain.model.HealthConnectAvailability
import com.windowhyun.health.domain.model.HealthConnectGateway
import com.windowhyun.health.domain.model.HealthPermissionState
import com.windowhyun.health.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HealthConnectUiState(
    val availability: HealthConnectAvailability = HealthConnectAvailability.UNAVAILABLE,
    val permissions: HealthPermissionState = HealthPermissionState(),
    val enabled: Boolean = false,
    val importWeight: Boolean = false,
    val lastSyncAt: Long = 0,
    val lastError: String? = null,
    val syncing: Boolean = false,
    /** 허용 화면에서 돌아왔는데 필요한 권한이 빠졌다. */
    val permissionDenied: Boolean = false,
) {
    /** 내보내기가 실제로 돌고 있다: 켜 두었고 권한도 있다. */
    val exporting: Boolean get() = enabled && permissions.canWrite

    /** 켜 두었지만 권한이 사라졌다(Health Connect 에서 껐다). */
    val needsPermission: Boolean get() = enabled && !permissions.canWrite

    val weightImporting: Boolean get() = exporting && importWeight && permissions.canReadWeight
}

/** 설정 화면의 Health Connect 부분. 허용 화면은 시스템이 띄우고, 결과만 여기로 돌아온다. */
@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val gateway: HealthConnectGateway,
    private val settingsRepository: SettingsRepository,
    private val syncer: HealthConnectSyncer,
) : ViewModel() {

    private data class Local(
        val availability: HealthConnectAvailability = HealthConnectAvailability.UNAVAILABLE,
        val permissions: HealthPermissionState = HealthPermissionState(),
        val syncing: Boolean = false,
        val permissionDenied: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<HealthConnectUiState> = combine(local, settingsRepository.settings) { local, settings ->
        HealthConnectUiState(
            availability = local.availability,
            permissions = local.permissions,
            enabled = settings.healthConnectEnabled,
            importWeight = settings.healthConnectImportWeight,
            lastSyncAt = settings.healthConnectLastSyncAt,
            lastError = settings.healthConnectLastError,
            syncing = local.syncing,
            permissionDenied = local.permissionDenied,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthConnectUiState())

    init {
        refresh()
    }

    /** 화면에 들어올 때마다 Health Connect 상태와 허용한 권한을 다시 본다(그사이 설정에서 바꿨을 수 있다). */
    fun refresh() {
        viewModelScope.launch {
            val availability = gateway.availability()
            val permissions = if (availability == HealthConnectAvailability.AVAILABLE) readPermissions() else HealthPermissionState()
            local.update { it.copy(availability = availability, permissions = permissions) }
        }
    }

    private suspend fun readPermissions(): HealthPermissionState = try {
        gateway.permissionState()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        HealthPermissionState()
    }

    /** 요청할 권한 전체. 허용 화면을 띄울 때 쓴다. */
    val permissionsToRequest: Set<String> get() = HealthConnectPermissions.all

    /** 허용 화면에서 돌아왔다. 내보내기에 필요한 권한이 모두 있으면 켠다. */
    fun onPermissionResult(granted: Set<String>) {
        val permissions = HealthConnectPermissions.stateFrom(granted)
        viewModelScope.launch {
            // 이전에 이미 허용한 것이 있을 수 있어서 결과만 믿지 않고 실제 상태를 다시 읽는다.
            val actual = readPermissions().let {
                HealthPermissionState(
                    canWrite = it.canWrite || permissions.canWrite,
                    canReadWeight = it.canReadWeight || permissions.canReadWeight,
                    canReadHeartRate = it.canReadHeartRate || permissions.canReadHeartRate,
                )
            }
            local.update { it.copy(permissions = actual, permissionDenied = !actual.canWrite) }
            if (actual.canWrite) {
                settingsRepository.update { it.copy(healthConnectEnabled = true, healthConnectLastError = null) }
                syncNow()
            } else {
                settingsRepository.update { it.copy(healthConnectEnabled = false) }
            }
        }
    }

    fun setExportEnabled(enabled: Boolean) {
        viewModelScope.launch {
            local.update { it.copy(permissionDenied = false) }
            // 끄면 보낸 기록은 Health Connect 에 그대로 둔다(지우는 것은 Health Connect 앱에서).
            settingsRepository.update { it.copy(healthConnectEnabled = enabled) }
        }
    }

    fun setImportWeight(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(healthConnectImportWeight = enabled) }
            if (enabled) syncNow()
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            local.update { it.copy(syncing = true) }
            try {
                val result = syncer.sync()
                // 실패 이유는 설정에 남아 화면이 보여 준다. 여기서는 권한 부족만 따로 알린다.
                if (result is HealthSyncResult.NoPermission) {
                    local.update { it.copy(permissionDenied = true) }
                }
            } finally {
                local.update { it.copy(syncing = false) }
            }
        }
    }
}
