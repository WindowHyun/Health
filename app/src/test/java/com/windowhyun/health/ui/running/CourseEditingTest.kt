package com.windowhyun.health.ui.running

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.data.tracking.ActiveCourse
import com.windowhyun.health.data.tracking.InMemoryCourses
import com.windowhyun.health.domain.model.Course
import com.windowhyun.health.domain.model.CoursePoint
import com.windowhyun.health.domain.model.LocationSample
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.domain.repository.LocationTracker
import com.windowhyun.health.ui.navigation.Routes
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

@OptIn(ExperimentalCoroutinesApi::class)
class CourseEditingTest {

    private val noLocation = object : LocationTracker {
        override fun isLocationAvailable() = false
        override fun hasLocationPermission() = false
        override fun locationUpdates(intervalMillis: Long) = emptyFlow<LocationSample>()
    }
    private val saved = Course(
        id = 7, name = "원래 이름",
        points = listOf(CoursePoint(37.0, 127.0), CoursePoint(37.001, 127.0), CoursePoint(37.002, 127.0)),
    )

    @After fun tearDown() = Dispatchers.resetMain()

    private fun editor(courses: InMemoryCourses, id: Long = 0L) =
        CourseEditorViewModel(courses, noLocation, SavedStateHandle(mapOf(Routes.ARG_COURSE_ID to id)))

    @Test
    fun `removing a point drops only that point and an out of range index is ignored`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = editor(InMemoryCourses())
        listOf(1.0, 2.0, 3.0).forEach { vm.addPoint(CoursePoint(it, it)) }
        vm.removePoint(1)
        assertThat(vm.state.value.points.map { it.latitude }).containsExactly(1.0, 3.0).inOrder()
        vm.removePoint(5)
        vm.removePoint(-1)
        assertThat(vm.state.value.points).hasSize(2)
    }

    @Test
    fun `editing a saved course loads it and saving keeps the same id`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val courses = InMemoryCourses(listOf(saved))
        val vm = editor(courses, id = 7)
        assertThat(vm.state.value.name).isEqualTo("원래 이름")
        assertThat(vm.state.value.points).hasSize(3)
        assertThat(vm.state.value.fit).hasSize(3)
        vm.setName("새 이름")
        vm.removePoint(2)
        var done = false
        vm.save { done = true }
        assertThat(done).isTrue()
        val after = courses.get(7)!!
        assertThat(after.name).isEqualTo("새 이름")
        assertThat(after.points).hasSize(2)
        assertThat(courses.snapshot()).hasSize(1)
    }

    @Test
    fun `editing a missing course shows an error and cannot save`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = editor(InMemoryCourses(), id = 99)
        assertThat(vm.state.value.error).isNotNull()
        assertThat(vm.state.value.canSave).isFalse()
    }
}
