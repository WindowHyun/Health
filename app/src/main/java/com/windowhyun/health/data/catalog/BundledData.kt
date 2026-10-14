package com.windowhyun.health.data.catalog

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
