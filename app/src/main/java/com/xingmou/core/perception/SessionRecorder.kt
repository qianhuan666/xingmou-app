package com.xingmou.core.perception

import android.util.Log
import com.xingmou.data.db.PerceptionSessionDao
import com.xingmou.data.db.PerceptionSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import org.json.JSONObject
import java.util.UUID

/**
 * 会话记录器：累积统计 + 会话结束时写入 DB。
 * 只存结构化 JSON 快照，不存原始视频帧。
 */
class SessionRecorder(
    private val dao: PerceptionSessionDao,
    private val childId: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionId = UUID.randomUUID().toString()
    private val startedAt = System.currentTimeMillis()
    private val emotionCounts = mutableMapOf<Emotion, Int>()
    private val focusCounts = mutableMapOf<FocusLevel, Int>()
    private var frameCount = 0
    private var feedbackCount = 0
    private var meltdownCount = 0

    fun onState(state: PerceptionState) {
        frameCount++
        emotionCounts[state.emotion] = (emotionCounts[state.emotion] ?: 0) + 1
        focusCounts[state.focusLevel] = (focusCounts[state.focusLevel] ?: 0) + 1
        if (state.isMeltdown) meltdownCount++
    }

    fun onFeedback(feedback: FeedbackController.Feedback) {
        if (feedback.level != FeedbackController.Feedback.Level.L0_SILENT) feedbackCount++
    }

    fun finish() {
        val now = System.currentTimeMillis()
        val emotionJson = JSONObject().apply {
            emotionCounts.forEach { put(it.key.name, it.value) }
        }.toString()
        val focusJson = JSONObject().apply {
            focusCounts.forEach { put(it.key.name, it.value) }
        }.toString()
        val report = JSONObject().apply {
            put("totalFrames", frameCount)
            put("emotion", emotionJson)
            put("focus", focusJson)
            put("feedbackCount", feedbackCount)
            put("meltdownCount", meltdownCount)
            put("durationMs", now - startedAt)
        }.toString()

        scope.launch {
            dao.upsert(
                PerceptionSessionEntity(
                    sessionId = sessionId,
                    childId = childId,
                    startedAt = startedAt,
                    finishedAt = now,
                    frameCount = frameCount,
                    emotionSummaryJson = emotionJson,
                    focusSummaryJson = focusJson,
                    feedbackCount = feedbackCount,
                    meltdownCount = meltdownCount,
                    finalReportJson = report
                )
            )
            Log.d(TAG, "Session $sessionId saved: $frameCount frames, $feedbackCount feedbacks, $meltdownCount meltdowns")
        }
    }

    companion object {
        private const val TAG = "SessionRecorder"
    }
}
