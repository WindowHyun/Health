package com.windowhyun.health.data.cloud

import com.windowhyun.health.domain.cloud.CloudException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 백업 파일을 클라우드에 올리기 좋게 바꾸는 규칙(압축 · 조각 나누기 · 해시).
 *
 * Firestore 문서 하나는 1MiB 까지라, 압축한 백업을 [CHUNK_BYTES] 씩 잘라 여러 문서에 나눠 둔다.
 * GPS 경로는 숫자가 반복돼 압축이 잘 된다(보통 몇 분의 일).
 */
internal object CloudBackupCodec {

    /** 문서 하나에 담는 크기. 문서 한도(1MiB)에 여유를 둔다. */
    const val CHUNK_BYTES = 600_000

    /** 압축한 백업이 이보다 크면 올리지 않는다(무료 저장 공간과 올리는 시간을 지키기 위해). */
    const val MAX_COMPRESSED_BYTES = 24 * 1024 * 1024

    /** 풀었을 때의 크기 한도. 손상되거나 일부러 부풀린 파일(압축 폭탄)이 메모리를 다 쓰지 않게 한다. */
    const val MAX_EXPANDED_BYTES = 512L * 1024 * 1024

    /** 압축해서 메모리에 모은다. [write] 가 쓰는 만큼만 압축된 채로 쌓여, 풀린 원본이 통째로 메모리에 올라가지 않는다. */
    inline fun <T> compress(write: (java.io.OutputStream) -> T): Pair<ByteArray, T> {
        val bytes = ByteArrayOutputStream()
        val result = GZIPOutputStream(bytes).use { write(it) }
        return bytes.toByteArray() to result
    }

    fun split(data: ByteArray, chunkBytes: Int = CHUNK_BYTES): List<ByteArray> {
        if (data.isEmpty()) return listOf(ByteArray(0))
        return (data.indices step chunkBytes).map { start -> data.copyOfRange(start, minOf(start + chunkBytes, data.size)) }
    }

    fun join(chunks: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream(chunks.sumOf { it.size })
        chunks.forEach { out.write(it) }
        return out.toByteArray()
    }

    fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    /** 압축을 풀어 읽는 스트림. 풀린 크기가 [MAX_EXPANDED_BYTES] 를 넘으면 오류로 멈춘다. */
    fun decompressed(zipped: ByteArray): InputStream = BoundedInputStream(GZIPInputStream(ByteArrayInputStream(zipped)), MAX_EXPANDED_BYTES)

    private class BoundedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L

        private fun add(n: Int): Int {
            if (n > 0) {
                count += n
                if (count > limit) throw CloudException("백업 파일이 비정상적으로 큽니다. 손상된 파일일 수 있어 복원하지 않았어요.")
            }
            return n
        }

        override fun read(): Int = super.read().also { if (it >= 0) add(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = add(super.read(b, off, len))
    }
}
