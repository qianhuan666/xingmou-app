package com.xingmou.ui.child

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

/** 本地语音控制器：只朗读已经通过规则层的儿童端短句，不参与决策。 */
class ChildSpeechController(context: Context) : TextToSpeech.OnInitListener {
    private val textToSpeech = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var speechRate = 1.0f
    private var speechVolume = 1.0f
    private var pendingText: String? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.SIMPLIFIED_CHINESE)
            ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            textToSpeech.setSpeechRate(speechRate)
            if (ready) pendingText?.let(::speak)
        } else {
            android.util.Log.w("XingmouTTS", "init failed status=$status")
        }
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.75f, 1.25f)
        textToSpeech.setSpeechRate(speechRate)
    }

    fun setSpeechVolume(volume: Float) {
        speechVolume = volume.coerceIn(0.5f, 1.0f)
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) {
            pendingText = text
            return
        }
        pendingText = null
        val parameters = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, speechVolume)
        }
        val utterance = text.take(80)
        textToSpeech.speak(utterance, TextToSpeech.QUEUE_FLUSH, parameters, "xingmou-child")
    }

    /**
     * 排队播报（不打断正在朗读的题目），用于感知提醒等次要语音。
     * TTS 未就绪时直接丢弃——提醒类语音过期即失效。
     */
    fun speakQueued(text: String) {
        if (text.isBlank() || !ready) return
        val parameters = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, speechVolume)
        }
        val utterance = text.take(60)
        textToSpeech.speak(utterance, TextToSpeech.QUEUE_ADD, parameters, "xingmou-perception")
    }

    fun stop() {
        pendingText = null
        textToSpeech.stop()
    }

    fun shutdown() {
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}
