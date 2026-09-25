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
import com.xingmou.core.domain.BaselineEngine
import com.xingmou.core.domain.BaselineSession
import com.xingmou.core.domain.BaselineStatus
import com.xingmou.core.domain.CourseProgressEngine
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
import com.xingmou.data.db.ChildEntity
import com.xingmou.data.db.QizhiDatabase
import com.xingmou.data.db.ReviewRequestEntity
import com.xingmou.data.db.SeedData
import com.xingmou.data.db.ConsentEntity
import com.xingmou.data.db.AbilityProfileEntity
import com.xingmou.data.db.HomeFeedbackEntity
import com.xingmou.data.db.HomeTaskEntity
import com.xingmou.data.db.AssessmentRecordEntity
import com.xingmou.data.catalog.QuestionCatalog
import com.xingmou.data.catalog.AssessmentCatalog
import com.xingmou.BaselineUiState
import com.xingmou.ReportMetricUi
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class XingmouViewModel(application: Application) : AndroidViewModel(application) {
    private val accessibilityPreferences = application.getSharedPreferences("xingmou_accessibility", 0)
    private val database = QizhiDatabase.getInstance(application)
    private val eventCoordinator = AgentEventCoordinator(
        AgentEventProcessor(),
        RoomAgentEventStore(database)
    )
    private val knowledgeRetriever = KnowledgeRetriever()
    private val analysisEngine = AnalysisEngine()
    private val baselineEngine = BaselineEngine()
    private val courseProgressEngine = CourseProgressEngine()
    private val planStateMachine = PlanStateMachine()
    private var activeChildId: String? = null
    private val localUserId = SeedData.DEMO_USER_ID
    private val childId: String
        get() = activeChildId ?: SeedData.defaultChild.childId

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
        XingmouUiState(
            accessibility = AccessibilityUiState(
                speechEnabled = accessibilityPreferences.getBoolean("speech_enabled", true),
                speechRate = normalizeSpeechRate(accessibilityPreferences.getFloat("speech_rate", 1.0f)),
                speechVolume = normalizeSpeechVolume(accessibilityPreferences.getFloat("speech_volume", 1.0f)),
                largeText = accessibilityPreferences.getBoolean("large_text", false),
                highContrast = accessibilityPreferences.getBoolean("high_contrast", false)
            ),
            aiConfigured = DeepSeekClientFactory.isConfigured()
        )
    )
    val uiState: StateFlow<XingmouUiState> = _uiState.asStateFlow()

    private var activePlan: PlanVersionEntity? = null
    private var activeReview: ReviewRequestEntity? = null
    private var baselineSession = BaselineSession()

    init {
        viewModelScope.launch {
            database.childDao().observeActive().collect { children ->
                val current = children.firstOrNull { it.childId == activeChildId } ?: children.firstOrNull()
                activeChildId = current?.childId ?: SeedData.defaultChild.childId
                _uiState.update {
                    it.copy(
                        activeChildId = activeChildId ?: SeedData.defaultChild.childId,
                        activeChildAlias = current?.alias ?: SeedData.defaultChild.alias,
                        availableChildren = children.map(::toChildSummary)
                    )
                }
                current?.let { loadConsentState(it.childId) }
                current?.let { loadBaseline(it) }
                current?.let { loadCourseProgress(it.childId) }
                current?.let { loadHomeSupport(it.childId) }
            }
        }
        refreshProfessionalAnalysis()
    }

    fun selectChild(childId: String) {
        viewModelScope.launch {
            val child = database.childDao().findById(childId) ?: return@launch
            activeChildId = child.childId
            _uiState.update { it.copy(activeChildId = child.childId, activeChildAlias = child.alias) }
            refreshProfessionalAnalysis()
            loadBaseline(child)
            loadCourseProgress(child.childId)
            loadHomeSupport(child.childId)
        }
    }

    fun createLocalChild(alias: String, ageBand: String = "学龄期") {
        val normalized = alias.trim().take(24)
        if (normalized.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val child = ChildEntity(
                childId = newId("child"), alias = normalized, ageBand = ageBand,
                communicationLevel = CommunicationLevel.SHORT_SENTENCE.name,
                supportLevel = "L1", createdAt = now, updatedAt = now
            )
            database.childDao().upsert(child)
            database.childBindingDao().upsert(
                com.xingmou.data.db.ChildBindingEntity(
                    userId = SeedData.DEMO_USER_ID, childId = child.childId,
                    role = "professional", validFrom = now
                )
            )
            selectChild(child.childId)
        }
    }

    fun updateActiveChild(alias: String, ageBand: String) {
        val id = activeChildId ?: return
        val normalized = alias.trim().take(24)
        val normalizedAge = ageBand.trim().take(24)
        if (normalized.isBlank() || normalizedAge.isBlank()) return
        viewModelScope.launch {
            database.childDao().updateBasicProfile(id, normalized, normalizedAge, System.currentTimeMillis())
        }
    }

    fun archiveActiveChild() {
        val id = activeChildId ?: return
        viewModelScope.launch {
            val count = database.childDao().observeActive().first().size
            if (count <= 1) return@launch
            database.childDao().archive(id, System.currentTimeMillis())
            activeChildId = database.childDao().firstActive()?.childId
        }
    }

    fun setRemoteAiConsent(granted: Boolean) = setConsent("remote_ai", granted)

    fun setExportConsent(granted: Boolean) = setConsent("export", granted)

    fun startBaseline() {
        if (_uiState.value.baseline.status != BaselineStatus.IN_PROGRESS) {
            baselineSession = baselineEngine.newSession(System.currentTimeMillis())
            persistBaseline()
        }
        publishBaseline(isOpen = true)
    }

    fun resumeBaseline() {
        if (_uiState.value.baseline.status == BaselineStatus.IN_PROGRESS) publishBaseline(isOpen = true)
    }

    fun leaveBaseline() {
        if (_uiState.value.baseline.status == BaselineStatus.IN_PROGRESS) publishBaseline(isOpen = false)
    }

    fun startCourse() {
        if (_uiState.value.child.courseUnlocked) {
            _uiState.update { it.copy(child = it.child.copy(courseOpen = true)) }
        }
    }

    fun leaveCourse() {
        _uiState.update { it.copy(child = it.child.copy(courseOpen = false)) }
    }

    fun resumeCourse() {
        startCourse()
    }

    private fun resetBaselineSession() {
        baselineSession = baselineEngine.newSession(System.currentTimeMillis())
        publishBaseline(isOpen = false)
        persistBaseline()
    }

    fun restartBaseline() {
        resetBaselineSession()
        publishBaseline(isOpen = true)
    }

    fun answerBaseline(option: Int) {
        if (_uiState.value.baseline.status != BaselineStatus.IN_PROGRESS || _uiState.value.baseline.isWorking) return
        _uiState.update { it.copy(baseline = it.baseline.copy(isWorking = true)) }
        viewModelScope.launch {
            baselineSession = baselineEngine.answer(baselineSession, option, System.currentTimeMillis())
            persistBaseline()
            if (baselineSession.status == BaselineStatus.COMPLETED) {
                val now = System.currentTimeMillis()
                val scores = baselineEngine.scores(baselineSession)
                database.abilityProfileDao().upsert(
                    AbilityProfileEntity(
                        profileId = "baseline-${childId}-${baselineSession.version}-${now}",
                        childId = childId,
                        status = "completed",
                        scoresJson = com.google.gson.Gson().toJson(scores),
                        confidence = if (baselineSession.answers.size == 6) 1.0 else 0.5,
                        evidenceJson = com.google.gson.Gson().toJson(baselineSession.answers),
                        createdAt = now
                    )
                )
            }
            publishBaseline(isOpen = true)
            loadCourseProgress(childId)
        }
    }

    private fun setConsent(purpose: String, granted: Boolean) {
        val childId = activeChildId ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            database.consentDao().upsert(
                ConsentEntity(
                    consentId = "consent-$childId-$purpose",
                    childId = childId,
                    purpose = purpose,
                    status = if (granted) "granted" else "revoked",
                    grantedAt = if (granted) now else null,
                    revokedAt = if (granted) null else now
                )
            )
            _uiState.update {
                when (purpose) {
                    "remote_ai" -> it.copy(remoteAiConsent = granted)
                    "export" -> it.copy(exportConsent = granted)
                    else -> it
                }
            }
        }
    }

    private suspend fun loadConsentState(childId: String) {
        val consents = database.consentDao().forChild(childId).associateBy { it.purpose }
        _uiState.update {
            it.copy(
                remoteAiConsent = consents["remote_ai"]?.status == "granted",
                exportConsent = consents["export"]?.status == "granted"
            )
        }
    }

    private suspend fun loadBaseline(child: ChildEntity) {
        baselineSession = baselineEngine.fromJson(child.baselineJson) ?: BaselineSession()
        publishBaseline(isOpen = false)
    }

    private suspend fun loadCourseProgress(childId: String) {
        val records = database.trainingRecordDao().recentForChild(childId, 100)
            .filter { it.taskId.startsWith("M02-L1-") || it.taskId == "图片配对" }
        val progress = courseProgressEngine.summarize(records)
        val question = progress.nextQuestion
        _uiState.update {
            it.copy(child = it.child.copy(
                instruction = question?.prompt ?: "第一关完成了，可以休息一下",
                options = question?.options ?: it.child.options,
                courseProgress = progress.completedCount,
                courseTotal = progress.total,
                courseQuestionId = question?.id,
                courseUnlocked = baselineSession.status == BaselineStatus.COMPLETED,
                courseOpen = true,
                courseSummary = progress.summary
            ))
        }
    }

    private fun publishBaseline(isOpen: Boolean = _uiState.value.baseline.isOpen) {
        val question = baselineEngine.currentQuestion(baselineSession)
        _uiState.update {
            it.copy(
                baseline = BaselineUiState(
                    status = baselineSession.status,
                    currentIndex = baselineSession.currentIndex,
                    totalCount = com.xingmou.data.catalog.QuestionCatalog.baselineQuestions.size,
                    question = question,
                    message = when (baselineSession.status) {
                        BaselineStatus.NOT_STARTED -> "先做几个小练习，帮助小星找到合适的起点。"
                        BaselineStatus.IN_PROGRESS -> "第 ${baselineSession.currentIndex + 1} 题，慢慢来。"
                        BaselineStatus.COMPLETED -> "基线完成了。我们会根据过程表现安排下一步。"
                        BaselineStatus.NEEDS_RETEST -> "这次可以稍后重新开始。"
                    },
                    scores = baselineEngine.scores(baselineSession),
                    isWorking = false,
                    isOpen = isOpen && baselineSession.status == BaselineStatus.IN_PROGRESS
                )
            )
        }
    }

    private fun persistBaseline() {
        val currentId = activeChildId ?: return
        viewModelScope.launch {
            val current = database.childDao().findById(currentId) ?: return@launch
            val nextVersion = if (baselineSession.status == BaselineStatus.COMPLETED) current.profileVersion + 1 else current.profileVersion
            database.childDao().updateBaseline(currentId, baselineEngine.toJson(baselineSession), nextVersion, System.currentTimeMillis())
        }
    }

    fun selectPort(port: Port) {
        _uiState.update { it.copy(selectedPort = port) }
        if (port == Port.PROFESSIONAL) refreshProfessionalAnalysis()
    }

    fun setSpeechEnabled(enabled: Boolean) {
        accessibilityPreferences.edit().putBoolean("speech_enabled", enabled).apply()
        _uiState.update { it.copy(accessibility = it.accessibility.copy(speechEnabled = enabled)) }
    }

    fun setSpeechRate(rate: Float) {
        val normalized = normalizeSpeechRate(rate)
        accessibilityPreferences.edit().putFloat("speech_rate", normalized).apply()
        _uiState.update { it.copy(accessibility = it.accessibility.copy(speechRate = normalized)) }
    }

    fun setSpeechVolume(volume: Float) {
        val normalized = normalizeSpeechVolume(volume)
        accessibilityPreferences.edit().putFloat("speech_volume", normalized).apply()
        _uiState.update { it.copy(accessibility = it.accessibility.copy(speechVolume = normalized)) }
    }

    fun setLargeText(enabled: Boolean) {
        accessibilityPreferences.edit().putBoolean("large_text", enabled).apply()
        _uiState.update { it.copy(accessibility = it.accessibility.copy(largeText = enabled)) }
    }

    fun setHighContrast(enabled: Boolean) {
        accessibilityPreferences.edit().putBoolean("high_contrast", enabled).apply()
        _uiState.update { it.copy(accessibility = it.accessibility.copy(highContrast = enabled)) }
    }

    fun completeChildTask(correct: Boolean) {
        val snapshot = _uiState.value.child
        if (snapshot.isWorking || snapshot.isSafetyStopped || !snapshot.courseUnlocked || !snapshot.courseOpen || snapshot.courseQuestionId == null || snapshot.courseProgress >= snapshot.courseTotal) return
        _uiState.update { it.copy(child = it.child.copy(isWorking = true)) }
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                val result = TrainingResult(
                    taskId = snapshot.courseQuestionId ?: "图片配对",
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
                loadCourseProgress(childId)
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

    fun updateFeedbackMood(value: String) {
        _uiState.update { it.copy(parent = it.parent.copy(feedbackMood = value)) }
    }

    fun updateFeedbackFatigue(value: String) {
        _uiState.update { it.copy(parent = it.parent.copy(feedbackFatigue = value)) }
    }

    fun updateFeedbackNote(value: String) {
        _uiState.update { it.copy(parent = it.parent.copy(feedbackNote = value.take(240))) }
    }

    fun completeHomeTask() = updateHomeTaskStatus("completed")

    fun skipHomeTask() = updateHomeTaskStatus("skipped")

    fun pauseHomeTask() = updateHomeTaskStatus("paused")

    fun advanceHomeDemo() {
        val current = _uiState.value.parent
        if (current.homeTaskSafetyStopped) {
            _uiState.update { it.copy(parent = it.parent.copy(feedbackMessage = "当前有安全暂停标记，陪练示范暂不可操作。")) }
            return
        }
        viewModelScope.launch {
            val task = database.homeTaskDao().latestForChild(childId) ?: return@launch
            val next = if (task.demoStep >= HOME_DEMO_STEPS.lastIndex) 0 else task.demoStep + 1
            database.homeTaskDao().updateDemoStep(childId, task.taskId, next, System.currentTimeMillis())
            _uiState.update { it.copy(parent = it.parent.copy(homeDemoStep = next, feedbackMessage = if (next == HOME_DEMO_STEPS.lastIndex) "陪练示范已完成，可以结束并记录今天的状态。" else "已完成：${HOME_DEMO_STEPS[task.demoStep]}")) }
        }
    }

    private fun updateHomeTaskStatus(status: String) {
        val current = _uiState.value.parent
        if (current.homeTaskSafetyStopped) {
            _uiState.update { it.copy(parent = it.parent.copy(feedbackMessage = "当前有安全暂停标记，家庭任务暂不可操作。")) }
            return
        }
        viewModelScope.launch {
            val task = database.homeTaskDao().latestForChild(childId) ?: return@launch
            database.homeTaskDao().updateStatus(childId, task.taskId, status, System.currentTimeMillis())
            _uiState.update { it.copy(parent = it.parent.copy(homeTaskStatus = status, feedbackMessage = "家庭任务已${homeTaskStatusLabel(status)}。")) }
        }
    }

    fun submitHomeFeedback() {
        val parent = _uiState.value.parent
        viewModelScope.launch {
            val task = database.homeTaskDao().latestForChild(childId)
            database.homeFeedbackDao().insert(
                HomeFeedbackEntity(
                    feedbackId = newId("feedback"), childId = childId, taskId = task?.taskId,
                    mood = parent.feedbackMood, fatigue = parent.feedbackFatigue,
                    note = parent.feedbackNote.trim(), createdAt = System.currentTimeMillis()
                )
            )
            _uiState.update { it.copy(parent = it.parent.copy(feedbackMessage = "观察已保存到当前儿童档案。", feedbackNote = "")) }
        }
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

    private suspend fun loadHomeSupport(childId: String) {
        val latestPlan = database.planDao().latest(childId)?.takeIf { it.status == "active" }
        val existing = database.homeTaskDao().latestForChild(childId)
        val task = if (latestPlan != null && existing?.planId != latestPlan.planId) {
            createHomeTaskFromPlan(latestPlan).also { database.homeTaskDao().upsert(it) }
        } else existing ?: HomeTaskEntity(
            taskId = "home-$childId-matching",
            childId = childId,
            title = "五分钟图片配对陪练",
            description = "准备两个熟悉的图片，先示范一次，再邀请孩子自己试试。出现疲劳或拒绝时暂停。",
            status = "pending",
            frequency = "每日 1–2 次",
            durationMinutes = 5,
            supportLevel = "L1",
            stopConditions = "出现疲劳、拒绝或风险时暂停",
            source = "LOCAL_TEMPLATE",
            updatedAt = System.currentTimeMillis()
        ).also { database.homeTaskDao().upsert(it) }
        val safetyStopped = database.safetyFlagDao().observeActive(childId).first().isNotEmpty()
        _uiState.update {
            it.copy(parent = it.parent.copy(
                homeTaskTitle = task.title,
                homeTaskDescription = task.description,
                homeTaskStatus = task.status,
                homeTaskPlanVersion = task.planVersion,
                homeTaskFrequency = task.frequency,
                homeTaskDurationMinutes = task.durationMinutes,
                homeTaskSupportLevel = task.supportLevel,
                homeTaskStopConditions = task.stopConditions,
                homeTaskSafetyStopped = safetyStopped,
                homeDemoStep = task.demoStep.coerceIn(0, HOME_DEMO_STEPS.lastIndex)
            ))
        }
    }

    private fun homeTaskStatusLabel(status: String): String = when (status) {
        "completed" -> "完成"
        "skipped" -> "跳过"
        "paused" -> "暂停"
        else -> "待完成"
    }

    fun updateReviewComment(text: String) {
        _uiState.update { it.copy(professional = it.professional.copy(reviewComment = text.take(300))) }
    }

    fun selectAssessment(assessmentId: String) {
        val definition = AssessmentCatalog.all.firstOrNull { it.id == assessmentId } ?: return
        _uiState.update {
            it.copy(professional = it.professional.copy(
                assessmentId = definition.id,
                assessmentName = definition.name,
                assessmentMessage = definition.note
            ))
        }
    }

    fun updateAssessmentDate(value: String) {
        _uiState.update { it.copy(professional = it.professional.copy(assessmentDate = value.take(24))) }
    }

    fun updateAssessmentSource(value: String) {
        _uiState.update { it.copy(professional = it.professional.copy(assessmentSource = value.take(120))) }
    }

    fun updateAssessmentScores(value: String) {
        _uiState.update { it.copy(professional = it.professional.copy(assessmentScores = value.take(500))) }
    }

    fun updateAssessmentNotes(value: String) {
        _uiState.update { it.copy(professional = it.professional.copy(assessmentNotes = value.take(500))) }
    }

    fun saveAssessmentRecord() {
        val professional = _uiState.value.professional
        if (professional.assessmentScores.trim().isBlank()) {
            _uiState.update { it.copy(professional = it.professional.copy(assessmentMessage = "请先填写分数摘要或专业记录。")) }
            return
        }
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                val version = database.assessmentRecordDao().latestVersion(childId, professional.assessmentId) + 1
                val date = professional.assessmentDate.trim().ifBlank { currentDateLabel() }
                val source = professional.assessmentSource.trim().ifBlank { "专业人员转录" }
                database.assessmentRecordDao().upsert(
                    AssessmentRecordEntity(
                        recordId = newId("assessment"),
                        childId = childId,
                        assessmentId = professional.assessmentId,
                        assessmentName = professional.assessmentName,
                        version = version,
                        recordType = if (version == 1) "初评" else "复评",
                        assessmentDate = date,
                        source = source,
                        scoresJson = professional.assessmentScores.trim(),
                        notes = professional.assessmentNotes.trim(),
                        status = "transcribed",
                        createdAt = now,
                        updatedAt = now
                    )
                )
                _uiState.update {
                    it.copy(professional = it.professional.copy(
                        assessmentDate = date,
                        assessmentSource = source,
                        assessmentScores = "",
                        assessmentNotes = "",
                        assessmentMessage = "${professional.assessmentName} V$version 已保存，${if (version == 1) "作为初评记录" else "已建立复评版本"}。"
                    ))
                }
                refreshProfessionalAnalysis()
            }.onFailure { error ->
                _uiState.update { it.copy(professional = it.professional.copy(assessmentMessage = "量表保存失败：${error.message ?: "未知错误"}")) }
            }
        }
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
                    payloadJson = """{"priority_domain":"A","observable_goal":"在低支持下完成图片配对","task":"图片配对","difficulty":${_uiState.value.child.difficulty},"support_level":"${_uiState.value.child.supportLevel.name}","frequency":"每日 1–2 次","duration":"5 分钟","stop_conditions":"出现疲劳、拒绝或风险时暂停"}""",
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
                if (activate && decision.updatedPlan?.status == "active") {
                    publishActivePlanToHomeTask(decision.updatedPlan)
                }
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
                val latestPlan = database.planDao().latest(childId)
                val review = latestPlan?.let { database.agentDao().latestReviewForTarget(it.planId) }
                val feedback = database.homeFeedbackDao().recentForChild(childId)
                val homeTasks = database.homeTaskDao().allForChild(childId).associateBy { it.taskId }
                val assessments = database.assessmentRecordDao().recentForChild(childId)
                val reportGroups = records.groupBy { it.domain to it.taskId }.map { (key, group) ->
                    ReportGroupUi(
                        domain = key.first,
                        task = key.second,
                        sampleCount = group.size,
                        accuracy = "%.0f%%".format(group.count { it.correct }.toDouble() / group.size * 100),
                        independentRate = "%.0f%%".format(group.count { it.supportLevel.equals("L0", true) || it.promptLevel == 0 }.toDouble() / group.size * 100),
                        averageReaction = group.mapNotNull { it.reactionMs }.takeIf { it.isNotEmpty() }?.let { "%.0f ms".format(it.average()) } ?: "—"
                    )
                }.sortedWith(compareBy({ it.domain }, { it.task }))
                val trainingDetails = records.take(10).map { item ->
                    TrainingDetailUi(
                        timestamp = item.createdAt,
                        domain = item.domain,
                        task = item.taskId,
                        result = if (item.correct) "完成" else "需再试",
                        support = "${item.supportLevel} / 提示 ${item.promptLevel}",
                        reaction = item.reactionMs?.let { "$it ms" } ?: "—"
                    )
                }
                if (latestPlan != null && latestPlan.status in setOf("draft", "confirmed") && review != null) {
                    activePlan = latestPlan
                    activeReview = review
                } else if (latestPlan == null || latestPlan.status !in setOf("draft", "confirmed")) {
                    activePlan = null
                    activeReview = null
                }
                _uiState.update {
                    it.copy(
                        parent = it.parent.copy(recordCount = records.size),
                        professional = it.professional.copy(
                            recordCount = analysis.sampleCount,
                            dataSufficient = analysis.dataSufficient,
                            analysisSummary = analysis.observations + if (analysis.dataSufficient) listOf("趋势：${analysis.trend.name}") else emptyList(),
                            warningSignals = analysis.warningSignals,
                            reportMetrics = listOf(
                                ReportMetricUi("正确率", analysis.accuracy.percentLabel(), "整体正确作答比例"),
                                ReportMetricUi("独立完成率", analysis.independentCompletionRate.percentLabel(), "L0 或无需提示的记录比例"),
                                ReportMetricUi("提示依赖", analysis.averagePromptLevel?.let { "等级 %.1f".format(it) } ?: "—", "平均提示等级，越低越独立"),
                                ReportMetricUi("平均反应时", analysis.averageReactionMs?.let { "%.0f ms".format(it) } ?: "—", "仅统计有反应时记录"),
                                ReportMetricUi("趋势", analysis.trend.label(), "按训练记录前后半段比较")
                            ),
                            reportGroups = reportGroups,
                            recentTrainingDetails = trainingDetails,
                            recentAssessments = assessments.map { item ->
                                AssessmentRecordUi(
                                    item.assessmentName,
                                    item.version,
                                    item.recordType,
                                    item.assessmentDate,
                                    item.source,
                                    item.scoresJson,
                                    item.notes
                                )
                            },
                            planStatus = latestPlan?.status?.uppercase()?.let { status -> runCatching { PlanStatus.valueOf(status) }.getOrNull() } ?: it.professional.planStatus,
                            planSummary = if (latestPlan != null) "当前方案 V${latestPlan.version} · ${latestPlan.status.uppercase()}" else if (analysis.dataSufficient || it.professional.planStatus != null) it.professional.planSummary else "达到 3 条有效记录后，可生成方案草案。",
                            recentEvent = if (analysis.sampleCount >= 3) "RECORDS_THRESHOLD_REACHED" else it.professional.recentEvent,
                            recentHomeFeedback = feedback.map { item ->
                                HomeFeedbackUi(item.createdAt, homeTasks[item.taskId]?.title ?: "家庭观察", item.mood, item.fatigue, item.note)
                            }
                        )
                    )
                }
            }
        }
    }

    private suspend fun publishActivePlanToHomeTask(plan: PlanVersionEntity) {
        database.homeTaskDao().upsert(createHomeTaskFromPlan(plan))
        loadHomeSupport(childId)
    }

    private fun createHomeTaskFromPlan(plan: PlanVersionEntity): HomeTaskEntity {
        val payload = plan.payloadJson
        val task = jsonString(payload, "task") ?: "图片配对"
        val frequency = jsonString(payload, "frequency") ?: "每日 1–2 次"
        val duration = jsonString(payload, "duration")?.filter { it.isDigit() }?.toIntOrNull()?.coerceIn(1, 60) ?: 5
        val support = jsonString(payload, "support_level") ?: _uiState.value.child.supportLevel.name
        val stop = jsonString(payload, "stop_conditions") ?: "出现疲劳、拒绝或风险时暂停"
        return HomeTaskEntity(
            taskId = "home-$childId-plan-${plan.version}",
            childId = childId,
            title = "专业下发：${task}短练习",
            description = "按方案 V${plan.version} 执行 ${task}，支持等级 $support；$stop。",
            status = "pending",
            planId = plan.planId,
            planVersion = plan.version,
            frequency = frequency,
            durationMinutes = duration,
            supportLevel = support,
            stopConditions = stop,
            source = "PLAN_V${plan.version}",
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun jsonString(json: String, key: String): String? =
        Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").find(json)?.groupValues?.getOrNull(1)

    private fun currentDateLabel(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun Double?.percentLabel(): String = this?.let { "%.0f%%".format(it * 100) } ?: "—"

    private fun com.xingmou.core.domain.Trend.label(): String = when (this) {
        com.xingmou.core.domain.Trend.IMPROVING -> "改善"
        com.xingmou.core.domain.Trend.STABLE -> "稳定"
        com.xingmou.core.domain.Trend.DECLINING -> "下降"
        com.xingmou.core.domain.Trend.INSUFFICIENT_DATA -> "数据不足"
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

    private fun toChildSummary(child: ChildEntity) = ChildSummaryUi(child.childId, child.alias, child.ageBand, child.status)
}

private val HOME_DEMO_STEPS = listOf("准备", "示范", "邀请", "回应", "结束")
