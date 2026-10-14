package com.windowhyun.health.data.catalog

import com.windowhyun.health.ui.gym.RoutineTemplates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** 테스트용: 파일 없이 메모리에만 두는 가져온 템플릿 저장소. 저장소와 같은 병합 규칙과 한도를 쓴다. */
class InMemoryUserTemplateRepository(initial: List<RoutineTemplates.Template> = emptyList()) : UserTemplateRepository {

    private val state = MutableStateFlow(initial)

    override val templates: Flow<List<RoutineTemplates.Template>> get() = state

    override suspend fun current(): List<RoutineTemplates.Template> = state.value

    override suspend fun save(templates: List<RoutineTemplates.Template>) {
        val merged = UserTemplateRepository.merge(state.value, templates)
        if (merged.size > UserTemplateRepository.MAX_TEMPLATES) throw TemplateStorageFullException(TemplateStorageFullException.TOO_MANY)
        state.value = merged
    }

    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun replaceAll(templates: List<RoutineTemplates.Template>) {
        if (templates.size > UserTemplateRepository.MAX_TEMPLATES) throw TemplateStorageFullException(TemplateStorageFullException.TOO_MANY)
        state.value = templates
    }
}
