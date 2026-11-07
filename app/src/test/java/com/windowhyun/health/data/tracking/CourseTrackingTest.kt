package com.windowhyun.health.data.tracking

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.CourseRepositoryImpl
import com.windowhyun.health.data.repository.RunRepositoryImpl
import com.windowhyun.health.domain.course.CourseGeometry
import com.windowhyun.health.domain.model.Course
import com.windowhyun.health.domain.model.CoursePoint
import com.windowhyun.health.domain.model.LocationSample
import com.windowhyun.health.domain.model.RunCue
import com.windowhyun.health.domain.model.RunGoal
import com.windowhyun.health.ui.screenshot.ScreenshotFixture
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 러닝 트래커가 코스를 따라가는 동안 안내 값과 알림(진동 · 음성)을 제대로 내는지. */
@RunWith(RobolectricTestRunner::class)
class CourseTrackingTest {

    private val lonPerMeter = 0.0113 / CourseGeometry.length(listOf(CoursePoint(37.0, 127.0), CoursePoint(37.0, 127.0113)))
    private val course = Course(
        id = 1, name = "동쪽 1km",
        points = listOf(CoursePoint(37.0, 127.0), CoursePoint(37.0, 127.0 + 1000 * lonPerMeter)),
    )

    private var t = 1_000L
    private fun at(meters: Double, offNorthMeters: Double = 0.0, accuracy: Float = 5f) =
        LocationSample(37.0 + offNorthMeters / 111_195.0, 127.0 + meters * lonPerMeter, accuracyMeters = accuracy, timestamp = t.also { t += 3_000 })

    private fun fixture() = ScreenshotFixture(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `guidance follows the runner and cues fire once on leaving returning and finishing`() = runTest {
        val f = fixture()
        val cues = mutableListOf<RunCue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.tracker.cues.collect { cues += it } }
        f.tracker.start(RunGoal(), null, course)
        assertThat(f.tracker.state.value.course).isEqualTo(course)

        f.tracker.onLocation(at(100.0))
        f.tracker.onLocation(at(300.0))
        assertThat(f.tracker.state.value.courseGuidance!!.progressMeters).isWithin(8.0).of(300.0)
        assertThat(f.tracker.state.value.courseGuidance!!.offCourse).isFalse()

        // 코스에서 100m 벗어남: 첫 위치는 흔들림으로 보고, 두 번째에 한 번만 알린다.
        f.tracker.onLocation(at(320.0, offNorthMeters = 100.0))
        assertThat(cues.filterIsInstance<RunCue.OffCourse>()).isEmpty()
        f.tracker.onLocation(at(340.0, offNorthMeters = 100.0))
        f.tracker.onLocation(at(360.0, offNorthMeters = 100.0))
        assertThat(cues.filterIsInstance<RunCue.OffCourse>()).hasSize(1)

        f.tracker.onLocation(at(380.0, offNorthMeters = 5.0))
        assertThat(cues.filterIsInstance<RunCue.BackOnCourse>()).hasSize(1)

        f.tracker.onLocation(at(990.0))
        assertThat(f.tracker.state.value.courseGuidance!!.finished).isTrue()
        f.tracker.onLocation(at(995.0))
        assertThat(cues.filterIsInstance<RunCue.CourseFinished>()).hasSize(1)
    }

    @Test
    fun `inaccurate fixes never count as leaving the course`() = runTest {
        val f = fixture()
        val cues = mutableListOf<RunCue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.tracker.cues.collect { cues += it } }
        f.tracker.start(RunGoal(), null, course)
        f.tracker.onLocation(at(100.0))
        repeat(4) { f.tracker.onLocation(at(120.0, offNorthMeters = 150.0, accuracy = 80f)) }
        assertThat(cues.filterIsInstance<RunCue.OffCourse>()).isEmpty()
    }

    @Test
    fun `a run without a course has no guidance`() = runTest {
        val f = fixture()
        f.tracker.start(RunGoal())
        f.tracker.onLocation(at(100.0))
        assertThat(f.tracker.state.value.course).isNull()
        assertThat(f.tracker.state.value.courseGuidance).isNull()
    }

    @Test
    fun `repository rejects one point courses and skips corrupt rows`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HealthDatabase::class.java)
            .allowMainThreadQueries().build().also { it.openHelper.writableDatabase }
        try {
            val repo = CourseRepositoryImpl(db.courseDao()) { 5L }
            runCatching { repo.save(Course(name = "x", points = listOf(CoursePoint(1.0, 1.0)))) }
                .also { assertThat(it.isFailure).isTrue() }
            assertThat(CourseRepositoryImpl.decode("garbage;1,2")).isNull()
            assertThat(CourseRepositoryImpl.decode("91,0;1,1")).isNull()
            assertThat(CourseRepositoryImpl.decode("1.5,2.5;bad;3,4")!!).hasSize(2)
            val id = repo.save(Course(name = "", points = listOf(CoursePoint(1.0, 1.0), CoursePoint(1.1, 1.1))))
            assertThat(repo.get(id)!!.name).isEqualTo("코스")
        } finally {
            db.close()
        }
    }

    @Test
    fun `starting far from the course says nothing until the runner reaches it`() = runTest {
        val f = fixture()
        val cues = mutableListOf<RunCue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.tracker.cues.collect { cues += it } }
        f.tracker.start(RunGoal(), null, course)
        repeat(4) { f.tracker.onLocation(at(0.0, offNorthMeters = 600.0)) }
        assertThat(f.tracker.state.value.courseGuidance!!.approaching).isTrue()
        assertThat(cues.filter { it is RunCue.OffCourse || it is RunCue.CourseEntered }).isEmpty()
        f.tracker.onLocation(at(10.0, offNorthMeters = 5.0))
        assertThat(cues.filterIsInstance<RunCue.CourseEntered>()).hasSize(1)
        f.tracker.onLocation(at(30.0, offNorthMeters = 5.0))
        assertThat(cues.filterIsInstance<RunCue.CourseEntered>()).hasSize(1)
    }

    @Test
    fun `starting on the course does not announce entering`() = runTest {
        val f = fixture()
        val cues = mutableListOf<RunCue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.tracker.cues.collect { cues += it } }
        f.tracker.start(RunGoal(), null, course)
        f.tracker.onLocation(at(5.0))
        f.tracker.onLocation(at(40.0))
        assertThat(cues.filterIsInstance<RunCue.CourseEntered>()).isEmpty()
    }

    @Test
    fun `a selected course is handed over once and then cleared`() {
        val active = ActiveCourse()
        active.select(course)
        assertThat(active.take()).isEqualTo(course)
        assertThat(active.take()).isNull()
    }
}
