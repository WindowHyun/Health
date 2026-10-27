package com.windowhyun.health.data.catalog

import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.data.local.entity.ExerciseEntity
import com.windowhyun.health.ui.gym.RoutineTemplates
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.Normalizer
import java.time.DayOfWeek

/**
 * 종목 · 루틴 템플릿 파일을 읽고 **전부** 검사한다.
 *
 * 앱에 포함된 파일(`resources/health`)과, 사용자가 가져오는 외부 파일이 같은 규칙을 쓴다.
 * 첫 문제에서 멈추지 않고 파일 전체를 훑어 모든 문제를 경로와 함께 모아 돌려주므로, 손으로 쓴 파일을
 * 한 번에 고칠 수 있다. 오류가 하나라도 있으면 [CatalogReport.isValid] 가 false 이고 아무것도 받아들이지 않는다.
 *
 * 파일 형식:
 * ```json
 * { "version": 1,
 *   "exercises": [ { "name": "벤치프레스", "category": "BARBELL", "bodyPart": "CHEST" } ],
 *   "templates": [ { "id": "my_split", "category": "SPLIT", "title": "...", "description": "...",
 *     "days": [ { "name": "하체 (화)", "scheduledDays": ["TUESDAY"],
 *       "exercises": [ { "name": "스쿼트", "sets": 4, "rest": 120 } ] } ] } ] }
 * ```
 * `exercises` 와 `templates` 는 둘 중 하나만 있어도 된다. 자세한 규칙은 docs/DATA.md.
 *
 * 밖에서 온 글이라는 점을 전제로 한다: 크기 · 중첩 깊이 · 항목 수 · 글자 수를 제한하고, 제어 문자와
 * 글자 방향을 바꾸는 문자를 거절하며, 모르는 키는 오타일 수 있어 오류로 본다.
 */
object CatalogReader {

    fun read(text: String, context: ImportContext = ImportContext()): CatalogReport =
        Session(context, Suggestions::closest).run(text)

    /** 비슷한 이름 찾기를 바꿔 끼울 수 있는 진입점(찾는 횟수를 세는 테스트용). */
    internal fun read(
        text: String,
        context: ImportContext,
        suggest: (String, Collection<String>) -> String?,
    ): CatalogReport = Session(context, suggest).run(text)

    private val ROOT_KEYS = listOf("version", "exercises", "templates")
    private val EXERCISE_KEYS = listOf("name", "category", "bodyPart", "trackingType", "defaultRestSeconds")
    private val TEMPLATE_KEYS = listOf("id", "category", "title", "description", "days")
    private val DAY_KEYS = listOf("name", "scheduledDays", "exercises")
    private val ITEM_KEYS = listOf("name", "sets", "rest", "superset")

    /** 이모지를 잇는 ZWJ. 설명(여러 줄 글)에서만 허용한다. */
    private const val ZERO_WIDTH_JOINER = 0x200D

    /**
     * 분류로는 걸러지지 않지만 화면에 아무것도 그리지 않는 글자(한글 채움 문자 · 점자 빈칸 · 크메르 내재 모음).
     * 이름이 이것뿐이면 비어 보이고, 뒤에 붙으면 같아 보이는 다른 이름이 된다.
     */
    private val BLANK_LOOKALIKES = setOf(0x3164, 0x115F, 0x1160, 0xFFA0, 0x2800, 0x17B4, 0x17B5)

    /** 파일 하나를 읽는 동안의 상태. */
    private class Session(
        private val ctx: ImportContext,
        private val suggest: (String, Collection<String>) -> String?,
    ) {

        private val limits = ctx.limits
        // 오류와 알림은 한도를 따로 쓴다. 알림이 먼저 많이 나와도 뒤의 진짜 오류가 숫자로만 남지 않게.
        private val errorIssues = ArrayList<CatalogIssue>()
        private val warningIssues = ArrayList<CatalogIssue>()
        private var errorCount = 0
        private var omittedErrors = 0
        private var omittedWarnings = 0

        /** 파일 안에서 정의한 종목 이름(이미 기기에 있어서 건너뛴 것 포함). */
        private val definedNames = HashSet<String>()
        private val newExercises = ArrayList<ExerciseEntity>()
        private val templates = ArrayList<RoutineTemplates.Template>()

        // 템플릿 사이에서 겹침을 보는 이름들
        private val templateIds = HashSet<String>()
        private val routineNames = HashSet<String>()

        /** 다른 템플릿이 가진 루틴 이름. 이 파일이 덮어쓸(같은 id) 템플릿의 이름은 곧 사라지므로 뺀다. */
        private var otherRoutineNames: Set<String> = emptySet()

        // 모르는 종목 이름의 "혹시 이거?" 는 비싸서 처음 몇 개만 찾는다.
        private val suggestions = HashMap<String, String?>()

        /** 이름 후보. 종목 항목을 다 읽은 뒤(템플릿을 읽을 때) 처음 필요할 때 한 번만 만든다. */
        private val candidateNames: List<String> by lazy { (ctx.knownExerciseNames + definedNames).toList() }
        private var hintedNewExercise = false

        fun run(text: String): CatalogReport {
            val root = parseTree(text)
            var hasExercises = false
            var hasTemplates = false
            if (root != null) {
                val obj = root as? JsonObject
                if (obj == null) {
                    error("", "파일의 맨 바깥은 { } 객체여야 합니다.")
                } else {
                    checkKeys(obj, "", ROOT_KEYS)
                    readVersion(obj)
                    hasExercises = "exercises" in obj
                    hasTemplates = "templates" in obj
                    if (!hasExercises && !hasTemplates) {
                        error("", "가져올 내용이 없습니다. 'exercises' 나 'templates' 가 있어야 합니다.")
                    }
                    if (hasExercises) readExercises(obj)
                    if (hasTemplates) readTemplates(obj)
                }
            }
            if (omittedErrors > 0) {
                errorIssues += CatalogIssue(IssueSeverity.ERROR, "", "그 밖에 오류 ${omittedErrors}개가 더 있습니다. 위의 문제부터 고쳐 보세요.")
            }
            if (omittedWarnings > 0) {
                warningIssues += CatalogIssue(IssueSeverity.WARNING, "", "그 밖에 알림 ${omittedWarnings}개가 더 있습니다.")
            }
            // 오류를 먼저 보여 준다.
            return CatalogReport(newExercises, templates, errorIssues + warningIssues, hasExercises, hasTemplates)
        }

        // ----- 문제 기록 -----

        private fun error(path: String, message: String) {
            errorCount++
            if (errorIssues.size < limits.maxIssues) {
                errorIssues += CatalogIssue(IssueSeverity.ERROR, path, message)
            } else {
                omittedErrors++
            }
        }

        private fun warn(path: String, message: String) {
            if (warningIssues.size < limits.maxIssues) {
                warningIssues += CatalogIssue(IssueSeverity.WARNING, path, message)
            } else {
                omittedWarnings++
            }
        }

        private fun join(path: String, segment: String) = if (path.isEmpty()) segment else "$path › $segment"

        /** 파일에 있던 글을 메시지에 그대로 옮기지 않는다: 길이를 줄이고 위험한 문자를 가린다. */
        private fun show(text: String): String {
            val clean = buildString {
                var i = 0
                while (i < text.length && length < MAX_SHOWN) {
                    val cp = text.codePointAt(i)
                    if (isIllegal(cp, multiline = false)) append('?') else appendCodePoint(cp)
                    i += Character.charCount(cp)
                }
            }
            return if (clean.length < text.length && text.codePointCount(0, text.length) > MAX_SHOWN) "$clean…" else clean
        }

        // ----- JSON 문법 -----

        private fun parseTree(text: String): JsonElement? {
            if (deepestNesting(text) > limits.maxDepth) {
                error("", "괄호 중첩이 너무 깊습니다. ${limits.maxDepth}단계까지만 가져옵니다.")
                return null
            }
            return try {
                Json.parseToJsonElement(text)
            } catch (e: IllegalArgumentException) {
                val detail = e.message.orEmpty()
                val offset = OFFSET.find(detail)?.groupValues?.get(1)?.toIntOrNull()
                val where = offset?.let { " (${lineOf(text, it)}행 ${columnOf(text, it)}열)" }.orEmpty()
                error("", "JSON 문법이 올바르지 않습니다$where. ${show(detail.lineSequence().first())}")
                null
            }
        }

        private fun deepestNesting(text: String): Int {
            var depth = 0
            var deepest = 0
            var inString = false
            var escaped = false
            for (c in text) {
                if (inString) {
                    when {
                        escaped -> escaped = false
                        c == '\\' -> escaped = true
                        c == '"' -> inString = false
                    }
                } else when (c) {
                    '"' -> inString = true
                    '{', '[' -> {
                        depth++
                        if (depth > deepest) deepest = depth
                    }
                    '}', ']' -> if (depth > 0) depth--
                }
            }
            return deepest
        }

        private fun lineOf(text: String, offset: Int) = text.take(offset).count { it == '\n' } + 1

        private fun columnOf(text: String, offset: Int): Int {
            val before = text.take(offset)
            return offset - (before.lastIndexOf('\n') + 1) + 1
        }

        // ----- 값 읽기 -----

        private fun checkKeys(obj: JsonObject, path: String, allowed: List<String>) {
            obj.keys.forEach { key ->
                if (key !in allowed) {
                    val hint = Suggestions.closest(key, allowed)?.let { " '$it' 를 쓰려던 건가요?" }.orEmpty()
                    error(path, "알 수 없는 키 '${show(key)}' 입니다.$hint (쓸 수 있는 키: ${allowed.joinToString()})")
                }
            }
        }

        private fun asObject(element: JsonElement, path: String): JsonObject? =
            (element as? JsonObject) ?: run {
                error(path, "{ } 객체여야 합니다.")
                null
            }

        private fun array(obj: JsonObject, key: String, path: String, maxItems: Int, required: Boolean = true): List<JsonElement>? {
            val at = join(path, key)
            val element = obj[key]
            if (element == null || element is JsonNull) {
                if (required) error(at, "필수 항목이 없습니다.")
                return null
            }
            if (element !is JsonArray) {
                error(at, "[ ] 배열이어야 합니다.")
                return null
            }
            if (element.size > maxItems) {
                error(at, "${element.size}개입니다. ${maxItems}개까지만 가져옵니다.")
                return null
            }
            return element.toList()
        }

        /** 보이는 이름표. 이름이 올바르지 않아도 어디인지 알 수 있게 한다. */
        private fun labelOf(obj: JsonObject, key: String): String {
            val primitive = obj[key] as? JsonPrimitive ?: return ""
            return if (primitive.isString) " '${show(primitive.content)}'" else ""
        }

        private fun text(
            obj: JsonObject,
            key: String,
            path: String,
            maxLength: Int,
            multiline: Boolean = false,
            pattern: Regex? = null,
            patternHint: String = "",
        ): String? {
            val at = join(path, key)
            val element = obj[key]
            if (element == null || element is JsonNull) {
                error(at, "필수 항목이 없습니다.")
                return null
            }
            val primitive = element as? JsonPrimitive
            if (primitive == null || !primitive.isString) {
                error(at, "글(따옴표로 감싼 문자열)이어야 합니다.")
                return null
            }
            var value = Normalizer.normalize(primitive.content, Normalizer.Form.NFC)
            if (multiline) value = value.replace("\r\n", "\n").replace('\r', '\n')
            return checkText(value, at, maxLength, multiline, pattern, patternHint)
        }

        private fun checkText(
            value: String,
            at: String,
            maxLength: Int,
            multiline: Boolean,
            pattern: Regex?,
            patternHint: String,
        ): String? {
            when {
                value.isBlank() -> error(at, "비어 있습니다.")
                // 이름은 글자 하나, 공백 하나까지 같아야 종목을 찾을 수 있다.
                !multiline && value != value.trim() -> error(at, "앞이나 뒤에 공백이 있습니다. 공백도 이름의 일부라 지워 주세요.")
                value.length > maxLength -> error(at, "${value.length}자입니다. ${maxLength}자까지 쓸 수 있습니다.")
                else -> {
                    val bad = if (ctx.rejectInvisibleText) firstIllegal(value, multiline) else null
                    when {
                        bad != null -> error(at, "쓸 수 없는 문자가 있습니다($bad).")
                        pattern != null && !pattern.matches(value) -> error(at, patternHint)
                        else -> return value
                    }
                }
            }
            return null
        }

        private fun int(obj: JsonObject, key: String, path: String, range: IntRange, required: Boolean = true): Int? {
            val at = join(path, key)
            val element = obj[key]
            if (element == null || element is JsonNull) {
                if (required) error(at, "필수 항목이 없습니다.")
                return null
            }
            val primitive = element as? JsonPrimitive
            if (primitive == null || primitive.isString) {
                error(at, "숫자여야 합니다(따옴표 없이 적습니다).")
                return null
            }
            val number = primitive.content.toIntOrNull()
            if (number == null) {
                error(at, "정수여야 합니다.")
                return null
            }
            if (number !in range) {
                error(at, "${range.first}~${range.last} 사이여야 합니다(지금 $number).")
                return null
            }
            return number
        }

        private inline fun <reified E : Enum<E>> enumField(
            obj: JsonObject,
            key: String,
            path: String,
            required: Boolean = true,
        ): E? {
            val at = join(path, key)
            val element = obj[key]
            if (element == null || element is JsonNull) {
                if (required) error(at, "필수 항목이 없습니다.")
                return null
            }
            val primitive = element as? JsonPrimitive
            if (primitive == null || !primitive.isString) {
                error(at, "글(따옴표로 감싼 문자열)이어야 합니다.")
                return null
            }
            return enumByName<E>(primitive.content, at)
        }

        private inline fun <reified E : Enum<E>> enumByName(value: String, at: String): E? {
            val values = enumValues<E>()
            values.firstOrNull { it.name == value }?.let { return it }
            val hint = Suggestions.closest(value, values.map { it.name })?.let { " '$it' 를 쓰려던 건가요?" }.orEmpty()
            error(at, "'${show(value)}' 는 올바른 값이 아닙니다.$hint (쓸 수 있는 값: ${values.joinToString { it.name }})")
            return null
        }

        // ----- 형식 버전 -----

        private fun readVersion(obj: JsonObject) {
            if (obj["version"] == null) return
            val version = int(obj, "version", "", 0..Int.MAX_VALUE, required = false) ?: return
            if (version != CatalogRules.FORMAT_VERSION) {
                error(
                    "version",
                    "이 앱이 읽을 수 없는 형식 버전입니다($version). 읽을 수 있는 버전은 ${CatalogRules.FORMAT_VERSION} 입니다.",
                )
            }
        }

        // ----- 종목 -----

        private fun readExercises(root: JsonObject) {
            val items = array(root, "exercises", "", limits.maxExercises) ?: return
            val seen = HashSet<String>()
            items.forEachIndexed { index, item ->
                val before = errorCount
                val base = "exercises[$index]"
                val obj = asObject(item, base) ?: return@forEachIndexed
                val path = base + labelOf(obj, "name")
                checkKeys(obj, path, EXERCISE_KEYS)
                val name = text(obj, "name", path, CatalogRules.MAX_NAME_LENGTH)
                val category = enumField<ExerciseCategory>(obj, "category", path)
                val bodyPart = enumField<BodyPart>(obj, "bodyPart", path)
                val tracking = enumField<ExerciseTrackingType>(obj, "trackingType", path, required = false)
                val rest = int(obj, "defaultRestSeconds", path, CatalogRules.REST_SECONDS, required = false)
                if (name != null && !seen.add(name)) {
                    error(path, "같은 이름의 종목이 파일에 두 번 나옵니다.")
                }
                if (errorCount != before || name == null || category == null || bodyPart == null) return@forEachIndexed

                definedNames += name
                if (name in ctx.knownExerciseNames) {
                    warn(path, "이미 있는 종목이라 건너뜁니다.")
                } else {
                    newExercises += ExerciseEntity(
                        name = name,
                        category = category,
                        bodyPart = bodyPart,
                        isBuiltIn = ctx.markExercisesBuiltIn,
                        defaultRestSeconds = rest,
                        trackingType = tracking ?: ExerciseTrackingType.WEIGHT_REPS,
                    )
                }
            }
        }

        // ----- 템플릿 -----

        private fun readTemplates(root: JsonObject) {
            val items = array(root, "templates", "", limits.maxTemplates) ?: return
            val idsInFile = items.mapNotNull { ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.toSet()
            otherRoutineNames = ctx.otherRoutineNames.filterKeys { it !in idsInFile }.values.flatten().toSet()
            items.forEachIndexed { index, item ->
                // 저장된 파일은 따로 가져온 템플릿들이 쌓인 것이라, 템플릿 사이의 이름 겹침은 따지지 않는다.
                if (!ctx.routineNamesUniqueAcrossTemplates) routineNames.clear()
                val before = errorCount
                val base = "templates[$index]"
                val obj = asObject(item, base) ?: return@forEachIndexed
                val path = base + labelOf(obj, "id")
                checkKeys(obj, path, TEMPLATE_KEYS)
                val id = text(
                    obj, "id", path, CatalogRules.MAX_ID_LENGTH,
                    pattern = CatalogRules.ID_PATTERN, patternHint = "영문자 · 숫자 · _ · - 만 쓸 수 있습니다.",
                )
                val category = enumField<RoutineTemplates.Category>(obj, "category", path)
                val title = text(obj, "title", path, CatalogRules.MAX_TITLE_LENGTH)
                val description = text(obj, "description", path, CatalogRules.MAX_DESCRIPTION_LENGTH, multiline = true)
                val dayItems = array(obj, "days", path, limits.maxDaysPerTemplate)
                if (dayItems != null && dayItems.isEmpty()) error(join(path, "days"), "하루도 없습니다.")
                if (id != null) checkTemplateId(id, path)

                val days = dayItems.orEmpty().mapIndexedNotNull { dayIndex, day -> readDay(day, dayIndex, path) }
                if (errorCount != before || id == null || category == null || title == null || description == null) return@forEachIndexed
                if (days.size != dayItems?.size) return@forEachIndexed
                templates += RoutineTemplates.Template(id, category, title, description, days)
            }
        }

        private fun checkTemplateId(id: String, path: String) {
            when {
                !templateIds.add(id) -> error(path, "같은 id 의 템플릿이 파일에 두 번 나옵니다.")
                id in ctx.reservedTemplateIds -> error(path, "앱에 포함된 템플릿과 id 가 같습니다. 다른 id 를 쓰세요.")
                id in ctx.replaceableTemplateIds -> warn(path, "이미 가져온 템플릿입니다. 가져오면 새 내용으로 바꿉니다.")
            }
        }

        private fun readDay(element: JsonElement, index: Int, parent: String): RoutineTemplates.Day? {
            val before = errorCount
            val base = join(parent, "days[$index]")
            val obj = asObject(element, base) ?: return null
            val path = base + labelOf(obj, "name")
            checkKeys(obj, path, DAY_KEYS)
            val name = text(obj, "name", path, CatalogRules.MAX_NAME_LENGTH)
            if (name != null) {
                when {
                    !routineNames.add(name) -> error(path, "루틴 이름이 같은 파일의 다른 날과 겹칩니다.")
                    name in otherRoutineNames -> warn(path, "같은 이름의 루틴이 다른 템플릿에도 있습니다. 적용하면 같은 이름이 두 개 생깁니다.")
                }
            }
            val scheduled = readScheduledDays(obj, path)
            val exerciseItems = array(obj, "exercises", path, limits.maxItemsPerDay)
            if (exerciseItems != null && exerciseItems.isEmpty()) error(join(path, "exercises"), "종목이 하나도 없습니다.")

            val lines = exerciseItems.orEmpty().mapIndexed { i, item -> readLine(item, i, path) }
            checkDuplicateNames(lines)
            checkSupersets(lines, path)

            if (errorCount != before || name == null || scheduled == null) return null
            val names = lines.map { it.name ?: return null }
            val supersets = LinkedHashMap<String, MutableList<String>>()
            lines.forEach { line -> line.superset?.let { supersets.getOrPut(it) { mutableListOf() } += line.name!! } }
            return RoutineTemplates.Day(
                routineName = name,
                exercises = lines.mapIndexed { i, line -> names[i] to (line.sets ?: return null) },
                supersets = supersets.values.toList(),
                restSeconds = lines.mapNotNull { line -> line.rest?.let { line.name!! to it } }.toMap(),
                scheduledDays = scheduled,
            )
        }

        private fun readScheduledDays(obj: JsonObject, path: String): Set<DayOfWeek>? {
            val element = obj["scheduledDays"]
            if (element == null || element is JsonNull) return emptySet()
            val items = array(obj, "scheduledDays", path, maxItems = 7) ?: return null
            val result = LinkedHashSet<DayOfWeek>()
            var ok = true
            items.forEachIndexed { i, item ->
                val at = join(path, "scheduledDays[$i]")
                val primitive = item as? JsonPrimitive
                if (primitive == null || !primitive.isString) {
                    error(at, "요일 이름(예: \"MONDAY\")이어야 합니다.")
                    ok = false
                    return@forEachIndexed
                }
                val day = enumByName<DayOfWeek>(primitive.content, at)
                if (day == null) {
                    ok = false
                } else if (!result.add(day)) {
                    error(at, "같은 요일이 두 번 있습니다.")
                    ok = false
                }
            }
            return result.takeIf { ok }
        }

        /** 루틴의 종목 한 줄. 필드가 틀려도 위치를 알 수 있게 읽은 만큼 담아 둔다. */
        private class Line(
            val index: Int,
            val path: String,
            val name: String?,
            val sets: Int?,
            val rest: Int?,
            val superset: String?,
        )

        private fun readLine(element: JsonElement, index: Int, parent: String): Line {
            val base = join(parent, "exercises[$index]")
            val obj = asObject(element, base) ?: return Line(index, base, null, null, null, null)
            val path = base + labelOf(obj, "name")
            checkKeys(obj, path, ITEM_KEYS)
            val name = text(obj, "name", path, CatalogRules.MAX_NAME_LENGTH)
            val sets = int(obj, "sets", path, CatalogRules.SETS)
            val rest = int(obj, "rest", path, CatalogRules.ITEM_REST_SECONDS, required = false)
            val superset = if (obj["superset"].let { it == null || it is JsonNull }) {
                null
            } else {
                text(obj, "superset", path, CatalogRules.MAX_SUPERSET_LABEL_LENGTH)
            }
            if (name != null) checkReference(name, path)
            return Line(index, path, name, sets, rest, superset)
        }

        /** 휴식 · 슈퍼셋을 이름으로 찾으므로 한 날에 같은 종목이 두 번 있으면 안 된다. */
        private fun checkDuplicateNames(lines: List<Line>) {
            val seen = HashSet<String>()
            lines.forEach { line ->
                val name = line.name ?: return@forEach
                if (!seen.add(name)) error(line.path, "같은 날에 이 종목이 두 번 나옵니다.")
            }
        }

        /** 같은 글자의 superset 은 두 종목 이상이 이어서 나와야 한다(앱의 슈퍼셋 규칙). */
        private fun checkSupersets(lines: List<Line>, path: String) {
            val groups = LinkedHashMap<String, MutableList<Int>>()
            lines.forEach { line -> line.superset?.let { groups.getOrPut(it) { mutableListOf() } += line.index } }
            groups.forEach { (label, positions) ->
                val at = join(path, "superset '${show(label)}'")
                when {
                    positions.size < 2 -> error(at, "종목이 하나뿐입니다. 슈퍼셋은 두 종목 이상을 묶습니다.")
                    positions.last() - positions.first() != positions.size - 1 -> error(at, "묶인 종목은 이어서 나와야 합니다.")
                }
            }
        }

        private fun checkReference(name: String, path: String) {
            if (!ctx.checkExerciseReferences) return
            if (name in ctx.knownExerciseNames || name in definedNames) return
            val suggestion = when {
                // 결과가 없는(null) 것도 기억한다: getOrPut 은 null 을 없는 것으로 봐서 같은 이름이 나올 때마다 다시 찾는다.
                name in suggestions -> suggestions[name]
                suggestions.size < MAX_SUGGESTIONS -> suggest(name, candidateNames).also { suggestions[name] = it }
                else -> null
            }
            val message = buildString {
                append("'${show(name)}' 종목이 없습니다.")
                if (suggestion != null) append(" '${show(suggestion)}' 를 쓰려던 건가요?")
                if (!hintedNewExercise) {
                    hintedNewExercise = true
                    append(" 새 종목이라면 파일의 exercises 에 함께 적을 수 있습니다.")
                }
            }
            error(join(path, "name"), message)
        }
    }

    /**
     * 종목 · 루틴 이름으로 그대로 쓸 수 있는가. 앱 안의 이름을 파일로 내보낼 때, 다시 읽어도 같은 이름이
     * 되는지(공백 · 길이 · 문자 · 정규화)를 미리 확인하는 데 쓴다.
     */
    fun isAcceptableName(text: String): Boolean =
        text.isNotBlank() &&
            text == text.trim() &&
            text.length <= CatalogRules.MAX_NAME_LENGTH &&
            firstIllegal(text, multiline = false) == null &&
            Normalizer.isNormalized(text, Normalizer.Form.NFC)

    private const val MAX_SHOWN = 40
    private const val MAX_SUGGESTIONS = 20
    private val OFFSET = Regex("offset (\\d+)")

    /**
     * 이 글자(코드 포인트)를 거절하는가.
     *
     * 목록으로 적지 않고 유니코드 분류로 거른다: 제어 문자 · 서식 문자(폭이 0인 공백, 글자 방향을 바꾸는 문자, 소프트 하이픈,
     * 태그 문자) · 줄/문단 구분 문자 · 보통 공백이 아닌 공백 · 사설 영역 · 짝 없는 서로게이트.
     * 여기에 분류로는 안 걸리는 빈 글자 몇 개를 더한다.
     * 여러 줄 글(설명)에서는 줄바꿈 · 탭 · 이모지를 잇는 ZWJ 는 허용한다.
     */
    internal fun isIllegal(codePoint: Int, multiline: Boolean): Boolean {
        if (multiline && (codePoint == '\n'.code || codePoint == '\t'.code || codePoint == ZERO_WIDTH_JOINER)) return false
        if (codePoint in BLANK_LOOKALIKES) return true
        // 보통 공백(U+0020) 말고 다른 공백(줄바꿈 없는 공백, 전각 공백 등)은 같아 보이는데 이름이 달라지므로 거절한다.
        if (Character.getType(codePoint) == Character.SPACE_SEPARATOR.toInt() && codePoint != ' '.code) return true
        return when (Character.getType(codePoint)) {
            Character.CONTROL.toInt(),
            Character.FORMAT.toInt(),
            Character.LINE_SEPARATOR.toInt(),
            Character.PARAGRAPH_SEPARATOR.toInt(),
            Character.PRIVATE_USE.toInt(),
            Character.SURROGATE.toInt(),
            -> true
            else -> false
        }
    }

    /** 쓸 수 없는 문자가 있으면 설명("U+202E")을, 없으면 null. */
    internal fun firstIllegal(text: String, multiline: Boolean): String? {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isIllegal(cp, multiline)) return "U+" + cp.toString(16).uppercase().padStart(4, '0')
            i += Character.charCount(cp)
        }
        return null
    }
}
