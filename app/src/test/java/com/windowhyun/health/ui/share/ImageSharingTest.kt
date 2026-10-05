package com.windowhyun.health.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** 공유용 PNG 를 만들고 정리하는 부분. 공유 시트와 갤러리 저장은 기기에서만 확인할 수 있다. */
@RunWith(RobolectricTestRunner::class)
class ImageSharingTest {

    private lateinit var context: Context
    private lateinit var dir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dir = File(context.cacheDir, "share-test-${System.nanoTime()}")
    }

    private fun bitmap(color: Int = Color.MAGENTA): Bitmap =
        Bitmap.createBitmap(40, 50, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test
    fun `writes a decodable png named after the card`() {
        val file = ImageSharing.writePng(dir, "health-workout-2026-10-03", bitmap(Color.MAGENTA))

        assertThat(file.name).isEqualTo("health-workout-2026-10-03.png")
        val decoded = BitmapFactory.decodeFile(file.absolutePath)
        assertThat(decoded.width).isEqualTo(40)
        assertThat(decoded.height).isEqualTo(50)
        assertThat(decoded.getPixel(5, 5)).isEqualTo(Color.MAGENTA)
    }

    @Test
    fun `sharing the same card twice overwrites instead of piling up`() {
        ImageSharing.writePng(dir, "card", bitmap(Color.RED))
        val second = ImageSharing.writePng(dir, "card", bitmap(Color.BLUE))

        assertThat(dir.listFiles()!!.map { it.name }).containsExactly("card.png")
        assertThat(BitmapFactory.decodeFile(second.absolutePath).getPixel(1, 1)).isEqualTo(Color.BLUE)
    }

    /** 공유가 끝난 옛 카드가 캐시에 계속 쌓이면 안 된다. 방금 만든 것은 받는 앱이 아직 읽을 수 있으니 남긴다. */
    @Test
    fun `removes stale cards but keeps fresh ones and other files`() {
        dir.mkdirs()
        val stale = File(dir, "old.png").apply { writeText("x"); setLastModified(System.currentTimeMillis() - 2 * 3_600_000L) }
        val fresh = File(dir, "fresh.png").apply { writeText("x") }
        val other = File(dir, "notes.txt").apply { writeText("x"); setLastModified(System.currentTimeMillis() - 2 * 3_600_000L) }

        ImageSharing.writePng(dir, "new", bitmap())

        assertThat(stale.exists()).isFalse()
        assertThat(fresh.exists()).isTrue()
        assertThat(other.exists()).isTrue()
        assertThat(File(dir, "new.png").exists()).isTrue()
    }

    /**
     * 공유 시트에 넘기는 주소가 실제로 만들어져야 하고(선언이 빠지면 공유할 때 앱이 죽는다),
     * 캐시의 share/ 밖 파일은 열어 주지 않아야 한다.
     *
     * FileProvider 는 설정을 정적으로 캐시해서, 테스트마다 임시 폴더가 바뀌면 두 번째 테스트가 첫 번째의
     * 폴더를 기억한다. 그래서 두 확인을 한 테스트 안에서 한다.
     */
    @Test
    fun `the file provider hands out shared cards and nothing else`() {
        val shareDir = File(context.cacheDir, "share")
        val file = ImageSharing.writePng(shareDir, "provider-check", bitmap())

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        assertThat(uri.scheme).isEqualTo("content")
        assertThat(uri.authority).isEqualTo("${context.packageName}.fileprovider")
        assertThat(uri.lastPathSegment).isEqualTo("provider-check.png")

        val outside = File(context.cacheDir, "secret.png").apply { writeText("x") }
        val result = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outside)
        }
        assertThat(result.isFailure).isTrue()
        file.delete()
        outside.delete()
    }
}
