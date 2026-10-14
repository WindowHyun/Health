package com.windowhyun.health.data.catalog

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.data.local.entity.ExerciseEntity
import com.windowhyun.health.data.seed.DefaultExercises
import com.windowhyun.health.ui.gym.RoutineTemplates
import kotlinx.serialization.json.Json
import org.junit.Test
import java.time.DayOfWeek

/** 쓴 글은 언제나 검증기로 다시 읽으면 같은 내용이어야 한다(저장 · 백업 · 내보내기가 모두 이것에 기댄다). */
class CatalogWriterTest {

    private fun template(
        id: String = "t",
        title: String = "제목",
        description: String = "설명",
        days: List<RoutineTemplates.Day> = listOf(
            RoutineTemplates.Day(routineName = "하루", exercises = listOf("스쿼트" to 3)),
        ),
    ) = RoutineTemplates.Template(id, RoutineTemplates.Category.SPLIT, title, description, days)

    private fun roundTrip(templates: List<RoutineTemplates.Template>, exercises: List<ExerciseEntity> = emptyList()): CatalogReport {
        val report = CatalogReader.read(CatalogWriter.write(templates, exercises))
        assertThat(report.errors).isEmpty()
        return report
    }

    @Test
    fun `every bundled template survives writing and reading`() {
        val report = roundTrip(RoutineTemplates.all)

        assertThat(report.templates).isEqualTo(RoutineTemplates.all)
    }

    @Test
    fun `every bundled exercise survives writing and reading`() {
        val report = roundTrip(emptyList(), DefaultExercises.all)

        assertThat(report.exercises).isEqualTo(DefaultExercises.all)
    }

    @Test
    fun `the output is plain valid json`() {
        val text = CatalogWriter.write(RoutineTemplates.all, DefaultExercises.all)

        Json.parseToJsonElement(text)
        assertThat(text).startsWith("{\n  \"version\": 1")
        assertThat(text).endsWith("}\n")
    }

    @Test
    fun `special characters are escaped and come back unchanged`() {
        val tricky = "따옴표 \" 역슬래시 \\ 줄바꿈\n탭\t 이모지 😀 슬래시 / 중괄호 {} 대괄호 []"
        val report = roundTrip(listOf(template(description = tricky, title = "제목 \"인용\"")))

        assertThat(report.templates.single().description).isEqualTo(tricky)
        assertThat(report.templates.single().title).isEqualTo("제목 \"인용\"")
    }

    @Test
    fun `an exercise is written once per line with only the non default fields`() {
        val plank = ExerciseEntity(name = "플랭크", category = ExerciseCategory.BODYWEIGHT, bodyPart = BodyPart.CORE, trackingType = ExerciseTrackingType.TIME, defaultRestSeconds = 45)
        val bench = ExerciseEntity(name = "벤치프레스", category = ExerciseCategory.BARBELL, bodyPart = BodyPart.CHEST)

        val text = CatalogWriter.write(emptyList(), listOf(plank, bench))

        assertThat(text).contains("""{ "name": "플랭크", "category": "BODYWEIGHT", "bodyPart": "CORE", "trackingType": "TIME", "defaultRestSeconds": 45 }""")
        assertThat(text).contains("""{ "name": "벤치프레스", "category": "BARBELL", "bodyPart": "CHEST" }""")
    }

    @Test
    fun `an empty section is left out`() {
        val onlyTemplates = CatalogWriter.write(listOf(template()))
        val onlyExercises = CatalogWriter.write(emptyList(), listOf(ExerciseEntity(name = "x", category = ExerciseCategory.OTHER, bodyPart = BodyPart.CORE)))

        // 최상위 항목은 두 칸 들여쓴 줄이다(하루 안의 "exercises" 와 구분된다).
        assertThat(onlyTemplates).doesNotContain("\n  \"exercises\"")
        assertThat(onlyTemplates).contains("\n  \"templates\"")
        assertThat(onlyExercises).doesNotContain("\n  \"templates\"")
        assertThat(onlyExercises).contains("\n  \"exercises\"")
    }

    @Test
    fun `an empty result is still json`() {
        Json.parseToJsonElement(CatalogWriter.write(emptyList()))
    }

    @Test
    fun `superset groups get labels in order and weekdays are in week order`() {
        val day = RoutineTemplates.Day(
            routineName = "하루",
            exercises = listOf("가" to 3, "나" to 3, "다" to 2, "라" to 2, "마" to 1),
            supersets = listOf(listOf("가", "나"), listOf("다", "라")),
            restSeconds = mapOf("나" to 60, "라" to 90),
            scheduledDays = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        )

        val text = CatalogWriter.write(listOf(template(days = listOf(day))))

        assertThat(text).contains("""{ "name": "가", "sets": 3, "superset": "A" }""")
        assertThat(text).contains("""{ "name": "나", "sets": 3, "rest": 60, "superset": "A" }""")
        assertThat(text).contains("""{ "name": "다", "sets": 2, "superset": "B" }""")
        assertThat(text).contains("""{ "name": "마", "sets": 1 }""")
        assertThat(text).contains(""""scheduledDays": ["MONDAY", "THURSDAY", "SUNDAY"]""")
        assertThat(roundTrip(listOf(template(days = listOf(day)))).templates.single().days.single()).isEqualTo(day)
    }

    @Test
    fun `a day without weekdays has no scheduledDays key`() {
        assertThat(CatalogWriter.write(listOf(template()))).doesNotContain("scheduledDays")
    }

    @Test
    fun `many templates keep their order`() {
        val list = (1..5).map { template(id = "t$it", days = listOf(RoutineTemplates.Day("하루$it", listOf("스쿼트" to 3)))) }

        assertThat(roundTrip(list).templates.map { it.id }).containsExactly("t1", "t2", "t3", "t4", "t5").inOrder()
    }

    @Test
    fun `labels are letters from A`() {
        assertThat(CatalogWriter.supersetLabel(0)).isEqualTo("A")
        assertThat(CatalogWriter.supersetLabel(1)).isEqualTo("B")
        assertThat(CatalogWriter.supersetLabel(14)).isEqualTo("O")
    }
}
