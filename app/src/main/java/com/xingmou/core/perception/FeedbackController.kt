package com.xingmou.core.perception

import android.util.Log

/**
 * 分层反馈控制器。
 * L0: 静默（正常状态，无干预）
 * L1: 轻柔语音提示（分心/轻微负面情绪，30s 冷却）
 * L2: 强提醒（持续分心/负面情绪升级，60s 冷却）
 * L3: 暂停任务（情绪崩溃 meltdown，立即触发）
 */
class FeedbackController(
    private val onFeedback: (Feedback) -> Unit
) {
    data class Feedback(
        val level: Level,
        val message: String,
        val emotion: Emotion,
        val focusLevel: FocusLevel
    ) {
        enum class Level { L0_SILENT, L1_GENTLE, L2_STRONG, L3_PAUSE }
    }

    companion object {
        private const val TAG = "FeedbackController"
        private const val L1_COOLDOWN_MS = 30_000L
        private const val L2_COOLDOWN_MS = 60_000L
        private const val DISTRACTED_DURATION_MS = 15_000L
        private const val NEGATIVE_DURATION_MS = 20_000L
    }

    private var lastL1Time = 0L
    private var lastL2Time = 0L
    private var distractedSince = 0L
    private var negativeSince = 0L
    private var meltdownActive = false

    fun reset() {
        lastL1Time = 0L
        lastL2Time = 0L
        distractedSince = 0L
        negativeSince = 0L
        meltdownActive = false
    }

    fun onState(state: PerceptionState, now: Long = System.currentTimeMillis()) {
        // L3: 情绪崩溃 → 立即暂停
        if (state.isMeltdown) {
            meltdownActive = true
            emit(Feedback.Level.L3_PAUSE, "检测到情绪崩溃，暂停任务", state)
            return
        }
        if (meltdownActive && state.emotion != Emotion.CRYING) {
            meltdownActive = false
        }

        // L2: 持续分心或持续负面情绪
        if (state.focusLevel == FocusLevel.DISTRACTED) {
            if (distractedSince == 0L) distractedSince = now
            if (now - distractedSince >= DISTRACTED_DURATION_MS && now - lastL2Time >= L2_COOLDOWN_MS) {
                lastL2Time = now
                emit(Feedback.Level.L2_STRONG, "注意力不集中，休息一下吧", state)
                distractedSince = 0L
                return
            }
        } else {
            distractedSince = 0L
        }

        val isNegative = state.emotion in setOf(Emotion.SAD, Emotion.ANGRY, Emotion.FEARFUL, Emotion.CRYING)
        if (isNegative) {
            if (negativeSince == 0L) negativeSince = now
            if (now - negativeSince >= NEGATIVE_DURATION_MS && now - lastL2Time >= L2_COOLDOWN_MS) {
                lastL2Time = now
                emit(Feedback.Level.L2_STRONG, "看起来心情不太好，先停下来好吗", state)
                negativeSince = 0L
                return
            }
        } else {
            negativeSince = 0L
        }

        // L1: 轻微分心或轻微负面
        if (state.focusLevel == FocusLevel.DISTRACTED || isNegative) {
            if (now - lastL1Time >= L1_COOLDOWN_MS) {
                lastL1Time = now
                emit(Feedback.Level.L1_GENTLE, "我们继续吧", state)
                return
            }
        }

        // L0: 正常
        emit(Feedback.Level.L0_SILENT, "", state)
    }

    private fun emit(level: Feedback.Level, message: String, state: PerceptionState) {
        if (level != Feedback.Level.L0_SILENT) {
            Log.d(TAG, "Feedback ${level.name}: $message (emotion=${state.emotion}, focus=${state.focusLevel})")
        }
        onFeedback(Feedback(level, message, state.emotion, state.focusLevel))
    }
}
