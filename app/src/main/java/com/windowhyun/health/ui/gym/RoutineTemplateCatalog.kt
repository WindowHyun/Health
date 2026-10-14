package com.windowhyun.health.ui.gym

import com.windowhyun.health.data.catalog.BundledData
import com.windowhyun.health.data.catalog.CatalogFormatException
import com.windowhyun.health.data.catalog.CatalogReader

/**
 * 루틴 템플릿 파일(`resources/health/routine_templates.json`) 읽기.
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
 * `superset` 은 같은 글자끼리 묶이며, 묶음 종목은 **이어서** 나와야 한다.
 * 종목 이름은 종목 파일의 이름과 정확히 같아야 한다(적용할 때 이름으로 찾는다).
 * 형식과 검사 규칙은 [CatalogReader] 와 docs/DATA.md 에 있다.
 */
object RoutineTemplateCatalog {

    const val RESOURCE_PATH = "health/routine_templates.json"

    /** 앱에 포함된 템플릿. */
    fun loadBundled(): List<RoutineTemplates.Template> = parse(BundledData.readText(RESOURCE_PATH))

    /** 파일 내용을 템플릿 목록으로 바꾼다. 형식이 틀리면 모든 문제를 담은 [CatalogFormatException]. */
    fun parse(text: String, source: String = RESOURCE_PATH): List<RoutineTemplates.Template> {
        val report = CatalogReader.read(text).requireValid(source)
        if (!report.hasTemplatesSection) throw CatalogFormatException("$source: 'templates' 항목이 없습니다.")
        return report.templates
    }
}
