package com.xingmou

import com.xingmou.core.agent.AgentEventDecision
import com.xingmou.core.agent.AgentRunState
import com.xingmou.core.domain.PlanStatus
import com.xingmou.core.model.SupportLevel
import com.xingmou.data.db.AgentEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStateReducerTest {
    @Test
    fun completedDecisionUpdatesDifficultyAndSupport() {
        val state = ChildUiState(difficulty = 1, supportLevel = SupportLevel.L2, isWorking = true)

        val updated = state.apply(decision(AgentRunState.COMPLETED, nextDifficulty = 2, nextSupport = "L1"))

        assertEquals(2, updated.difficulty)
        assertEquals(SupportLevel.L1, updated.supportLevel)
        assertFalse(updated.isPaused)
        assertFalse(updated.isSafetyStopped)
        assertFalse(updated.isWorking)
    }

    @Test
    fun pausedAndSafetyStopAreVisibleStates() {
        val paused = ChildUiState().apply(decision(AgentRunState.PAUSED))
        val stopped = ChildUiState().apply(decision(AgentRunState.SAFETY_STOP))

        assertTrue(paused.isPaused)
        assertTrue(stopped.isSafetyStopped)
    }

    @Test
    fun professionalPlanRequiresTwoStepActivation() {
        val directActivation = reducePlanStatus(PlanStatus.DRAFT, PlanUiAction.ACTIVATE, "已核对")
        val confirmed = reducePlanStatus(PlanStatus.DRAFT, PlanUiAction.CONFIRM, "已核对")
        val active = reducePlanStatus(confirmed, PlanUiAction.ACTIVATE, "签署生效")

        assertEquals(PlanStatus.DRAFT, directActivation)
        assertEquals(PlanStatus.CONFIRMED, confirmed)
        assertEquals(PlanStatus.ACTIVE, active)
    }

    @Test
    fun rejectionRequiresComment() {
        val unchanged = reducePlanStatus(PlanStatus.DRAFT, PlanUiAction.REJECT, "")
        val rejected = reducePlanStatus(PlanStatus.DRAFT, PlanUiAction.REJECT, "目标需要进一步拆分")

        assertEquals(PlanStatus.DRAFT, unchanged)
        assertEquals(PlanStatus.REJECTED, rejected)
    }

    private fun decision(
        state: AgentRunState,
        nextDifficulty: Int? = null,
        nextSupport: String? = null
    ) = AgentEventDecision(
        event = AgentEventEntity(
            eventId = "event-1",
            runId = "run-1",
            childId = "child-1",
            eventType = "TRAINING_COMPLETED",
            payloadSummary = "test",
            status = "processed",
            createdAt = 0L,
            processedAt = 0L
        ),
        targetState = state,
        message = "状态已更新",
        nextDifficulty = nextDifficulty,
        nextSupportLevel = nextSupport
    )
}
