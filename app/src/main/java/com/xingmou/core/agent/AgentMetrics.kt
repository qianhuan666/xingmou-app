package com.xingmou.core.agent

import com.xingmou.data.db.AgentEventEntity
import com.xingmou.data.db.AgentRunEntity
import com.xingmou.data.db.AgentStepEntity
import com.xingmou.data.db.ReviewRequestEntity
import com.xingmou.data.db.ToolCallEntity
import com.xingmou.data.db.QizhiDatabase

/** 分母为零返回 null，避免把“未采样”误报为 0% 合格。 */
data class AgentMetrics(
    val runCount: Int,
    val taskCompletionRate: Double?,
    val toolSuccessRate: Double?,
    val unauthorizedBlockRate: Double?,
    val riskBlockRate: Double?,
    val unsupportedJudgmentRate: Double?,
    val jsonValidationFailureRate: Double?,
    val fallbackRate: Double?,
    val averageSteps: Double?,
    val averageResponseMs: Double?,
    val reviewAcceptanceRate: Double?,
    val reviewModificationRate: Double?,
    val userInterruptionRate: Double?
)

object AgentMetricsCalculator {
    fun calculate(
        runs: List<AgentRunEntity>,
        steps: List<AgentStepEntity>,
        calls: List<ToolCallEntity>,
        events: List<AgentEventEntity>,
        reviews: List<ReviewRequestEntity>
    ): AgentMetrics {
        val runIds = runs.map { it.runId }.toSet()
        val scopedSteps = steps.filter { it.runId in runIds }
        val scopedCalls = calls.filter { it.runId in runIds }
        val scopedEvents = events.filter { it.runId in runIds }
        val resolvedReviews = reviews.filter { it.runId in runIds && it.status != "pending" }
        val completedDurations = runs.mapNotNull { run ->
            run.finishedAt?.let { (it - run.startedAt).takeIf { duration -> duration >= 0 } }
        }
        return AgentMetrics(
            runCount = runs.size,
            taskCompletionRate = rate(runs.count { it.status == "COMPLETED" }, runs.size),
            toolSuccessRate = rate(scopedCalls.count { it.executionStatus == "succeeded" }, scopedCalls.size),
            unauthorizedBlockRate = rate(scopedCalls.count { it.authorizationStatus == "DENIED" }, scopedCalls.size),
            riskBlockRate = rate(runs.count { it.status == "SAFETY_STOP" }, runs.size),
            // 缺少逐条 claim 的人工依据标注，不允许用空来源字段推断无依据判断率。
            unsupportedJudgmentRate = null,
            jsonValidationFailureRate = rate(scopedEvents.count { it.eventType == "JSON_VALIDATION_FAILED" }, runs.size),
            fallbackRate = rate(scopedEvents.count { it.eventType == "FALLBACK_TRIGGERED" }, runs.size),
            averageSteps = average(scopedSteps.size.toLong(), runs.size),
            averageResponseMs = average(completedDurations.sum(), completedDurations.size),
            reviewAcceptanceRate = rate(resolvedReviews.count { it.status == "approved" }, resolvedReviews.size),
            reviewModificationRate = rate(resolvedReviews.count { !it.diffJson.isNullOrBlank() && it.diffJson != "[]" }, resolvedReviews.size),
            userInterruptionRate = rate(runs.count { it.status == "CANCELLED" }, runs.size)
        )
    }

    private fun rate(numerator: Int, denominator: Int): Double? =
        if (denominator == 0) null else numerator.toDouble() / denominator

    private fun average(total: Long, count: Int): Double? =
        if (count == 0) null else total.toDouble() / count
}

class AgentMetricsRepository(private val database: QizhiDatabase) {
    suspend fun forChild(childId: String): AgentMetrics {
        require(childId.isNotBlank())
        val dao = database.agentDao()
        return AgentMetricsCalculator.calculate(
            dao.runsForChild(childId), dao.stepsForChild(childId), dao.toolCallsForChild(childId),
            dao.eventsForChild(childId), dao.reviewsForChild(childId)
        )
    }
}
