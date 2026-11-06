package com.windowhyun.health.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.windowhyun.health.domain.model.LocationSample
import com.windowhyun.health.domain.repository.LocationTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Fused Location Provider 기반 구현. */
@Singleton
class FusedLocationTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: FusedLocationProviderClient,
) : LocationTracker {

    private val _problem = MutableStateFlow<String?>(null)
    override val problem: StateFlow<String?> = _problem.asStateFlow()

    override fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun isLocationAvailable(): Boolean {
        if (!hasLocationPermission()) return false
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission") // 호출 전에 hasLocationPermission() 으로 확인한다.
    override fun locationUpdates(intervalMillis: Long): Flow<LocationSample> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("위치 권한이 없습니다."))
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis)
            .setWaitForAccurateLocation(false)
            .build()

        var received = false
        fun deliver(sample: LocationSample) {
            received = true
            _problem.value = null
            trySend(sample)
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location -> deliver(location.toSample()) }
            }
        }

        // Google 위치 서비스가 조용하거나 실패해도 위치를 받을 수 있도록 기기 GPS 를 함께 쓸 수 있게 준비해 둔다.
        val manager = context.getSystemService(LocationManager::class.java)
        var platformStarted = false
        val platformListener = object : LocationListener {
            override fun onLocationChanged(location: Location) = deliver(location.toSample())

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        fun startPlatform() {
            if (platformStarted || manager == null) return
            platformStarted = true
            runCatching {
                if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMillis, 0f, platformListener, Looper.getMainLooper())
                }
            }.onFailure { Log.w(TAG, "기기 GPS 로 받기를 시작하지 못했습니다", it) }
        }

        // 요청이 거절되거나 실패해도 예외로 나오지 않고 이 결과로만 알려 준다. 예전에는 이를 버려서,
        // 실패해도 앱은 모른 채 위치가 영영 안 왔다.
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { error ->
                Log.w(TAG, "Google 위치 서비스가 위치 요청을 거절했습니다", error)
                _problem.value = describeLocationError(error)
                startPlatform()
            }

        // 실패 응답도 없이 조용한 경우(서비스가 멈춤 등)도 있어서, 한동안 하나도 안 오면 기기 GPS 로도 받는다.
        launch {
            delay(FUSED_SILENCE_MILLIS)
            if (!received) {
                if (_problem.value == null) _problem.value = FALLBACK_NOTICE
                startPlatform()
            }
        }

        awaitClose {
            client.removeLocationUpdates(callback)
            if (platformStarted) runCatching { manager?.removeUpdates(platformListener) }
            _problem.value = null
        }
    }

    private fun Location.toSample() = LocationSample(
        latitude = latitude,
        longitude = longitude,
        altitude = if (hasAltitude()) altitude else 0.0,
        // 정확도를 모르면 0(최상)이 아니라 null 로 둔다. 0 으로 두면
        // 품질을 알 수 없는 좌표가 노이즈 필터를 그대로 통과한다.
        accuracyMeters = accuracy.takeIf { hasAccuracy() },
        timestamp = time.takeIf { it > 0 } ?: System.currentTimeMillis(),
    )

    private companion object {
        const val TAG = "FusedLocationTracker"
    }
}
