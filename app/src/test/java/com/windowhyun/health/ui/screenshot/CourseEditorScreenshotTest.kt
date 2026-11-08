package com.windowhyun.health.ui.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.data.tracking.InMemoryCourses
import com.windowhyun.health.domain.model.CoursePoint
import com.windowhyun.health.domain.model.LocationSample
import com.windowhyun.health.domain.model.ThemeMode
import com.windowhyun.health.domain.repository.LocationTracker
import com.windowhyun.health.ui.running.CourseEditorScreen
import com.windowhyun.health.ui.running.CourseEditorViewModel
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 코스 만들기 화면의 배치를 눈으로 본다: 지도는 자기 영역 안에만 그려지고, 아래 패널은 지도와 겹치지 않는다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class CourseEditorScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val noLocation = object : LocationTracker {
        override fun isLocationAvailable() = false
        override fun hasLocationPermission() = false
        override fun locationUpdates(intervalMillis: Long) = emptyFlow<LocationSample>()
    }

    private fun shot(name: String, dark: Boolean) {
        val vm = CourseEditorViewModel(InMemoryCourses(), noLocation, SavedStateHandle())
        listOf(37.5665 to 126.9780, 37.5700 to 126.9800, 37.5720 to 126.9760).forEach { (la, lo) -> vm.addPoint(CoursePoint(la, lo)) }
        compose.setContent {
            HealthTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) { CourseEditorScreen(onClose = {}, viewModel = vm) }
            }
        }
        compose.saveScreenshot(name)
    }

    @Test fun light() = shot("course_editor_light", dark = false)
    @Test fun dark() = shot("course_editor_dark", dark = true)
}
