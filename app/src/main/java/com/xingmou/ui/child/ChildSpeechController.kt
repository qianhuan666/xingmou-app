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
        textToSpeech.speak(text.take(80), TextToSpeech.QUEUE_FLUSH, parameters, "xingmou-child")
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
