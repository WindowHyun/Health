package com.windowhyun.health.service

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTextToSpeech

/** 러닝 음성 안내: 음성 엔진 초기화가 실패해도 다음 러닝에서 다시 살아난다. */
@RunWith(RobolectricTestRunner::class)
class RunVoiceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun lastEngine(): TextToSpeech = ShadowTextToSpeech.getLastTextToSpeechInstance()

    /** 초기화가 실패한 죽은 엔진을 붙들고 있으면 다시는 말하지 못한다. */
    @Test
    fun `after a failed init the next speech creates a new engine and the dead one is shut down`() {
        val voice = AndroidRunVoice(context)

        voice.speak("1킬로미터")
        val first = lastEngine()
        shadowOf(first).onInitListener.onInit(TextToSpeech.ERROR)

        assertThat(shadowOf(first).isShutdown).isTrue()

        voice.speak("2킬로미터")
        assertThat(lastEngine()).isNotSameInstanceAs(first)
    }

    /** 초기화에 실패해 쌓인 말이 있어도, 러닝이 끝나 해제를 부르면 미루지 않고 엔진을 닫는다. */
    @Test
    fun `releasing after a failed init does not wait forever`() {
        val voice = AndroidRunVoice(context)
        voice.speak("1킬로미터")
        val engine = lastEngine()
        shadowOf(engine).onInitListener.onInit(TextToSpeech.ERROR)

        voice.release()

        assertThat(shadowOf(engine).isShutdown).isTrue()
    }

    @Test
    fun `a successful init speaks what was waiting`() {
        val voice = AndroidRunVoice(context)
        voice.speak("1킬로미터")
        val engine = lastEngine()
        shadowOf(engine).onInitListener.onInit(TextToSpeech.SUCCESS)

        assertThat(shadowOf(engine).lastSpokenText).isEqualTo("1킬로미터")
    }
}
