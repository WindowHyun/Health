package com.windowhyun.health.ui

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.data.backup.BackupRepositoryImpl
import com.windowhyun.health.data.datastore.SettingsRepositoryImpl
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.RoutineRepositoryImpl
import com.windowhyun.health.data.repository.RunRepositoryImpl
import com.windowhyun.health.data.repository.WorkoutRepositoryImpl
import com.windowhyun.health.data.sensor.SensorStepCounter
import com.windowhyun.health.data.tracking.RunTracker
import com.windowhyun.health.domain.model.PipSpace
import com.windowhyun.health.domain.model.PipSpacePosition
import com.windowhyun.health.domain.model.RunGoalType
import com.windowhyun.health.ui.home.HomeScreen
import com.windowhyun.health.ui.home.HomeViewModel
import com.windowhyun.health.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** 유튜브 PiP 가 가리는 만큼 앱 화면 끝을 비워 두는 기능. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp-xxhdpi")
class PipSpaceTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var db: HealthDatabase
    private lateinit var settings: SettingsRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).allowMainThreadQueries().build().also { it.openHelper.writableDatabase }
        settings = SettingsRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
                File(context.cacheDir, "pip-${System.nanoTime()}.preferences_pb")
            },
        )
    }

    @After
    fun tearDown() = db.close()

    // ----- 설정 값 -----

    @Test
    fun `it is off by default and sits at the bottom`() {
        val pip = PipSpace()

        assertThat(pip.enabled).isFalse()
        assertThat(pip.position).isEqualTo(PipSpacePosition.BOTTOM)
        assertThat(pip.widthPercent).isEqualTo(PipSpace.DEFAULT_WIDTH_PERCENT)
    }

    @Test
    fun `the size stays in a usable range`() {
        assertThat(PipSpace.clampWidth(5)).isEqualTo(PipSpace.MIN_WIDTH_PERCENT)
        assertThat(PipSpace.clampWidth(65)).isEqualTo(65)
        assertThat(PipSpace.clampWidth(9_999)).isEqualTo(PipSpace.MAX_WIDTH_PERCENT)
        // 미리 정해 둔 크기는 모두 범위 안이고 작은 것부터 순서대로다.
        assertThat(PipSpace.PRESETS.map { it.second }).isInOrder()
        PipSpace.PRESETS.forEach { (_, percent) -> assertThat(PipSpace.clampWidth(percent)).isEqualTo(percent) }
    }

    /** 16:9 창의 가로가 화면의 90% 라면 세로는 그 9/16 이고, 가장자리 여백이 더해진다. */
    @Test
    fun `the height follows the width of the pip window`() {
        val full = PipSpace(widthPercent = 90).heightDp(screenWidthDp = 400f)

        assertThat(full).isWithin(0.01f).of(400f * 0.9f * 9f / 16f + PipSpace.MARGIN_DP)
        // 같은 비율이면 화면이 좁은 폰에서는 더 작게 비운다.
        assertThat(PipSpace(widthPercent = 90).heightDp(320f)).isLessThan(full)
        // 가로가 작은 PiP 는 더 적게 비운다.
        assertThat(PipSpace(widthPercent = 40).heightDp(400f)).isLessThan(full)
    }

    @Test
    fun `a stored value outside the range is brought back in`() = runBlocking<Unit> {
        settings.update { it.copy(pipSpaceEnabled = true, pipSpaceWidthPercent = 5) }

        assertThat(settings.current().pipSpace.widthPercent).isEqualTo(PipSpace.MIN_WIDTH_PERCENT)
        assertThat(settings.current().pipSpace.enabled).isTrue()
    }

    @Test
    fun `settings are stored`() = runBlocking<Unit> {
        assertThat(settings.current().pipSpace).isEqualTo(PipSpace())

        settings.update {
            it.copy(pipSpaceEnabled = true, pipSpacePosition = PipSpacePosition.TOP, pipSpaceWidthPercent = 90)
        }

        assertThat(settings.current().pipSpace).isEqualTo(PipSpace(true, PipSpacePosition.TOP, 90))
    }

    @Test
    fun `the settings screen cannot set a nonsense size`() = runBlocking<Unit> {
        val viewModel = SettingsViewModel(settings)

        viewModel.setPipSpaceWidthPercent(10_000)
        awaitSetting { it.pipSpaceWidthPercent == PipSpace.MAX_WIDTH_PERCENT }
        viewModel.setPipSpaceWidthPercent(-5)
        awaitSetting { it.pipSpaceWidthPercent == PipSpace.MIN_WIDTH_PERCENT }
        viewModel.setPipSpacePosition(PipSpacePosition.TOP)
        awaitSetting { it.pipSpacePosition == PipSpacePosition.TOP }
        viewModel.setPipSpaceEnabled(true)
        awaitSetting { it.pipSpaceEnabled }
    }

    /**
     * 화면 스레드(메인 루퍼)를 돌려 주며 저장이 끝나길 기다린다. ViewModel 은 메인 스레드에서 일을 시작하고,
     * 로봇일렉트릭의 메인 루퍼는 이렇게 돌려 주지 않으면 서 있다.
     */
    private fun awaitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertThat(condition()).isTrue()
    }

    private fun awaitSetting(condition: (com.windowhyun.health.domain.model.AppSettings) -> Boolean) =
        awaitUntil { runBlocking { condition(settings.current()) } }

    /** 화면 크기가 기기마다 달라서 이 설정은 백업에 넣지 않는다(다른 기기에서 복원해도 그 기기의 설정이 남는다). */
    @Test
    fun `a backup does not carry the pip space`() = runBlocking<Unit> {
        val backup = BackupRepositoryImpl(db, db.backupDao(), settings, Dispatchers.IO)
        val runs = RunRepositoryImpl(db.runDao())
        val runId = runs.startRun(RunGoalType.FREE, 0.0)
        runs.finishRun(runId, System.currentTimeMillis() + 5_000, 5_000.0, 1_500, 300.0, 280.0, 300, 4_000)
        settings.update { it.copy(pipSpaceEnabled = true, pipSpaceWidthPercent = 90, weeklyWorkoutGoal = 3) }
        val bytes = ByteArrayOutputStream().also { backup.exportBackup(it) }.toByteArray()

        settings.update { it.copy(pipSpaceEnabled = false, pipSpaceWidthPercent = 50, weeklyWorkoutGoal = 0) }
        backup.restoreBackup(ByteArrayInputStream(bytes))

        val restored = settings.current()
        assertThat(restored.weeklyWorkoutGoal).isEqualTo(3)
        assertThat(restored.pipSpaceEnabled).isFalse()
        assertThat(restored.pipSpaceWidthPercent).isEqualTo(50)
    }

    // ----- 화면 배치 -----

    /** 시스템 막대 높이를 흉내 낸다(로봇일렉트릭은 기본적으로 0 이다). */
    private fun giveSystemBars(topPx: Int, bottomPx: Int) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, topPx, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bottomPx))
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, insets) }
        compose.waitForIdle()
    }

    /**
     * 화면 안쪽에는 막대 높이를 지키는 "탭"(아래)과 "제목"(위)이 있다. 자리가 막대 높이를 대신 받으면
     * 탭은 자리 바로 위에, 제목은 자리 바로 아래에 붙는다(막대 여백을 한 번 더 두지 않는다).
     */
    private fun show(pip: PipSpace) {
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        compose.setContent {
            HealthTheme {
                PipSpaceHost(pip) {
                    Box(Modifier.fillMaxSize().testTag("content")) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .height(40.dp)
                                .testTag("tab"),
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .height(40.dp)
                                .testTag("title"),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    /** 이 테스트 화면의 너비(dp). 설정이 정한 % 로 비울 높이를 계산하는 데 쓴다. */
    private val screenWidthDp = 400f

    private fun expectedHeight(percent: Int) = PipSpace(widthPercent = percent).heightDp(screenWidthDp)

    @Test
    fun `off leaves the screen exactly as it was`() {
        show(PipSpace(enabled = false, widthPercent = 90))

        compose.onNodeWithTag(PIP_SPACE_TAG).assertDoesNotExistCompat()
        assertThat(bounds("content").height.value).isWithin(0.5f).of(800f)
    }

    @Test
    fun `at the bottom the blank space is the last thing and the app ends above it`() {
        show(PipSpace(enabled = true, position = PipSpacePosition.BOTTOM, widthPercent = 65))

        val band = bounds(PIP_SPACE_TAG)
        val content = bounds("content")
        assertThat(band.height.value).isWithin(0.5f).of(expectedHeight(65))
        assertThat(band.bottom.value).isWithin(0.5f).of(800f)
        assertThat(content.top.value).isWithin(0.5f).of(0f)
        assertThat(content.bottom.value).isWithin(0.5f).of(band.top.value)
    }

    @Test
    fun `at the top the blank space comes first and the app starts below it`() {
        show(PipSpace(enabled = true, position = PipSpacePosition.TOP, widthPercent = 65))

        val band = bounds(PIP_SPACE_TAG)
        val content = bounds("content")
        assertThat(band.top.value).isWithin(0.5f).of(0f)
        assertThat(band.height.value).isWithin(0.5f).of(expectedHeight(65))
        assertThat(content.top.value).isWithin(0.5f).of(band.bottom.value)
        assertThat(content.bottom.value).isWithin(0.5f).of(800f)
    }

    /** 사용자의 폰처럼 가로 90% 의 큰 PiP 가 위에 뜨는 경우: 화면 맨 위부터 PiP 아래까지 비워진다. */
    @Test
    fun `a big pip at the top is covered from the very top of the screen`() {
        show(PipSpace(enabled = true, position = PipSpacePosition.TOP, widthPercent = 90))

        val band = bounds(PIP_SPACE_TAG)
        // 400dp 폭 화면: 가로 360dp -> 세로 202.5dp + 여백 12dp.
        assertThat(band.top.value).isWithin(0.5f).of(0f)
        assertThat(band.height.value).isWithin(0.5f).of(214.5f)
    }

    /** 같은 90% 라도 화면이 좁은 폰에서는 PiP 도 작아서 덜 비운다(실제 화면 폭으로 계산한다). */
    @Test
    @Config(qualifiers = "w320dp-h800dp-xxhdpi")
    fun `the blank space follows the real screen width`() {
        show(PipSpace(enabled = true, position = PipSpacePosition.TOP, widthPercent = 90))

        // 320dp 폭: 가로 288dp -> 세로 162dp + 여백 12dp.
        assertThat(bounds(PIP_SPACE_TAG).height.value).isWithin(0.5f).of(174f)
    }

    @Test
    fun `the size follows the setting`() {
        show(PipSpace(enabled = true, widthPercent = 40))
        val small = bounds(PIP_SPACE_TAG).height.value
        assertThat(small).isWithin(0.5f).of(expectedHeight(40))
    }

    /**
     * 빈 자리는 화면 가장자리부터 재서 막대 영역도 안에 들어간다. 그래서 아래 자리를 켜면 하단 탭이 내비게이션 막대
     * 여백을 한 번 더 두지 않고 자리 바로 위에 붙어야 한다.
     */
    @Test
    fun `the bottom space includes the navigation bar and the tab sits right above it`() {
        show(PipSpace(enabled = true, position = PipSpacePosition.BOTTOM, widthPercent = 65))
        giveSystemBars(topPx = 0, bottomPx = 144) // 144px = 48dp (xxhdpi 3x)

        val band = bounds(PIP_SPACE_TAG)
        assertThat(band.height.value).isWithin(0.5f).of(expectedHeight(65))
        assertThat(band.bottom.value).isWithin(0.5f).of(800f)
        assertThat(bounds("tab").bottom.value).isWithin(0.5f).of(band.top.value)
    }

    @Test
    fun `the top space includes the status bar and the title sits right below it`() {
        show(PipSpace(enabled = true, position = PipSpacePosition.TOP, widthPercent = 65))
        giveSystemBars(topPx = 72, bottomPx = 0) // 72px = 24dp

        val band = bounds(PIP_SPACE_TAG)
        assertThat(band.top.value).isWithin(0.5f).of(0f)
        assertThat(band.height.value).isWithin(0.5f).of(expectedHeight(65))
        assertThat(bounds("title").top.value).isWithin(0.5f).of(band.bottom.value)
    }

    /** 반대쪽 막대 여백은 그대로 안쪽 화면이 지킨다. */
    @Test
    fun `the other bar is still respected inside`() {
        show(PipSpace(enabled = true, position = PipSpacePosition.BOTTOM, widthPercent = 65))
        giveSystemBars(topPx = 72, bottomPx = 144)

        assertThat(bounds("title").top.value).isWithin(0.5f).of(24f)
    }

    /** 앱 맨 바깥(Activity 가 쓰는 곳)이 설정을 PiP 자리로 이어 주는지. */
    @Test
    fun `the app root turns the setting into blank space`() {
        val app = com.windowhyun.health.domain.model.AppSettings(
            pipSpaceEnabled = true, pipSpacePosition = PipSpacePosition.BOTTOM, pipSpaceWidthPercent = 50,
        )
        compose.setContent { HealthRoot(app) { Box(Modifier.fillMaxSize().testTag("content")) } }
        compose.waitForIdle()

        assertThat(bounds(PIP_SPACE_TAG).height.value).isWithin(0.5f).of(expectedHeight(50))
        assertThat(bounds("content").bottom.value).isWithin(0.5f).of(bounds(PIP_SPACE_TAG).top.value)
    }

    @Test
    fun `the app root leaves the screen alone when it is off`() {
        compose.setContent {
            HealthRoot(com.windowhyun.health.domain.model.AppSettings()) { Box(Modifier.fillMaxSize().testTag("content")) }
        }
        compose.waitForIdle()

        compose.onNodeWithTag(PIP_SPACE_TAG).assertDoesNotExistCompat()
        assertThat(bounds("content").height.value).isWithin(0.5f).of(800f)
    }

    // ----- 홈 빠른 버튼 -----

    private fun home(): HomeViewModel = HomeViewModel(
        workoutRepository = WorkoutRepositoryImpl(db.workoutDao(), db.routineDao(), db.exerciseDao(), db.personalRecordDao()),
        routineRepository = RoutineRepositoryImpl(db.routineDao()),
        runRepository = RunRepositoryImpl(db.runDao()),
        settingsRepository = settings,
        runTracker = RunTracker(RunRepositoryImpl(db.runDao()), settings, SensorStepCounter(context)),
    )

    private fun showHome(viewModel: HomeViewModel) {
        compose.setContent {
            HealthTheme {
                HomeScreen(
                    onOpenSettings = {}, onStartWorkout = {}, onOpenGym = {}, onOpenRunning = {},
                    onOpenRunResult = {}, onOpenWorkout = {}, onOpenRun = {}, viewModel = viewModel,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the home button turns the space on and off`() {
        val viewModel = home()
        showHome(viewModel)

        compose.onNodeWithContentDescription("PiP 자리 켜기").performClick()
        awaitUntil { viewModel.uiState.value.settings.pipSpaceEnabled }
        compose.waitForIdle()
        assertThat(runBlocking { settings.current().pipSpaceEnabled }).isTrue()

        // 켜진 뒤에는 설명이 바뀌고, 다시 누르면 꺼진다.
        compose.onNodeWithContentDescription("PiP 자리 끄기").performClick()
        awaitUntil { !viewModel.uiState.value.settings.pipSpaceEnabled }
        assertThat(runBlocking { settings.current().pipSpaceEnabled }).isFalse()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("PiP 자리 켜기").assertExistsCompat()
    }

    /** 홈 버튼은 켜고 끌 뿐, 설정에서 맞춰 둔 크기와 위치는 그대로다. */
    @Test
    fun `the home button keeps the size and place you set`() {
        runBlocking { settings.update { it.copy(pipSpacePosition = PipSpacePosition.TOP, pipSpaceWidthPercent = 85) } }
        val viewModel = home()
        showHome(viewModel)

        compose.onNodeWithContentDescription("PiP 자리 켜기").performClick()
        awaitUntil { viewModel.uiState.value.settings.pipSpaceEnabled }

        assertThat(runBlocking { settings.current().pipSpace }).isEqualTo(PipSpace(true, PipSpacePosition.TOP, 85))
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() = assertDoesNotExist()

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() = assertExists()
