package com.windowhyun.health.ui.running

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.domain.model.LocationSample
import com.windowhyun.health.domain.repository.LocationTracker
import com.windowhyun.health.domain.repository.StepCounter
import com.windowhyun.health.service.RunServiceController
import com.windowhyun.health.ui.screenshot.ScreenshotFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 시작 화면이 보이는 동안 GPS 를 미리 켜 두어, 시작한 뒤에 첫 신호를 오래 기다리지 않게 한다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RunSetupGpsWarmUpTest {

    private class FakeLocation(var permission: Boolean = true, var enabled: Boolean = true) : LocationTracker {
        val updates = MutableSharedFlow<LocationSample>()
        var subscriptions = 0
        var listening = false

        override fun isLocationAvailable() = enabled
        override fun hasLocationPermission() = permission
        override fun locationUpdates(intervalMillis: Long): Flow<LocationSample> =
            updates.onStart { subscriptions++; listening = true }.onCompletion { listening = false }
    }

    private val steps = object : StepCounter {
        override fun isAvailable() = false
        override fun hasPermission() = false
        override fun cumulativeSteps() = emptyFlow<Long>()
    }

    private lateinit var fixture: ScreenshotFixture
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fixture = ScreenshotFixture(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        fixture.close()
    }

    private fun viewModel(location: LocationTracker) =
        RunSetupViewModel(location, steps, RunServiceController(context), fixture.tracker, fixture.settings)

    private fun sample(accuracy: Float) = LocationSample(37.5, 127.0, accuracyMeters = accuracy, timestamp = 1_000)

    @Test
    fun `the accuracy shows while the screen is open and clears when it closes`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val location = FakeLocation()
        val viewModel = viewModel(location)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        val watching = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.watchGps() }

        assertThat(location.listening).isTrue()
        assertThat(viewModel.uiState.value.gpsAccuracyMeters).isNull() // 아직 첫 신호 전

        location.updates.emit(sample(48f))
        assertThat(viewModel.uiState.value.gpsAccuracyMeters).isEqualTo(48f)
        location.updates.emit(sample(9f))
        assertThat(viewModel.uiState.value.gpsAccuracyMeters).isEqualTo(9f)

        watching.cancel() // 화면을 벗어남
        watching.join()
        assertThat(location.listening).isFalse()
        assertThat(viewModel.uiState.value.gpsAccuracyMeters).isNull()
    }

    @Test
    fun `nothing is listened to without permission or with gps off`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        listOf(FakeLocation(permission = false), FakeLocation(enabled = false)).forEach { location ->
            val viewModel = viewModel(location)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }

            viewModel.watchGps() // 바로 돌아온다

            assertThat(location.subscriptions).isEqualTo(0)
            assertThat(viewModel.uiState.value.gpsAccuracyMeters).isNull()
        }
    }

    /** 미리 보는 것일 뿐이라, 위치를 받다 실패해도 시작 화면이 깨지지 않는다. */
    @Test
    fun `a failing preview does not break the screen`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val failing = object : LocationTracker {
            override fun isLocationAvailable() = true
            override fun hasLocationPermission() = true
            override fun locationUpdates(intervalMillis: Long): Flow<LocationSample> =
                kotlinx.coroutines.flow.flow { throw SecurityException("거절") }
        }
        val viewModel = viewModel(failing)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }

        viewModel.watchGps() // 예외 없이 끝난다

        assertThat(viewModel.uiState.value.gpsAccuracyMeters).isNull()
    }
}
