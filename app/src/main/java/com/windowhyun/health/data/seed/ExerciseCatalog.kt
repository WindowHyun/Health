package com.windowhyun.health.data.seed

import com.windowhyun.health.data.catalog.BundledData
import com.windowhyun.health.data.catalog.CatalogFormatException
import com.windowhyun.health.data.catalog.CatalogReader
import com.windowhyun.health.data.local.entity.ExerciseEntity

/**
 * 기본 운동 종목 파일(`resources/health/exercises.json`) 읽기.
 *
 * ```json
 * { "exercises": [
 *     { "name": "벤치프레스", "category": "BARBELL", "bodyPart": "CHEST" },
 *     { "name": "플랭크", "category": "BODYWEIGHT", "bodyPart": "CORE", "trackingType": "TIME" }
 * ] }
 * ```
 *
 * `trackingType` 을 빼면 중량×횟수(WEIGHT_REPS), `defaultRestSeconds` 를 빼면 앱 설정의 휴식을 쓴다.
 * 형식과 검사 규칙은 [CatalogReader] 와 docs/DATA.md 에 있다.
 */
object ExerciseCatalog {

    const val RESOURCE_PATH = "health/exercises.json"

    /** 앱에 포함된 기본 종목. */
    fun loadBundled(): List<ExerciseEntity> = parse(BundledData.readText(RESOURCE_PATH))

    /** 파일 내용을 종목 목록으로 바꾼다. 형식이 틀리면 모든 문제를 담은 [CatalogFormatException]. */
    fun parse(text: String, source: String = RESOURCE_PATH): List<ExerciseEntity> {
        val report = CatalogReader.read(text).requireValid(source)
        if (!report.hasExercisesSection) throw CatalogFormatException("$source: 'exercises' 항목이 없습니다.")
        return report.exercises
    }
}
