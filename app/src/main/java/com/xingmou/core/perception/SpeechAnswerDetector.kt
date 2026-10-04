package com.xingmou.core.perception

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.xingmou.data.catalog.ExpectedSpeech
import java.util.Locale

/**
 * 系统语音识别薄封装。
 * onSpeech(text) 每段识别结果回调一次（含 partial）；
 * 由 ViewModel 结合 ExpectedSpeech 决定「开口即完成 / 关键词命中」。
 * onUnavailable() 表示设备无识别服务或发生不可恢复错误，调用方应转手动兜底。
 */
class SpeechAnswerDetector(
    private val context: Context,
    private val onSpeech: (String) -> Unit,
    private val onUnavailable: () -> Unit
) : RecognitionListener {

    private var recognizer: SpeechRecognizer? = null
    private var failed = false

    fun isAvailable(): Boolean =
        runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    fun start() {
        if (!isAvailable()) {
            onUnavailable()
            return
        }
        failed = false
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(this) }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { recognizer?.startListening(intent) }.onFailure {
            Log.w(TAG, "startListening failed", it); onUnavailable()
        }
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    override fun onResults(results: Bundle?) = emit(results)
    override fun onPartialResults(partialResults: Bundle?) = emit(partialResults)

    private fun emit(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        if (text.isNotBlank()) onSpeech(text)
    }

    override fun onError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Unit // 继续听，直到题目超时
            else -> {
                if (!failed) {
                    failed = true
                    Log.w(TAG, "speech recognizer error=$error")
                    onUnavailable()
                }
            }
        }
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    companion object {
        private const val TAG = "SpeechAnswerDetector"

        /** ANY：有效文本 ≥2 字符即开口；KEYWORD：返回命中词，说了没命中返回空串标记。 */
        fun evaluate(text: String, expected: ExpectedSpeech): SpeechHit {
            val normalized = KeywordMatcher.normalize(text)
            if (normalized.length < 2) return SpeechHit.NONE
            return when (expected.mode) {
                com.xingmou.data.catalog.SpeechMode.ANY -> SpeechHit.SPOKEN(null)
                com.xingmou.data.catalog.SpeechMode.KEYWORD -> {
                    val kw = KeywordMatcher.matches(text, expected.keywords)
                    if (kw != null) SpeechHit.SPOKEN(kw) else SpeechHit.SPOKEN(null)
                }
            }
        }
    }
}

/** 语音判定结果。NONE=无效；SPOKEN(matchedKeyword) 开口即完成，meta 非 null 为命中目标词。 */
sealed class SpeechHit {
    data object NONE : SpeechHit()
    data class SPOKEN(val matchedKeyword: String?) : SpeechHit()
}
