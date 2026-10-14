package com.windowhyun.health.ui.gym

import com.windowhyun.health.core.util.BundledData
import com.windowhyun.health.core.util.CatalogJson
import com.windowhyun.health.data.seed.ExerciseCatalog
import kotlinx.serialization.Serializable
import java.time.DayOfWeek

/**
 * 루틴 템플릿 파일(`resources/health/routine_templates.json`)의 형식과 읽기.
 *
 * ```json
 * { "templates": [ {
 *     "id": "my_split", "category": "SPLIT", "title": "...", "description": "...",
 *     "days": [ {
 *         "name": "하체 (화)", "scheduledDays": ["TUESDAY"],
 *         "exercises": [
 *             { "name": "스쿼트", "sets": 4, "rest": 120 },
 *             { "name": "케이블 푸시다운", "sets": 3, "rest": 60, "superset": "A" },
 *             { "name": "바벨 컬", "sets": 3, "rest": 75, "superset": "A" }
 *         ]
 *     } ]
 * } ] }
 * ```
 *
 * 종목은 한 줄에 한 번만 적는다. 휴식(`rest`, 초)을 빼면 그 종목의 기본 휴식을 쓴다.
 * `superset` 은 같은 라벨끼리 묶이며, 묶음 종목은 **이어서** 나와야 한다.
 * `category` 는 [RoutineTemplates.Category] 의 이름, `scheduledDays` 는 `MONDAY` … `SUNDAY` 이다.
 * 종목 이름은 [ExerciseCatalog] 의 종목 이름과 정확히 같아야 한다(적용할 때 이름으로 찾는다).
 */
object RoutineTemplateCatalog {

    const val RESOURCE_PATH = "health/routine_templates.json"

    /** 편집 화면의 세트 수 한도. */
    private val SETS_RANGE = 1..20

    @Serializable
    internal data class TemplateFile(val templates: List<TemplateItem>)

    @Serializable
    internal data class TemplateItem(
        val id: String,
        val category: String,
        val title: String,
        val description: String,
        val days: List<DayItem>,
    )

    @Serializable
    internal data class DayItem(
        val name: String,
        val scheduledDays: List<String> = emptyList(),
        val exercises: List<ExerciseItem>,
    )

    @Serializable
    internal data class ExerciseItem(
        val name: String,
        val sets: Int,
        val rest: Int? = null,
        val superset: String? = null,
    )

    /** 앱에 포함된 템플릿. */
    fun loadBundled(): List<RoutineTemplates.Template> = parse(BundledData.readText(RESOURCE_PATH))

    /** 파일 내용을 템플릿 목록으로 바꾼다. 형식이 틀리면 [com.windowhyun.health.core.util.CatalogFormatException]. */
    fun parse(text: String, source: String = RESOURCE_PATH): List<RoutineTemplates.Template> {
        val file = CatalogJson.decode<TemplateFile>(text, source)
        val ids = HashSet<String>()
        val routineNames = HashSet<String>()
        return file.templates.mapIndexed { index, item ->
            val where = "$source templates[$index] '${item.id}'"
            if (item.id.isBlank()) CatalogJson.fail(where, "id 가 비어 있습니다")
            if (!ids.add(item.id)) CatalogJson.fail(where, "같은 id 의 템플릿이 두 번 나옵니다")
            if (item.title.isBlank()) CatalogJson.fail(where, "title 이 비어 있습니다")
            if (item.description.isBlank()) CatalogJson.fail(where, "description 이 비어 있습니다")
            if (item.days.isEmpty()) CatalogJson.fail(where, "days 가 비어 있습니다")
            RoutineTemplates.Template(
                id = item.id,
                category = CatalogJson.enumNamed<RoutineTemplates.Category>(where, "category", item.category),
                title = item.title,
                description = item.description,
                days = item.days.mapIndexed { dayIndex, day ->
                    parseDay("$where days[$dayIndex] '${day.name}'", day, routineNames)
                },
            )
        }
    }

    private fun parseDay(where: String, day: DayItem, routineNames: MutableSet<String>): RoutineTemplates.Day {
        if (day.name.isBlank()) CatalogJson.fail(where, "name 이 비어 있습니다")
        // 같은 이름의 루틴이 두 개 생기면 어느 것이 어느 프로그램인지 알 수 없다.
        if (!routineNames.add(day.name)) CatalogJson.fail(where, "루틴 이름이 다른 날과 겹칩니다")
        if (day.exercises.isEmpty()) CatalogJson.fail(where, "exercises 가 비어 있습니다")

        val names = day.exercises.map { it.name }
        day.exercises.forEachIndexed { index, exercise ->
            val at = "$where exercises[$index] '${exercise.name}'"
            if (exercise.name.isBlank()) CatalogJson.fail(at, "name 이 비어 있습니다")
            // 휴식 · 슈퍼셋을 이름으로 찾으므로 한 날에 같은 종목이 두 번 있으면 안 된다.
            if (names.indexOf(exercise.name) != index) CatalogJson.fail(at, "같은 날에 이 종목이 두 번 나옵니다")
            if (exercise.sets !in SETS_RANGE) CatalogJson.fail(at, "sets 는 ${SETS_RANGE.first}~${SETS_RANGE.last} 여야 합니다 (${exercise.sets})")
            exercise.rest?.let {
                if (it !in ExerciseCatalog.REST_RANGE) {
                    CatalogJson.fail(at, "rest 는 ${ExerciseCatalog.REST_RANGE.first}~${ExerciseCatalog.REST_RANGE.last} 초여야 합니다 ($it)")
                }
            }
            if (exercise.superset?.isBlank() == true) CatalogJson.fail(at, "superset 라벨이 비어 있습니다")
        }

        // 라벨이 처음 나온 순서대로 묶음 번호가 정해진다.
        val groups = LinkedHashMap<String, MutableList<Int>>()
        day.exercises.forEachIndexed { index, exercise ->
            exercise.superset?.let { groups.getOrPut(it) { mutableListOf() }.add(index) }
        }
        groups.forEach { (label, indices) ->
            if (indices.size < 2) CatalogJson.fail(where, "superset '$label' 는 종목이 하나뿐입니다. 슈퍼셋은 두 종목 이상을 묶습니다")
            if (indices.last() - indices.first() != indices.size - 1) {
                CatalogJson.fail(where, "superset '$label' 의 종목은 이어서 나와야 합니다")
            }
        }

        val scheduled = day.scheduledDays.map { CatalogJson.enumNamed<DayOfWeek>(where, "scheduledDays", it) }
        if (scheduled.toSet().size != scheduled.size) CatalogJson.fail(where, "scheduledDays 에 같은 요일이 두 번 있습니다")

        return RoutineTemplates.Day(
            routineName = day.name,
            exercises = day.exercises.map { it.name to it.sets },
            supersets = groups.values.map { indices -> indices.map { names[it] } },
            restSeconds = day.exercises.mapNotNull { e -> e.rest?.let { e.name to it } }.toMap(),
            scheduledDays = scheduled.toSet(),
        )
    }
}
