package com.windowhyun.health.data.catalog

import com.windowhyun.health.data.local.entity.ExerciseEntity
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.ui.gym.RoutineTemplates
import java.time.LocalDate

/**
 * 이 기기의 루틴을 템플릿 파일로 바꾼다. 다른 기기에서 불러오거나, 손으로 고쳐 새 템플릿을 만드는 출발점이다.
 *
 * 파일 형식이 표현할 수 없는 루틴(이름 규칙에 안 맞는 종목 · 같은 종목이 두 번 · 종목이 없거나 너무 많음)은
 * 빼고 [Result.skipped] 로 알려 준다. 직접 만든 종목은 `exercises` 에 함께 담아 파일 하나로 완결된다.
 */
object RoutineExport {

    class Result(
        /** 내보낼 글. 내보낼 루틴이 없으면 빈 문자열. */
        val json: String,
        val exportedRoutines: Int,
        /** 형식에 맞지 않아 뺀 루틴의 이름. */
        val skipped: List<String>,
        /** 파일에 함께 담은 직접 만든 종목의 수. */
        val customExercises: Int,
    )

    fun build(routines: List<Routine>, date: LocalDate): Result {
        val skipped = ArrayList<String>()
        val usable = routines.filter { routine ->
            val ok = canExport(routine)
            if (!ok) skipped += routine.name
            ok
        }
        if (usable.isEmpty()) return Result("", 0, skipped, 0)

        val taken = HashSet<String>()
        val days = usable.map { routine -> toDay(routine, uniqueName(routine.name, taken)) }
        val stamp = date.toString().replace("-", "")
        val chunks = days.chunked(CatalogRules.MAX_DAYS_PER_TEMPLATE_FOR_EXPORT)
        val templates = chunks.mapIndexed { index, chunk ->
            val number = if (chunks.size == 1) "" else " ${index + 1}"
            RoutineTemplates.Template(
                id = "my_routines_$stamp" + if (index == 0) "" else "_${index + 1}",
                category = RoutineTemplates.Category.SPLIT,
                title = "내 루틴 ($date)$number",
                description = "이 기기에서 내보낸 루틴 ${chunk.size}개입니다. 불러오면 템플릿 목록에 나타납니다.",
                days = chunk,
            )
        }
        val custom = usable.flatMap { r -> r.items.map { it.exercise } }
            .filterNot { it.isBuiltIn }
            .distinctBy { it.name }
            .map { e ->
                ExerciseEntity(
                    name = e.name,
                    category = e.category,
                    bodyPart = e.bodyPart,
                    isBuiltIn = false,
                    defaultRestSeconds = e.defaultRestSeconds,
                    trackingType = e.trackingType,
                )
            }
        return Result(CatalogWriter.write(templates, custom), usable.size, skipped, custom.size)
    }

    private fun canExport(routine: Routine): Boolean {
        if (routine.items.isEmpty() || routine.items.size > ImportLimits.EXTERNAL.maxItemsPerDay) return false
        // 루틴 이름은 다듬어서 쓰지만, 다듬고 나서 아무것도 남지 않으면 내보낼 수 없다.
        if (sanitize(routine.name).isEmpty()) return false
        val names = routine.items.map { it.exercise.name }
        if (names.toSet().size != names.size) return false
        return routine.items.all { item ->
            CatalogReader.isAcceptableName(item.exercise.name) &&
                item.defaultSets in CatalogRules.SETS &&
                (item.restSeconds == null || item.restSeconds in CatalogRules.REST_SECONDS)
        }
    }

    /** 루틴 이름은 자유롭게 지을 수 있어서, 파일 규칙에 맞게 다듬는다(종목 이름과 달리 다른 곳이 이름으로 찾지 않는다). */
    private fun sanitize(name: String): String {
        val cleaned = buildString {
            var i = 0
            while (i < name.length) {
                val cp = name.codePointAt(i)
                if (CatalogReader.isIllegal(cp, multiline = false)) append(' ') else appendCodePoint(cp)
                i += Character.charCount(cp)
            }
        }.trim()
        return takeChars(java.text.Normalizer.normalize(cleaned, java.text.Normalizer.Form.NFC), CatalogRules.MAX_NAME_LENGTH).trim()
    }

    /** 앞에서 [count] 글자. 이모지처럼 두 글자로 이루어진 글자의 한가운데에서 자르지 않는다. */
    private fun takeChars(text: String, count: Int): String {
        val cut = text.take(count)
        return if (cut.length < text.length && cut.isNotEmpty() && Character.isHighSurrogate(cut.last())) cut.dropLast(1) else cut
    }

    private fun uniqueName(name: String, taken: MutableSet<String>): String {
        val base = sanitize(name)
        var candidate = base
        var n = 2
        while (!taken.add(candidate)) {
            val suffix = " ($n)"
            candidate = takeChars(base, CatalogRules.MAX_NAME_LENGTH - suffix.length).trim() + suffix
            n++
        }
        return candidate
    }

    private fun toDay(routine: Routine, name: String): RoutineTemplates.Day {
        val items = routine.items.sortedBy { it.orderIndex }
        // 같은 번호가 이어진 종목들이 슈퍼셋 하나다. 하나뿐인 번호는 묶음이 아니다.
        val supersets = ArrayList<List<String>>()
        var run = ArrayList<String>()
        var runGroup = 0
        fun flush() {
            if (run.size >= 2) supersets += run.toList()
            run = ArrayList()
        }
        items.forEach { item ->
            if (item.supersetGroup == 0 || item.supersetGroup != runGroup) flush()
            runGroup = item.supersetGroup
            if (item.supersetGroup != 0) run += item.exercise.name
        }
        flush()
        return RoutineTemplates.Day(
            routineName = name,
            exercises = items.map { it.exercise.name to it.defaultSets },
            supersets = supersets,
            restSeconds = items.mapNotNull { item -> item.restSeconds?.let { item.exercise.name to it } }.toMap(),
            scheduledDays = routine.scheduledDays,
        )
    }
}
