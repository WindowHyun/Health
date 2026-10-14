package com.windowhyun.health.ui.gym

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.data.catalog.CatalogReader
import com.windowhyun.health.data.catalog.ImportContext
import com.windowhyun.health.data.catalog.ImportLimits
import com.windowhyun.health.data.catalog.InMemoryUserTemplateRepository
import com.windowhyun.health.data.catalog.TemplateImporter
import com.windowhyun.health.data.catalog.UserTemplateRepository
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.repository.RoutineRepositoryImpl
import com.windowhyun.health.data.seed.ExerciseSeedCallback
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.model.RoutineItem
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * 가져오기 화면의 흐름: 파일/글 → 검사 결과 → 확인 → 저장, 그리고 내보내기.
 * 파일은 `file:` 주소로 열어 안드로이드의 ContentResolver 를 실제로 거친다.
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TemplateLibraryViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: HealthDatabase
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var routines: RoutineRepositoryImpl
    private lateinit var stored: InMemoryUserTemplateRepository
    private lateinit var viewModel: TemplateLibraryViewModel
    private var latest = TemplateLibraryState()
    private val exportNames = mutableListOf<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .addCallback(ExerciseSeedCallback)
            .allowMainThreadQueries()
            .build().also { it.openHelper.writableDatabase } // 먼저 열어 둔다: 닫을 때 여는 중이면 서로 기다려 멈춘다
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        routines = RoutineRepositoryImpl(db.routineDao())
        stored = InMemoryUserTemplateRepository()
        viewModel = TemplateLibraryViewModel(
            context, TemplateImporter(exercises, stored, Dispatchers.Unconfined), stored, routines, Dispatchers.Unconfined,
        )
        // stateIn 은 구독자가 있어야 값이 갱신된다.
        GlobalScope.launch(Dispatchers.Unconfined) { viewModel.state.collect { latest = it } }
        GlobalScope.launch(Dispatchers.Unconfined) { viewModel.exportReady.collect { exportNames += it } }
        settle()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** 백그라운드 작업(Room · 파일)이 끝나 [condition] 이 될 때까지 메인 루퍼를 돌리며 기다린다. */
    private fun settle(timeoutMs: Long = 8_000, condition: () -> Boolean = { !latest.working }) {
        val end = System.currentTimeMillis() + timeoutMs
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            check(System.currentTimeMillis() < end) { "시간 안에 끝나지 않았습니다: $latest" }
            Thread.sleep(5)
        }
    }

    private val simple = """{"templates":[{"id":"mine","category":"SPLIT","title":"내 분할","description":"설명",
        "days":[{"name":"내 하체","exercises":[{"name":"스쿼트","sets":4},{"name":"레그 컬","sets":3}]}]}]}"""

    private fun fileUri(name: String, text: String): Uri {
        val file = folder.newFile(name)
        file.writeText(text, Charsets.UTF_8)
        return Uri.fromFile(file)
    }

    // ----- 가져오기 -----

    @Test
    fun `starts with nothing`() {
        assertThat(latest.userTemplates).isEmpty()
        assertThat(latest.preview).isNull()
        assertThat(latest.message).isNull()
        assertThat(latest.working).isFalse()
    }

    @Test
    fun `pasted text is checked and shown before anything is saved`() {
        viewModel.importFromText(simple)
        settle { latest.preview != null }

        assertThat(latest.preview!!.canImport).isTrue()
        assertThat(latest.userTemplates).isEmpty()
        assertThat(runBlocking { stored.current() }).isEmpty()
    }

    @Test
    fun `confirming saves the template and tells what came in`() {
        viewModel.importFromText(simple)
        settle { latest.preview != null }

        viewModel.confirmImport()
        settle { latest.preview == null && latest.message != null }

        assertThat(latest.message).isEqualTo("가져왔습니다 · 템플릿 1개(루틴 1개)")
        assertThat(latest.userTemplates.map { it.id }).containsExactly("mine")
        assertThat(runBlocking { stored.current() }.map { it.id }).containsExactly("mine")
    }

    @Test
    fun `the message mentions new exercises and replaced templates`() {
        val file = """{"exercises":[{"name":"내 새 운동","category":"CABLE","bodyPart":"BACK"}],
            "templates":[{"id":"mine","category":"SPLIT","title":"내 분할","description":"설명",
            "days":[{"name":"내 하체","exercises":[{"name":"내 새 운동","sets":4}]}]}]}"""
        viewModel.importFromText(file)
        settle { latest.preview != null }
        viewModel.confirmImport()
        settle { latest.preview == null && latest.message != null }
        assertThat(latest.message).isEqualTo("가져왔습니다 · 템플릿 1개(루틴 1개), 새 종목 1개")

        viewModel.importFromText(file)
        settle { latest.preview != null }
        viewModel.confirmImport()
        settle { latest.preview == null && latest.message?.contains("바꿨습니다") == true }
        assertThat(latest.message).isEqualTo("가져왔습니다 · 템플릿 1개(루틴 1개) · 1개는 새 내용으로 바꿨습니다")
    }

    @Test
    fun `importing only exercises says only that`() {
        viewModel.importFromText("""{"exercises":[{"name":"내 새 운동","category":"CABLE","bodyPart":"BACK"}]}""")
        settle { latest.preview != null }

        viewModel.confirmImport()
        settle { latest.preview == null && latest.message != null }

        assertThat(latest.message).isEqualTo("가져왔습니다 · 새 종목 1개")
    }

    @Test
    fun `a preview with errors cannot be confirmed`() {
        viewModel.importFromText("""{"templates":[{"id":"x"}]}""")
        settle { latest.preview != null }
        assertThat(latest.preview!!.canImport).isFalse()

        viewModel.confirmImport()
        settle()

        assertThat(latest.preview).isNotNull()
        assertThat(runBlocking { stored.current() }).isEmpty()
        assertThat(latest.message).isNull()
    }

    @Test
    fun `confirm without a preview does nothing`() {
        viewModel.confirmImport()
        settle()

        assertThat(latest.message).isNull()
    }

    @Test
    fun `dismissing drops the preview and saves nothing`() {
        viewModel.importFromText(simple)
        settle { latest.preview != null }

        viewModel.dismissPreview()

        assertThat(latest.preview).isNull()
        assertThat(runBlocking { stored.current() }).isEmpty()
    }

    @Test
    fun `a file is read through the content resolver and checked`() {
        viewModel.importFromUri(fileUri("t.json", simple))
        settle { latest.preview != null }

        assertThat(latest.preview!!.canImport).isTrue()
        assertThat(latest.preview!!.templates.single().title).isEqualTo("내 분할")
    }

    @Test
    fun `a file that is not utf8 or too large shows an error preview`() {
        val big = folder.newFile("big.json").also { it.writeBytes(ByteArray(ImportLimits.EXTERNAL.maxBytes + 1) { ' '.code.toByte() }) }

        viewModel.importFromUri(Uri.fromFile(big))
        settle { latest.preview != null }

        assertThat(latest.preview!!.canImport).isFalse()
        assertThat(latest.preview!!.errors.single().message).contains("너무 큽니다")
    }

    @Test
    fun `a file that cannot be opened says so`() {
        viewModel.importFromUri(Uri.fromFile(File(folder.root, "없는파일.json")))
        settle { latest.message != null }

        assertThat(latest.message).isEqualTo("파일을 열 수 없습니다.")
        assertThat(latest.preview).isNull()
    }

    @Test
    fun `a new check replaces the old message`() {
        viewModel.importFromUri(Uri.fromFile(File(folder.root, "없는파일.json")))
        settle { latest.message != null }

        viewModel.importFromText(simple)
        settle { latest.preview != null }

        assertThat(latest.message).isNull()
    }

    @Test
    fun `clearing the message removes it`() {
        viewModel.importFromUri(Uri.fromFile(File(folder.root, "없는파일.json")))
        settle { latest.message != null }

        viewModel.clearMessage()

        assertThat(latest.message).isNull()
    }

    @Test
    fun `a full storage at confirm time shows the reason and keeps the old templates`() {
        viewModel.importFromText(simple)
        settle { latest.preview != null }
        // 검사한 뒤 저장소가 가득 찼다.
        runBlocking {
            stored.save((1..UserTemplateRepository.MAX_TEMPLATES).map {
                RoutineTemplates.Template("t$it", RoutineTemplates.Category.SPLIT, "제목", "설명", listOf(RoutineTemplates.Day("루틴 $it", listOf("스쿼트" to 3))))
            })
        }

        viewModel.confirmImport()
        settle { latest.preview == null && latest.message != null }

        assertThat(latest.message).contains("${UserTemplateRepository.MAX_TEMPLATES}개까지")
        assertThat(runBlocking { stored.current() }.map { it.id }).doesNotContain("mine")
    }

    @Test
    fun `an imported template can be applied like a bundled one`() {
        viewModel.importFromText(simple)
        settle { latest.preview != null }
        viewModel.confirmImport()
        settle { latest.preview == null && latest.message != null }

        val applier = RoutineTemplateViewModel(routines, exercises)
        applier.applyTemplate(latest.userTemplates.single())
        val result = runBlocking { applier.applied.first() }

        assertThat(result.createdRoutineNames).containsExactly("내 하체")
        val routine = runBlocking { routines.observeRoutines().first() }.single()
        assertThat(routine.items.map { it.exercise.name to it.defaultSets }).containsExactly("스쿼트" to 4, "레그 컬" to 3).inOrder()
    }

    /** 파일을 읽는 동안 다른 요청이 들어와도 겹쳐 처리하지 않는다. */
    @Test
    fun `a second request while one is running is ignored`() {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val gated = object : UserTemplateRepository by stored {
            override suspend fun current(): List<RoutineTemplates.Template> {
                gate.await()
                return stored.current()
            }
        }
        val slow = TemplateLibraryViewModel(
            context, TemplateImporter(exercises, gated, Dispatchers.Unconfined), gated, routines, Dispatchers.Unconfined,
        )
        var seen = TemplateLibraryState()
        GlobalScope.launch(Dispatchers.Unconfined) { slow.state.collect { seen = it } }

        slow.importFromText(simple)
        assertThat(seen.working).isTrue()
        slow.importFromText("""{"exercises":[{"name":"두번째","category":"CABLE","bodyPart":"BACK"}]}""")
        gate.complete(Unit)
        settle { seen.preview != null && !seen.working }

        // 첫 요청의 결과만 남는다.
        assertThat(seen.preview!!.templates.map { it.id }).containsExactly("mine")
        assertThat(seen.preview!!.newExercises).isEmpty()
    }

    // ----- 삭제 -----

    @Test
    fun `deleting a template removes it and says which`() {
        viewModel.importFromText(simple)
        settle { latest.preview != null }
        viewModel.confirmImport()
        settle { latest.preview == null && latest.message != null }

        viewModel.deleteUserTemplate("mine")
        settle { latest.userTemplates.isEmpty() }

        assertThat(latest.message).isEqualTo("'내 분할' 템플릿을 지웠습니다. 이미 만든 루틴은 그대로 남습니다.")
    }

    @Test
    fun `deleting an unknown template does nothing`() {
        viewModel.deleteUserTemplate("없음")
        settle()

        assertThat(latest.message).isNull()
    }

    // ----- 내보내기 -----

    private fun seedRoutine(name: String = "내 상체") = runBlocking {
        val all = exercises.observeExercises().first()
        val squat = all.first { it.name == "스쿼트" }
        val curl = all.first { it.name == "레그 컬" }
        routines.saveRoutine(
            Routine(
                name = name,
                items = listOf(
                    RoutineItem(exercise = squat, orderIndex = 0, defaultSets = 4, restSeconds = 120),
                    RoutineItem(exercise = curl, orderIndex = 1, defaultSets = 3),
                ),
            ),
        )
    }

    @Test
    fun `exporting with no routines says so and opens no file picker`() {
        viewModel.prepareExport()
        settle { latest.message != null }

        assertThat(latest.message).isEqualTo("내보낼 루틴이 없습니다.")
        assertThat(exportNames).isEmpty()
    }

    @Test
    fun `exporting offers a file name and then writes a file that reads back`() {
        seedRoutine()

        viewModel.prepareExport()
        settle { exportNames.isNotEmpty() }
        assertThat(exportNames.single()).matches("health-routines-\\d{4}-\\d{2}-\\d{2}\\.json")

        val target = folder.newFile("out.json")
        viewModel.writeExport(Uri.fromFile(target))
        settle { latest.message != null }

        assertThat(latest.message).isEqualTo("루틴 1개를 내보냈습니다")
        val known = runBlocking { exercises.observeExercises().first() }.map { it.name }.toSet()
        val report = CatalogReader.read(
            target.readText(),
            ImportContext(limits = ImportLimits.EXTERNAL, markExercisesBuiltIn = false, checkExerciseReferences = true, knownExerciseNames = known),
        )
        assertThat(report.errors).isEmpty()
        assertThat(report.templates.single().days.single().exercises).containsExactly("스쿼트" to 4, "레그 컬" to 3).inOrder()
    }

    @Test
    fun `an exported file can be imported again by the same view model`() {
        seedRoutine()
        viewModel.prepareExport()
        settle { exportNames.isNotEmpty() }
        val target = folder.newFile("out.json")
        viewModel.writeExport(Uri.fromFile(target))
        settle { latest.message != null }

        viewModel.importFromUri(Uri.fromFile(target))
        settle { latest.preview != null }

        assertThat(latest.preview!!.errors).isEmpty()
        assertThat(latest.preview!!.canImport).isTrue()
        assertThat(latest.preview!!.templates.single().days.single().routineName).isEqualTo("내 상체")
    }

    @Test
    fun `writing without a prepared export does nothing`() {
        val target = folder.newFile("out.json")

        viewModel.writeExport(Uri.fromFile(target))
        settle()

        assertThat(target.length()).isEqualTo(0L)
        assertThat(latest.message).isNull()
    }

    @Test
    fun `cancelling the file picker drops the prepared export`() {
        seedRoutine()
        viewModel.prepareExport()
        settle { exportNames.isNotEmpty() }

        viewModel.cancelExport()
        val target = folder.newFile("out.json")
        viewModel.writeExport(Uri.fromFile(target))
        settle()

        assertThat(target.length()).isEqualTo(0L)
    }

    @Test
    fun `a prepared export is written only once`() {
        seedRoutine()
        viewModel.prepareExport()
        settle { exportNames.isNotEmpty() }
        val first = folder.newFile("a.json")
        val second = folder.newFile("b.json")

        viewModel.writeExport(Uri.fromFile(first))
        settle { latest.message != null }
        viewModel.writeExport(Uri.fromFile(second))
        settle()

        assertThat(first.length()).isGreaterThan(0L)
        assertThat(second.length()).isEqualTo(0L)
    }

    @Test
    fun `the export message names skipped routines and custom exercises`() {
        seedRoutine()
        runBlocking {
            routines.saveRoutine(Routine(name = "비어 있는 루틴"))
            val custom = exercises.addExercise(
                com.windowhyun.health.domain.model.Exercise(0, "내가 만든 운동", ExerciseCategory.CABLE, BodyPart.BACK),
            )
            routines.saveRoutine(
                Routine(
                    name = "직접 만든 것",
                    items = listOf(RoutineItem(exercise = exercises.getExercise(custom)!!, orderIndex = 0, defaultSets = 3)),
                ),
            )
        }

        viewModel.prepareExport()
        settle { exportNames.isNotEmpty() }
        viewModel.writeExport(Uri.fromFile(folder.newFile("out.json")))
        settle { latest.message != null }

        assertThat(latest.message).isEqualTo("루틴 2개를 내보냈습니다 (직접 만든 종목 1개 포함) · 파일 형식에 맞지 않아 뺀 루틴: 비어 있는 루틴")
    }

    @Test
    fun `when only unusable routines exist the message explains`() {
        runBlocking { routines.saveRoutine(Routine(name = "비어 있는 루틴")) }

        viewModel.prepareExport()
        settle { latest.message != null }

        assertThat(latest.message).isEqualTo("내보낼 수 있는 루틴이 없습니다. (비어 있는 루틴 은(는) 파일 형식에 맞지 않습니다)")
        assertThat(exportNames).isEmpty()
    }
}
