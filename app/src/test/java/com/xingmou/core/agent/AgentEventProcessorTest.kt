package com.xingmou.core.agent

import com.xingmou.core.domain.TrainingResult
import com.xingmou.core.model.SupportLevel
import com.xingmou.data.db.AgentRunEntity
import com.xingmou.data.db.PlanVersionEntity
import com.xingmou.data.db.ReviewRequestEntity
import com.xingmou.data.db.ToolCallEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentEventProcessorTest {
    private val processor = AgentEventProcessor()

    @Test fun trainingCompletionAdjustsDifficultyAndPersistsRecord() {
        val success = TrainingResult("图片配对", true, true, 500, null, 0)
        val event = TrainingCompletedEvent(
            "e1", "run-1", "child-1", 100, "认知", 2, SupportLevel.L1,
            success, recentResults = listOf(success, success)
        )
        val decision = processor.process(event)
        assertEquals(AgentRunState.COMPLETED, decision.targetState)
        assertEquals(3, decision.nextDifficulty)
        assertEquals("L0", decision.nextSupportLevel)
        assertNotNull(decision.trainingRecord)
        assertEquals("TRAINING_COMPLETED", decision.event.eventType)
    }

    @Test fun consecutiveFailuresPauseAndRiskCreatesSafetyFlag() {
        val failure = TrainingResult("图片配对", false, false, 1200, "wrong", 2)
        val training = TrainingCompletedEvent(
            "e2", "run-2", "child-1", 101, "认知", 3, SupportLevel.L1,
            failure, recentResults = listOf(failure)
        )
        assertEquals(AgentRunState.PAUSED, processor.process(training).targetState)

        val risk = processor.process(RiskDetectedEvent("e3", "run-3", "child-1", 102, "孩子呼吸困难，电话13800138000"))
        assertEquals(AgentRunState.SAFETY_STOP, risk.targetState)
        assertNotNull(risk.safetyFlag)
        assertFalse(risk.safetyFlag!!.triggerText!!.contains("13800138000"))
    }

    @Test fun recordThresholdAndSessionResumeFollowRules() {
        val insufficient = processor.process(RecordsThresholdReachedEvent("e4", null, "child-1", 1, 2))
        assertEquals(AgentRunState.COMPLETED, insufficient.targetState)
        val ready = processor.process(RecordsThresholdReachedEvent("e5", null, "child-1", 2, 3))
        assertEquals(AgentRunState.PLANNING, ready.targetState)
        val resumed = processor.process(SessionResumedEvent("e6", "run-6", "child-1", 3, AgentRunState.PAUSED))
        assertEquals(AgentRunState.PLANNING, resumed.targetState)
    }

    @Test fun professionalApprovalRequiresTwoStateTransitions() {
        val draft = plan("draft")
        val confirmed = processor.process(ReviewApprovedEvent("e7", "run-7", "child-1", 200, draft, review(), "professional-1", "确认草案"))
        assertEquals("confirmed", confirmed.updatedPlan?.status)
        assertTrue(confirmed.reviewRequired)

        val active = processor.process(ReviewApprovedEvent("e8", "run-8", "child-1", 201, confirmed.updatedPlan!!, review(), "professional-1", "签署生效", activate = true))
        assertEquals("active", active.updatedPlan?.status)
        assertFalse(active.reviewRequired)

        val skipped = processor.process(ReviewApprovedEvent("e9", "run-9", "child-1", 202, draft, review(), "professional-1", "直接激活", activate = true))
        assertEquals(AgentRunState.WAITING_APPROVAL, skipped.targetState)
        assertEquals(null, skipped.updatedPlan)
    }

    @Test fun rejectionNeedsReasonAndCoordinatorSavesArtifacts() = runBlocking {
        val blank = processor.process(PlanRejectedEvent("e10", null, "child-1", 300, plan("draft"), review(), "professional-1", ""))
        assertEquals(AgentRunState.WAITING_APPROVAL, blank.targetState)

        val saved = mutableListOf<AgentEventDecision>()
        val coordinator = AgentEventCoordinator(processor, object : AgentEventStore {
            override suspend fun save(decision: AgentEventDecision) { saved += decision }
        })
        val rejected = coordinator.handle(PlanRejectedEvent("e11", null, "child-1", 301, plan("draft"), review(), "professional-1", "目标需调整"))
        assertEquals("rejected", rejected.updatedPlan?.status)
        assertEquals("rejected", rejected.updatedReview?.status)
        assertEquals(1, saved.size)
    }

    @Test fun runtimeRestoresPausedRunWithoutRepeatingEffects() {
        val executed = mutableListOf<String>()
        val runtime = AgentRuntime(handlers = mapOf("training_record" to { executed += it.idempotencyKey; "saved-again" }))
        val previousCall = ToolCallEntity(
            "call-old", "run-r", "training_record", "{}", "ALLOWED", "succeeded",
            "record-key", "saved", 2, 2
        )
        val restored = runtime.restore(
            AgentRunEntity("run-r", "training", "CHILD", "child-1", "PAUSED", "PAUSED", 1, null, null),
            toolCalls = listOf(previousCall)
        )
        assertEquals(AgentRunState.PAUSED, restored.state)
        assertTrue(runtime.transition("run-r", AgentRunState.EXECUTING_TOOL).accepted)
        val duplicate = runtime.executeTool(
            ToolCallRequest("run-r", "call-new", "training_record", com.xingmou.core.model.Port.CHILD, "{}", "record-key")
        )
        assertEquals("duplicate", duplicate.executionStatus)
        assertEquals("saved", duplicate.resultSummary)
        assertTrue(executed.isEmpty())
    }

    private fun plan(status: String) = PlanVersionEntity("plan-1", "child-1", 1, status, true, "{}", 1, 1)

    private fun review() = ReviewRequestEntity(
        "review-1", "child-1", "run", "plan", "plan-1", "{}", null,
        "pending", null, null, 1, null
    )
}
