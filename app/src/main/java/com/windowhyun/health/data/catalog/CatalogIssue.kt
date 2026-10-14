package com.windowhyun.health.data.catalog

import com.windowhyun.health.data.local.entity.ExerciseEntity
import com.windowhyun.health.ui.gym.RoutineTemplates

enum class IssueSeverity { ERROR, WARNING }

/**
 * 데이터 파일에서 찾은 문제 한 건.
 *
 * [path] 는 어디인지(예: `templates[1] 'my_split' › days[0] '하체 (화)' › exercises[3] '벤치프레스' › sets`),
 * [message] 는 무엇이 왜 문제인지다. 오류([IssueSeverity.ERROR])가 하나라도 있으면 파일을 받아들이지 않고,
 * 경고([IssueSeverity.WARNING])는 알려 주기만 한다.
 */
data class CatalogIssue(
    val severity: IssueSeverity,
    val path: String,
    val message: String,
) {
    val isError: Boolean get() = severity == IssueSeverity.ERROR

    override fun toString(): String = if (path.isEmpty()) message else "$path: $message"
}

/** 앱에 포함된 파일이나 가져온 파일이 형식에 맞지 않을 때. 메시지는 파일 이름으로 시작한다. */
class CatalogFormatException(
    message: String,
    val issues: List<CatalogIssue> = emptyList(),
) : IllegalArgumentException(message)

/** [CatalogReader] 가 파일 하나를 읽은 결과. */
class CatalogReport(
    /** 오류 없이 읽힌 새 종목. 기기에 이미 있는 이름은 빠져 있다. */
    val exercises: List<ExerciseEntity>,
    /** 오류 없이 읽힌 템플릿. */
    val templates: List<RoutineTemplates.Template>,
    val issues: List<CatalogIssue>,
    /** 파일에 `exercises` 항목이 있었는지. */
    val hasExercisesSection: Boolean,
    /** 파일에 `templates` 항목이 있었는지. */
    val hasTemplatesSection: Boolean,
) {
    val errors: List<CatalogIssue> get() = issues.filter { it.isError }
    val warnings: List<CatalogIssue> get() = issues.filterNot { it.isError }

    /** 오류가 없다. 이때만 파일 내용을 받아들인다. */
    val isValid: Boolean get() = errors.isEmpty()

    /** 오류가 있으면 모든 문제를 담은 [CatalogFormatException] 을 던진다. */
    fun requireValid(source: String): CatalogReport {
        if (isValid) return this
        val errors = errors
        val lines = errors.joinToString("\n") { "- $it" }
        throw CatalogFormatException("$source: 문제 ${errors.size}개\n$lines", issues)
    }
}
