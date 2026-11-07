package com.windowhyun.health.ui.running

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.data.tracking.ActiveCourse
import com.windowhyun.health.data.tracking.InMemoryCourses
import com.windowhyun.health.domain.model.Course
import com.windowhyun.health.domain.model.CoursePoint
import com.windowhyun.health.domain.model.LocationSample
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.repository.LocationTracker
import com.windowhyun.health.domain.repository.StepCounter
import com.windowhyun.health.service.RunServiceController
import com.windowhyun.health.ui.screenshot.ScreenshotFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 코스를 고르면 목표 거리가 코스 길이로 맞춰지되, 이미 정한 목표는 건드리지 않는다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CourseGoalSyncTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = ScreenshotFixture(context)
    private val noSteps = object : StepCounter {
        override fun isAvailable() = false
        override fun hasPermission() = false
        override fun cumulativeSteps() = emptyFlow<Long>()
    }
    private val location = object : LocationTracker {
        override fun isLocationAvailable() = true
        override fun hasLocationPermission() = true
        override fun locationUpdates(intervalMillis: Long) = emptyFlow<LocationSample>()
    }

    // 위도 0.0225도 = 약 2.5km
    private val course = Course(id = 1, name = "2.5km", points = listOf(CoursePoint(37.0, 127.0), CoursePoint(37.0225, 127.0)))
    private val active = ActiveCourse()

    @After fun tearDown() { Dispatchers.resetMain(); fixture.close() }

    private fun TestScopeVm(testScheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        RunSetupViewModel(location, noSteps, RunServiceController(context), fixture.tracker, fixture.settings, InMemoryCourses(listOf(course)), active)

    /** 설정(DataStore)이 다른 스레드에서 읽혀 오기까지 기다린다. 조건이 맞으면 바로 돌아온다. */
    private fun kotlinx.coroutines.test.TestScope.waitUntil(condition: () -> Boolean) {
        repeat(300) {
            testScheduler.advanceUntilIdle()
            if (condition()) return
            Thread.sleep(10)
        }
        check(condition()) { "조건이 3초 안에 맞지 않았어요" }
    }

    @Test
    fun `choosing a course in free mode sets the goal distance to its length`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = TestScopeVm(testScheduler)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        testScheduler.advanceUntilIdle()
        vm.selectCourse(1)
        waitUntil { vm.uiState.value.goalType == RunGoalType.DISTANCE }
        assertThat(vm.uiState.value.goalType).isEqualTo(RunGoalType.DISTANCE)
        assertThat(vm.uiState.value.goalDistanceKm).isWithin(0.01).of(2.5)
    }

    @Test
    fun `an already chosen goal is left alone`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = TestScopeVm(testScheduler)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        testScheduler.advanceUntilIdle()
        vm.setGoalType(RunGoalType.DURATION)
        waitUntil { vm.uiState.value.goalType == RunGoalType.DURATION }
        vm.selectCourse(1)
        waitUntil { vm.uiState.value.selectedCourseId == 1L }
        assertThat(vm.uiState.value.goalType).isEqualTo(RunGoalType.DURATION)
        assertThat(vm.uiState.value.selectedCourseId).isEqualTo(1L)
    }

    @Test
    fun `starting hands the chosen course to the service exactly once`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        active.select(course)
        assertThat(active.take()).isEqualTo(course)
        assertThat(active.take()).isNull()
    }
}
