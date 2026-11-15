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

/** 코스를 골라도 목표는 건드리지 않는다(자유 러닝은 자유 러닝으로, 정해 둔 목표는 그대로). */
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

    /**
     * 코스를 골라도 목표는 그대로다. 예전에는 자유 러닝을 "목표 거리 = 코스 길이(0.5km 단위)" 로 바꿨는데,
     * 목표 거리에 닿으면 러닝이 자동으로 끝나는 규칙 때문에 코스 끝 전에 러닝이 끝났다.
     */
    @Test
    fun `choosing a course keeps free mode so the run is not ended early`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = TestScopeVm(testScheduler)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        testScheduler.advanceUntilIdle()
        vm.selectCourse(1)
        waitUntil { vm.uiState.value.selectedCourseId == 1L }
        assertThat(vm.uiState.value.goalType).isEqualTo(RunGoalType.FREE)
        assertThat(vm.uiState.value.toGoal().type).isEqualTo(RunGoalType.FREE)
    }

    /** 코스를 해제해도 남는 목표가 없다(예전에는 처음 고른 코스의 거리가 목표로 남았다). */
    @Test
    fun `clearing the course leaves no goal behind`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = TestScopeVm(testScheduler)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        testScheduler.advanceUntilIdle()
        vm.selectCourse(1)
        waitUntil { vm.uiState.value.selectedCourseId == 1L }
        vm.selectCourse(null)
        waitUntil { vm.uiState.value.selectedCourseId == null }
        assertThat(vm.uiState.value.goalType).isEqualTo(RunGoalType.FREE)
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
