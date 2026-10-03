package com.windowhyun.health.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * 정리 카드 이미지를 저장하고 공유한다. 서버도 저장소 권한도 쓰지 않는다.
 *
 * - 공유: 캐시의 `share/` 에 PNG 를 쓰고 FileProvider 주소를 공유 시트에 넘긴다.
 * - 갤러리 저장: Android 10+ 는 MediaStore 로 `Pictures/Health` 에 넣는다(권한 불필요).
 *   Android 9 이하는 저장소 권한이 필요해서 저장 버튼 없이 공유만 지원한다.
 */
object ImageSharing {

    val canSaveToGallery: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private const val SHARE_DIR = "share"
    private const val STALE_MILLIS = 60 * 60 * 1000L

    /** 카드 PNG 를 [dir] 에 쓴다. 한 시간이 지난 옛 카드는 정리한다. */
    internal fun writePng(dir: File, fileName: String, bitmap: Bitmap): File {
        if (!dir.exists() && !dir.mkdirs()) throw IOException("공유용 폴더를 만들 수 없습니다.")
        val cutoff = System.currentTimeMillis() - STALE_MILLIS
        dir.listFiles { file -> file.extension == "png" && file.lastModified() < cutoff }?.forEach { it.delete() }

        val file = File(dir, "$fileName.png")
        file.outputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("이미지를 만들 수 없습니다.")
        }
        return file
    }

    /** 공유 시트를 연다. */
    fun share(context: Context, bitmap: Bitmap, fileName: String) {
        val file = writePng(File(context.cacheDir, SHARE_DIR), fileName, bitmap)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "정리 카드 공유").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /** `Pictures/Health` 에 저장한다. 저장하지 못하면 이유를 담아 던진다. */
    fun saveToGallery(context: Context, bitmap: Bitmap, fileName: String) {
        check(canSaveToGallery) { "이 기기에서는 갤러리에 바로 저장할 수 없습니다. 공유를 이용해 주세요." }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Health")
            // 다 쓸 때까지는 갤러리에 보이지 않게 한다.
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("갤러리에 파일을 만들 수 없습니다.")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("이미지를 만들 수 없습니다.")
            } ?: throw IOException("갤러리 파일을 열 수 없습니다.")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Throwable) {
            // 반쯤 쓴 파일이 갤러리에 남지 않게 한다.
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }
}
