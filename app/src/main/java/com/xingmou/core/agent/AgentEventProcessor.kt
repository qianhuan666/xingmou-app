package com.xingmou.core.agent

import androidx.room.withTransaction
import com.xingmou.core.domain.PlanActor
import com.xingmou.core.domain.PlanStateMachine
import com.xingmou.core.domain.PlanStatus
import com.xingmou.core.domain.TrainingEngine
import com.xingmou.core.llm.PromptBuilder
import com.xingmou.core.model.RiskLevel
import com.xingmou.core.rule.RiskEngine
import com.xingmou.core.safety.SafeResponses
import com.xingmou.data.db.AgentEventEntity
import com.xingmou.data.db.PlanVersionEntity
import com.xingmou.data.db.QizhiDatabase
import com.xingmou.data.db.ReviewRequestEntity
import com.xingmou.data.db.SafetyFlagEntity
import com.xingmou.data.db.TrainingRecordEntity

data class AgentEventDecision(
    val event: AgentEventEntity,
    val targetState: AgentRunState,
    val message: String,
    val trainingRecord: TrainingRecordEntity? = null,
    val safetyFlag: SafetyFlagEntity? = null,
    val updatedPlan: PlanVersionEntity? = null,
    val updatedReview: ReviewRequestEntity? = null,
    val nextDifficulty: Int? = null,
    val nextSupportLevel: String? = null,
    val reviewRequired: Boolean = false
)

class AgentEventProcessor(
    private val trainingEngine: TrainingEngine = TrainingEngine(),
    private val planStateMachine: PlanStateMachine = PlanStateMachine()
) {
    fun process(event: AgentEvent, processedAt: Long = event.occurredAt): AgentEventDecision = when (event) {
        is TrainingCompletedEvent -> processTraining(event, processedAt)
        is ConsecutiveFailuresEvent -> decision(event, processedAt,
            if (event.count >= 2) AgentRunState.PAUSED else AgentRunState.COMPLETED,
            if (event.count >= 2) SafeResponses.PAUSE_CHILD.joinToString(" ") else "继续保持当前支持。")
        is RiskDetectedEvent -> processRisk(event, processedAt)
        is RecordsThresholdReachedEvent -> decision(event, processedAt,
            if (event.recordCount >= 3) AgentRunState.PLANNING else AgentRunState.COMPLETED,
            if (event.recordCount >= 3) "记录已达到分析阈值，可以生成过程分析。" else SafeResponses.INSUFFICIENT_DATA)
        is ParentObservationAddedEvent -> decision(event, processedAt, AgentRunState.PLANNING, "已加入家长观察，等待受控分析。")
        is UserMessageEvent -> decision(event, processedAt, AgentRunState.ROUTING, "用户消息已进入风险与权限路由。")
        is SessionResumedEvent -> decision(event, processedAt,
            if (event.previousState == AgentRunState.PAUSED) AgentRunState.PLANNING else event.previousState,
            if (event.previousState == AgentRunState.PAUSED) "会话已恢复，从低负担步骤继续。" else "当前状态不支持自动恢复。")
        is ReviewApprovedEvent -> processApproval(event, processedAt)
        is PlanRejectedEvent -> processRejection(event, processedAt)
    }

    private fun processTraining(event: TrainingCompletedEvent, processedAt: Long): AgentEventDecision {
        val failures = (event.recentResults + event.result).takeLast(3).count { !it.correct }
        val risk = RiskEngine.assessDetailed(event.observationText, event.riskFlags, failures)
        val results = event.recentResults + event.result
        val nextDifficulty = trainingEngine.nextDifficulty(event.currentDifficulty, results, risk.level == RiskLevel.SAFETY_STOP)
        val nextSupport = trainingEngine.nextSupportLevel(event.currentSupportLevel, results, risk.level == RiskLevel.SAFETY_STOP)
        val record = trainingEngine.toRecord(event.childId, event.domain, event.currentDifficulty, event.currentSupportLevel, event.result, event.occurredAt)
        val safetyFlag = if (risk.level == RiskLevel.SAFETY_STOP) safetyFlag(event, risk.matchedTypes.firstOrNull() ?: "urgent", event.observationText) else null
        val state = when (risk.level) {
            RiskLevel.SAFETY_STOP -> AgentRunState.SAFETY_STOP
            RiskLevel.PAUSE -> AgentRunState.PAUSED
            RiskLevel.NONE -> AgentRunState.COMPLETED
        }
        val message = when (risk.level) {
            RiskLevel.SAFETY_STOP -> SafeResponses.SAFETY_STOP_CHILD.joinToString(" ")
            RiskLevel.PAUSE -> SafeResponses.PAUSE_CHILD.joinToString(" ")
            RiskLevel.NONE -> if (event.result.correct) "完成啦，我们休息一下。" else "没关系，我们下次再试。"
        }
        return AgentEventDecision(toEntity(event, "processed", processedAt), state, message, record, safetyFlag,
            nextDifficulty = nextDifficulty, nextSupportLevel = nextSupport.name)
    }

    private fun processRisk(event: RiskDetectedEvent, processedAt: Long): AgentEventDecision {
        val risk = RiskEngine.assessDetailed(event.text, event.riskFlags, 0)
        val state = when (risk.level) {
            RiskLevel.SAFETY_STOP -> AgentRunState.SAFETY_STOP
            RiskLevel.PAUSE -> AgentRunState.PAUSED
            RiskLevel.NONE -> AgentRunState.COMPLETED
        }
        return AgentEventDecision(
            toEntity(event, "processed", processedAt), state,
            when (risk.level) {
                RiskLevel.SAFETY_STOP -> SafeResponses.SAFETY_STOP_PARENT
                RiskLevel.PAUSE -> SafeResponses.PAUSE_PARENT
                RiskLevel.NONE -> "未检测到需要暂停的风险信号。"
            },
            safetyFlag = if (risk.level == RiskLevel.SAFETY_STOP) safetyFlag(event, risk.matchedTypes.firstOrNull() ?: "urgent", event.text) else null
        )
    }

    private fun processApproval(event: ReviewApprovedEvent, processedAt: Long): AgentEventDecision {
        val current = planStatus(event.plan.status)
        val target = if (event.activate) PlanStatus.ACTIVE else PlanStatus.CONFIRMED
        val transition = planStateMachine.transition(current, target, PlanActor.PROFESSIONAL, event.comment)
        val accepted = transition.accepted
        return AgentEventDecision(
            toEntity(event, if (accepted) "processed" else "rejected", processedAt),
            if (accepted) AgentRunState.COMPLETED else AgentRunState.WAITING_APPROVAL,
            transition.reason ?: if (accepted) "专业审核已生效。" else "审核状态转换被拒绝。",
            updatedPlan = if (accepted) event.plan.copy(status = target.name.lowercase(), reviewRequired = target != PlanStatus.ACTIVE, updatedAt = processedAt) else null,
            updatedReview = event.review.copy(status = if (accepted) "approved" else "pending", reviewerId = event.reviewerId,
                reviewerComment = event.comment, resolvedAt = if (accepted) processedAt else null),
            reviewRequired = !accepted || target != PlanStatus.ACTIVE
        )
    }

    private fun processRejection(event: PlanRejectedEvent, processedAt: Long): AgentEventDecision {
        val transition = planStateMachine.transition(planStatus(event.plan.status), PlanStatus.REJECTED, PlanActor.PROFESSIONAL, event.reason)
        return AgentEventDecision(
            toEntity(event, if (transition.accepted) "processed" else "rejected", processedAt),
            if (transition.accepted) AgentRunState.COMPLETED else AgentRunState.WAITING_APPROVAL,
            transition.reason ?: "方案已退回。",
            updatedPlan = if (transition.accepted) event.plan.copy(status = "rejected", reviewRequired = true, updatedAt = processedAt) else null,
            updatedReview = event.review.copy(status = if (transition.accepted) "rejected" else "pending", reviewerId = event.reviewerId,
                reviewerComment = event.reason, resolvedAt = if (transition.accepted) processedAt else null),
            reviewRequired = true
        )
    }

    private fun decision(event: AgentEvent, processedAt: Long, state: AgentRunState, message: String) =
        AgentEventDecision(toEntity(event, "processed", processedAt), state, message)

    private fun safetyFlag(event: AgentEvent, riskType: String, trigger: String) = SafetyFlagEntity(
        flagId = "flag-${event.eventId}", childId = requireNotNull(event.childId), riskType = riskType,
        level = "SAFETY_STOP", status = "active", triggerText = PromptBuilder.sanitize(trigger),
        actionTaken = "停止训练并转人工/急救指引", createdAt = event.occurredAt, resolvedAt = null
    )

    private fun toEntity(event: AgentEvent, status: String, processedAt: Long) = AgentEventEntity(
        event.eventId, event.runId, event.childId, event.type.name, PromptBuilder.sanitize(summary(event)),
        status, event.occurredAt, processedAt
    )

    private fun summary(event: AgentEvent): String = when (event) {
        is UserMessageEvent -> event.text
        is TrainingCompletedEvent -> "${event.domain}/${event.result.taskId}/correct=${event.result.correct}"
        is ConsecutiveFailuresEvent -> "count=${event.count}"
        is RiskDetectedEvent -> event.text
        is RecordsThresholdReachedEvent -> "recordCount=${event.recordCount}"
        is ParentObservationAddedEvent -> event.summary
        is PlanRejectedEvent -> "plan=${event.plan.planId};reason=${event.reason}"
        is ReviewApprovedEvent -> "plan=${event.plan.planId};activate=${event.activate}"
        is SessionResumedEvent -> "previousState=${event.previousState}"
    }

    private fun planStatus(status: String): PlanStatus = runCatching { PlanStatus.valueOf(status.uppercase()) }.getOrDefault(PlanStatus.DRAFT)
}

interface AgentEventStore {
    suspend fun save(decision: AgentEventDecision)
}

class RoomAgentEventStore(private val database: QizhiDatabase) : AgentEventStore {
    override suspend fun save(decision: AgentEventDecision) {
        database.withTransaction {
            database.agentDao().upsertEvent(decision.event)
            decision.trainingRecord?.let { database.trainingRecordDao().insert(it) }
            decision.safetyFlag?.let { database.safetyFlagDao().upsert(it) }
            decision.updatedPlan?.let { database.planDao().upsert(it) }
            decision.updatedReview?.let { database.agentDao().upsertReview(it) }
        }
    }
}

class AgentEventCoordinator(
    private val processor: AgentEventProcessor,
    private val store: AgentEventStore
) {
    suspend fun handle(event: AgentEvent, processedAt: Long = event.occurredAt): AgentEventDecision =
        processor.process(event, processedAt).also { store.save(it) }
}
