package com.windowhyun.health.data.seed

import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.util.BundledData
import com.windowhyun.health.core.util.CatalogJson
import com.windowhyun.health.data.local.entity.ExerciseEntity
import kotlinx.serialization.Serializable

/**
 * 기본 운동 종목 파일(`resources/health/exercises.json`)의 형식과 읽기.
 *
 * ```json
 * { "exercises": [
 *     { "name": "벤치프레스", "category": "BARBELL", "bodyPart": "CHEST" },
 *     { "name": "플랭크", "category": "BODYWEIGHT", "bodyPart": "CORE", "trackingType": "TIME" }
 * ] }
 * ```
 *
 * `trackingType` 을 빼면 중량×횟수(WEIGHT_REPS), `defaultRestSeconds` 를 빼면 앱 설정의 휴식을 쓴다.
 * 값은 [BodyPart] · [ExerciseCategory] · [ExerciseTrackingType] 의 이름 그대로 적는다.
 */
object ExerciseCatalog {

    const val RESOURCE_PATH = "health/exercises.json"

    @Serializable
    internal data class ExerciseFile(val exercises: List<ExerciseItem>)

    @Serializable
    internal data class ExerciseItem(
        val name: String,
        val category: String,
        val bodyPart: String,
        val trackingType: String = ExerciseTrackingType.WEIGHT_REPS.name,
        val defaultRestSeconds: Int? = null,
    )

    /** 앱에 포함된 기본 종목. */
    fun loadBundled(): List<ExerciseEntity> = parse(BundledData.readText(RESOURCE_PATH))

    /** 파일 내용을 종목 목록으로 바꾼다. 형식이 틀리면 [com.windowhyun.health.core.util.CatalogFormatException]. */
    fun parse(text: String, source: String = RESOURCE_PATH): List<ExerciseEntity> {
        val file = CatalogJson.decode<ExerciseFile>(text, source)
        val seen = HashSet<String>()
        return file.exercises.mapIndexed { index, item ->
            val where = "$source exercises[$index] '${item.name}'"
            if (item.name.isBlank()) CatalogJson.fail(where, "name 이 비어 있습니다")
            // 이름은 DB 의 유니크 키이자 템플릿 · 기록이 가리키는 키다.
            if (!seen.add(item.name)) CatalogJson.fail(where, "같은 이름의 종목이 두 번 나옵니다")
            item.defaultRestSeconds?.let {
                if (it !in REST_RANGE) CatalogJson.fail(where, "defaultRestSeconds 는 ${REST_RANGE.first}~${REST_RANGE.last} 초여야 합니다 ($it)")
            }
            ExerciseEntity(
                name = item.name,
                category = CatalogJson.enumNamed<ExerciseCategory>(where, "category", item.category),
                bodyPart = CatalogJson.enumNamed<BodyPart>(where, "bodyPart", item.bodyPart),
                isBuiltIn = true,
                defaultRestSeconds = item.defaultRestSeconds,
                trackingType = CatalogJson.enumNamed<ExerciseTrackingType>(where, "trackingType", item.trackingType),
            )
        }
    }

    /** 설정의 기본 휴식과 같은 한도(5~600초). */
    internal val REST_RANGE = 5..600
}
