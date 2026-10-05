package com.windowhyun.health.ui.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.domain.model.ProgressMetric
import com.windowhyun.health.domain.model.ThemeMode
import com.windowhyun.health.domain.model.ProgressPoint
import com.windowhyun.health.ui.exercise.ProgressChart
import com.windowhyun.health.ui.exercise.format
import com.windowhyun.health.ui.exercise.formatTick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.Run
import com.windowhyun.health.domain.model.Workout
import com.windowhyun.health.ui.history.CalendarUiState
import com.windowhyun.health.ui.history.HistoryCalendar
import com.windowhyun.health.ui.history.HistoryEntry
import java.time.YearMonth

/**
 * 눈으로 확인하는 용도의 스크린샷. 검증은 다른 테스트가 한다.
 * build/screenshots 에 PNG 를 남긴다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-xxhdpi")
class HistoryScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val points = listOf(
        0L to 80.0, 4L to 82.5, 9L to 85.0, 11L to 84.0, 18L to 87.5,
        25L to 90.0, 39L to 92.5, 42L to 91.0, 46L to 95.0, 53L to 97.5,
    ).map { (day, kg) ->
        ProgressPoint(workoutId = day, date = LocalDate.of(2026, 8, 1).plusDays(day), value = kg * (1 + 5 / 30.0))
    }

    private fun shoot(name: String, dark: Boolean) {
        compose.setContent {
            HealthTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Column(
                    modifier = Modifier
                        .testTag("root")
                        .width(400.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                ) {
                    ProgressChart(
                        points = points,
                        formatValue = { ProgressMetric.ESTIMATED_ONE_RM.format(it, WeightUnit.KG) },
                        formatTick = { ProgressMetric.ESTIMATED_ONE_RM.formatTick(it) },
                        contentDescription = "예상 1RM 그래프",
                    )
                }
            }
        }
        compose.waitForIdle()
        // captureToImage 는 Robolectric 에서 다시 그리기를 기다리다 멈춘다. 뷰를 직접 그린다.
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shootCalendar(name: String, dark: Boolean) {
        val month = YearMonth.of(2026, 9)
        fun gym(day: Int) = HistoryEntry.Gym(
            Workout(
                id = day.toLong(),
                routineId = null,
                routineName = "상체",
                date = month.atDay(day),
                startTime = 0L,
                endTime = 3_600_000L,
                durationSeconds = 3_600,
            ),
        )
        fun run(day: Int) = HistoryEntry.Running(
            Run(id = 100L + day, date = month.atDay(day), startTime = 1L, distanceMeters = 5_000.0, durationSeconds = 1_800),
        )
        val entries = listOf(gym(1), gym(3), run(3), gym(8), run(10), gym(15), gym(17), run(17), run(22), gym(24), gym(25), run(25))
            .groupBy { it.date }
        val state = CalendarUiState(
            month = month,
            selectedDate = month.atDay(25),
            entriesByDate = entries,
            loading = false,
        )
        compose.setContent {
            HealthTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                // 앱에서는 Scaffold 가 글자색을 정해 준다. 같은 조건을 만들려고 Surface 로 감싼다.
                Surface(color = MaterialTheme.colorScheme.surface) {
                    HistoryCalendar(
                        state = state,
                        settings = AppSettings(),
                        onPreviousMonth = {},
                        onNextMonth = {},
                        onThisMonth = {},
                        onSelectDate = {},
                        onOpenWorkout = {},
                        onOpenRun = {},
                    )
                }
            }
        }
        save(name)
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `calendar light`() = shootCalendar("calendar_light", dark = false)

    @Test
    fun `calendar dark`() = shootCalendar("calendar_dark", dark = true)

    @Test
    fun `progress chart light`() = shoot("progress_chart_light", dark = false)

    @Test
    fun `progress chart dark`() = shoot("progress_chart_dark", dark = true)
}
