package com.windowhyun.health.ui.screenshot

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.data.backup.AutoBackupManager
import com.windowhyun.health.data.backup.BackupRepositoryImpl
import com.windowhyun.health.data.backup.FakeBackupFolder
import com.windowhyun.health.domain.model.ThemeMode
import com.windowhyun.health.ui.exercise.ExerciseDetailScreen
import com.windowhyun.health.ui.exercise.ExerciseDetailViewModel
import com.windowhyun.health.ui.gym.RoutineListScreen
import com.windowhyun.health.ui.gym.RoutineListViewModel
import com.windowhyun.health.ui.gym.RoutineTemplateViewModel
import com.windowhyun.health.ui.running.RunDetailScreen
import com.windowhyun.health.ui.running.RunDetailViewModel
import com.windowhyun.health.ui.history.HistoryMode
import com.windowhyun.health.ui.history.HistoryScreen
import com.windowhyun.health.ui.history.HistoryViewModel
import com.windowhyun.health.ui.history.WorkoutDetailScreen
import com.windowhyun.health.ui.history.WorkoutDetailViewModel
import com.windowhyun.health.ui.navigation.Routes
import com.windowhyun.health.ui.session.WorkoutSummaryScreen
import com.windowhyun.health.ui.session.WorkoutSummaryViewModel
import com.windowhyun.health.ui.settings.BackupViewModel
import com.windowhyun.health.ui.settings.SettingsScreen
import com.windowhyun.health.data.healthconnect.HealthConnectSyncer
import com.windowhyun.health.domain.model.HealthConnectLedger
import com.windowhyun.health.domain.model.UnavailableHealthConnect
import com.windowhyun.health.ui.settings.HealthConnectViewModel
import com.windowhyun.health.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 헬스 · 기록 · 설정 등 나머지 화면을 build/screenshots 에 남긴다. 눈으로 보는 용도다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h900dp-xxhdpi")
class ScreensScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var fixture: ScreenshotFixture
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fixture = ScreenshotFixture(context)
        fixture.seed()
    }

    @After
    fun tearDown() = fixture.close()

    private fun show(dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            HealthTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }

    private fun gymList(name: String, dark: Boolean) {
        val viewModel = RoutineListViewModel(fixture.routines, fixture.workouts)
        val template = RoutineTemplateViewModel(fixture.routines, fixture.exercises)
        show(dark) {
            RoutineListScreen(
                onCreateRoutine = {}, onEditRoutine = {}, onStartWorkout = {},
                viewModel = viewModel, templateViewModel = template,
            )
        }
        waitFor { viewModel.uiState.value.routines.isNotEmpty() }
        compose.saveScreenshot(name)
    }

    private fun history(name: String, dark: Boolean, mode: HistoryMode) {
        val viewModel = HistoryViewModel(fixture.workouts, fixture.runs, fixture.settings)
        viewModel.setMode(mode)
        show(dark) {
            HistoryScreen(onOpenWorkout = {}, onOpenRun = {}, onOpenExercise = {}, viewModel = viewModel)
        }
        waitFor { !viewModel.uiState.value.loading && viewModel.stats.value.stats != null }
        compose.saveScreenshot(name)
    }

    private fun workoutSummary(name: String, dark: Boolean) {
        val viewModel = WorkoutSummaryViewModel(
            fixture.workouts, fixture.settings, SavedStateHandle(mapOf(Routes.ARG_WORKOUT_ID to 1L)), appScope,
        )
        show(dark) { WorkoutSummaryScreen(onClose = {}, viewModel = viewModel) }
        waitFor { viewModel.uiState.value.workout != null && viewModel.uiState.value.personalRecords.isNotEmpty() }
        compose.saveScreenshot(name)
    }

    private fun workoutDetail(name: String, dark: Boolean) {
        val viewModel = WorkoutDetailViewModel(
            fixture.workouts, fixture.settings, SavedStateHandle(mapOf(Routes.ARG_WORKOUT_ID to 1L)), appScope,
        )
        show(dark) { WorkoutDetailScreen(onBack = {}, onOpenExercise = {}, viewModel = viewModel) }
        waitFor { viewModel.uiState.value.workout != null }
        compose.saveScreenshot(name)
    }

    /** 끝난 운동 기록 상세에서 공유 아이콘을 누르면 정리 카드와 공유 · 저장 버튼이 뜬다. */
    private fun workoutDetailCard(name: String) {
        val viewModel = WorkoutDetailViewModel(
            fixture.workouts, fixture.settings, SavedStateHandle(mapOf(Routes.ARG_WORKOUT_ID to 1L)), appScope,
        )
        show(false) { WorkoutDetailScreen(onBack = {}, onOpenExercise = {}, viewModel = viewModel) }
        waitFor { viewModel.uiState.value.workout != null }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("정리 카드 만들기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("공유").assertExists()
        compose.onNodeWithText("HEALTH").assertExists()
        compose.onNodeWithText("닫기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("HEALTH").assertDoesNotExist()
    }

    /** 지난 러닝 상세에서도 같은 방식으로 정리 카드가 열린다. */
    private fun runDetailCard() {
        val viewModel = RunDetailViewModel(
            runRepository = fixture.runs,
            settingsRepository = fixture.settings,
            savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_RUN_ID to 1L)),
            appScope = appScope,
        )
        show(false) { RunDetailScreen(onBack = {}, viewModel = viewModel) }
        waitFor { viewModel.uiState.value.run != null }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("정리 카드 만들기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("공유").assertExists()
        compose.onNodeWithText("러닝").assertExists()
        compose.onNodeWithText("닫기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("공유").assertDoesNotExist()
    }

    private fun exerciseDetail(name: String, dark: Boolean) {
        val viewModel = ExerciseDetailViewModel(
            fixture.workouts, fixture.exercises, fixture.settings,
            SavedStateHandle(mapOf(Routes.ARG_EXERCISE_ID to 3L)),
        )
        show(dark) { ExerciseDetailScreen(onBack = {}, onOpenWorkout = {}, viewModel = viewModel) }
        waitFor { !viewModel.uiState.value.loading }
        compose.saveScreenshot(name)
    }

    private fun settings(name: String, dark: Boolean, autoBackup: Boolean = false) {
        val viewModel = SettingsViewModel(fixture.settings)
        val backupRepository = BackupRepositoryImpl(fixture.db, fixture.db.backupDao(), fixture.settings, Dispatchers.IO)
        if (autoBackup) {
            runBlocking {
                fixture.settings.update {
                    it.copy(
                        autoBackupFolderUri = "content://com.android.externalstorage.documents/tree/primary%3ABackups%2Fhealth",
                        lastAutoBackupAt = System.currentTimeMillis() - 3_600_000,
                    )
                }
            }
        }
        val backup = BackupViewModel(
            context,
            backupRepository,
            fixture.settings,
            AutoBackupManager(backupRepository, fixture.settings, FakeBackupFolder()),
        )
        val healthConnect = HealthConnectViewModel(
            UnavailableHealthConnect,
            fixture.settings,
            HealthConnectSyncer(
                UnavailableHealthConnect,
                object : HealthConnectLedger {
                    override suspend fun read() = emptyMap<String, String>()
                    override suspend fun write(entries: Map<String, String>) = Unit
                },
                fixture.settings,
                fixture.runs,
                fixture.workouts,
                CoroutineScope(Dispatchers.IO),
            ),
        )
        show(dark) {
            SettingsScreen(onBack = {}, viewModel = viewModel, backupViewModel = backup, healthConnectViewModel = healthConnect)
        }
        if (autoBackup) {
            // 데이터 구역은 화면 아래쪽이라 그 자리까지 스크롤한 뒤 찍는다.
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("자동 백업 · 켜짐"))
        }
        compose.saveScreenshot(name)
    }

    @Test fun `gym list light`() = gymList("gym_light", dark = false)
    @Test fun `gym list dark`() = gymList("gym_dark", dark = true)
    @Test fun `history list light`() = history("history_list_light", dark = false, HistoryMode.LIST)
    @Test fun `history calendar light`() = history("history_calendar_light", dark = false, HistoryMode.CALENDAR)
    @Test fun `history exercises light`() = history("history_exercises_light", dark = false, HistoryMode.EXERCISES)
    @Test fun `history stats light`() = history("history_stats_light", dark = false, HistoryMode.STATS)
    @Test fun `history stats dark`() = history("history_stats_dark", dark = true, HistoryMode.STATS)
    @Test fun `history list dark`() = history("history_list_dark", dark = true, HistoryMode.LIST)
    @Test fun `summary light`() = workoutSummary("summary_light", dark = false)
    @Test fun `run detail opens the share card`() = runDetailCard()
    @Test fun `workout detail opens the share card`() = workoutDetailCard("detail_card")
    @Test fun `detail light`() = workoutDetail("detail_light", dark = false)
    @Test fun `exercise light`() = exerciseDetail("exercise_light", dark = false)
    @Test fun `settings light`() = settings("settings_light", dark = false)
    @Test fun `settings auto backup light`() = settings("settings_autobackup_light", dark = false, autoBackup = true)
    @Test fun `settings dark`() = settings("settings_dark", dark = true)
}
