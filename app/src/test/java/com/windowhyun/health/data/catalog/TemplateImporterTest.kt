package com.windowhyun.health.data.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.repository.ExerciseRepositoryImpl
import com.windowhyun.health.data.seed.ExerciseSeedCallback
import com.windowhyun.health.ui.gym.RoutineTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * 밖에서 받은 글을 검사하고 가져오는 흐름. 기기의 실제 종목(시드 포함)과 맞춰 보므로 Room 으로 확인한다.
 */
@RunWith(RobolectricTestRunner::class)
class TemplateImporterTest {

    private lateinit var db: HealthDatabase
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var stored: InMemoryUserTemplateRepository
    private lateinit var importer: TemplateImporter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .addCallback(ExerciseSeedCallback)
            .allowMainThreadQueries()
            .build().also { it.openHelper.writableDatabase } // 먼저 열어 둔다: 닫을 때 여는 중이면 서로 기다려 멈춘다
        exercises = ExerciseRepositoryImpl(db.exerciseDao())
        stored = InMemoryUserTemplateRepository()
        importer = TemplateImporter(exercises, stored, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = db.close()

    private fun line(name: String, sets: Int = 3) = """{"name":"$name","sets":$sets}"""

    private fun day(name: String, vararg lines: String) = """{"name":"$name","exercises":[${lines.joinToString(",")}]}"""

    private fun tpl(id: String, title: String = "제목 $id", vararg days: String) =
        """{"id":"$id","category":"SPLIT","title":"$title","description":"설명","days":[${days.joinToString(",")}]}"""

    private fun templates(vararg items: String) = """{"templates":[${items.joinToString(",")}]}"""

    private fun analyze(text: String) = runBlocking { importer.analyzeText(text) }

    private fun commit(preview: ImportPreview) = runBlocking { importer.commit(preview) }

    private val simple get() = templates(tpl("mine", "내 분할", day("내 하체", line("스쿼트", 4), line("레그 컬"))))

    // ----- 검사 -----

    @Test
    fun `a valid file can be imported`() {
        val preview = analyze(simple)

        assertThat(preview.issues).isEmpty()
        assertThat(preview.canImport).isTrue()
        assertThat(preview.templates.map { it.id }).containsExactly("mine")
        assertThat(preview.routineCount).isEqualTo(1)
        assertThat(preview.newExercises).isEmpty()
        assertThat(preview.replacedTitles).isEmpty()
    }

    @Test
    fun `analyzing never saves anything`() {
        analyze(templates(tpl("mine", days = arrayOf(day("하루", line("스쿼트"))))))

        assertThat(runBlocking { stored.current() }).isEmpty()
    }

    @Test
    fun `an exercise the device does not have is an error with the nearest name`() {
        val preview = analyze(templates(tpl("mine", days = arrayOf(day("하루", line("레그컬"))))))

        assertThat(preview.canImport).isFalse()
        val error = preview.errors.single().toString()
        assertThat(error).contains("'레그컬' 종목이 없습니다")
        assertThat(error).contains("'레그 컬' 를 쓰려던 건가요")
        assertThat(preview.templates).isEmpty()
    }

    @Test
    fun `an exercise the user deleted counts as missing`() {
        runBlocking { exercises.deleteExercise(exercises.observeExercises().first().first { it.name == "스쿼트" }) }

        val preview = analyze(simple)

        assertThat(preview.canImport).isFalse()
        assertThat(preview.errors.single().toString()).contains("'스쿼트' 종목이 없습니다")
    }

    @Test
    fun `a template id of a bundled template is refused`() {
        val preview = analyze(templates(tpl("ppl", days = arrayOf(day("하루", line("스쿼트"))))))

        assertThat(preview.canImport).isFalse()
        assertThat(preview.errors.single().toString()).contains("앱에 포함된 템플릿")
    }

    @Test
    fun `a routine name that a bundled template uses only warns`() {
        val preview = analyze(templates(tpl("mine", days = arrayOf(day("푸시 (PPL)", line("스쿼트"))))))

        assertThat(preview.canImport).isTrue()
        assertThat(preview.warnings.single().toString()).contains("푸시 (PPL)")
    }

    @Test
    fun `nothing new to import is not importable`() {
        val preview = analyze("""{"exercises":[{"name":"스쿼트","category":"BARBELL","bodyPart":"LEG"}]}""")

        assertThat(preview.errors).isEmpty()
        assertThat(preview.canImport).isFalse()
        assertThat(preview.warnings.map { it.message }).contains("새로 가져올 내용이 없습니다.")
        assertThat(preview.warnings.map { it.message }.any { "이미 있는 종목" in it }).isTrue()
    }

    // ----- 가져오기 -----

    @Test
    fun `commit saves the templates`() {
        val result = commit(analyze(simple))

        assertThat(result.importedTemplates).isEqualTo(1)
        assertThat(result.importedRoutines).isEqualTo(1)
        assertThat(result.addedExercises).isEqualTo(0)
        assertThat(result.replacedTemplates).isEqualTo(0)
        val saved = runBlocking { stored.current() }.single()
        assertThat(saved.id).isEqualTo("mine")
        assertThat(saved.days.single().exercises).containsExactly("스쿼트" to 4, "레그 컬" to 3).inOrder()
    }

    @Test
    fun `new exercises of the file are added as custom exercises`() {
        val before = runBlocking { exercises.observeExercises().first() }.size
        val file = """{"exercises":[{"name":"내 새 운동","category":"CABLE","bodyPart":"BACK","trackingType":"TIME","defaultRestSeconds":45}],
            "templates":[${tpl("mine", days = arrayOf(day("하루", line("내 새 운동"), line("스쿼트"))))}]}"""

        val preview = analyze(file)
        assertThat(preview.issues).isEmpty()
        val result = commit(preview)

        assertThat(result.addedExercises).isEqualTo(1)
        val all = runBlocking { exercises.observeExercises().first() }
        assertThat(all).hasSize(before + 1)
        val added = all.first { it.name == "내 새 운동" }
        assertThat(added.isBuiltIn).isFalse()
        assertThat(added.category).isEqualTo(ExerciseCategory.CABLE)
        assertThat(added.bodyPart).isEqualTo(BodyPart.BACK)
        assertThat(added.trackingType).isEqualTo(ExerciseTrackingType.TIME)
        assertThat(added.defaultRestSeconds).isEqualTo(45)
        assertThat(runBlocking { stored.current() }).hasSize(1)
    }

    @Test
    fun `an exercise the device already has is not added twice`() {
        val before = runBlocking { exercises.observeExercises().first() }.size
        val file = """{"exercises":[{"name":"스쿼트","category":"DUMBBELL","bodyPart":"ARM"}],
            "templates":[${tpl("mine", days = arrayOf(day("하루", line("스쿼트"))))}]}"""

        val preview = analyze(file)
        assertThat(preview.warnings.single().toString()).contains("이미 있는 종목")
        val result = commit(preview)

        assertThat(result.addedExercises).isEqualTo(0)
        val all = runBlocking { exercises.observeExercises().first() }
        assertThat(all).hasSize(before)
        // 있던 종목은 파일의 값으로 바뀌지 않는다.
        assertThat(all.first { it.name == "스쿼트" }.category).isEqualTo(ExerciseCategory.BARBELL)
    }

    @Test
    fun `importing again replaces the template with the same id`() {
        commit(analyze(templates(tpl("mine", "처음 제목", day("내 하체", line("스쿼트"))))))

        val preview = analyze(templates(tpl("mine", "바뀐 제목", day("내 하체", line("스쿼트", 5)))))

        assertThat(preview.canImport).isTrue()
        assertThat(preview.replacedTitles).containsExactly("처음 제목")
        // 바뀔 템플릿의 루틴 이름은 곧 사라지므로 이름이 겹친다는 알림은 없다.
        assertThat(preview.warnings.map { it.toString() }.none { "다른 템플릿" in it }).isTrue()
        assertThat(preview.warnings.map { it.toString() }.any { "바꿉니다" in it }).isTrue()
        val result = commit(preview)

        assertThat(result.replacedTemplates).isEqualTo(1)
        val saved = runBlocking { stored.current() }.single()
        assertThat(saved.title).isEqualTo("바뀐 제목")
        assertThat(saved.days.single().exercises).containsExactly("스쿼트" to 5)
    }

    @Test
    fun `a routine name another imported template uses warns`() {
        commit(analyze(templates(tpl("first", days = arrayOf(day("같은 루틴", line("스쿼트")))))))

        val preview = analyze(templates(tpl("second", days = arrayOf(day("같은 루틴", line("스쿼트"))))))

        assertThat(preview.canImport).isTrue()
        assertThat(preview.warnings.single().toString()).contains("다른 템플릿")
    }

    @Test
    fun `commit refuses a preview with errors`() {
        val preview = analyze(templates(tpl("mine", days = arrayOf(day("하루", line("없는 종목"))))))

        assertThrows(IllegalArgumentException::class.java) { commit(preview) }
        assertThat(runBlocking { stored.current() }).isEmpty()
    }

    @Test
    fun `commit refuses a preview with nothing to import`() {
        val preview = analyze("""{"exercises":[{"name":"스쿼트","category":"BARBELL","bodyPart":"LEG"}]}""")

        assertThrows(IllegalArgumentException::class.java) { commit(preview) }
    }

    @Test
    fun `an imported template can be used even if the file only added exercises`() {
        val preview = analyze("""{"exercises":[{"name":"내 새 운동","category":"CABLE","bodyPart":"BACK"}]}""")

        assertThat(preview.canImport).isTrue()
        val result = commit(preview)

        assertThat(result.addedExercises).isEqualTo(1)
        assertThat(result.importedTemplates).isEqualTo(0)
        assertThat(runBlocking { stored.current() }).isEmpty()
    }

    // ----- 받은 글의 첫 관문 -----

    private fun stream(bytes: ByteArray): InputStream = ByteArrayInputStream(bytes)

    @Test
    fun `a file is read as utf8 with or without a byte order mark`() {
        val bytes = simple.toByteArray(Charsets.UTF_8)
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + bytes

        assertThat(runBlocking { importer.analyzeStream(stream(bytes)) }.canImport).isTrue()
        assertThat(runBlocking { importer.analyzeStream(stream(withBom)) }.canImport).isTrue()
    }

    @Test
    fun `utf16 broken bytes and empty files are refused`() {
        val utf16 = simple.toByteArray(Charsets.UTF_16)
        val broken = byteArrayOf('{'.code.toByte(), 0xC3.toByte(), '}'.code.toByte())

        listOf(utf16, broken, ByteArray(0)).forEach { bytes ->
            val preview = runBlocking { importer.analyzeStream(stream(bytes)) }
            assertThat(preview.canImport).isFalse()
            assertThat(preview.errors).hasSize(1)
            assertThat(preview.templates).isEmpty()
        }
    }

    @Test
    fun `a file over the size limit is refused without being parsed`() {
        val big = ByteArray(ImportLimits.EXTERNAL.maxBytes + 1) { ' '.code.toByte() }

        val preview = runBlocking { importer.analyzeStream(stream(big)) }

        assertThat(preview.canImport).isFalse()
        assertThat(preview.errors.single().message).contains("너무 큽니다")
    }

    @Test
    fun `a file exactly at the size limit is still read`() {
        val padding = ImportLimits.EXTERNAL.maxBytes - simple.toByteArray().size
        val text = simple + " ".repeat(padding)
        assertThat(text.toByteArray().size).isEqualTo(ImportLimits.EXTERNAL.maxBytes)

        assertThat(runBlocking { importer.analyzeStream(stream(text.toByteArray())) }.canImport).isTrue()
    }

    @Test
    fun `an endless stream does not hang`() {
        val endless = object : InputStream() {
            override fun read(): Int = 'a'.code
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                java.util.Arrays.fill(b, off, off + len, 'a'.code.toByte())
                return len
            }
        }

        val preview = runBlocking { importer.analyzeStream(endless) }

        assertThat(preview.errors.single().message).contains("너무 큽니다")
    }

    @Test
    fun `pasted text works the same as a file`() {
        assertThat(analyze("﻿" + simple).canImport).isTrue()
        assertThat(analyze("").canImport).isFalse()
        assertThat(analyze("그냥 글").errors.single().message).contains("JSON 문법")
        assertThat(analyze("가".repeat(ImportLimits.EXTERNAL.maxBytes)).errors.single().message).contains("너무 큽니다")
    }

    @Test
    fun `a stream is closed after reading`() {
        var closed = false
        val input = object : ByteArrayInputStream(simple.toByteArray()) {
            override fun close() {
                closed = true
                super.close()
            }
        }

        runBlocking { importer.analyzeStream(input) }

        assertThat(closed).isTrue()
    }

    // ----- 저장 한도 -----

    private fun storedTemplate(id: String, big: Boolean = false): RoutineTemplates.Template {
        val days = if (big) {
            (1..14).map { d -> RoutineTemplates.Day("$id 하루 $d", (1..30).map { "종목 $it" to 3 }, restSeconds = (1..30).associate { "종목 $it" to 60 }) }
        } else {
            listOf(RoutineTemplates.Day("$id 하루", listOf("스쿼트" to 3)))
        }
        return RoutineTemplates.Template(id, RoutineTemplates.Category.SPLIT, "제목", if (big) "설".repeat(2000) else "설명", days)
    }

    @Test
    fun `a full storage refuses new templates`() {
        runBlocking { stored.save((1..UserTemplateRepository.MAX_TEMPLATES).map { storedTemplate("t$it") }) }

        val preview = analyze(templates(tpl("one-too-many", days = arrayOf(day("새 루틴", line("스쿼트"))))))

        assertThat(preview.canImport).isFalse()
        assertThat(preview.errors.single().message).contains("${UserTemplateRepository.MAX_TEMPLATES}개까지")
    }

    @Test
    fun `replacing a template in a full storage is still allowed`() {
        runBlocking { stored.save((1..UserTemplateRepository.MAX_TEMPLATES).map { storedTemplate("t$it") }) }

        val preview = analyze(templates(tpl("t5", days = arrayOf(day("t5 하루", line("스쿼트"))))))

        assertThat(preview.errors).isEmpty()
        assertThat(preview.canImport).isTrue()
    }

    @Test
    fun `a storage that would grow past the size limit refuses new templates`() {
        runBlocking { stored.save((1..25).map { storedTemplate("big$it", big = true) }) }
        val before = runBlocking { stored.current() }.size

        val preview = analyze(templates(tpl("more", days = arrayOf(day("새 루틴", line("스쿼트"))))))

        assertThat(before).isEqualTo(25)
        assertThat(preview.canImport).isFalse()
        assertThat(preview.errors.single().message).contains("너무 커서")
    }
}
