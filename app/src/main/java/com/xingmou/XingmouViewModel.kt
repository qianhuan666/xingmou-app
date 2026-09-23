package com.xingmou

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xingmou.core.agent.AgentContextInput
import com.xingmou.core.agent.AgentEventCoordinator
import com.xingmou.core.agent.AgentEventProcessor
import com.xingmou.core.agent.AgentOrchestrationRequest
import com.xingmou.core.agent.AgentOrchestrator
import com.xingmou.core.agent.ConsecutiveFailuresEvent
import com.xingmou.core.agent.ModelGateway
import com.xingmou.core.agent.ParentObservationAddedEvent
import com.xingmou.core.agent.PlanRejectedEvent
import com.xingmou.core.agent.ReviewApprovedEvent
import com.xingmou.core.agent.RiskDetectedEvent
import com.xingmou.core.agent.RoomAgentEventStore
import com.xingmou.core.agent.SessionResumedEvent
import com.xingmou.core.agent.TrainingCompletedEvent
import com.xingmou.core.domain.AnalysisEngine
import com.xingmou.core.domain.KnowledgeRetriever
import com.xingmou.core.domain.KnowledgeRoute
import com.xingmou.core.domain.PlanActor
import com.xingmou.core.domain.PlanStateMachine
import com.xingmou.core.domain.PlanStatus
import com.xingmou.core.domain.TrainingResult
import com.xingmou.core.llm.DeepSeekClientFactory
import com.xingmou.core.model.CommunicationLevel
import com.xingmou.core.model.Port
import com.xingmou.core.model.SessionContext
import com.xingmou.core.safety.SafeResponses
import com.xingmou.data.db.PlanVersionEntity
import com.xingmou.data.db.QizhiDatabase
import com.xingmou.data.db.ReviewRequestEntity
import com.xingmou.data.db.SeedData
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class XingmouViewModel(application: Application) : AndroidViewModel(application) {
    private val database = QizhiDatabase.getInstance(application)
    private val eventCoordinator = AgentEventCoordinator(
        AgentEventProcessor(),
        RoomAgentEventStore(database)
    )
    private val knowledgeRetriever = KnowledgeRetriever()
    private val analysisEngine = AnalysisEngine()
    private val planStateMachine = PlanStateMachine()
    private val childId = "child-demo"

    private val localSafeModel = ModelGateway { systemPrompt, _ ->
        val json = when {
            systemPrompt.contains("专业") -> """{"status":"draft","claims":[],"sources":[],"facts":[],"inferences":[],"review_required":true,"review_items":["请核对训练记录与停止条件"]}"""
            systemPrompt.contains("家长") -> """{"mode":"answer","acknowledgement":"已收到观察。","claims":[],"sources":[],"home_support":[],"disclaimer":"${SafeResponses.DISCLAIMER}"}"""
            else -> """{"state":"continue","speech":["慢慢来"],"next_action":"show_choice","support_level":"L1","ui":{"options":["圆形","三角形"],"show_break_button":true,"use_voice":false}}"""
        }
        Result.success(json)
    }
    private val orchestrator = AgentOrchestrator(localSafeModel)

    private val _uiState = MutableStateFlow(
        XingmouUiState(aiConfigured = DeepSeekClientFactory.isConfigured())
    )
    val uiState: StateFlow<XingmouUiState> = _uiState.asStateFlow()

    private var activePlan: PlanVersionEntity? = null
    private var activeReview: ReviewRequestEntity? = null

    init {
        refreshProfessionalAnalysis()
    }

    fun selectPort(port: Port) {
        _uiState.update { it.copy(selectedPort = port) }
        if (port == Port.PROFESSIONAL) refreshProfessionalAnalysis()
    }

    fun completeChildTask(correct: Boolean) {
        val snapshot = _uiState.value.child
        if (snapshot.isWorking || snapshot.isSafetyStopped) return
        _uiState.update { it.copy(child = it.child.copy(isWorking = true)) }
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                val result = TrainingResult(
                    taskId = "图片配对",
                    correct = correct,
                    firstCorrect = correct,
                    reactionMs = 1_500L,
                    errorType = if (correct) null else "choice_mismatch",
                    promptLevel = snapshot.supportLevel.ordinal
                )
                val decision = eventCoordinator.handle(
                    TrainingCompletedEvent(
                        eventId = newId("training-event"),
                        runId = newId("child-run"),
                        childId = childId,
                        occurredAt = now,
                        domain = "A",
                        currentDifficulty = snapshot.difficulty,
                        currentSupportLevel = snapshot.supportLevel,
                        result = result,
                        recentResults = snapshot.recentResults
                    )
                )
                val failures = if (correct) 0 else snapshot.consecutiveFailures + 1
                val updated = snapshot.apply(decision).copy(
                    recentResults = (snapshot.recentResults + result).takeLast(3),
                    consecutiveFailures = failures,
                    instruction = if (correct) "再找一次圆形" else "看一看，再选一次"
                )
                _uiState.update { it.copy(child = updated) }
                refreshProfessionalAnalysis()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(child = it.child.copy(isWorking = false, message = "记录没有保存，请先休息后再试。", lastEvent = error.javaClass.simpleName))
                }
            }
        }
    }

    fun pauseChildTraining() {
        val snapshot = _uiState.value.child
        if (snapshot.isWorking || snapshot.isSafetyStopped) return
        _uiState.update { it.copy(child = it.child.copy(isWorking = true)) }
        viewModelScope.launch {
            runCatching {
                val decision = eventCoordinator.handle(
                    ConsecutiveFailuresEvent(
                        eventId = newId("pause-event"),
                        runId = newId("child-run"),
                        childId = childId,
                        occurredAt = System.currentTimeMillis(),
                        count = 2
                    )
                )
                _uiState.update { it.copy(child = it.child.apply(decision)) }
            }.onFailure {
                _uiState.update { it.copy(child = it.child.copy(isWorking = false, message = SafeResponses.PAUSE_CHILD.joinToString(" "), isPaused = true)) }
            }
        }
    }

    fun resumeChildTraining() {
        val snapshot = _uiState.value.child
        if (!snapshot.isPaused || snapshot.isWorking) return
        _uiState.update { it.copy(child = it.child.copy(isWorking = true)) }
        viewModelScope.launch {
            runCatching {
                val decision = eventCoordinator.handle(
                    SessionResumedEvent(
                        eventId = newId("resume-event"),
                        runId = newId("child-run"),
                        childId = childId,
                        occurredAt = System.currentTimeMillis(),
                        previousState = com.xingmou.core.agent.AgentRunState.PAUSED
                    )
                )
                _uiState.update {
                    it.copy(child = it.child.apply(decision).copy(isPaused = false, message = "准备好了，我们从简单的一步开始。"))
                }
            }.onFailure {
                _uiState.update { it.copy(child = it.child.copy(isWorking = false)) }
            }
        }
    }

    fun updateParentQuery(text: String) {
        _uiState.update { it.copy(parent = it.parent.copy(query = text.take(240))) }
    }

    fun askParentQuestion() {
        val query = _uiState.value.parent.query.trim()
        if (query.isBlank()) {
            _uiState.update { it.copy(parent = it.parent.copy(message = "请先写下一个具体观察。", riskLabel = "需要补充")) }
            return
        }
        _uiState.update { it.copy(parent = it.parent.copy(isWorking = true, message = "正在读取本地已审核知识…")) }
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                val runId = newId("parent-run")
                val knowledge = database.knowledgeDao().verifiedItems().ifEmpty { SeedData.knowledgeItems }
                val records = database.trainingRecordDao().recentForChild(childId)
                val riskDecision = eventCoordinator.handle(
                    RiskDetectedEvent(newId("risk-event"), runId, childId, now, query)
                )
                eventCoordinator.handle(
                    ParentObservationAddedEvent(newId("parent-event"), runId, childId, now, query)
                )
                val retrieval = knowledgeRetriever.retrieve(query, Port.PARENT, items = knowledge)
                val orchestration = orchestrator.run(
                    AgentOrchestrationRequest(
                        runId = runId,
                        taskType = "parent_support",
                        childId = childId,
                        input = AgentContextInput(Port.PARENT, session(), query, knowledgeItems = knowledge, recentRecords = records)
                    )
                )
                val message = when (retrieval.route) {
                    KnowledgeRoute.NORMAL -> retrieval.reason
                    KnowledgeRoute.CLARIFY -> retrieval.reason
                    KnowledgeRoute.NOT_FOUND -> "本地已审核知识中暂未找到直接匹配项。请补充发生场景、持续时间和孩子当时的状态。"
                    KnowledgeRoute.REFER -> retrieval.reason
                    KnowledgeRoute.SAFETY_STOP -> riskDecision.message
                }
                _uiState.update {
                    it.copy(
                        parent = it.parent.copy(
                            route = retrieval.route,
                            message = message,
                            suggestions = retrieval.matchedItems.map { item -> item.content },
                            sources = retrieval.matchedItems.map { item -> "${item.title} · ${item.sourceRef ?: "本地已审核资料"}" },
                            recordCount = records.size,
                            riskLabel = retrieval.riskLevel,
                            isWorking = false,
                            agentRunId = runId,
                            agentStatus = orchestration.route.name
                        )
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(parent = it.parent.copy(isWorking = false, message = SafeResponses.INSUFFICIENT_DATA, agentStatus = "FAILED: ${error.javaClass.simpleName}"))
                }
            }
        }
    }

    fun updateReviewComment(text: String) {
        _uiState.update { it.copy(professional = it.professional.copy(reviewComment = text.take(300))) }
    }

    fun createPlanDraft() {
        val professional = _uiState.value.professional
        if (!professional.dataSufficient || professional.isWorking) return
        _uiState.update { it.copy(professional = it.professional.copy(isWorking = true, reviewMessage = "正在生成受控草案…")) }
        viewModelScope.launch {
            runCatching {
                check(planStateMachine.createDraft(PlanActor.MODEL).accepted)
                val now = System.currentTimeMillis()
                val runId = newId("professional-run")
                val records = database.trainingRecordDao().recentForChild(childId)
                val knowledge = database.knowledgeDao().verifiedItems().ifEmpty { SeedData.knowledgeItems }
                val orchestration = orchestrator.run(
                    AgentOrchestrationRequest(
                        runId = runId,
                        taskType = "plan_draft",
                        childId = childId,
                        input = AgentContextInput(Port.PROFESSIONAL, session(), "基于现有训练记录生成待审核草案", knowledgeItems = knowledge, recentRecords = records)
                    )
                )
                val version = (database.planDao().latest(childId)?.version ?: 0) + 1
                val plan = PlanVersionEntity(
                    planId = newId("plan"),
                    childId = childId,
                    version = version,
                    status = "draft",
                    reviewRequired = true,
                    payloadJson = """{"priority_domain":"A","observable_goal":"在低支持下完成图片配对","task":"图片配对","difficulty":${_uiState.value.child.difficulty},"support_level":"${_uiState.value.child.supportLevel.name}","frequency":"短时练习","duration":"5-10分钟"}""",
                    createdAt = now,
                    updatedAt = now
                )
                val review = ReviewRequestEntity(
                    reviewId = newId("review"),
                    childId = childId,
                    runId = runId,
                    targetType = "plan",
                    targetId = plan.planId,
                    draftJson = plan.payloadJson,
                    diffJson = null,
                    status = "pending",
                    reviewerId = null,
                    reviewerComment = null,
                    createdAt = now,
                    resolvedAt = null
                )
                database.planDao().upsert(plan)
                database.agentDao().upsertReview(review)
                activePlan = plan
                activeReview = review
                _uiState.update {
                    it.copy(professional = it.professional.copy(
                        planStatus = PlanStatus.DRAFT,
                        planSummary = "优先领域 A · 图片配对 · 难度 ${_uiState.value.child.difficulty} · 支持 ${_uiState.value.child.supportLevel.name} · 每次 5–10 分钟",
                        reviewMessage = "草案已生成，需先确认，再签署生效。",
                        isWorking = false,
                        agentRunId = runId,
                        agentStatus = orchestration.route.name,
                        recentEvent = "PLAN_DRAFT_CREATED"
                    ))
                }
            }.onFailure { error ->
                _uiState.update { it.copy(professional = it.professional.copy(isWorking = false, reviewMessage = "草案生成失败：${error.message ?: "未知错误"}")) }
            }
        }
    }

    fun confirmPlanDraft() = reviewPlan(activate = false)

    fun activatePlan() = reviewPlan(activate = true)

    private fun reviewPlan(activate: Boolean) {
        val plan = activePlan ?: return
        val review = activeReview ?: return
        if (_uiState.value.professional.isWorking) return
        _uiState.update { it.copy(professional = it.professional.copy(isWorking = true)) }
        viewModelScope.launch {
            runCatching {
                val decision = eventCoordinator.handle(
                    ReviewApprovedEvent(
                        eventId = newId("review-event"),
                        runId = review.runId,
                        childId = childId,
                        occurredAt = System.currentTimeMillis(),
                        plan = plan,
                        review = review,
                        reviewerId = "local-professional",
                        comment = _uiState.value.professional.reviewComment.ifBlank { "已核对训练记录与停止条件" },
                        activate = activate
                    )
                )
                decision.updatedPlan?.let { activePlan = it }
                decision.updatedReview?.let { activeReview = it }
                _uiState.update {
                    it.copy(professional = it.professional.copy(
                        planStatus = decision.updatedPlan?.status?.uppercase()?.let(PlanStatus::valueOf) ?: it.professional.planStatus,
                        reviewMessage = decision.message,
                        isWorking = false,
                        recentEvent = decision.event.eventType
                    ))
                }
            }.onFailure { error ->
                _uiState.update { it.copy(professional = it.professional.copy(isWorking = false, reviewMessage = error.message ?: "审核失败")) }
            }
        }
    }

    fun rejectPlan() {
        val plan = activePlan ?: return
        val review = activeReview ?: return
        val reason = _uiState.value.professional.reviewComment.trim()
        if (reason.isBlank()) {
            _uiState.update { it.copy(professional = it.professional.copy(reviewMessage = "退回修改前请填写审核理由。")) }
            return
        }
        _uiState.update { it.copy(professional = it.professional.copy(isWorking = true)) }
        viewModelScope.launch {
            runCatching {
                val decision = eventCoordinator.handle(
                    PlanRejectedEvent(newId("reject-event"), review.runId, childId, System.currentTimeMillis(), plan, review, "local-professional", reason)
                )
                decision.updatedPlan?.let { activePlan = it }
                decision.updatedReview?.let { activeReview = it }
                _uiState.update {
                    it.copy(professional = it.professional.copy(
                        planStatus = PlanStatus.REJECTED,
                        reviewMessage = decision.message,
                        isWorking = false,
                        recentEvent = decision.event.eventType
                    ))
                }
            }.onFailure { error ->
                _uiState.update { it.copy(professional = it.professional.copy(isWorking = false, reviewMessage = error.message ?: "退回失败")) }
            }
        }
    }

    fun refreshProfessionalAnalysis() {
        viewModelScope.launch {
            runCatching {
                val records = database.trainingRecordDao().recentForChild(childId)
                val analysis = analysisEngine.analyze(records)
                _uiState.update {
                    it.copy(
                        parent = it.parent.copy(recordCount = records.size),
                        professional = it.professional.copy(
                            recordCount = analysis.sampleCount,
                            dataSufficient = analysis.dataSufficient,
                            analysisSummary = analysis.observations + if (analysis.dataSufficient) listOf("趋势：${analysis.trend.name}") else emptyList(),
                            warningSignals = analysis.warningSignals,
                            planSummary = if (analysis.dataSufficient || it.professional.planStatus != null) it.professional.planSummary else "达到 3 条有效记录后，可生成方案草案。",
                            recentEvent = if (analysis.sampleCount >= 3) "RECORDS_THRESHOLD_REACHED" else it.professional.recentEvent
                        )
                    )
                }
            }
        }
    }

    private fun session() = SessionContext(
        childAlias = "小星",
        ageBand = "学龄期",
        communicationLevel = CommunicationLevel.SHORT_SENTENCE,
        supportLevel = _uiState.value.child.supportLevel,
        currentDomain = "A",
        currentTask = "图片配对"
    )

    private fun newId(prefix: String): String = "$prefix-${UUID.randomUUID()}"
}
