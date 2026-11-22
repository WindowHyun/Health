package com.windowhyun.health.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.SetType
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.data.FakeSettingsRepository
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RoutineRepositoryImpl
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.WorkoutSet
import com.windowhyun.health.ui.session.SetRow
import com.windowhyun.health.ui.session.shouldAskNotificationPermission
import com.windowhyun.health.ui.gym.ExercisePickerSheet
import com.windowhyun.health.ui.gym.RoutineEditViewModel
import com.windowhyun.health.ui.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 운동 중 · 루틴 만들 때 느끼는 불편을 줄인 변경의 회귀 테스트.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UsabilityFixesTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: HealthDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun exercise(id: Long, name: String) = Exercise(
        id = id,
        name = name,
        category = ExerciseCategory.BARBELL,
        bodyPart = BodyPart.CHEST,
    )

    // ----- 알림 권한 -----

    @Test
    fun `notification permission is asked once on Android 13 and later`() {
        assertThat(shouldAskNotificationPermission(sdkInt = 33, granted = false, alreadyAsked = false)).isTrue()
        assertThat(shouldAskNotificationPermission(sdkInt = 35, granted = false, alreadyAsked = false)).isTrue()
    }

    @Test
    fun `notification permission is not asked when granted or already asked or not needed`() {
        assertThat(shouldAskNotificationPermission(sdkInt = 33, granted = true, alreadyAsked = false)).isFalse()
        assertThat(shouldAskNotificationPermission(sdkInt = 33, granted = false, alreadyAsked = true)).isFalse()
        assertThat(shouldAskNotificationPermission(sdkInt = 32, granted = false, alreadyAsked = false)).isFalse()
    }

    // ----- 루틴 편집: 휴식시간 기준 -----

    private fun editor(settings: FakeSettingsRepository) = RoutineEditViewModel(
        routineRepository = RoutineRepositoryImpl(db.routineDao()),
        exerciseRepository = ExerciseRepositoryImpl(db.exerciseDao()),
        settingsRepository = settings,
        savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_ROUTINE_ID to 0L)),
    )

    @Test
    fun `routine editor exposes the app default rest so plus and minus start from it`() = runTest(dispatcher) {
        val viewModel = editor(FakeSettingsRepository(AppSettings(defaultRestSeconds = 120)))

        // 설정이 흘러 들어오면 120초가 된다(그 전에는 앱 기본값 60초).
        assertThat(viewModel.defaultRestSeconds.first { it == 120 }).isEqualTo(120)
    }

    // ----- 운동 선택 시트: 여러 종목 담기 -----

    @Test
    fun `picker stays open after picking and marks what was added`() {
        val picked = mutableListOf<Long>()
        var dismissed = 0
        compose.setContent {
            HealthTheme {
                ExercisePickerSheet(
                    exercises = listOf(exercise(1, "벤치프레스"), exercise(2, "스쿼트")),
                    onPick = { picked += it.id },
                    onCreate = { _, _, _, _ -> },
                    onDismiss = { dismissed++ },
                    addedCounts = mapOf(1L to 2),
                )
            }
        }

        compose.onNodeWithText("담음 ×2").assertIsDisplayed()
        compose.onNodeWithText("2개 담음", substring = true).assertIsDisplayed()

        compose.onNodeWithText("스쿼트").performClick()
        compose.onNodeWithText("벤치프레스").performClick()

        assertThat(picked).containsExactly(2L, 1L).inOrder()
        assertThat(dismissed).isEqualTo(0)
    }

    @Test
    fun `picker done button closes it`() {
        var dismissed = 0
        compose.setContent {
            HealthTheme {
                ExercisePickerSheet(
                    exercises = listOf(exercise(1, "벤치프레스")),
                    onPick = { },
                    onCreate = { _, _, _, _ -> },
                    onDismiss = { dismissed++ },
                )
            }
        }

        compose.onNodeWithText("완료").performClick()

        assertThat(dismissed).isEqualTo(1)
    }

    // ----- 세트 번호 칩: 화면 읽기 도구 설명 -----

    @Test
    fun `set number chip describes the set and its type for screen readers`() {
        compose.setContent {
            HealthTheme {
                SetRow(
                    set = WorkoutSet(id = 1, setNumber = 3, weightKg = 60.0, reps = 8, completed = false, setType = SetType.WARMUP),
                    weightUnit = WeightUnit.KG,
                    onValuesChange = { _, _, _ -> },
                    onToggleCompleted = { _, _, _ -> },
                    onRemove = { },
                    onCycleSetType = { },
                )
            }
        }

        compose.onNodeWithContentDescription("3번 세트, 워밍업. 누르면 종류가 바뀝니다").assertIsDisplayed()
    }
}
