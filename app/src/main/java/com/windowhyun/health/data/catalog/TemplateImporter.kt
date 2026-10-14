package com.windowhyun.health.data.catalog

import com.windowhyun.health.data.local.entity.ExerciseEntity
import com.windowhyun.health.di.IoDispatcher
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.repository.ExerciseRepository
import com.windowhyun.health.ui.gym.RoutineTemplates
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** 가져오기 전에 보여 주는 검사 결과. 오류가 하나라도 있으면 가져올 수 없다. */
class ImportPreview(
    val issues: List<CatalogIssue>,
    /** 가져오면 새로 생기는 종목(직접 만든 종목이 된다). */
    val newExercises: List<ExerciseEntity>,
    val templates: List<RoutineTemplates.Template>,
    /** 이미 가져온 같은 id 의 템플릿 중 새 내용으로 바뀌는 것의 제목. */
    val replacedTitles: List<String>,
) {
    val errors: List<CatalogIssue> get() = issues.filter { it.isError }
    val warnings: List<CatalogIssue> get() = issues.filterNot { it.isError }
    val routineCount: Int get() = templates.sumOf { it.days.size }

    /** 오류가 없고 새로 들어올 내용이 있을 때만 가져올 수 있다. */
    val canImport: Boolean get() = errors.isEmpty() && (newExercises.isNotEmpty() || templates.isNotEmpty())

    companion object {
        fun rejected(problem: String) = ImportPreview(
            issues = listOf(CatalogIssue(IssueSeverity.ERROR, "", problem)),
            newExercises = emptyList(),
            templates = emptyList(),
            replacedTitles = emptyList(),
        )
    }
}

data class ImportResult(
    val addedExercises: Int,
    val importedTemplates: Int,
    val importedRoutines: Int,
    val replacedTemplates: Int,
)

/**
 * 밖에서 받은 글(파일 · 붙여넣기)을 검사하고, 사용자가 확인하면 기기에 넣는다.
 *
 * 흐름: [analyzeStream] / [analyzeText] 로 검사 → 사용자가 결과를 보고 → [commit].
 * 검사에서 오류가 나면 아무것도 저장하지 않는다. 종목 이름은 기기에 있는 종목과 맞춰 보므로,
 * 이름이 틀린 종목이 루틴에서 조용히 빠지는 일이 없다.
 */
@Singleton
class TemplateImporter @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val userTemplates: UserTemplateRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun analyzeStream(input: InputStream): ImportPreview {
        val read = withContext(io) { input.use { ExternalText.read(it, ImportLimits.EXTERNAL.maxBytes) } }
        return analyzeRead(read)
    }

    suspend fun analyzeText(text: String): ImportPreview =
        analyzeRead(ExternalText.fromPasted(text, ImportLimits.EXTERNAL.maxBytes))

    private suspend fun analyzeRead(read: ExternalText.Result): ImportPreview {
        val text = read.text ?: return ImportPreview.rejected(read.problem.orEmpty())
        val stored = userTemplates.current()
        val builtIn = RoutineTemplates.all
        val context = ImportContext(
            limits = ImportLimits.EXTERNAL,
            markExercisesBuiltIn = false,
            knownExerciseNames = exerciseRepository.observeExercises().first().mapTo(HashSet()) { it.name },
            checkExerciseReferences = true,
            reservedTemplateIds = builtIn.mapTo(HashSet()) { it.id },
            replaceableTemplateIds = stored.mapTo(HashSet()) { it.id },
            otherRoutineNames = (builtIn + stored).associate { t -> t.id to t.days.mapTo(HashSet()) { it.routineName } },
        )
        val report = withContext(io) { CatalogReader.read(text, context) }

        val issues = report.issues.toMutableList()
        val incomingIds = report.templates.mapTo(HashSet()) { it.id }
        if (report.isValid) {
            val merged = UserTemplateRepository.merge(stored, report.templates)
            if (merged.size > UserTemplateRepository.MAX_TEMPLATES) {
                issues += CatalogIssue(IssueSeverity.ERROR, "", TemplateStorageFullException.TOO_MANY)
            } else if (withContext(io) { CatalogWriter.write(merged).toByteArray(Charsets.UTF_8).size } > UserTemplateRepository.MAX_BYTES) {
                issues += CatalogIssue(IssueSeverity.ERROR, "", TemplateStorageFullException.TOO_LARGE)
            }
            if (report.exercises.isEmpty() && report.templates.isEmpty()) {
                issues += CatalogIssue(IssueSeverity.WARNING, "", "새로 가져올 내용이 없습니다.")
            }
        }
        return ImportPreview(
            issues = issues,
            newExercises = report.exercises,
            templates = report.templates,
            replacedTitles = stored.filter { it.id in incomingIds }.map { it.title },
        )
    }

    /** 검사를 통과한 [preview] 를 기기에 넣는다. 종목을 먼저 만든 뒤 템플릿을 저장한다. */
    suspend fun commit(preview: ImportPreview): ImportResult {
        require(preview.canImport) { "오류가 있는 파일은 가져올 수 없습니다." }
        val existing = exerciseRepository.observeExercises().first().mapTo(HashSet()) { it.name }
        val toAdd = preview.newExercises.filter { it.name !in existing }
        toAdd.forEach { e ->
            exerciseRepository.addExercise(
                Exercise(
                    id = 0,
                    name = e.name,
                    category = e.category,
                    bodyPart = e.bodyPart,
                    isBuiltIn = false,
                    defaultRestSeconds = e.defaultRestSeconds,
                    trackingType = e.trackingType,
                ),
            )
        }
        if (preview.templates.isNotEmpty()) userTemplates.save(preview.templates)
        return ImportResult(
            addedExercises = toAdd.size,
            importedTemplates = preview.templates.size,
            importedRoutines = preview.routineCount,
            replacedTemplates = preview.replacedTitles.size,
        )
    }
}
