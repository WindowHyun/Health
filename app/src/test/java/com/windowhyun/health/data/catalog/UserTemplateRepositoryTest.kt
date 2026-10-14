package com.windowhyun.health.data.catalog

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.ui.gym.RoutineTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 가져온 템플릿 저장소: 파일에 남고, 쓰다 꺼져도 깨지지 않고, 한도를 지킨다. */
class UserTemplateRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file get() = File(folder.root, "user_templates.json")

    private fun repository() = FileUserTemplateRepository(file, Dispatchers.Unconfined)

    private fun template(id: String, title: String = "제목 $id", routine: String = "루틴 $id") = RoutineTemplates.Template(
        id = id,
        category = RoutineTemplates.Category.SPLIT,
        title = title,
        description = "설명",
        days = listOf(RoutineTemplates.Day(routine, listOf("스쿼트" to 3))),
    )

    @Test
    fun `starts empty when there is no file`() = runBlocking<Unit> {
        assertThat(repository().current()).isEmpty()
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `saved templates are written to the file and read back by a new instance`() = runBlocking<Unit> {
        repository().save(listOf(template("a"), template("b")))

        assertThat(file.exists()).isTrue()
        val reopened = repository().current()
        assertThat(reopened.map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(reopened).isEqualTo(listOf(template("a"), template("b")))
    }

    @Test
    fun `saving an existing id replaces it in place and a new id is appended`() = runBlocking<Unit> {
        val repo = repository()
        repo.save(listOf(template("a"), template("b"), template("c")))

        repo.save(listOf(template("b", title = "새 제목"), template("d")))

        assertThat(repo.current().map { it.id }).containsExactly("a", "b", "c", "d").inOrder()
        assertThat(repo.current()[1].title).isEqualTo("새 제목")
        assertThat(repository().current().map { it.title }).containsExactly("제목 a", "새 제목", "제목 c", "제목 d").inOrder()
    }

    @Test
    fun `delete removes one template and ignores an unknown id`() = runBlocking<Unit> {
        val repo = repository()
        repo.save(listOf(template("a"), template("b")))

        repo.delete("a")
        repo.delete("없는 것")

        assertThat(repo.current().map { it.id }).containsExactly("b")
        assertThat(repository().current().map { it.id }).containsExactly("b")
    }

    @Test
    fun `deleting the last template removes the file`() = runBlocking<Unit> {
        val repo = repository()
        repo.save(listOf(template("a")))

        repo.delete("a")

        assertThat(file.exists()).isFalse()
        assertThat(repository().current()).isEmpty()
    }

    @Test
    fun `replace all swaps the whole list`() = runBlocking<Unit> {
        val repo = repository()
        repo.save(listOf(template("a"), template("b")))

        repo.replaceAll(listOf(template("x")))

        assertThat(repo.current().map { it.id }).containsExactly("x")
        assertThat(repository().current().map { it.id }).containsExactly("x")

        repo.replaceAll(emptyList())
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `the flow shows the stored list and every change`() = runBlocking<Unit> {
        val repo = repository()
        repo.save(listOf(template("a")))

        assertThat(repo.templates.first().map { it.id }).containsExactly("a")
        repo.save(listOf(template("b")))
        assertThat(repo.templates.first().map { it.id }).containsExactly("a", "b")
        repo.delete("a")
        assertThat(repo.templates.first().map { it.id }).containsExactly("b")
    }

    @Test
    fun `no temporary file is left behind`() = runBlocking<Unit> {
        repository().save(listOf(template("a")))

        assertThat(folder.root.list()!!.toList()).containsExactly("user_templates.json")
    }

    @Test
    fun `an unreadable file is kept aside and the list starts empty`() = runBlocking<Unit> {
        file.writeText("{ 이건 json 이 아님", Charsets.UTF_8)

        val repo = repository()
        assertThat(repo.current()).isEmpty()

        val broken = File(file.path + FileUserTemplateRepository.BROKEN_SUFFIX)
        assertThat(broken.readText()).isEqualTo("{ 이건 json 이 아님")

        // 새로 저장하면 정상 파일이 되고, 망가진 파일은 그대로 옆에 남는다.
        repo.save(listOf(template("a")))
        assertThat(repository().current().map { it.id }).containsExactly("a")
        assertThat(broken.exists()).isTrue()
    }

    @Test
    fun `a file that parses but breaks the rules is treated as unreadable`() = runBlocking<Unit> {
        file.writeText("""{"templates":[{"id":"x","category":"NOPE","title":"t","description":"d","days":[]}]}""")

        assertThat(repository().current()).isEmpty()
        assertThat(File(file.path + FileUserTemplateRepository.BROKEN_SUFFIX).exists()).isTrue()
    }

    @Test
    fun `an empty file is treated as unreadable and does not crash`() = runBlocking<Unit> {
        file.writeText("")

        assertThat(repository().current()).isEmpty()
    }

    @Test
    fun `more than the maximum number of templates is refused and nothing changes`() = runBlocking<Unit> {
        val repo = repository()
        repo.save((1..UserTemplateRepository.MAX_TEMPLATES).map { template("t$it") })

        val e = assertThrows(TemplateStorageFullException::class.java) {
            runBlocking { repo.save(listOf(template("one-too-many"))) }
        }

        assertThat(e.message).contains("${UserTemplateRepository.MAX_TEMPLATES}개")
        assertThat(repo.current()).hasSize(UserTemplateRepository.MAX_TEMPLATES)
        assertThat(repository().current()).hasSize(UserTemplateRepository.MAX_TEMPLATES)
        // 같은 id 를 바꾸는 것은 개수가 늘지 않으니 가득 차 있어도 된다.
        repo.save(listOf(template("t1", title = "바뀜")))
        assertThat(repo.current().first().title).isEqualTo("바뀜")
    }

    @Test
    fun `replace all also respects the maximum number and keeps the old list`() = runBlocking<Unit> {
        val repo = repository()
        repo.save(listOf(template("keep")))

        assertThrows(TemplateStorageFullException::class.java) {
            runBlocking { repo.replaceAll((1..UserTemplateRepository.MAX_TEMPLATES + 1).map { template("t$it") }) }
        }

        assertThat(repo.current().map { it.id }).containsExactly("keep")
        assertThat(repository().current().map { it.id }).containsExactly("keep")
    }

    @Test
    fun `a stored size over the limit is refused and the old file stays`() = runBlocking<Unit> {
        val repo = repository()
        repo.save(listOf(template("small")))
        val before = file.readText()
        val hugeDays = (1..14).map { d ->
            RoutineTemplates.Day("하루 $d", (1..30).map { "종목 $it" to 3 }, restSeconds = (1..30).associate { "종목 $it" to 60 })
        }
        val huge = (1..30).map { n ->
            RoutineTemplates.Template("huge$n", RoutineTemplates.Category.SPLIT, "제목", "설".repeat(2000), hugeDays.map { it.copy(routineName = "${it.routineName} $n") })
        }

        val e = assertThrows(TemplateStorageFullException::class.java) { runBlocking { repo.save(huge) } }

        assertThat(e.message).contains("너무 커서")
        assertThat(file.readText()).isEqualTo(before)
        assertThat(repo.current().map { it.id }).containsExactly("small")
    }

    @Test
    fun `saves from many coroutines are all kept`() = runBlocking<Unit> {
        val repo = repository()

        (1..20).map { n -> async(Dispatchers.Default) { repo.save(listOf(template("t$n"))) } }.awaitAll()

        assertThat(repo.current().map { it.id }.toSet()).hasSize(20)
        assertThat(repository().current().map { it.id }.toSet()).hasSize(20)
    }

    @Test
    fun `merge replaces in place and appends the rest`() {
        val merged = UserTemplateRepository.merge(
            existing = listOf(template("a"), template("b")),
            incoming = listOf(template("c"), template("a", title = "새")),
        )

        assertThat(merged.map { it.id }).containsExactly("a", "b", "c").inOrder()
        assertThat(merged.first().title).isEqualTo("새")
    }
}
