package com.windowhyun.health.data.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.windowhyun.health.domain.repository.StepCounter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 기기 걸음 센서 기반 구현. */
@Singleton
class SensorStepCounter @Inject constructor(
    @ApplicationContext private val context: Context,
) : StepCounter {

    private val sensorManager: SensorManager? =
        context.getSystemService(SensorManager::class.java)

    private val stepSensor: Sensor?
        get() = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    override fun isAvailable(): Boolean = stepSensor != null

    override fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    override fun cumulativeSteps(): Flow<Long> {
        val sensor = stepSensor ?: return emptyFlow()
        val manager = sensorManager ?: return emptyFlow()
        if (!hasPermission()) return emptyFlow()

        return callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // values[0] 은 부팅 이후 누적 걸음 수(float 이지만 정수값).
                    event.values.firstOrNull()?.let { trySend(it.toLong()) }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            // 러닝 중에는 1초 정도 간격이면 충분하다. 배터리도 아낀다.
            manager.registerListener(listener, sensor, SENSOR_DELAY_MICROS)
            awaitClose { manager.unregisterListener(listener) }
        }
    }

    private companion object {
        const val SENSOR_DELAY_MICROS = 1_000_000
    }
}
