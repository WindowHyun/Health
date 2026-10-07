package com.windowhyun.health.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** 러닝 중 문장을 소리 내어 읽는 일. 서비스가 이것만 알면 되게 한다(테스트에서는 가짜로 바꾼다). */
interface RunVoice {
    /** 음성 엔진을 준비한다. 여러 번 불러도 한 번만 만든다. */
    fun prepare()

    /** [text] 를 읽는다. 엔진이 준비되기 전이면 준비되는 대로 읽는다. */
    fun speak(text: String)

    /** 엔진을 내린다. 읽고 있는 말이 있으면 끝까지 읽은 뒤에 내린다(목표 달성 안내가 잘리지 않게). */
    fun release()
}

/**
 * 안드로이드 TextToSpeech 로 읽는다.
 *
 * - 음악이나 팟캐스트를 듣고 있으면 소리를 줄였다가(ducking) 읽은 뒤 되돌린다.
 * - 한국어 음성이 없으면 기기 기본 언어로 읽는다. 엔진이 아예 없으면 조용히 포기한다(진동 안내는 그대로).
 */
@Singleton
class AndroidRunVoice @Inject constructor(
    @ApplicationContext private val context: Context,
) : RunVoice {

    private var engine: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<String>()
    private var activeUtterances = 0
    private var releaseWhenIdle = false
    private var focusRequest: AudioFocusRequest? = null
    private val audioManager get() = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    @Synchronized
    override fun prepare() {
        releaseWhenIdle = false
        if (engine != null) return
        engine = runCatching {
            TextToSpeech(context) { status -> onInit(status) }
        }.onFailure { Log.w(TAG, "TextToSpeech could not be created", it) }.getOrNull()
    }

    @Synchronized
    private fun onInit(status: Int) {
        val tts = engine ?: return
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TextToSpeech init failed: $status")
            return
        }
        val korean = tts.setLanguage(Locale.KOREAN)
        if (korean == TextToSpeech.LANG_MISSING_DATA || korean == TextToSpeech.LANG_NOT_SUPPORTED) {
            // 한국어 음성이 없으면 기기 기본 언어로 읽는다. 어색해도 아무 소리도 없는 것보다 낫다.
            tts.setLanguage(Locale.getDefault())
        }
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = finished()

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finished()
            },
        )
        ready = true
        while (pending.isNotEmpty()) speakNow(tts, pending.removeFirst())
    }

    @Synchronized
    override fun speak(text: String) {
        if (engine == null) prepare()
        val tts = engine ?: return
        if (!ready) {
            // 준비되는 동안 쌓이는 말은 몇 개만 둔다. 오래된 구간 안내를 한꺼번에 읽으면 오히려 방해다.
            if (pending.size >= MAX_PENDING) pending.removeFirst()
            pending.addLast(text)
            return
        }
        speakNow(tts, text)
    }

    private fun speakNow(tts: TextToSpeech, text: String) {
        requestFocus()
        activeUtterances++
        val result = tts.speak(text, TextToSpeech.QUEUE_ADD, null, "run-${System.nanoTime()}")
        if (result != TextToSpeech.SUCCESS) finished()
    }

    @Synchronized
    private fun finished() {
        activeUtterances = (activeUtterances - 1).coerceAtLeast(0)
        if (activeUtterances == 0) {
            abandonFocus()
            if (releaseWhenIdle) releaseNow()
        }
    }

    private fun requestFocus() {
        val manager = audioManager ?: return
        if (focusRequest != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .build()
        if (manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focusRequest = request
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        audioManager?.abandonAudioFocusRequest(request)
        focusRequest = null
    }

    @Synchronized
    override fun release() {
        if (activeUtterances > 0 || pending.isNotEmpty()) {
            releaseWhenIdle = true
            return
        }
        releaseNow()
    }

    private fun releaseNow() {
        releaseWhenIdle = false
        runCatching {
            engine?.stop()
            engine?.shutdown()
        }
        engine = null
        ready = false
        pending.clear()
        activeUtterances = 0
        abandonFocus()
    }

    private companion object {
        const val TAG = "RunVoice"
        const val MAX_PENDING = 3
    }
}
