package com.windowhyun.health.core.util

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 앱에 함께 들어간 데이터 파일(JSON)이 형식에 맞지 않을 때. 메시지에 파일 · 위치 · 이유가 들어 있다. */
class CatalogFormatException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 * 앱에 함께 들어간 데이터 파일(`app/src/main/resources/health` 폴더의 JSON)을 읽는다.
 *
 * 자바 리소스로 읽기 때문에 Context 가 필요 없고, 단위 테스트에서도 실제 앱과 같은 경로로 읽힌다.
 * 파일은 APK 에 함께 들어가므로 고치려면 앱을 다시 빌드해야 한다.
 */
object BundledData {

    fun readText(path: String): String {
        val stream = BundledData::class.java.classLoader?.getResourceAsStream(path)
            ?: throw CatalogFormatException("앱에 포함된 데이터 파일을 찾을 수 없습니다: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}

/** 데이터 파일 읽기에 쓰는 도구. 모르는 키나 잘못된 값은 조용히 넘기지 않고 오류로 알려 준다. */
internal object CatalogJson {

    // 기본값 그대로: 모르는 키 · 빠진 필수 값 · 타입 불일치는 모두 예외다(오타를 바로 알기 위해).
    val json = Json

    inline fun <reified T> decode(text: String, source: String): T =
        try {
            json.decodeFromString<T>(text)
        } catch (e: SerializationException) {
            throw CatalogFormatException("$source: ${e.message}", e)
        }

    /** 문자열을 enum 으로 바꾼다. 없는 값이면 가능한 값을 알려 준다. */
    inline fun <reified E : Enum<E>> enumNamed(where: String, field: String, value: String): E =
        enumValues<E>().firstOrNull { it.name == value }
            ?: throw CatalogFormatException(
                "$where: $field '$value' 는 올바르지 않습니다. 가능한 값: ${enumValues<E>().joinToString { it.name }}",
            )

    fun fail(where: String, reason: String): Nothing = throw CatalogFormatException("$where: $reason")
}
