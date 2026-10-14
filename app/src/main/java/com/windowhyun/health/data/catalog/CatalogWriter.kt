package com.windowhyun.health.data.catalog

import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.data.local.entity.ExerciseEntity
import com.windowhyun.health.ui.gym.RoutineTemplates
import kotlinx.serialization.json.JsonPrimitive

/**
 * 종목 · 템플릿을 [CatalogReader] 가 읽는 JSON 글로 쓴다.
 *
 * 가져온 템플릿을 기기에 저장하고, 백업에 넣고, 내 루틴을 파일로 내보낼 때 쓴다.
 * 앱에 포함된 파일(`resources/health`)과 같은 모양이라 손으로 고치기 쉽다: 종목은 한 줄에 하나다.
 * 쓴 글은 언제나 [CatalogReader] 로 다시 읽으면 같은 내용이 된다.
 */
object CatalogWriter {

    fun write(
        templates: List<RoutineTemplates.Template>,
        exercises: List<ExerciseEntity> = emptyList(),
    ): String = buildString {
        appendLine("{")
        append("  \"version\": ${CatalogRules.FORMAT_VERSION}")
        if (exercises.isNotEmpty()) {
            appendLine(",")
            appendLine("  \"exercises\": [")
            exercises.forEachIndexed { i, e ->
                val parts = mutableListOf(
                    "\"name\": ${quote(e.name)}",
                    "\"category\": \"${e.category.name}\"",
                    "\"bodyPart\": \"${e.bodyPart.name}\"",
                )
                if (e.trackingType != ExerciseTrackingType.WEIGHT_REPS) parts += "\"trackingType\": \"${e.trackingType.name}\""
                e.defaultRestSeconds?.let { parts += "\"defaultRestSeconds\": $it" }
                appendLine("    { ${parts.joinToString(", ")} }${comma(i, exercises.size)}")
            }
            append("  ]")
        }
        if (templates.isNotEmpty()) {
            appendLine(",")
            appendLine("  \"templates\": [")
            templates.forEachIndexed { ti, t -> appendTemplate(t, last = ti == templates.lastIndex) }
            append("  ]")
        }
        appendLine()
        appendLine("}")
    }

    private fun StringBuilder.appendTemplate(t: RoutineTemplates.Template, last: Boolean) {
        appendLine("    {")
        appendLine("      \"id\": ${quote(t.id)},")
        appendLine("      \"category\": \"${t.category.name}\",")
        appendLine("      \"title\": ${quote(t.title)},")
        appendLine("      \"description\": ${quote(t.description)},")
        appendLine("      \"days\": [")
        t.days.forEachIndexed { di, d ->
            appendLine("        {")
            appendLine("          \"name\": ${quote(d.routineName)},")
            if (d.scheduledDays.isNotEmpty()) {
                val days = d.scheduledDays.sortedBy { it.value }.joinToString(", ") { "\"${it.name}\"" }
                appendLine("          \"scheduledDays\": [$days],")
            }
            appendLine("          \"exercises\": [")
            d.exercises.forEachIndexed { xi, (name, sets) ->
                val parts = mutableListOf("\"name\": ${quote(name)}", "\"sets\": $sets")
                d.restSeconds[name]?.let { parts += "\"rest\": $it" }
                val group = d.supersets.indexOfFirst { name in it }
                if (group >= 0) parts += "\"superset\": \"${supersetLabel(group)}\""
                appendLine("            { ${parts.joinToString(", ")} }${comma(xi, d.exercises.size)}")
            }
            appendLine("          ]")
            appendLine("        }${comma(di, t.days.size)}")
        }
        appendLine("      ]")
        appendLine("    }${if (last) "" else ","}")
    }

    /** 묶음 번호(0부터)를 A, B, C … 로. 한 날의 묶음은 열다섯 개를 넘지 않는다. */
    internal fun supersetLabel(group: Int): String = ('A' + group).toString()

    private fun comma(index: Int, size: Int) = if (index == size - 1) "" else ","

    /** 따옴표 · 역슬래시 · 제어 문자를 JSON 규칙대로 감싼다. */
    private fun quote(text: String): String = JsonPrimitive(text).toString()
}
