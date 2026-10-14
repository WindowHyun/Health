package com.windowhyun.health.data.catalog

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * 밖에서 받은 글(파일 · 붙여넣기)을 앱 안으로 들이는 첫 관문.
 *
 * 크기를 먼저 막고(끝없이 이어지는 스트림도 한도까지만 읽는다), UTF-8 이 아니거나 깨진 글은 거절한다.
 * 한 번 더 걸러야 할 내용(JSON 문법 · 형식)은 [CatalogReader] 가 본다.
 */
object ExternalText {

    /** 읽은 글 또는 읽지 못한 이유(둘 중 하나만 있다). */
    class Result private constructor(val text: String?, val problem: String?) {
        companion object {
            fun ok(text: String) = Result(text, null)
            fun rejected(problem: String) = Result(null, problem)
        }
    }

    /** [input] 을 최대 [maxBytes] 까지만 읽어 글로 바꾼다. */
    fun read(input: InputStream, maxBytes: Int): Result {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            // 한도보다 한 바이트만 더 읽어 보면 넘었는지 알 수 있다.
            val n = input.read(chunk, 0, minOf(chunk.size, maxBytes + 1 - total))
            if (n < 0) break
            total += n
            buffer.write(chunk, 0, n)
            if (total > maxBytes) return Result.rejected(tooLarge(maxBytes))
        }
        return decode(buffer.toByteArray(), maxBytes)
    }

    /** 이미 가지고 있는 바이트(또는 붙여넣은 글)를 검사한다. */
    fun decode(bytes: ByteArray, maxBytes: Int): Result {
        if (bytes.size > maxBytes) return Result.rejected(tooLarge(maxBytes))
        if (bytes.isEmpty()) return Result.rejected("비어 있는 파일입니다.")
        if (startsWith(bytes, 0xFF, 0xFE) || startsWith(bytes, 0xFE, 0xFF)) {
            return Result.rejected("UTF-16 으로 저장된 파일입니다. UTF-8 로 저장해 주세요.")
        }
        val start = if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) 3 else 0
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            Result.ok(decoder.decode(ByteBuffer.wrap(bytes, start, bytes.size - start)).toString())
        } catch (e: CharacterCodingException) {
            Result.rejected("UTF-8 글이 아니거나 깨진 글자가 있습니다.")
        }
    }

    /** 붙여넣은 글. 바이트 수로 한도를 보고, 맨 앞의 BOM 은 [decode] 가 뗀다. */
    fun fromPasted(text: String, maxBytes: Int): Result =
        decode(text.toByteArray(Charsets.UTF_8), maxBytes)

    private fun tooLarge(maxBytes: Int) = "파일이 너무 큽니다. ${maxBytes / 1024}KB 까지 가져올 수 있습니다."

    private fun startsWith(bytes: ByteArray, vararg prefix: Int): Boolean =
        bytes.size >= prefix.size && prefix.indices.all { (bytes[it].toInt() and 0xFF) == prefix[it] }
}
