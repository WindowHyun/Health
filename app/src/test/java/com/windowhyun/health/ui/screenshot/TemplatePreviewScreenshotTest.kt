package com.windowhyun.health.ui.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.ui.gym.RoutineTemplates
import com.windowhyun.health.ui.gym.TemplatePreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 템플릿 미리보기(슈퍼셋 · 요일이 있는 실제 템플릿)를 그려 build/screenshots 에 남긴다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class TemplatePreviewScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `template preview with supersets`() {
        val template = RoutineTemplates.all.firstOrNull { t -> t.days.any { it.supersets.isNotEmpty() } }
            ?: RoutineTemplates.all.first()
        compose.setContent {
            HealthTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    TemplatePreview(template = template, onBack = {}, onAdd = {})
                }
            }
        }
        compose.waitForIdle()
        compose.saveScreenshot("template_preview")
    }
}
