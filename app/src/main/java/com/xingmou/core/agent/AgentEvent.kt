package com.xingmou.core.agent

import com.xingmou.core.domain.PlanStatus
import com.xingmou.core.domain.TrainingResult
import com.xingmou.core.model.Port
import com.xingmou.core.model.SupportLevel
import com.xingmou.data.db.PlanVersionEntity
import com.xingmou.data.db.ReviewRequestEntity

enum class AgentEventType {
    USER_MESSAGE, TRAINING_COMPLETED, CONSECUTIVE_FAILURES, RISK_DETECTED,
    RECORDS_THRESHOLD_REACHED, PARENT_OBSERVATION_ADDED, PLAN_REJECTED,
    REVIEW_APPROVED, SESSION_RESUMED
}

sealed interface AgentEvent {
    val eventId: String
    val runId: String?
    val childId: String?
    val occurredAt: Long
    val type: AgentEventType
}

data class UserMessageEvent(
    override val eventId: String, override val runId: String?, override val childId: String?,
    override val occurredAt: Long, val port: Port, val text: String
) : AgentEvent { override val type = AgentEventType.USER_MESSAGE }

data class TrainingCompletedEvent(
    override val eventId: String, override val runId: String, override val childId: String,
    override val occurredAt: Long, val domain: String, val currentDifficulty: Int,
    val currentSupportLevel: SupportLevel, val result: TrainingResult,
    val recentResults: List<TrainingResult> = emptyList(), val observationText: String = "",
    val riskFlags: List<String> = emptyList()
) : AgentEvent { override val type = AgentEventType.TRAINING_COMPLETED }

data class ConsecutiveFailuresEvent(
    override val eventId: String, override val runId: String, override val childId: String,
    override val occurredAt: Long, val count: Int
) : AgentEvent { override val type = AgentEventType.CONSECUTIVE_FAILURES }

data class RiskDetectedEvent(
    override val eventId: String, override val runId: String?, override val childId: String,
    override val occurredAt: Long, val text: String, val riskFlags: List<String> = emptyList()
) : AgentEvent { override val type = AgentEventType.RISK_DETECTED }

data class RecordsThresholdReachedEvent(
    override val eventId: String, override val runId: String?, override val childId: String,
    override val occurredAt: Long, val recordCount: Int
) : AgentEvent { override val type = AgentEventType.RECORDS_THRESHOLD_REACHED }

data class ParentObservationAddedEvent(
    override val eventId: String, override val runId: String?, override val childId: String,
    override val occurredAt: Long, val summary: String
) : AgentEvent { override val type = AgentEventType.PARENT_OBSERVATION_ADDED }

data class PlanRejectedEvent(
    override val eventId: String, override val runId: String?, override val childId: String,
    override val occurredAt: Long, val plan: PlanVersionEntity, val review: ReviewRequestEntity,
    val reviewerId: String, val reason: String
) : AgentEvent { override val type = AgentEventType.PLAN_REJECTED }

data class ReviewApprovedEvent(
    override val eventId: String, override val runId: String?, override val childId: String,
    override val occurredAt: Long, val plan: PlanVersionEntity, val review: ReviewRequestEntity,
    val reviewerId: String, val comment: String?, val activate: Boolean = false
) : AgentEvent { override val type = AgentEventType.REVIEW_APPROVED }

data class SessionResumedEvent(
    override val eventId: String, override val runId: String, override val childId: String?,
    override val occurredAt: Long, val previousState: AgentRunState
) : AgentEvent { override val type = AgentEventType.SESSION_RESUMED }
