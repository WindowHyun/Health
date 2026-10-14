package com.windowhyun.health.ui.gym

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.designsystem.theme.HealthTheme
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.data.catalog.CatalogIssue
import com.windowhyun.health.data.catalog.ImportPreview
import com.windowhyun.health.data.catalog.IssueSeverity
import com.windowhyun.health.data.local.entity.ExerciseEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 가져오기 화면: 검사 결과 창 · 붙여넣기 창 · 시트의 불러오기 버튼과 가져온 템플릿. */
@RunWith(RobolectricTestRunner::class)
class TemplateImportUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun template(id: String, title: String = "제목 $id") = RoutineTemplates.Template(
        id = id,
        category = RoutineTemplates.Category.SPLIT,
        title = title,
        description = "설명 $id",
        days = listOf(RoutineTemplates.Day("루틴 $id", listOf("스쿼트" to 3))),
    )

    private fun exercise(name: String) = ExerciseEntity(name = name, category = ExerciseCategory.CABLE, bodyPart = BodyPart.BACK)

    private fun error(path: String, message: String) = CatalogIssue(IssueSeverity.ERROR, path, message)
    private fun warning(path: String, message: String) = CatalogIssue(IssueSeverity.WARNING, path, message)

    private fun preview(
        issues: List<CatalogIssue> = emptyList(),
        templates: List<RoutineTemplates.Template> = listOf(template("mine", "내 분할")),
        exercises: List<ExerciseEntity> = emptyList(),
        replaced: List<String> = emptyList(),
    ) = ImportPreview(issues, exercises, templates, replaced)

    // ----- 검사 결과 창 -----

    private fun showImport(
        preview: ImportPreview,
        onConfirm: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onCopy: (String) -> Unit = {},
    ) {
        compose.setContent { HealthTheme { TemplateImportDialog(preview, onConfirm, onDismiss, onCopy) } }
    }

    @Test
    fun `a clean preview can be imported and shows what comes in`() {
        var confirmed = 0
        showImport(preview(exercises = listOf(exercise("내 새 운동"))), onConfirm = { confirmed++ })

        compose.onNodeWithTag("import-title").assertExists()
        compose.onNodeWithText("가져올까요?").assertExists()
        compose.onNodeWithText("템플릿 1개 · 루틴 1개: 내 분할").assertExists()
        compose.onNodeWithText("새 종목 1개: 내 새 운동").assertExists()
        compose.onNodeWithTag("import-confirm").assertIsEnabled().performClick()
        assertThat(confirmed).isEqualTo(1)
        compose.onNodeWithTag("import-issues").assertDoesNotExist()
        compose.onNodeWithTag("import-copy").assertDoesNotExist()
    }

    @Test
    fun `errors block the import and are listed with their place`() {
        var confirmed = 0
        showImport(
            preview(
                issues = listOf(error("templates[0] 'mine' › days[0] '하체' › exercises[1] '레그컬' › name", "'레그컬' 종목이 없습니다. '레그 컬' 를 쓰려던 건가요?")),
                templates = emptyList(),
            ),
            onConfirm = { confirmed++ },
        )

        compose.onNodeWithText("가져올 수 없습니다").assertExists()
        compose.onNodeWithTag("import-confirm").assertIsNotEnabled()
        compose.onNodeWithText("오류").assertExists()
        compose.onNodeWithText("templates[0] 'mine' › days[0] '하체' › exercises[1] '레그컬' › name").assertExists()
        compose.onNodeWithText("'레그컬' 종목이 없습니다. '레그 컬' 를 쓰려던 건가요?").assertExists()
        compose.onNodeWithText("오류 1개 — 파일을 고친 뒤 다시 불러오세요. 아무것도 저장되지 않았습니다.").assertExists()
        compose.onNodeWithTag("import-confirm").performClick()
        assertThat(confirmed).isEqualTo(0)
    }

    @Test
    fun `warnings are shown but do not block the import`() {
        showImport(preview(issues = listOf(warning("templates[0] 'mine'", "이미 가져온 템플릿입니다. 가져오면 새 내용으로 바꿉니다.")), replaced = listOf("옛 제목")))

        compose.onNodeWithText("가져올까요?").assertExists()
        compose.onNodeWithText("알림").assertExists()
        compose.onNodeWithText("이미 가져온 템플릿입니다. 가져오면 새 내용으로 바꿉니다.").assertExists()
        compose.onNodeWithText("새 내용으로 바뀌는 템플릿: 옛 제목").assertExists()
        compose.onNodeWithTag("import-confirm").assertIsEnabled()
    }

    @Test
    fun `an issue without a place shows only its message`() {
        showImport(preview(issues = listOf(error("", "파일이 너무 큽니다. 256KB 까지 가져올 수 있습니다.")), templates = emptyList()))

        compose.onNodeWithText("파일이 너무 큽니다. 256KB 까지 가져올 수 있습니다.").assertExists()
    }

    @Test
    fun `nothing to import keeps the button disabled even without errors`() {
        showImport(preview(issues = listOf(warning("", "새로 가져올 내용이 없습니다.")), templates = emptyList()))

        compose.onNodeWithTag("import-confirm").assertIsNotEnabled()
    }

    @Test
    fun `the problem list can be copied`() {
        var copied: String? = null
        showImport(
            preview(issues = listOf(error("a › b", "문제 하나"), warning("c", "알림 하나")), templates = emptyList()),
            onCopy = { copied = it },
        )

        compose.onNodeWithTag("import-copy").performClick()

        assertThat(copied).isEqualTo("[오류] a › b: 문제 하나\n[알림] c: 알림 하나")
    }

    @Test
    fun `closing the dialog calls dismiss`() {
        var dismissed = 0
        showImport(preview(), onDismiss = { dismissed++ })

        compose.onNodeWithTag("import-dismiss").performClick()

        assertThat(dismissed).isEqualTo(1)
    }

    // ----- 요약 글 -----

    @Test
    fun `the summary lists at most five new exercise names`() {
        val lines = importSummaryLines(preview(templates = emptyList(), exercises = (1..8).map { exercise("종목$it") }))

        assertThat(lines).containsExactly("새 종목 8개: 종목1, 종목2, 종목3, 종목4, 종목5 외 3개")
    }

    @Test
    fun `the summary of an empty preview is empty and errors are counted`() {
        assertThat(importSummaryLines(preview(templates = emptyList()))).isEmpty()
        assertThat(importSummaryLines(preview(templates = emptyList(), issues = listOf(error("", "a"), error("", "b")))))
            .containsExactly("오류 2개 — 파일을 고친 뒤 다시 불러오세요. 아무것도 저장되지 않았습니다.")
    }

    @Test
    fun `problems are written one per line with their kind`() {
        assertThat(issuesAsText(listOf(error("", "첫째"), warning("x", "둘째")))).isEqualTo("[오류] 첫째\n[알림] x: 둘째")
        assertThat(issuesAsText(emptyList())).isEmpty()
    }

    // ----- 붙여넣기 창 -----

    @Test
    fun `the check button needs some text and passes it as typed`() {
        var checked: String? = null
        compose.setContent { HealthTheme { TemplatePasteDialog(onCheck = { checked = it }, onDismiss = {}) } }

        compose.onNodeWithTag("paste-check").assertIsNotEnabled()
        compose.onNodeWithTag("paste-field").performTextInput("""{"templates":[]}""")
        compose.onNodeWithTag("paste-check").assertIsEnabled().performClick()

        assertThat(checked).isEqualTo("""{"templates":[]}""")
    }

    @Test
    fun `blank text cannot be checked`() {
        compose.setContent { HealthTheme { TemplatePasteDialog(onCheck = {}, onDismiss = {}) } }

        compose.onNodeWithTag("paste-field").performTextInput("   ")

        compose.onNodeWithTag("paste-check").assertIsNotEnabled()
    }

    @Test
    fun `cancelling the paste dialog calls dismiss`() {
        var dismissed = 0
        compose.setContent { HealthTheme { TemplatePasteDialog(onCheck = {}, onDismiss = { dismissed++ }) } }

        compose.onNodeWithTag("paste-cancel").performClick()

        assertThat(dismissed).isEqualTo(1)
    }

    // ----- 시트 -----

    private val builtIn = listOf(RoutineTemplates.Category.BEGINNER to listOf(template("builtin", "기본 템플릿")))

    private fun showSheet(
        state: TemplateLibraryState = TemplateLibraryState(),
        onPick: (RoutineTemplates.Template) -> Unit = {},
        onImportFile: () -> Unit = {},
        onPaste: () -> Unit = {},
        onExport: () -> Unit = {},
        onDelete: (RoutineTemplates.Template) -> Unit = {},
    ) {
        compose.setContent {
            HealthTheme {
                TemplateSheetBody(state, builtIn, onPick, onImportFile, onPaste, onExport, onDelete)
            }
        }
    }

    @Test
    fun `the sheet offers file paste and export and lists the bundled templates`() {
        var file = 0
        var paste = 0
        var export = 0
        showSheet(onImportFile = { file++ }, onPaste = { paste++ }, onExport = { export++ })

        compose.onNodeWithTag("template-import-file").performClick()
        compose.onNodeWithTag("template-import-paste").performClick()
        compose.onNodeWithTag("template-export").performClick()

        assertThat(listOf(file, paste, export)).containsExactly(1, 1, 1)
        compose.onNodeWithText("기본 템플릿").assertExists()
        compose.onNodeWithText("가져온 템플릿").assertDoesNotExist()
    }

    @Test
    fun `picking a bundled template reports it`() {
        var picked: RoutineTemplates.Template? = null
        showSheet(onPick = { picked = it })

        compose.onNodeWithText("기본 템플릿").performClick()

        assertThat(picked?.id).isEqualTo("builtin")
    }

    @Test
    fun `imported templates are listed first and can be picked or deleted`() {
        var picked: RoutineTemplates.Template? = null
        var deleted: RoutineTemplates.Template? = null
        val mine = template("mine", "내가 가져온 것")
        showSheet(TemplateLibraryState(userTemplates = listOf(mine)), onPick = { picked = it }, onDelete = { deleted = it })

        compose.onNodeWithText("가져온 템플릿").assertExists()
        compose.onNodeWithText("내가 가져온 것").performClick()
        assertThat(picked).isEqualTo(mine)

        compose.onNodeWithTag("user-template-delete-mine").performClick()
        assertThat(deleted).isEqualTo(mine)
        // 앱에 포함된 템플릿에는 삭제 버튼이 없다.
        compose.onNodeWithTag("user-template-delete-builtin").assertDoesNotExist()
    }

    @Test
    fun `the last result message is shown`() {
        showSheet(TemplateLibraryState(message = "가져왔습니다 · 템플릿 1개(루틴 1개)"))

        compose.onNodeWithTag("template-message").assertExists()
        compose.onNodeWithText("가져왔습니다 · 템플릿 1개(루틴 1개)").assertExists()
    }

    @Test
    fun `no message means no message line`() {
        showSheet()

        compose.onNodeWithTag("template-message").assertDoesNotExist()
    }

    @Test
    fun `buttons are locked while a file is being read`() {
        showSheet(TemplateLibraryState(working = true))

        compose.onNodeWithTag("template-import-file").assertIsNotEnabled()
        compose.onNodeWithTag("template-import-paste").assertIsNotEnabled()
        compose.onNodeWithTag("template-export").assertIsNotEnabled()
    }
}
