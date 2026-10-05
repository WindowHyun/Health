package com.windowhyun.health.ui.gym

import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.domain.model.ExerciseDeleteResult
import com.windowhyun.health.domain.model.ExerciseEditResult
import com.windowhyun.health.domain.model.ExerciseUsage
import com.windowhyun.health.domain.repository.ExerciseRepository

/**
 * 종목 선택 시트가 직접 만든 종목을 고치고 지울 때 부르는 창구.
 * 화면이 저장소를 직접 쥐지 않게 하고, 화면마다 고친 뒤 할 일(루틴 편집 중인 목록 갱신 등)을
 * 덧붙일 수 있게 한다.
 */
interface ExerciseManager {
    suspend fun usage(id: Long): ExerciseUsage

    suspend fun edit(
        id: Long,
        name: String,
        category: ExerciseCategory,
        bodyPart: BodyPart,
        trackingType: ExerciseTrackingType,
    ): ExerciseEditResult

    suspend fun delete(id: Long): ExerciseDeleteResult
}

class RepositoryExerciseManager(private val repository: ExerciseRepository) : ExerciseManager {
    override suspend fun usage(id: Long) = repository.getUsage(id)

    override suspend fun edit(
        id: Long,
        name: String,
        category: ExerciseCategory,
        bodyPart: BodyPart,
        trackingType: ExerciseTrackingType,
    ) = repository.editExercise(id, name, category, bodyPart, trackingType)

    override suspend fun delete(id: Long) = repository.deleteCustomExercise(id)
}
