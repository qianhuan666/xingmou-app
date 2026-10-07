package com.xingmou

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xingmou.core.agent.AgentContextInput
import com.xingmou.core.agent.AgentEventCoordinator
import com.xingmou.core.agent.AgentEventProcessor
import com.xingmou.core.agent.AgentOrchestrationRequest
import com.xingmou.core.agent.AgentOrchestrationResult
import com.xingmou.core.agent.AgentOrchestrator
import com.xingmou.core.agent.AgentRunState
import com.xingmou.core.agent.DecisionTraceFactory
import com.xingmou.core.agent.DecisionEvidence
import com.xingmou.core.agent.PolicyBackedModelGateway
import com.xingmou.core.agent.ConsecutiveFailuresEvent
import com.xingmou.core.agent.ModelGateway
import com.xingmou.core.agent.ParentObservationAddedEvent
import com.xingmou.core.agent.PlanRejectedEvent
import com.xingmou.core.agent.ReviewApprovedEvent
import com.xingmou.core.agent.RiskDetectedEvent
import com.xingmou.core.agent.RoomAgentEventStore
import com.xingmou.core.agent.SessionResumedEvent
import com.xingmou.core.agent.TrainingCompletedEvent
import com.xingmou.core.consent.ConsentStatus
import com.xingmou.core.consent.DataRightsManager
import com.xingmou.core.consent.AuthorizedImportManager
import com.xingmou.core.organization.LocalCapability
import com.xingmou.core.organization.LocalRolePolicy
import com.xingmou.core.llm.ChatLlmGateway
import com.xingmou.core.llm.ChatLlmProvider
import com.xingmou.core.llm.ChatMessage
import com.xingmou.core.llm.DirectDeepSeekGateway
import com.xingmou.core.llm.GatewayCallRequest
import com.xingmou.core.llm.GatewayPolicy
import com.xingmou.core.llm.LocalApiKeyStore
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
import com.xingmou.core.domain.QuestionEvaluator
import com.xingmou.core.domain.PlanQuestionPolicy
import com.xingmou.core.domain.TrainingResult
import com.xingmou.core.domain.AdaptiveDifficultyEngine
import com.xingmou.core.domain.AdaptiveOutcome
import com.xingmou.core.domain.BaselineDifficultyPolicy
import com.xingmou.core.domain.ModuleQuestionSelectionRequest
import com.xingmou.core.domain.ModuleQuestionSelector
import com.xingmou.core.model.CommunicationLevel
import com.xingmou.core.model.Port
import com.xingmou.core.model.SessionContext
import com.xingmou.core.safety.SafeResponses
import com.xingmou.core.perception.ActionGestureDetector
import com.xingmou.core.perception.DetectSessionController
import com.xingmou.core.perception.FeedbackController
import com.xingmou.core.perception.PerceptionManager
import com.xingmou.core.perception.PerceptionState
import com.xingmou.core.perception.SessionRecorder
import com.xingmou.core.perception.SpeechAnswerDetector
import com.xingmou.core.perception.SpeechHit
import com.xingmou.core.perception.StateAnalyzer
import com.xingmou.data.db.PlanVersionEntity
import com.xingmou.data.db.ModuleAdaptiveStateEntity
import com.xingmou.data.db.ChildEntity
import com.xingmou.data.db.QizhiDatabase
import com.xingmou.data.db.ReviewRequestEntity
import com.xingmou.data.db.SeedData
import com.xingmou.data.db.ConsentEntity
import com.xingmou.data.db.DataRequestEntity
import com.xingmou.data.db.AbilityProfileEntity
import com.xingmou.data.db.HomeFeedbackEntity
import com.xingmou.data.db.HomeTaskEntity
import com.xingmou.data.db.AssessmentRecordEntity
import com.xingmou.data.db.CareRecordEntity
import com.xingmou.data.db.AgentEventEntity
import com.xingmou.data.db.OrganizationEntity
import com.xingmou.data.db.LocalSessionEntity
import com.xingmou.data.db.LocalUserEntity
import com.xingmou.data.catalog.ExpectedAction
import com.xingmou.data.catalog.ExpectedSpeech
import com.xingmou.data.catalog.QuestionCatalog
import com.xingmou.data.catalog.QuestionDefinition
import com.xingmou.data.catalog.CurriculumCatalog
import com.xingmou.data.catalog.CurriculumCatalog.GeneratedCurriculumLevel
import com.xingmou.data.catalog.InterestThemeSkins
import com.xingmou.data.catalog.DomainCatalog
import com.xingmou.data.catalog.TaskCatalog
import com.xingmou.data.catalog.AssessmentCatalog
import com.xingmou.AutoDetectState
import com.xingmou.BaselineUiState
import com.xingmou.DetectMode
import com.xingmou.DetectPhase
import com.xingmou.ReportMetricUi
import com.xingmou.ReportTrendPointUi
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class XingmouViewModel(application: Application) : AndroidViewModel(application) {
    private val accessibilityPreferences = application.getSharedPreferences("xingmou_accessibility", 0)
    private val curriculumPrefs = application.getSharedPreferences("xingmou_curriculum", 0)
    private val loginPreferences = application.getSharedPreferences("xingmou_login", 0)
    private val database = QizhiDatabase.getInstance(application)
    private val eventCoordinator = AgentEventCoordinator(
        AgentEventProcessor(),
        RoomAgentEventStore(database)
    )
    private val knowledgeRetriever = KnowledgeRetriever()
    private val analysisEngine = AnalysisEngine()
    private val baselineEngine = BaselineEngine()
    private val planStateMachine = PlanStateMachine()
    private val dataRightsManager = DataRightsManager()
    private val importManager = AuthorizedImportManager()
    private val apiKeyStore = LocalApiKeyStore(application)

    /**
     * 当前供应商是否已对 App 端就绪。
     * 豆包：内置 ARK_API_KEY 非空（开箱即用）或用户在专业端自填 Key；
     * DeepSeek/千问：必须用户在专业端自填 Key。
     */
    private fun isProviderReady(provider: ChatLlmProvider): Boolean =
        if (provider == ChatLlmProvider.DOUBAO) BuildConfig.ARK_API_KEY.isNotBlank() || apiKeyStore.isConfigured(provider)
        else apiKeyStore.isConfigured(provider)
    private var activeChildId: String? = null
    private var pendingImport: com.xingmou.core.consent.AuthorizedChildExport? = null
    private var generatedCurriculum: List<GeneratedCurriculumLevel> = emptyList()
    private val curriculumPassedOrders = mutableSetOf<Int>()
    private val curriculumLevelStars = mutableMapOf<Int, Int>()
    private var curriculumInterestChosen = false
    private var curriculumInterest = ""
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

    private val _uiState = MutableStateFlow(
        XingmouUiState(
            accessibility = AccessibilityUiState(
                speechEnabled = accessibilityPreferences.getBoolean("speech_enabled", true),
                speechRate = normalizeSpeechRate(accessibilityPreferences.getFloat("speech_rate", 1.0f)),
                speechVolume = normalizeSpeechVolume(accessibilityPreferences.getFloat("speech_volume", 1.0f)),
                largeText = accessibilityPreferences.getBoolean("large_text", false),
                highContrast = accessibilityPreferences.getBoolean("high_contrast", false),
                slowMotion = accessibilityPreferences.getBoolean("slow_motion", false)
            ),
            aiConfigured = apiKeyStore.isConfigured(),
            apiKeyMessage = if (apiKeyStore.isConfigured()) "已设置设备本地 API Key。" else "未设置 API Key；当前使用本地安全模式。",
            child = ChildUiState(
                chatProvider = apiKeyStore.selectedProvider(),
                chatProviderConfigured = isProviderReady(apiKeyStore.selectedProvider())
            )
        )
    )
    val uiState: StateFlow<XingmouUiState> = _uiState.asStateFlow()

    private var activePlan: PlanVersionEntity? = null
    private var activeReview: ReviewRequestEntity? = null
    private var baselineSession = BaselineSession()
    // 保证基线落库严格按作答顺序串行，避免旧题快照在 IO 线程乱序覆盖完成状态
    private val baselinePersistMutex = Mutex()
    private var perceptionManager: PerceptionManager? = null
    private var feedbackController: FeedbackController? = null
    private var sessionRecorder: SessionRecorder? = null
    private var lastMeltdown = false
    private var lastChildRestoreAttempted = false

    // 观察题自动检测
    private val detectController = DetectSessionController()
    private var actionDetector: ActionGestureDetector? = null
    private var speechDetector: SpeechAnswerDetector? = null
    private var detectTickerJob: kotlinx.coroutines.Job? = null
    private var guardPerception = false
    private var questionCameraWanted = false
    private var savedPreviewEnabled: Boolean? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    init {
        viewModelScope.launch {
            loadLocalOrganization()
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
                if (!lastChildRestoreAttempted) {
                    lastChildRestoreAttempted = true
                    loginPreferences.getString("last_child_id", null)
                        ?.takeIf { id -> children.any { it.childId == id } }
                        ?.let { quickEnterChild(it) }
                }
            }
        }
        refreshProfessionalAnalysis()

        detectController.onAutoAnswer = { questionId, matchedKeyword ->
            autoAnswerDetected(questionId, matchedKeyword)
        }
        detectController.onEncourage = { isCamera ->
            val msg = if (isCamera) "再来一次，让小星看到你的小手～" else "没关系，再大声说一次～"
            _uiState.update { it.copy(child = it.child.copy(perceptionFeedback = msg)) }
        }
        viewModelScope.launch {
            _uiState.collect { snapshot ->
                syncDetectSession(snapshot)
            }
        }
    }

    private suspend fun loadLocalOrganization() {
        val organization = database.organizationDao().active() ?: SeedData.defaultOrganization
        val user = database.localUserDao().findById(localUserId) ?: SeedData.defaultUser
        database.organizationDao().upsert(organization)
        database.localUserDao().upsert(user)
        database.localSessionDao().revokeAllActive()
        database.localSessionDao().upsert(LocalSessionEntity(newId("session"), user.userId, user.role, createdAt = System.currentTimeMillis(), expiresAt = null))
        val users = database.localUserDao().activeForOrganization(organization.organizationId)
        _uiState.update { it.copy(organizationName = organization.name, localUserName = user.displayName, localUserRole = user.role, institutionMessage = "已启用本地角色：${LocalRolePolicy.normalize(user.role).label}", localUsers = users.map { LocalUserUi(it.userId, it.displayName, it.login, it.role, it.status) }) }
    }

    fun saveLocalOrganization(name: String, displayName: String, role: String) {
        val normalizedName = name.trim().take(80)
        val normalizedDisplay = displayName.trim().take(40)
        val normalizedRole = LocalRolePolicy.normalize(role).name.lowercase()
        if (normalizedName.isBlank() || normalizedDisplay.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            database.organizationDao().upsert(OrganizationEntity(SeedData.DEMO_ORGANIZATION_ID, normalizedName, createdAt = 0L, updatedAt = now))
            database.localUserDao().update(localUserId, normalizedDisplay, normalizedRole, "active", now)
            database.localSessionDao().revokeAllActive()
            database.localSessionDao().upsert(LocalSessionEntity(newId("session"), localUserId, normalizedRole, createdAt = now, expiresAt = null))
            val users = database.localUserDao().activeForOrganization(SeedData.DEMO_ORGANIZATION_ID)
            _uiState.update { it.copy(organizationName = normalizedName, localUserName = normalizedDisplay, localUserRole = normalizedRole, institutionMessage = "已切换本地角色：${LocalRolePolicy.normalize(normalizedRole).label}", localUsers = users.map { LocalUserUi(it.userId, it.displayName, it.login, it.role, it.status) }) }
        }
    }

    fun createLocalRoleUser(displayName: String, login: String, role: String) {
        if (!LocalRolePolicy.can(_uiState.value.localUserRole, LocalCapability.MANAGE_USERS)) return
        val name = displayName.trim().take(40)
        val account = login.trim().lowercase().take(40)
        if (name.isBlank() || account.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                database.localUserDao().upsert(LocalUserEntity(newId("user"), SeedData.DEMO_ORGANIZATION_ID, name, account, LocalRolePolicy.normalize(role).name.lowercase(), createdAt = now, updatedAt = now))
                database.localUserDao().activeForOrganization(SeedData.DEMO_ORGANIZATION_ID)
            }.onSuccess { users ->
                _uiState.update { it.copy(localUsers = users.map { user -> LocalUserUi(user.userId, user.displayName, user.login, user.role, user.status) }, institutionMessage = "已新增本地角色用户。") }
            }.onFailure { error ->
                _uiState.update { it.copy(institutionMessage = "新增用户失败：${error.message ?: "login 可能重复"}") }
            }
        }
    }

    fun updateLocalRoleUser(userId: String, displayName: String, role: String, active: Boolean) {
        if (!LocalRolePolicy.can(_uiState.value.localUserRole, LocalCapability.MANAGE_USERS)) return
        if (userId == localUserId && !active) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            database.localUserDao().update(userId, displayName.trim().take(40), LocalRolePolicy.normalize(role).name.lowercase(), if (active) "active" else "disabled", now)
            val users = database.localUserDao().activeForOrganization(SeedData.DEMO_ORGANIZATION_ID)
            _uiState.update { it.copy(localUsers = users.map { user -> LocalUserUi(user.userId, user.displayName, user.login, user.role, user.status) }, institutionMessage = "本地角色已更新。") }
        }
    }

    fun selectChild(childId: String) {
        viewModelScope.launch {
            val child = database.childDao().findById(childId) ?: return@launch
            activeChildId = child.childId
            _uiState.update { it.copy(activeChildId = child.childId, activeChildAlias = child.alias,
                baseline = it.baseline.copy(isOpen = false, isWorking = false),
                professional = it.professional.copy(auditRuns = emptyList(), auditReplay = emptyList(),
                    auditSelectedRunId = null, auditMessage = "点击刷新查看当前儿童的 Agent 运行记录。")) }
            loadConsentState(child.childId)
            refreshProfessionalAnalysis()
            loadBaseline(child)
            loadCourseProgress(child.childId)
            loadHomeSupport(child.childId)
        }
    }

    // ---------- 感知系统 ----------
    fun togglePerception(enabled: Boolean) {
        if (enabled) startPerception() else stopPerception()
    }

    fun togglePerceptionPreview(show: Boolean) {
        _uiState.update { it.copy(child = it.child.copy(perceptionPreview = show)) }
    }

    fun attachPerceptionPreviewView(previewView: androidx.camera.view.PreviewView) {
        perceptionManager?.attachPreviewView(previewView)
    }

    fun detachPerceptionPreviewView(previewView: androidx.camera.view.PreviewView) {
        perceptionManager?.detachPreviewView(previewView)
    }

    private var guardInited = false

    private fun startPerception() {
        guardPerception = true
        if (!guardInited) {
            guardInited = true
            initGuardFeedback()
        }
        refreshPerception()
        _uiState.update { it.copy(child = it.child.copy(perceptionEnabled = true)) }
    }

    private fun stopPerception() {
        guardPerception = false
        feedbackController?.reset()
        refreshPerception()
        // 守护开关关闭：结束并释放守护侧反馈/记录资源（动作题会话不持有它们）。
        sessionRecorder?.finish()
        sessionRecorder = null
        feedbackController = null
        lastMeltdown = false
        guardInited = false
        _uiState.update {
            it.copy(child = it.child.copy(
                perceptionEnabled = false,
                perceptionEmotion = "",
                perceptionFocus = "",
                perceptionFeedback = ""
            ))
        }
    }

    private fun initGuardFeedback() {
        feedbackController = FeedbackController { feedback ->
            _uiState.update { it.copy(child = it.child.copy(perceptionFeedback = feedback.message)) }
            if (feedback.level == FeedbackController.Feedback.Level.L3_PAUSE) {
                _uiState.update { it.copy(child = it.child.copy(isPaused = true)) }
            }
        }
        sessionRecorder = SessionRecorder(database.perceptionSessionDao(), childId)
    }

    /** 引用计数：守护开关或动作题任一方需要就启动，双方都不需要才停止。 */
    private fun refreshPerception() {
        val need = guardPerception || questionCameraWanted
        val app = getApplication<Application>()
        if (need && perceptionManager == null) {
            val mgr = PerceptionManager(app) { frame ->
                if (guardPerception) {
                    val state = StateAnalyzer.analyze(frame, lastMeltdown)
                    lastMeltdown = state.isMeltdown
                    sessionRecorder?.onState(state)
                    feedbackController?.onState(state)
                    _uiState.update {
                        it.copy(child = it.child.copy(
                            perceptionEmotion = state.emotion.name,
                            perceptionFocus = state.focusLevel.name
                        ))
                    }
                }
                if (questionCameraWanted) {
                    val detector = actionDetector
                    val qid = detectController.session?.questionId
                    if (detector != null && qid != null) {
                        val now = System.currentTimeMillis()
                        val hit = runCatching { detector.onFrame(frame, now) }.getOrDefault(false)
                        if (hit) mainHandler.post { detectController.onHit(null, now) }
                    }
                }
            }
            perceptionManager = mgr
            mgr.start()
        } else if (!need && perceptionManager != null) {
            perceptionManager?.stop()
            perceptionManager = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPerception()
        endDetectSession()
    }

    // ---------- 观察题自动检测会话 ----------
    private fun currentDetectQuestion(snapshot: XingmouUiState): QuestionDefinition? {
        val baseline = snapshot.baseline
        if (baseline.isOpen && baseline.status == com.xingmou.core.domain.BaselineStatus.IN_PROGRESS) {
            baseline.question?.let { return it }
        }
        val child = snapshot.child
        val player = child.curriculumPlayer
        if (!child.isPaused && !child.isSafetyStopped && !player.finished && !player.isWorking) {
            player.question?.let { return it }
        }
        return null
    }

    private fun syncDetectSession(snapshot: XingmouUiState) {
        val q = currentDetectQuestion(snapshot)
        val isDetectable = q != null && (q.expectedAction != null || q.expectedSpeech != null)
        val activeId = detectController.session?.questionId
        when {
            !isDetectable -> {
                if (activeId != null) endDetectSession()
            }
            q!!.id != activeId -> beginDetectSession(q)
        }
    }

    private fun beginDetectSession(q: QuestionDefinition) {
        endDetectSession(restorePreview = false)
        val isCamera = q.expectedAction != null
        actionDetector = q.expectedAction?.let { ActionGestureDetector(it) }
        detectController.start(q.id, isCamera)
        pushDetectState(q, DetectPhase.AWAIT_PERMISSION)

        val permission = if (isCamera) Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO
        val granted = ContextCompat.checkSelfPermission(getApplication(), permission) ==
            PackageManager.PERMISSION_GRANTED

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // 未授权：停留在 AWAIT_PERMISSION，由 QuestionPermissionGate 引导申请（不自动兜底）。
            // 语音题已授权但系统无识别服务：视同不可用，直接手动兜底。
            val serviceUnavailable = isCamera.not() &&
                !SpeechAnswerDetector(getApplication(), {}, {}).isAvailable()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (detectController.session?.questionId != q.id) return@withContext
                if (!granted) return@withContext
                detectController.onPermissionReady(!serviceUnavailable)
                if (serviceUnavailable) {
                    pushDetectState(q, DetectPhase.MANUAL_FALLBACK)
                } else {
                    startDetectionHardware(q)
                }
            }
        }
    }

    private fun startDetectionHardware(q: QuestionDefinition) {
        detectController.begin(System.currentTimeMillis())
        startDetectTicker()
        when {
            q.expectedAction != null -> {
                savedPreviewEnabled = savedPreviewEnabled ?: _uiState.value.child.perceptionPreview
                _uiState.update { it.copy(child = it.child.copy(perceptionPreview = true)) }
                questionCameraWanted = true
                refreshPerception()
                viewModelScope.launch {
                    // 模拟器调试：liteMode 下 30 秒后才降级，方便观察检测面板 UI
                    delay(30_000)
                    if (perceptionManager?.liteMode == true &&
                        detectController.session?.questionId == q.id) {
                        forceManualFallback(q.id)
                    }
                }
            }
            q.expectedSpeech != null -> {
                val expected = q.expectedSpeech
                val detector = SpeechAnswerDetector(
                    getApplication(),
                    onSpeech = { text ->
                        if (detectController.session?.questionId != q.id) return@SpeechAnswerDetector
                        when (val hit = SpeechAnswerDetector.evaluate(text, expected)) {
                            is SpeechHit.SPOKEN -> detectController.onHit(hit.matchedKeyword, System.currentTimeMillis())
                            SpeechHit.NONE -> Unit
                        }
                    },
                    onUnavailable = { mainHandler.post { forceManualFallback(q.id) } }
                )
                speechDetector = detector
                detector.start()
            }
        }
        pushDetectState(q, DetectPhase.DETECTING)
    }

    private fun forceManualFallback(questionId: String) {
        if (detectController.session?.questionId != questionId) return
        // 状态机迁移到 MANUAL_FALLBACK；停止硬件但保留会话（ticker 下次刷新会把新相位推给 UI）
        detectController.forceManual()
        questionCameraWanted = false
        speechDetector?.stop(); speechDetector = null
        refreshPerception()
        val q = currentDetectQuestion(_uiState.value) ?: return
        pushDetectState(q, DetectPhase.MANUAL_FALLBACK)
    }

    private fun startDetectTicker() {
        detectTickerJob?.cancel()
        detectTickerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(100)
                detectController.tick(System.currentTimeMillis())
                val s = detectController.session ?: break
                _uiState.update { st ->
                    st.copy(child = st.child.copy(autoDetect = st.child.autoDetect.copy(
                        questionId = s.questionId,
                        mode = if (s.isCamera) DetectMode.CAMERA else DetectMode.SPEECH,
                        phase = DetectPhase.valueOf(s.phase.name),
                        remainingMs = s.remainingMs,
                        timeoutMs = s.timeoutMs
                    )))
                }
            }
        }
    }

    private fun autoAnswerDetected(questionId: String, matchedKeyword: String?) {
        val q = currentDetectQuestion(_uiState.value) ?: return
        if (q.id != questionId) return
        questionCameraWanted = false
        speechDetector?.stop(); speechDetector = null
        refreshPerception()
        detectTickerJob?.cancel()
        if (matchedKeyword != null) {
            _uiState.update { it.copy(child = it.child.copy(perceptionFeedback = "对啦，是$matchedKeyword！")) }
        }
        pushDetectState(q, DetectPhase.HIT)
        // 路由到现有作答通道，等价于自动点「自己完成」(option 0)
        val isBaseline = _uiState.value.baseline.question?.id == questionId
        if (isBaseline) answerBaseline(0) else answerCurriculumActivity(0)
    }

    private fun pushDetectState(q: QuestionDefinition, phase: DetectPhase) {
        val isCamera = q.expectedAction != null
        val hint = when (phase) {
            DetectPhase.AWAIT_PERMISSION -> if (isCamera) "小星想看看你的小手" else "小星想听听你的声音"
            DetectPhase.DETECTING, DetectPhase.HIT ->
                if (isCamera) "小星正在看你的小手～" else "小星在听哦，大声说出来～"
            DetectPhase.RETRYING ->
                if (isCamera) "再来一次，让小星看到你的小手～" else "没关系，再大声说一次～"
            DetectPhase.MANUAL_FALLBACK -> "没关系，也可以点这里告诉我"
            DetectPhase.IDLE -> ""
        }
        val permission = if (isCamera) Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO
        _uiState.update {
            it.copy(child = it.child.copy(autoDetect = AutoDetectState(
                questionId = q.id,
                mode = if (isCamera) DetectMode.CAMERA else DetectMode.SPEECH,
                phase = phase,
                hint = hint,
                remainingMs = detectController.session?.remainingMs ?: 0L,
                permission = permission,
                matchedKeyword = null
            )))
        }
    }

    private fun endDetectSession(restorePreview: Boolean = true) {
        detectTickerJob?.cancel()
        questionCameraWanted = false
        actionDetector = null
        speechDetector?.stop(); speechDetector = null
        refreshPerception()
        if (restorePreview) {
            savedPreviewEnabled?.let { saved ->
                _uiState.update { it.copy(child = it.child.copy(perceptionPreview = saved)) }
            }
        }
        savedPreviewEnabled = null
        detectController.stop()
        _uiState.update { it.copy(child = it.child.copy(autoDetect = AutoDetectState())) }
    }

    // 供 UI 调用
    fun onDetectPermissionResolved(granted: Boolean) {
        val q = currentDetectQuestion(_uiState.value) ?: return
        if (detectController.session?.questionId != q.id) return
        detectController.onPermissionReady(granted)
        if (granted) startDetectionHardware(q) else pushDetectState(q, DetectPhase.MANUAL_FALLBACK)
    }

    fun onDetectChooseManual() {
        // AWAIT_PERMISSION 由 chooseManual 处理；DETECTING/RETRYING 由 forceManual 处理。
        // 硬件继续运行，孩子手动作/开口仍可自动作答，谁先到算谁。
        detectController.chooseManual()
        detectController.forceManual()
        val q = currentDetectQuestion(_uiState.value) ?: return
        pushDetectState(q, DetectPhase.MANUAL_FALLBACK)
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

    fun saveInstitutionApiKey(value: String) {
        val saved = runCatching { apiKeyStore.save(value) }.getOrDefault(false)
        _uiState.update {
            it.copy(
                aiConfigured = apiKeyStore.isConfigured(),
                apiKeyMessage = if (saved) "API Key 已保存到当前设备。" else "保存失败：请检查 Key 格式（以 sk- 开头）。"
            )
        }
    }

    fun clearInstitutionApiKey() {
        val cleared = apiKeyStore.clear()
        _uiState.update {
            it.copy(
                aiConfigured = apiKeyStore.isConfigured(),
                apiKeyMessage = if (cleared) "已清除设备 API Key，恢复本地安全模式。" else "清除失败，请重试。"
            )
        }
    }

    // ---- 交互模式（多供应商 LLM 聊天） ----

    fun selectChatProvider(provider: ChatLlmProvider) {
        apiKeyStore.setSelectedProvider(provider)
        _uiState.update {
            it.copy(child = it.child.copy(
                chatProvider = provider,
                chatProviderConfigured = isProviderReady(provider),
                chatError = null
            ))
        }
    }

    fun saveChatApiKey(provider: ChatLlmProvider, value: String) {
        val saved = runCatching { apiKeyStore.save(provider, value) }.getOrDefault(false)
        _uiState.update {
            it.copy(
                apiKeyMessage = if (saved) "${provider.label} API Key 已保存。" else "保存失败：Key 格式不正确。",
                child = if (provider == it.child.chatProvider) {
                    it.child.copy(chatProviderConfigured = isProviderReady(provider))
                } else it.child
            )
        }
    }

    fun clearChatApiKey(provider: ChatLlmProvider) {
        apiKeyStore.clear(provider)
        _uiState.update {
            it.copy(
                apiKeyMessage = "已清除 ${provider.label} API Key。",
                child = if (provider == it.child.chatProvider) {
                    it.child.copy(chatProviderConfigured = isProviderReady(provider))
                } else it.child
            )
        }
    }

    fun saveModelEndpoint(provider: ChatLlmProvider, endpoint: String) {
        apiKeyStore.setModelEndpoint(provider, endpoint)
        _uiState.update { it.copy(apiKeyMessage = "${provider.label} 推理接入点已保存。") }
    }

    private fun chatGateway(): ChatLlmGateway {
        val provider = _uiState.value.child.chatProvider
        return ChatLlmGateway(
            provider = provider,
            keyProvider = {
                when (provider) {
                    ChatLlmProvider.DEEPSEEK -> apiKeyStore.get(provider)
                    ChatLlmProvider.DOUBAO -> BuildConfig.ARK_API_KEY.takeIf { it.isNotBlank() } ?: apiKeyStore.get(provider)
                    else -> apiKeyStore.get(provider)
                }
            },
            modelOrEndpoint = apiKeyStore.modelEndpoint(provider)
        )
    }

    private val chatSystemPrompt = """
        你是"小星"，一个陪伴儿童进行言语与认知训练的温柔伙伴。
        请用简短、温暖、鼓励的语气回答，每次回答不超过三句话。
        不说危险、暴力或不适宜儿童的内容；遇到不会的问题就温和地说"这个小星还不太清楚，我们一起想想吧"。
    """.trimIndent()

    fun sendChatMessage(text: String) {
        val content = text.trim()
        if (content.isBlank()) return
        val userMsg = ChatMessageUi(newId("chat"), "user", content)
        _uiState.update {
            it.copy(child = it.child.copy(
                chatMessages = it.child.chatMessages + userMsg,
                chatLoading = true,
                chatError = null,
                chatHint = ""
            ))
        }
        viewModelScope.launch {
            val provider = _uiState.value.child.chatProvider
            val history = _uiState.value.child.chatMessages
                .filterNot { it.isError }
                .map { ChatMessage(it.role, it.content) }
            val result = chatGateway().chat(chatSystemPrompt, history, content)
            result.fold(
                onSuccess = { answer ->
                    _uiState.update {
                        it.copy(child = it.child.copy(
                            chatMessages = it.child.chatMessages + ChatMessageUi(newId("chat"), "assistant", answer),
                            chatLoading = false
                        ))
                    }
                },
                onFailure = { err ->
                    val reason = when (err.message) {
                        "api_key_missing" -> "还没有配置 ${provider.label} 的 API Key，请先到设置里填写。"
                        "network_error" -> "网络好像不太通畅，小星没连上，稍后再试试吧。"
                        "http_429" -> "问得太快啦，小星需要喘口气，稍等一下。"
                        "http_401", "http_403" -> "${provider.label} 的 Key 好像不对，请检查一下。"
                        "model_endpoint_missing" -> "豆包需要填写推理接入点 ID（ep- 开头）。"
                        else -> "小星走神了，没听清，能再说一遍吗？"
                    }
                    _uiState.update {
                        it.copy(child = it.child.copy(
                            chatMessages = it.child.chatMessages + ChatMessageUi(newId("chat"), "assistant", reason, isError = true),
                            chatLoading = false,
                            chatError = reason
                        ))
                    }
                }
            )
        }
    }

    private fun buildParentOnlineSystemPrompt(childContext: String): String = """
        你是一位专业的儿童言语治疗师和家庭教育指导师。你正在为一个有特殊需要的孩子的家长提供建议。

        【孩子背景信息】
        $childContext

        【家长当前观察】
        家长描述了以下情况，请结合孩子的背景给出个性化建议。

        要求：
        1. 先分析孩子当前状态与背景的关联（如：孩子在语言域较弱，家长观察到的困难可能与这个相关）
        2. 给出 3-5 条具体建议，每条包含：做什么、怎么做、什么时候做
        3. 建议要结合孩子的实际能力水平，不要建议超出当前能力的任务
        4. 如果观察到危险信号（能力倒退、持续哭闹、回避所有任务），建议咨询专业人员
        5. 用温暖、鼓励的语气，像朋友一样交流，控制在 300 字以内
        6. 不要使用任何 Markdown 符号（如 **、##、-），用自然的中文分段纯文本叙述
    """.trimIndent()

    fun askParentQuestionOnline() {
        val query = _uiState.value.parent.query.trim()
        if (query.isBlank()) {
            _uiState.update { it.copy(parent = it.parent.copy(message = "请先写下一个具体观察。", riskLabel = "需要补充")) }
            return
        }
        val provider = _uiState.value.child.chatProvider
        if (!isProviderReady(provider)) {
            _uiState.update { it.copy(parent = it.parent.copy(message = "请先在设置中配置 ${provider.label} 的 API Key，或使用本地知识库。", riskLabel = "未配置")) }
            return
        }
        _uiState.update { it.copy(parent = it.parent.copy(isWorking = true, message = "正在分析孩子数据并获取建议…", agentStatus = agentStateLabel(AgentRunState.CREATED))) }
        viewModelScope.launch {
            runCatching {
                // 获取孩子训练数据
                val records = database.trainingRecordDao().recentForChild(childId)
                val profile = database.abilityProfileDao().latestForChild(childId)
                val recentFeedback = database.homeFeedbackDao().recentForChild(childId, 7)

                // 构建孩子上下文
                val childContext = buildString {
                    appendLine("- 训练记录：最近完成 ${records.size} 次训练")
                    if (records.isNotEmpty()) {
                        val correct = records.count { it.correct }
                        val accuracy = correct * 100 / records.size
                        appendLine("- 正确率：$accuracy%（${correct}/${records.size}）")
                        val domains = records.groupBy { it.domain }.map { (d, r) ->
                            val c = r.count { it.correct }
                            "$d 域 ${c * 100 / r.size}% (${c}/${r.size})"
                        }
                        appendLine("- 各域表现：${domains.joinToString(", ")}")
                    }
                    if (profile != null) {
                        val scores = parseProfileScores(profile.scoresJson)
                        val weakDomains = scores.filter { it.value < 50 }.map { DomainCatalog.find(it.key)?.name ?: it.key }
                        if (weakDomains.isNotEmpty()) {
                            appendLine("- 需要加强的域：${weakDomains.joinToString("、")}")
                        }
                        val strongDomains = scores.filter { it.value >= 70 }.map { DomainCatalog.find(it.key)?.name ?: it.key }
                        if (strongDomains.isNotEmpty()) {
                            appendLine("- 优势域：${strongDomains.joinToString("、")}")
                        }
                    }
                    if (recentFeedback.isNotEmpty()) {
                        val latest = recentFeedback.first()
                        appendLine("- 最近状态：${latest.mood}（${java.text.SimpleDateFormat("MM-dd").format(java.util.Date(latest.createdAt))}）")
                    }
                }

                val systemPrompt = buildParentOnlineSystemPrompt(childContext)
                val result = chatGateway().chat(systemPrompt, emptyList(), query)
                result.fold(
                    onSuccess = { answer ->
                        // 纯文本展示：去掉模型可能残留的 Markdown 加粗/标题符号
                        val cleaned = answer.replace("**", "").replace("##", "").trim()
                        _uiState.update {
                            it.copy(parent = it.parent.copy(
                                isWorking = false,
                                message = "已基于孩子数据获取个性化建议",
                                suggestions = listOf(cleaned),
                                sources = listOf("AI 个性化建议（${provider.label} · 基于孩子训练数据）"),
                                agentStatus = agentStateLabel(AgentRunState.COMPLETED)
                            ))
                        }
                    },
                    onFailure = { err ->
                        val reason = when (err.message) {
                            "api_key_missing" -> "请先在设置中配置 ${provider.label} 的 API Key"
                            "network_error" -> "网络连接失败，请检查网络后重试"
                            "http_429" -> "请求太频繁，请稍后再试"
                            "http_401", "http_403" -> "API Key 无效，请检查配置"
                            else -> "获取在线建议失败，请稍后重试"
                        }
                        _uiState.update {
                            it.copy(parent = it.parent.copy(
                                isWorking = false,
                                message = reason,
                                agentStatus = agentStateLabel(AgentRunState.FAILED)
                            ))
                        }
                    }
                )
            }
        }
    }

    fun setExportConsent(granted: Boolean) = setConsent("export", granted)

    fun exportAuthorizedData(destination: Uri, format: String = "json") {
        val currentId = activeChildId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(dataRightsWorking = true, dataRightsMessage = "正在生成授权数据导出文件。") }
            runCatching {
                val child = database.childDao().findById(currentId) ?: error("child_not_found")
                check(database.childBindingDao().findActive(localUserId, currentId) != null) { "child_access_denied" }
                val consentStatus = database.consentDao().find(currentId, "export")?.status.toConsentStatus()
                val now = System.currentTimeMillis()
                val requestId = newId("export-request")
                val json = dataRightsManager.buildAuthorizedExport(
                    consentStatus = consentStatus,
                    child = child,
                    trainingRecords = database.trainingRecordDao().allForChild(currentId),
                    homeTasks = database.homeTaskDao().allForChild(currentId),
                    homeFeedback = database.homeFeedbackDao().allForChild(currentId),
                    assessments = database.assessmentRecordDao().allForChild(currentId),
                    careRecords = database.careRecordDao().allForChild(currentId),
                    exportedAt = now,
                    abilityProfiles = database.abilityProfileDao().allForChild(currentId),
                    plans = database.planDao().allForChild(currentId),
                    reviews = database.agentDao().reviewsForChild(currentId),
                    consents = database.consentDao().forChild(currentId)
                )
                // 再次读取授权，防止系统文件选择器打开期间撤回授权。
                check(database.consentDao().find(currentId, "export")?.status == "granted") {
                    "export_requires_explicit_consent"
                }
                val content = if (format == "csv") dataRightsManager.toCsv(json) else json
                val bytes = content.toByteArray(Charsets.UTF_8)
                getApplication<Application>().contentResolver.openOutputStream(destination, "w")?.use {
                    it.write(bytes)
                } ?: error("export_destination_unavailable")
                database.dataRightsDao().upsertRequest(
                    DataRequestEntity(
                        requestId = requestId,
                        childId = currentId,
                        requestType = "EXPORT",
                        status = "completed",
                        requestedAt = now,
                        completedAt = now,
                        resultJson = """{"destination":"user_selected","format":"$format","bytes":${bytes.size}}""",
                        requesterUserId = localUserId
                    )
                )
                database.agentDao().upsertEvent(
                    AgentEventEntity(
                        eventId = newId("audit-export"),
                        runId = null,
                        childId = currentId,
                        eventType = "DATA_EXPORT_COMPLETED",
                        payloadSummary = "requestId=$requestId;scope=current-child;bytes=${bytes.size}",
                        status = "processed",
                        createdAt = now,
                        processedAt = now
                    )
                )
                _uiState.update {
                    it.copy(
                        dataRightsWorking = false,
                        dataRightsMessage = "已保存当前儿童授权数据到所选文件。"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        dataRightsWorking = false,
                        dataRightsMessage = when (error.message) {
                            "export_requires_explicit_consent" -> "导出前请先打开“数据导出”授权。"
                            else -> "导出未完成：${error.message ?: error.javaClass.simpleName}"
                        }
                    )
                }
            }
        }
    }

    fun previewAuthorizedImport(source: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = getApplication<Application>().contentResolver.openInputStream(source)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("import_source_unavailable")
                val data = importManager.parse(text)
                val preview = importManager.preview(data)
                check(database.childDao().findById(preview.childId) == null) { "import_child_conflict" }
                _uiState.update { it.copy(importReady = true, dataRightsMessage = "已读取恢复预览：${preview.alias}，共 ${preview.recordCount} 条授权记录；确认后将新建本地副本。") }
                pendingImport = data
            }.onFailure { error ->
                pendingImport = null
                _uiState.update { it.copy(importReady = false, dataRightsMessage = "恢复文件不可用：${error.message ?: error.javaClass.simpleName}") }
            }
        }
    }

    fun confirmAuthorizedImport() {
        val data = pendingImport ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(dataRightsWorking = true, importReady = false, dataRightsMessage = "正在恢复授权数据副本。") }
            runCatching {
                val count = importManager.restore(data, database, localUserId)
                pendingImport = null
                _uiState.update { it.copy(dataRightsWorking = false, dataRightsMessage = "恢复完成：新增 $count 条授权记录；远程 AI/导出授权需在本设备重新确认。") }
            }.onFailure { error ->
                pendingImport = null
                _uiState.update { it.copy(dataRightsWorking = false, importReady = false, dataRightsMessage = "恢复未完成：${error.message ?: error.javaClass.simpleName}") }
            }
        }
    }

    fun cancelAuthorizedImport() {
        pendingImport = null
        _uiState.update { it.copy(importReady = false, dataRightsMessage = "已取消数据恢复。") }
    }

    fun deleteActiveChild() {
        val currentId = activeChildId ?: return
        viewModelScope.launch {
            val activeChildren = database.childDao().observeActive().first()
            if (activeChildren.size <= 1) {
                _uiState.update { it.copy(dataRightsMessage = "当前至少保留一个儿童档案，暂不能删除唯一档案。") }
                return@launch
            }
            _uiState.update { it.copy(dataRightsWorking = true, dataRightsMessage = "正在删除当前儿童及其授权范围数据。") }
            runCatching {
                val now = System.currentTimeMillis()
                check(database.childBindingDao().findActive(localUserId, currentId) != null) { "child_access_denied" }
                val requestId = newId("delete-request")
                database.dataRightsDao().executeChildDeletion(
                    DataRequestEntity(
                        requestId = requestId, childId = currentId, requestType = "DELETE",
                        status = "requested", requestedAt = now, requesterUserId = localUserId
                    ),
                    completedAt = System.currentTimeMillis()
                )
                _uiState.update {
                    it.copy(
                        dataRightsWorking = false,
                        dataRightsMessage = "已删除当前儿童档案及关联数据，删除结果已留存。"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        dataRightsWorking = false,
                        dataRightsMessage = "删除未完成：${error.message ?: error.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun startBaseline() {
        if (_uiState.value.baseline.status != BaselineStatus.IN_PROGRESS) {
            baselineSession = baselineEngine.newSession(
                System.currentTimeMillis(),
                nextBaselineVersion()
            )
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
        baselineSession = baselineEngine.newSession(
            System.currentTimeMillis(),
            nextBaselineVersion()
        )
        publishBaseline(isOpen = false)
        persistBaseline()
    }

    private fun nextBaselineVersion(): Int = when (baselineSession.status) {
        BaselineStatus.COMPLETED, BaselineStatus.NEEDS_RETEST -> baselineSession.version + 1
        else -> baselineSession.version
    }

    fun restartBaseline() {
        resetBaselineSession()
        publishBaseline(isOpen = true)
    }

    fun answerBaseline(option: Int) {
        if (_uiState.value.baseline.status != BaselineStatus.IN_PROGRESS || _uiState.value.baseline.isWorking) return
        // 作答瞬间就同步记录正误，让音效立即播放，不等落库
        val answeredAt = System.currentTimeMillis()
        val questionNow = _uiState.value.baseline.question
        val preEvaluation = questionNow?.let {
            runCatching { QuestionEvaluator.evaluate(it, option.coerceIn(0, it.options.lastIndex)) }.getOrNull()
        }
        _uiState.update { it.copy(
            baseline = it.baseline.copy(isWorking = true),
            child = it.child.copy(lastAnswerCorrect = preEvaluation?.correct, lastAnswerAt = answeredAt)
        ) }
        viewModelScope.launch {
            baselineSession = baselineEngine.answer(baselineSession, option, answeredAt)
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
                initializeAdaptiveStates(scores, baselineSession.version, now)
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

    private fun String?.toConsentStatus(): ConsentStatus = when (this) {
        "granted" -> ConsentStatus.GRANTED
        "revoked" -> ConsentStatus.REVOKED
        else -> ConsentStatus.NOT_GRANTED
    }

    private suspend fun loadBaseline(child: ChildEntity) {
        // 保存答题结果会触发 childDao 观察流再次加载儿童；保留当前打开状态，
        // 避免答完一题后被数据库刷新折叠成“继续基线”。切换儿童时由 selectChild 主动关闭。
        val keepOpen = _uiState.value.baseline.isOpen
        baselineSession = baselineEngine.fromJson(child.baselineJson) ?: BaselineSession()
        publishBaseline(isOpen = keepOpen)
    }

    private suspend fun loadCourseProgress(childId: String) {
        val activePlan = database.planDao().latestActive(childId)
        val planPolicy = PlanQuestionPolicy.from(activePlan)
        val childSnapshot = _uiState.value.child
        val allRecords = database.trainingRecordDao().recentForChild(childId, 400)
        val domainScores = if (baselineSession.status == BaselineStatus.COMPLETED) baselineEngine.scores(baselineSession) else emptyMap()
        val moduleQueue = planPolicy?.moduleId?.let { listOf(it) }
            ?: BaselineDifficultyPolicy.priorityModules(domainScores)
                .ifEmpty { TaskCatalog.all.map { it.id } }
        val stateByModule = database.moduleAdaptiveStateDao().allForChild(childId).associateBy { it.moduleId }
        val completedIds = allRecords.filter { it.correct }.map { it.taskId }.toSet()
        val currentModuleId = moduleQueue.firstOrNull { moduleId ->
            val bank = if (planPolicy == null) QuestionCatalog.starterCourseQuestions else QuestionCatalog.fullCourseQuestions
            bank.any { it.moduleId == moduleId && it.id !in completedIds }
        }
        val currentState = currentModuleId?.let { stateByModule[it] }?.takeIf { state ->
            state.baselineVersion == baselineSession.version &&
                if (planPolicy == null) {
                    state.planId == null && state.planVersion == null
                } else {
                    state.planId == planPolicy.planId && state.planVersion == planPolicy.planVersion
                }
        }
        val initialDifficulty = planPolicy?.difficulty
            ?: currentModuleId?.let { BaselineDifficultyPolicy.initialDifficulty(it, domainScores) }
            ?: childSnapshot.difficulty
        val effectiveDifficulty = currentState?.currentDifficulty?.coerceIn(1, 5) ?: initialDifficulty.coerceIn(1, 5)
        val samePlan = planPolicy != null && planPolicy.planId == childSnapshot.coursePlanId && planPolicy.planVersion == childSnapshot.coursePlanVersion
        val effectiveSupportLevel = if (samePlan) childSnapshot.supportLevel else planPolicy?.supportLevel ?: currentState?.currentSupportLevel
            ?.let { runCatching { com.xingmou.core.model.SupportLevel.valueOf(it) }.getOrNull() }
            ?: childSnapshot.supportLevel
        val bank = if (planPolicy == null) QuestionCatalog.starterCourseQuestions else QuestionCatalog.fullCourseQuestions
        val moduleQuestions = currentModuleId?.let { id -> bank.filter { it.moduleId == id } }.orEmpty()
        val records = allRecords.filter { it.taskId in moduleQuestions.map { question -> question.id }.toSet() }
        val progressEngine = CourseProgressEngine(moduleQuestions)
        val progress = progressEngine.summarize(records)
        val encouragement = progressEngine.encouragement(progress, records)
        val currentModuleName = currentModuleId?.let { TaskCatalog.find(it)?.name ?: it }
        val nextQuestion = currentModuleId?.let { moduleId ->
            ModuleQuestionSelector.select(
                bank,
                ModuleQuestionSelectionRequest(
                    moduleId = moduleId,
                    difficulty = effectiveDifficulty,
                    sessionId = "course-$childId-$moduleId",
                    completedQuestionIds = completedIds,
                    limit = 1
                )
            ).firstOrNull()
        }
        val completedLevels = moduleQueue.indexOf(currentModuleId).coerceAtLeast(0)
        val courseMap = V08_COURSE_LEVELS.mapIndexed { index, level ->
            val status = when {
                baselineSession.status != BaselineStatus.COMPLETED && index == 0 -> "完成基线后解锁"
                index < completedLevels -> "已完成"
                index == completedLevels -> "进行中"
                else -> "待解锁"
            }
            level.copy(status = status)
        }
        val currentLevel = (completedLevels + 1).coerceIn(1, V08_COURSE_LEVELS.size)
        _uiState.update {
            it.copy(child = it.child.copy(
                instruction = nextQuestion?.prompt ?: if (currentModuleId == null) "课程完成了，可以休息一下" else "当前模块已完成，可以继续下一题",
                options = nextQuestion?.options ?: it.child.options,
                courseProgress = progress.completedCount,
                courseTotal = moduleQuestions.size,
                currentCourseLevel = currentLevel,
                courseTitle = currentModuleName ?: "课程完成",
                courseQuestionId = nextQuestion?.id,
                courseQuestionType = nextQuestion?.type ?: com.xingmou.data.catalog.QuestionType.CHOICE,
                courseStimulus = nextQuestion?.stimulus.orEmpty(),
                coursePreviewMs = nextQuestion?.previewMs ?: 3_000L,
                assetKey = nextQuestion?.assetKey ?: it.child.assetKey,
                coursePlanId = planPolicy?.planId,
                coursePlanVersion = planPolicy?.planVersion,
                coursePlanModuleId = planPolicy?.moduleId,
                coursePlanGoal = planPolicy?.observableGoal.orEmpty(),
                difficulty = effectiveDifficulty,
                supportLevel = effectiveSupportLevel,
                courseUnlocked = baselineSession.status == BaselineStatus.COMPLETED,
                courseOpen = true,
                courseSummary = if (currentModuleId == null) "22 个训练模块已完成" else "$currentModuleName · ${progress.completedCount}/${moduleQuestions.size} 题",
                coursePoints = encouragement.points,
                completedRounds = encouragement.completedRounds,
                encouragementTrend = encouragement.trendLabel,
                rewardMessage = encouragement.rewardMessage,
                courseMap = courseMap
            ))
        }
        loadCurriculumState(childId)
        loadRainbowProfile(childId)
        refreshCurriculumMap()
    }

    private suspend fun initializeAdaptiveStates(scores: Map<String, Int>, baselineVersion: Int, now: Long) {
        BaselineDifficultyPolicy.moduleSeeds(scores).forEach { seed ->
            val existing = database.moduleAdaptiveStateDao().find(childId, seed.moduleId)
            if (existing?.baselineVersion == baselineVersion) return@forEach
            database.moduleAdaptiveStateDao().upsert(
                ModuleAdaptiveStateEntity(
                    stateId = "adaptive-$childId-${seed.moduleId}",
                    childId = childId,
                    moduleId = seed.moduleId,
                    baselineVersion = baselineVersion,
                    planId = null,
                    planVersion = null,
                    currentDifficulty = seed.initialDifficulty,
                    currentSupportLevel = "L1",
                    correctStreak = 0,
                    errorStreak = 0,
                    lastQuestionId = null,
                    updatedAt = now
                )
            )
        }
    }

    private suspend fun persistAdaptiveState(
        question: QuestionDefinition,
        result: TrainingResult,
        nextDifficulty: Int,
        nextSupportLevel: String?,
        planId: String? = null,
        planVersion: Int? = null,
        now: Long
    ) {
        val previous = database.moduleAdaptiveStateDao().find(childId, question.moduleId)?.takeIf { state ->
            state.baselineVersion == baselineSession.version &&
                state.planId == planId && state.planVersion == planVersion
        }
        val outcome = AdaptiveDifficultyEngine.outcomeOf(result)
        val positive = outcome == AdaptiveOutcome.CORRECT_INDEPENDENT || outcome == AdaptiveOutcome.OBSERVED_INDEPENDENT
        val negative = outcome == AdaptiveOutcome.INCORRECT || outcome == AdaptiveOutcome.OBSERVED_NOT_COMPLETED
        database.moduleAdaptiveStateDao().upsert(
            ModuleAdaptiveStateEntity(
                stateId = previous?.stateId ?: "adaptive-$childId-${question.moduleId}",
                childId = childId,
                moduleId = question.moduleId,
                baselineVersion = baselineSession.version,
                planId = planId,
                planVersion = planVersion,
                currentDifficulty = nextDifficulty.coerceIn(1, 5),
                currentSupportLevel = nextSupportLevel ?: previous?.currentSupportLevel ?: "L1",
                correctStreak = if (positive) (previous?.correctStreak ?: 0) + 1 else 0,
                errorStreak = if (negative) (previous?.errorStreak ?: 0) + 1 else 0,
                lastQuestionId = question.id,
                updatedAt = now
            )
        )
    }

    private fun adaptiveOutcome(question: QuestionDefinition, selectedOption: Int, evaluation: com.xingmou.core.domain.QuestionEvaluation): AdaptiveOutcome {
        if (question.type == com.xingmou.data.catalog.QuestionType.OBSERVED) {
            return when (selectedOption) {
                0 -> AdaptiveOutcome.OBSERVED_INDEPENDENT
                1 -> AdaptiveOutcome.OBSERVED_SUPPORTED
                else -> AdaptiveOutcome.OBSERVED_NOT_COMPLETED
            }
        }
        return if (evaluation.correct == true) AdaptiveOutcome.CORRECT_INDEPENDENT else AdaptiveOutcome.INCORRECT
    }

    private fun domainEmoji(id: String): String = when (id) {
        "A" -> "👀"; "B" -> "🧠"; "C" -> "🧩"; "D" -> "💬"; "E" -> "😊"; "F" -> "👐"
        else -> "⭐"
    }

    /** 复刻 Web 端 SafeProfileEngine 的离线鼓励叙述：最弱项作为「先练」方向，最强项作为「好办法」。 */
    private fun childNarrative(scores: Map<String, Int>): String {
        val entries = scores.entries.sortedByDescending { it.value }
        if (entries.isEmpty()) return "你的游戏足迹已经保存，我们会按你的节奏安排下一次训练。"
        val strongest = DomainCatalog.find(entries.first().key)?.name ?: "游戏"
        val gentle = DomainCatalog.find(entries.last().key)?.name ?: "游戏"
        return "你在${strongest}小游戏里找到了自己的好办法！接下来我们会从轻松的${gentle}游戏开始，慢慢玩、慢慢进步，每一次尝试都值得一颗星星。"
    }

    /** 家长向能力概括：逐域分组点评+领域专属建议+整体水平。避免诊断性语言。 */
    private fun parentSummary(scores: Map<String, Int>): String {
        val entries = scores.entries.sortedByDescending { it.value }
        if (entries.isEmpty()) return "孩子还没完成起点小测，完成后这里会显示能力概括。"
        val parts = mutableListOf<String>()
        // 强项
        val strong = entries.filter { it.value >= 50 }
        if (strong.isNotEmpty()) {
            val names = strong.joinToString("、") { "${DomainCatalog.find(it.key)?.name ?: it.key}（${it.value}分）" }
            parts.add("表现不错的是${names}")
        }
        // 发展中
        val developing = entries.filter { it.value in 30..49 }
        if (developing.isNotEmpty()) {
            val names = developing.joinToString("、") { "${DomainCatalog.find(it.key)?.name ?: it.key}（${it.value}分）" }
            parts.add("正在发展中的是${names}，日常可以多穿插相关小活动")
        }
        // 需更多练习（<30分）
        val weak = entries.filter { it.value < 30 }
        if (weak.isNotEmpty()) {
            val weakParts = weak.map { e ->
                val name = DomainCatalog.find(e.key)?.name ?: e.key
                val suggestion = domainSuggestion(e.key)
                val status = if (e.value == 0) "尚未测评" else "${e.value}分"
                "$name（$status）" to suggestion
            }
            val weakNames = weakParts.joinToString("、") { it.first }
            val tips = weakParts.mapNotNull { it.second }.filter { it.isNotBlank() }.joinToString("；")
            parts.add("需要更多练习的是${weakNames}${if (tips.isNotBlank()) "。建议：$tips" else ""}")
        }
        // 整体
        val avg = entries.map { it.value }.average().toInt()
        val assessed = entries.count { it.value > 0 }
        parts.add("整体来看，已测评${assessed}个领域，均分约${avg}，处于${scoreLevelWord(avg)}水平")
        return parts.joinToString("；") + "。"
    }

    /** 展开区用的详细建议文本：列出弱项领域的日常陪练方向。 */
    private fun parentDetailAdvice(scores: Map<String, Int>): String {
        val entries = scores.entries.sortedByDescending { it.value }
        val weak = entries.filter { it.value < 30 }
        if (weak.isEmpty()) return "各领域发展均衡，继续保持日常练习。"
        return weak.joinToString("；") { e ->
            val name = DomainCatalog.find(e.key)?.name ?: e.key
            val status = if (e.value == 0) "尚未测评" else "${e.value}分"
            val tip = domainSuggestion(e.key)
            "$name（$status）${if (tip.isNotBlank()) "：$tip" else ""}"
        } + "。"
    }

    /** 每个领域的日常陪练小建议，给家长具体可操作的方向。 */
    private fun domainSuggestion(domainId: String): String = when (domainId) {
        "A" -> "玩'找不同'图片、听声音猜东西；玩'红绿灯'练习等待和停止"
        "B" -> "让孩子记3样购物清单、复述刚讲的短故事；玩翻牌配对记忆游戏"
        "C" -> "按颜色或用途分类卡片、按'红蓝红蓝'规律排序；玩简单迷宫和拼图"
        "D" -> "一起看绘本让孩子复述、指物说名字；玩过家家练习对话"
        "E" -> "看表情图卡识别开心/难过/生气、讲故事问'他什么心情'；玩轮流拍球练习等待"
        "F" -> "让孩子摆碗筷、分类收玩具；练习穿外套系扣子、用安全剪刀沿线剪"
        else -> ""
    }

    private fun parseProfileScores(json: String): Map<String, Int> {
        val raw = runCatching {
            com.google.gson.Gson().fromJson<Map<String, Double>>(
                json,
                object : com.google.gson.reflect.TypeToken<Map<String, Double>>() {}.type
            )
        }.getOrNull() ?: emptyMap()
        return raw.mapValues { it.value.toInt().coerceIn(0, 100) }
    }

    private fun buildRainbowProfileUi(profile: AbilityProfileEntity?, scores: Map<String, Int>): RainbowProfileUi {
        if (profile == null) return RainbowProfileUi()
        val createdLabel = runCatching {
            SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(profile.createdAt))
        }.getOrDefault("最近")
        return RainbowProfileUi(
            present = true,
            domainBars = listOf("A", "B", "C", "D", "E", "F").map { id ->
                val domain = DomainCatalog.find(id)
                RainbowDomainUi(
                    id = id,
                    name = domain?.name ?: id,
                    emoji = domainEmoji(id),
                    colorKey = domain?.displayColor ?: "slate",
                    score = scores[id] ?: 0,
                    description = domain?.summary ?: ""
                )
            },
            narrative = childNarrative(scores),
            parentSummary = parentSummary(scores),
            parentDetailAdvice = parentDetailAdvice(scores),
            createdLabel = createdLabel
        )
    }

    /** 从最新 ability_profiles 记录构建彩虹画像；配了 API Key 时异步用模型生成叙述，失败则保留本地文案。 */
    private suspend fun loadRainbowProfile(scopedChildId: String) {
        val profile = database.abilityProfileDao().latestForChild(scopedChildId)
        val scores = if (profile == null) emptyMap() else parseProfileScores(profile.scoresJson)
        val ui = buildRainbowProfileUi(profile, scores)
        _uiState.update { it.copy(child = it.child.copy(rainbowProfile = ui), parent = it.parent.copy(profile = ui)) }
        if (ui.present && apiKeyStore.isConfigured()) {
            viewModelScope.launch {
                val ai = fetchAiNarrative(scopedChildId, scores)
                if (ai != null && activeChildId == scopedChildId) {
                    val enriched = ui.copy(narrative = ai)
                    _uiState.update {
                        it.copy(child = it.child.copy(rainbowProfile = enriched), parent = it.parent.copy(profile = enriched))
                    }
                }
            }
        }
    }

    /** 配 API Key、已授权远程 AI 且存在绑定关系时，调用 DeepSeek 生成儿童友好叙述；否则返回 null 回退本地文案。 */
    private suspend fun fetchAiNarrative(scopedChildId: String, scores: Map<String, Int>): String? {
        val consent = database.consentDao().find(scopedChildId, "remote_ai")?.status.toConsentStatus()
        val binding = database.childBindingDao().findActive(localUserId, scopedChildId)
        if (!apiKeyStore.isConfigured() || consent != ConsentStatus.GRANTED || binding == null) return null
        val entries = scores.entries.sortedByDescending { it.value }
        if (entries.isEmpty()) return null
        val strongest = DomainCatalog.find(entries.first().key)?.name ?: "游戏"
        val gentle = DomainCatalog.find(entries.last().key)?.name ?: "游戏"
        val systemPrompt = "你是儿童康复训练平台的安全文案助手。只写鼓励性、非诊断、非标签化文字，不比较儿童，不使用落后、缺陷、异常、失败、智力低等词，不承诺疗效。"
        val userText = "六域训练起点分数：${com.google.gson.Gson().toJson(scores)}。相对强项：${strongest}；建议先练：${gentle}。请写给儿童本人，只输出 JSON 对象 {\"narrative\":\"一句60字以内中文\"}。"
        val gateway = PolicyBackedModelGateway(
            delegate = DirectDeepSeekGateway(apiKeyStore::get),
            policy = GatewayPolicy(),
            requestProvider = {
                GatewayCallRequest(
                    runId = newId("profile-narrative"),
                    childId = scopedChildId,
                    port = Port.PARENT.name.lowercase(),
                    systemPrompt = "",
                    userText = "",
                    consentStatus = consent
                )
            }
        )
        val result = withTimeoutOrNull(15_000L) { gateway.complete(systemPrompt, userText) }
        return result?.getOrNull()?.let { sanitizeNarrative(it) }
    }

    /** 二次安全过滤：剥掉 think/换行，抽取 JSON 的 narrative 字段，命中危险词则回退本地文案。 */
    private fun sanitizeNarrative(raw: String): String? {
        val unsafe = Regex("诊断|智商|智力低|缺陷|异常|落后|失败|不如|治愈|保证|病|障碍严重")
        val cleaned = raw.replace(Regex("<think>[\\s\\S]*?</think>"), "")
            .replace(Regex("[\\r\\n#*]"), "").trim().take(100)
        if (cleaned.isBlank() || unsafe.containsMatchIn(cleaned)) return null
        val extracted = runCatching {
            val obj = com.google.gson.JsonParser.parseString(cleaned).asJsonObject
            obj.get("narrative")?.takeIf { it.isJsonPrimitive }?.asString
                ?: obj.get("text")?.takeIf { it.isJsonPrimitive }?.asString
                ?: obj.get("content")?.takeIf { it.isJsonPrimitive }?.asString
                ?: ""
        }.getOrDefault("")
        val final = if (extracted.isNotBlank()) extracted.take(100) else cleaned
        return final.takeIf { it.isNotBlank() && !unsafe.containsMatchIn(it) }
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

    private fun persistBaseline(snapshot: BaselineSession = baselineSession) {
        // 默认参数在调用线程同步求值，立即捕获此刻的会话；协程内禁止再读共享字段 baselineSession——
        // Room 观察流会在落库回调中用旧 JSON 覆盖该字段，延迟读取会把刚完成的 COMPLETED 写回旧快照。
        // 儿童体验模式下 activeChildId 为 null，需用带默认档案兜底的 childId。
        val currentId = childId
        viewModelScope.launch {
            baselinePersistMutex.withLock {
                val current = database.childDao().findById(currentId) ?: return@withLock
                val nextVersion = if (snapshot.status == BaselineStatus.COMPLETED) current.profileVersion + 1 else current.profileVersion
                database.childDao().updateBaseline(currentId, baselineEngine.toJson(snapshot), nextVersion, System.currentTimeMillis())
            }
        }
    }

    fun selectPort(port: Port) {
        val capability = when (port) {
            Port.CHILD -> LocalCapability.TRAIN_CHILD
            Port.PARENT -> LocalCapability.RECORD_HOME
            Port.PROFESSIONAL -> LocalCapability.REVIEW_PLAN
        }
        if (!LocalRolePolicy.can(_uiState.value.localUserRole, capability)) {
            _uiState.update { it.copy(institutionMessage = "当前角色无权进入${port.name}端。") }
            return
        }
        _uiState.update { it.copy(selectedPort = port) }
        if (port == Port.PROFESSIONAL) refreshProfessionalAnalysis()
    }

    fun updateLoginIdentifier(value: String) {
        _uiState.update { it.copy(loginIdentifier = value.take(80), loginMessage = "") }
    }

    fun updateLoginPassword(value: String) {
        _uiState.update { it.copy(loginPassword = value.take(128), loginMessage = "") }
    }

    fun selectLoginRole(port: Port) {
        _uiState.update { it.copy(loginRole = port, loginMessage = "") }
    }

    fun openAdultLogin() {
        _uiState.update {
            it.copy(showAdultLogin = true, loginRole = it.loginRole ?: Port.PARENT, loginMessage = "请选择登录身份。")
        }
    }

    fun closeAdultLogin() {
        _uiState.update { it.copy(showAdultLogin = false, loginMessage = "") }
    }

    /** 儿童零输入进入：复用本机 session 隐式登录并直接切到对应儿童档案。 */
    fun quickEnterChild(childId: String) {
        if (_uiState.value.availableChildren.none { it.childId == childId }) return
        viewModelScope.launch {
            val child = database.childDao().findById(childId) ?: return@launch
            val session = database.localSessionDao().active()
            val user = session?.let { database.localUserDao().findById(it.userId) }
                ?: database.localUserDao().activeForOrganization(SeedData.DEMO_ORGANIZATION_ID)
                    .firstOrNull { it.login == "parent" }
            if (user == null) return@launch
            database.localSessionDao().revokeAllActive()
            database.localSessionDao().upsert(
                LocalSessionEntity(newId("session"), user.userId, user.role, createdAt = System.currentTimeMillis(), expiresAt = null)
            )
            activeChildId = child.childId
            _uiState.update {
                it.copy(
                    isLoggedIn = true, loginRole = Port.CHILD, selectedPort = Port.CHILD,
                    activeChildId = child.childId, activeChildAlias = child.alias,
                    localUserName = user.displayName, localUserRole = user.role,
                    loginIdentifier = "", loginPassword = "", loginMessage = "",
                    showAdultLogin = false
                )
            }
            loadConsentState(child.childId)
            loadBaseline(child)
            loadCourseProgress(child.childId)
            loadHomeSupport(child.childId)
            loginPreferences.edit().putString("last_child_id", childId).apply()
        }
    }

    fun loginAs(port: Port) {
        val state = _uiState.value
        val identifier = state.loginIdentifier.trim()
        if (identifier.isBlank()) {
            _uiState.update { it.copy(loginMessage = "请输入登录标识。") }
            return
        }
        viewModelScope.launch {
            val users = database.localUserDao().activeForOrganization(SeedData.DEMO_ORGANIZATION_ID)
            val user = users.firstOrNull { it.login.equals(identifier, ignoreCase = true) }
            val allowedRole = when (port) {
                Port.CHILD -> setOf("admin", "professional", "parent")
                Port.PARENT -> setOf("admin", "professional", "parent")
                Port.PROFESSIONAL -> setOf("admin", "professional")
            }
            if (user == null || user.role !in allowedRole) {
                val label = when (port) { Port.CHILD -> "儿童端"; Port.PARENT -> "家长端"; Port.PROFESSIONAL -> "康复专业人员端" }
                _uiState.update { it.copy(loginMessage = "当前登录标识无权进入$label。") }
                return@launch
            }
            database.localSessionDao().revokeAllActive()
            database.localSessionDao().upsert(LocalSessionEntity(newId("session"), user.userId, user.role, createdAt = System.currentTimeMillis(), expiresAt = null))
            _uiState.update { it.copy(isLoggedIn = true, loginRole = port, selectedPort = port,
                localUserName = user.displayName, localUserRole = user.role,
                loginPassword = "", loginMessage = "") }
        }
    }

    fun logout() {
        viewModelScope.launch {
            database.localSessionDao().revokeAllActive()
            _uiState.update { it.copy(isLoggedIn = false, loginRole = null, loginPassword = "", loginMessage = "请选择登录身份。") }
        }
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

    fun setSlowMotion(enabled: Boolean) {
        accessibilityPreferences.edit().putBoolean("slow_motion", enabled).apply()
        _uiState.update { it.copy(accessibility = it.accessibility.copy(slowMotion = enabled)) }
    }

    fun selectInterest(value: String) {
        // 与地图顶栏的 chooseCurriculumInterest 完全等价：设置页选主题同样视为“已选兴趣”，
        // 否则地图的选主题卡片不消失、基线完成后第 1 关也不会解锁。
        chooseCurriculumInterest(value)
    }

    // ---- 20 关彩虹冒险：地图 + 播放器（作答落库 + 进度持久化）----

    /** 通关/星级按「孩子 + 兴趣」分桶：不同兴趣各有一条独立冒险进度。 */
    private fun passedOrdersKey(scopedChildId: String, interest: String) = "passed_orders_${scopedChildId}_$interest"
    private fun levelStarsKey(scopedChildId: String, interest: String) = "level_stars_${scopedChildId}_$interest"

    private fun parsePassedOrders(raw: String?) {
        curriculumPassedOrders.clear()
        if (!raw.isNullOrBlank()) {
            curriculumPassedOrders.addAll(raw.split(',').mapNotNull { it.trim().toIntOrNull() })
        }
    }

    private fun parseLevelStars(raw: String?) {
        curriculumLevelStars.clear()
        if (!raw.isNullOrBlank()) {
            raw.split(',').forEach { pair ->
                val parts = pair.trim().split(':')
                val order = parts.getOrNull(0)?.toIntOrNull()
                val stars = parts.getOrNull(1)?.toIntOrNull()
                if (order != null && stars != null && stars in 1..3) curriculumLevelStars[order] = stars
            }
        }
    }

    /** 从 SharedPreferences 恢复当前儿童的兴趣门槛与「当前兴趣」的通关进度（键按 childId+兴趣隔离）。 */
    private fun loadCurriculumState(scopedChildId: String) {
        curriculumInterestChosen = curriculumPrefs.getBoolean("interest_chosen_$scopedChildId", false)
        curriculumInterest = curriculumPrefs.getString("interest_value_$scopedChildId", null).orEmpty()

        curriculumPassedOrders.clear()
        curriculumLevelStars.clear()
        if (curriculumInterest.isBlank()) {
            // 尚未选过兴趣，不存在通关数据（第 1 关本就锁定）；旧数据会在选出兴趣后的迁移分支接管
            return
        }

        val bucketKey = passedOrdersKey(scopedChildId, curriculumInterest)
        val bucketStarsKey = levelStarsKey(scopedChildId, curriculumInterest)
        if (curriculumPrefs.contains(bucketKey) || curriculumPrefs.contains(bucketStarsKey)) {
            parsePassedOrders(curriculumPrefs.getString(bucketKey, null))
            parseLevelStars(curriculumPrefs.getString(bucketStarsKey, null))
        } else {
            // 首次升级到兴趣分桶：旧版无后缀数据归属到当时保存的兴趣
            val legacyPassed = curriculumPrefs.getString("passed_orders_$scopedChildId", null)
            val legacyStars = curriculumPrefs.getString("level_stars_$scopedChildId", null)
            parsePassedOrders(legacyPassed)
            parseLevelStars(legacyStars)
            if (!legacyPassed.isNullOrBlank() || !legacyStars.isNullOrBlank()) {
                persistCurriculumState()
                curriculumPrefs.edit()
                    .remove("passed_orders_$scopedChildId")
                    .remove("level_stars_$scopedChildId")
                    .apply()
            }
        }
        _uiState.update { it.copy(child = it.child.copy(interest = curriculumInterest)) }
    }

    /** 把当前兴趣的通关顺序、星级与兴趣门槛写回 SharedPreferences，进程重启后解锁链不归零。 */
    private fun persistCurriculumState() {
        val editor = curriculumPrefs.edit()
            .putBoolean("interest_chosen_$childId", curriculumInterestChosen)
            .putString("interest_value_$childId", curriculumInterest)
        if (curriculumInterest.isNotBlank()) {
            editor
                .putString(passedOrdersKey(childId, curriculumInterest), curriculumPassedOrders.sorted().joinToString(","))
                .putString(levelStarsKey(childId, curriculumInterest), curriculumLevelStars.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" })
        }
        editor.apply()
    }

    /** 通关正确率映射星级：≥90% 三星、≥75% 两星、通过即至少一星（与 isPassed 的 60% 门槛对齐）。 */
    private fun starRatingFor(correct: Int, total: Int): Int {
        if (total <= 0) return 0
        val accuracy = correct.toDouble() / total
        return when {
            accuracy >= 0.9 -> 3
            accuracy >= 0.75 -> 2
            else -> 1
        }
    }

    /** 按基线得分（最弱在前）排序能力域；无基线时用默认 A..F。 */
    private fun domainScoreOrder(): List<String> {
        val scores = baselineEngine.scores(baselineSession)
        val known = listOf("A", "B", "C", "D", "E", "F")
        if (scores.isEmpty()) return known
        return known.sortedBy { scores[it] ?: 100 }
    }

    private fun ensureCurriculum(): List<GeneratedCurriculumLevel> {
        if (generatedCurriculum.isEmpty()) {
            generatedCurriculum = CurriculumCatalog.buildCurriculumLevels(domainScoreOrder())
        }
        return generatedCurriculum
    }

    private fun isLevelAvailable(order: Int): Boolean {
        if (order == 1) return baselineSession.status == BaselineStatus.COMPLETED && curriculumInterestChosen
        return (order - 1) in curriculumPassedOrders
    }

    private fun buildCurriculumMap(): CurriculumMapUi {
        val child = _uiState.value.child
        val baselineDone = baselineSession.status == BaselineStatus.COMPLETED
        val levels = ensureCurriculum().map { level ->
            val status = when {
                level.order in curriculumPassedOrders -> CurriculumLevelStatus.COMPLETED
                level.order == 1 && baselineDone && curriculumInterestChosen -> CurriculumLevelStatus.AVAILABLE
                level.order > 1 && (level.order - 1) in curriculumPassedOrders -> CurriculumLevelStatus.AVAILABLE
                else -> CurriculumLevelStatus.LOCKED
            }
            CurriculumLevelUi(
                order = level.order,
                title = level.title,
                icon = level.icon,
                theme = level.theme,
                difficulty = level.difficulty,
                status = status,
                stars = if (status == CurriculumLevelStatus.COMPLETED) curriculumLevelStars[level.order] ?: 1 else 0
            )
        }
        return CurriculumMapUi(
            levels = levels,
            completedLevels = levels.count { it.status == CurriculumLevelStatus.COMPLETED },
            totalLevels = levels.size,
            activeLevel = levels.firstOrNull { it.status == CurriculumLevelStatus.AVAILABLE }?.order ?: 1,
            interest = child.interest,
            interestOptions = child.interestOptions,
            interestChosen = curriculumInterestChosen
        )
    }

    fun refreshCurriculumMap() {
        _uiState.update { it.copy(child = it.child.copy(curriculumMap = buildCurriculumMap())) }
    }

    /**
     * 兴趣门槛/随时换主题：选好或更换主题后，把旧兴趣的进度桶落盘，再载入新兴趣自己的
     * 通关/星级（各兴趣进度相互独立），第一关对已选兴趣均开放（门槛只表示“选过主题”）。
     */
    fun chooseCurriculumInterest(value: String) {
        if (value == curriculumInterest && curriculumInterestChosen) {
            refreshCurriculumMap()
            return
        }
        // 先把当前（旧）兴趣的进度确保落盘
        if (curriculumInterest.isNotBlank()) persistCurriculumState()
        curriculumInterestChosen = true
        curriculumInterest = value
        // 内存切换为新兴趣的桶：有存档就读，没有就是一条全新冒险线
        parsePassedOrders(curriculumPrefs.getString(passedOrdersKey(childId, value), null))
        parseLevelStars(curriculumPrefs.getString(levelStarsKey(childId, value), null))
        persistCurriculumState()
        _uiState.update { it.copy(child = it.child.copy(interest = value)) }
        refreshCurriculumMap()
    }

    /**
     * 家长重置：清空基线结果、所有兴趣各自的关卡点亮/星级与兴趣门槛，回到首次进入地图的状态
     * （第 1 关锁定、底部提示先做起点小测）。基线 JSON 置空、profileVersion 归零。
     */
    fun resetCurriculumAndBaseline() {
        baselineSession = BaselineSession()
        val currentId = childId
        viewModelScope.launch {
            baselinePersistMutex.withLock {
                val current = database.childDao().findById(currentId)
                if (current != null) {
                    database.childDao().updateBaseline(currentId, "", 0, System.currentTimeMillis())
                }
            }
        }
        curriculumPassedOrders.clear()
        curriculumLevelStars.clear()
        curriculumInterestChosen = false
        curriculumInterest = ""
        // 删除该孩子全部兴趣分桶（passed_orders_<id>_<兴趣> / level_stars_<id>_<兴趣>），
        // 以及旧版无后缀键；用「精确或下划线分隔」匹配，避免误伤 id 前缀相同的其他孩子
        val editor = curriculumPrefs.edit()
        curriculumPrefs.all.keys
            .filter { key ->
                key == "passed_orders_$currentId" || key.startsWith("passed_orders_${currentId}_") ||
                    key == "level_stars_$currentId" || key.startsWith("level_stars_${currentId}_")
            }
            .forEach { editor.remove(it) }
        editor
            .remove("interest_chosen_$currentId")
            .remove("interest_value_$currentId")
            .apply()
        publishBaseline(isOpen = false)
        _uiState.update { it.copy(child = it.child.copy(courseOpen = false, interest = "")) }
        refreshCurriculumMap()
    }

    fun openCurriculumLevel(order: Int) {
        if (order !in curriculumPassedOrders && !isLevelAvailable(order)) return
        viewModelScope.launch {
            val level = ensureCurriculum().firstOrNull { it.order == order } ?: return@launch
            val activities = level.activities
            if (activities.isEmpty()) return@launch
            val policy = PlanQuestionPolicy.from(database.planDao().latestActive(childId))
            val first = activities.first()
            val question = CurriculumCatalog.resolveQuestion(first)?.let { base ->
                val leveled = if (policy == null) base else PlanQuestionPolicy.run { base.forDifficulty(policy.difficulty) }
                InterestThemeSkins.apply(leveled, curriculumInterest)
            }
            _uiState.update { it.copy(child = it.child.copy(
                difficulty = policy?.difficulty ?: it.child.difficulty,
                supportLevel = policy?.supportLevel ?: it.child.supportLevel,
                coursePlanId = policy?.planId,
                coursePlanVersion = policy?.planVersion,
                coursePlanModuleId = policy?.moduleId,
                coursePlanGoal = policy?.observableGoal.orEmpty(),
                curriculumPlayer = CurriculumPlayerUi(
                    levelOrder = order,
                    levelTitle = level.title,
                    activityIndex = 0,
                    activityTotal = activities.size,
                    activityLabel = first.label,
                    question = question,
                    runCompleted = 0, runCorrect = 0, runTotal = 0,
                    isWorking = false,
                    message = "",
                    finished = false, passed = false
                )
            )) }
        }
    }

    fun leaveCurriculumLevel() {
        _uiState.update { it.copy(child = it.child.copy(curriculumPlayer = CurriculumPlayerUi())) }
        refreshCurriculumMap()
    }

    fun answerCurriculumActivity(option: Int) {
        val snapshot = _uiState.value.child
        val player = snapshot.curriculumPlayer
        val question = player.question
        if (player.isWorking || player.finished || question == null) return
        val evaluation = QuestionEvaluator.evaluate(question, option)
        if (!evaluation.isValidSelection) {
            _uiState.update { it.copy(child = it.child.copy(curriculumPlayer = player.copy(message = "请选择题目中的一个选项。"))) }
            return
        }
        val completed = evaluation.completed
        val correct = evaluation.correct ?: completed
        // 作答瞬间就同步记录正误，让音效立即播放，不等落库
        _uiState.update { it.copy(child = it.child.copy(
            curriculumPlayer = player.copy(isWorking = true),
            lastAnswerCorrect = evaluation.correct,
            lastAnswerAt = System.currentTimeMillis()
        )) }
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                val result = TrainingResult(
                    taskId = question.id,
                    correct = correct,
                    firstCorrect = correct,
                    reactionMs = 1_500L,
                    errorType = if (correct) null else "choice_mismatch",
                    promptLevel = if (question.type == com.xingmou.data.catalog.QuestionType.OBSERVED) snapshot.supportLevel.ordinal else 0,
                    adaptiveOutcome = adaptiveOutcome(question, option, evaluation)
                )
                // 作答结果经事件协调器落库（training_records），并做难度/支持/安全调整。
                val decision = eventCoordinator.handle(
                    TrainingCompletedEvent(
                        eventId = newId("training-event"),
                        runId = newId("child-run"),
                        childId = childId,
                        occurredAt = now,
                        domain = question.domain,
                        currentDifficulty = snapshot.difficulty,
                        currentSupportLevel = snapshot.supportLevel,
                        result = result,
                        recentResults = snapshot.recentResults
                    )
                )
                persistAdaptiveState(
                    question = question,
                    result = result,
                    nextDifficulty = decision.nextDifficulty ?: snapshot.difficulty,
                    nextSupportLevel = decision.nextSupportLevel,
                    planId = snapshot.coursePlanId,
                    planVersion = snapshot.coursePlanVersion,
                    now = now
                )
                val level = ensureCurriculum().firstOrNull { it.order == player.levelOrder }
                val activities = level?.activities.orEmpty()
                val nextIndex = player.activityIndex + 1
                val runCompleted = player.runCompleted + (if (completed) 1 else 0)
                val runCorrect = player.runCorrect + (if (correct) 1 else 0)
                val runTotal = player.runTotal + 1
                val finished = nextIndex >= activities.size
                val passed = level != null && CurriculumCatalog.isPassed(level, runCompleted, runCorrect, runTotal)
                if (passed) {
                    level?.let {
                        curriculumPassedOrders.add(it.order)
                        val earned = starRatingFor(runCorrect, runTotal)
                        if (earned > (curriculumLevelStars[it.order] ?: 0)) curriculumLevelStars[it.order] = earned
                    }
                }
                persistCurriculumState()
                val nextActivity = if (!finished) activities.getOrNull(nextIndex) else null
                val nextQuestion = nextActivity?.let { activity ->
                    CurriculumCatalog.resolveQuestion(activity)?.let { base ->
                        val leveled = PlanQuestionPolicy.run { base.forDifficulty(decision.nextDifficulty ?: snapshot.difficulty) }
                        InterestThemeSkins.apply(leveled, curriculumInterest)
                    }
                }
                val updatedChild = snapshot.apply(decision).copy(
                    recentResults = (snapshot.recentResults + result).takeLast(3),
                    consecutiveFailures = if (correct) 0 else snapshot.consecutiveFailures + 1,
                    curriculumPlayer = player.copy(
                        activityIndex = nextIndex.coerceAtMost(activities.size),
                        activityLabel = nextActivity?.label ?: player.activityLabel,
                        question = nextQuestion,
                        runCompleted = runCompleted,
                        runCorrect = runCorrect,
                        runTotal = runTotal,
                        isWorking = false,
                        message = when {
                            passed -> "太棒了！这一关通过了。"
                            finished -> "这一关结束，可以再试一次。"
                            correct -> listOf("做得好！", "真棒！", "答对啦！", "太厉害了！")[Math.floorMod(runTotal, 4)]
                            else -> listOf("没关系，你已经很棒了，继续。", "别着急，我们看看下一个。", "这次没选对也没关系，继续加油。", "已经很认真啦，慢慢来。")[Math.floorMod(runTotal, 4)]
                        },
                        finished = finished,
                        passed = passed
                    )
                )
                _uiState.update { it.copy(child = updatedChild) }
                refreshCurriculumMap()
            }.onFailure { error ->
                _uiState.update { it.copy(child = it.child.copy(
                    curriculumPlayer = it.child.curriculumPlayer.copy(isWorking = false, message = "记录没有保存，请先休息后再试。"),
                    lastEvent = error.javaClass.simpleName
                )) }
            }
        }
    }

    fun completeChildTask(selectedOption: Int) {
        val snapshot = _uiState.value.child
        if (snapshot.isWorking || snapshot.isSafetyStopped || !snapshot.courseUnlocked || !snapshot.courseOpen || snapshot.courseQuestionId == null || snapshot.courseProgress >= snapshot.courseTotal) return
        val question = QuestionCatalog.fullCourseQuestions
            .firstOrNull { it.id == snapshot.courseQuestionId }
            ?.let { PlanQuestionPolicy.run { it.forDifficulty(snapshot.difficulty) } }
        if (question == null) {
            _uiState.update { it.copy(child = it.child.copy(message = "当前题目不存在，请先返回关卡地图。", lastEvent = "QUESTION_NOT_FOUND")) }
            return
        }
        val evaluation = QuestionEvaluator.evaluate(question, selectedOption)
        if (!evaluation.isValidSelection) {
            _uiState.update { it.copy(child = it.child.copy(message = "请选择题目中的一个选项。", lastEvent = "INVALID_SELECTION")) }
            return
        }
        val correct = evaluation.correct ?: evaluation.completed
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
                    promptLevel = if (question.type == com.xingmou.data.catalog.QuestionType.OBSERVED) snapshot.supportLevel.ordinal else 0,
                    adaptiveOutcome = adaptiveOutcome(question, selectedOption, evaluation)
                )
                val decision = eventCoordinator.handle(
                    TrainingCompletedEvent(
                        eventId = newId("training-event"),
                        runId = newId("child-run"),
                        childId = childId,
                        occurredAt = now,
                        domain = question?.domain ?: "A",
                        currentDifficulty = snapshot.difficulty,
                        currentSupportLevel = snapshot.supportLevel,
                        result = result,
                        recentResults = snapshot.recentResults,
                        observationText = "question_version=${question.version};plan_id=${snapshot.coursePlanId ?: "none"};plan_version=${snapshot.coursePlanVersion ?: 0};module_id=${question.moduleId}"
                    )
                )
                persistAdaptiveState(
                    question = question,
                    result = result,
                    nextDifficulty = decision.nextDifficulty ?: snapshot.difficulty,
                    nextSupportLevel = decision.nextSupportLevel,
                    planId = snapshot.coursePlanId,
                    planVersion = snapshot.coursePlanVersion,
                    now = now
                )
                val failures = if (correct) 0 else snapshot.consecutiveFailures + 1
                val updated = snapshot.apply(decision).copy(
                    recentResults = (snapshot.recentResults + result).takeLast(3),
                    consecutiveFailures = failures,
                    instruction = if (correct) "再找一次圆形" else "没关系，慢慢看，再试一次。"
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
                    mood = parent.feedbackMood, fatigue = "",
                    note = parent.feedbackNote.trim(), createdAt = System.currentTimeMillis()
                )
            )
            _uiState.update { it.copy(parent = it.parent.copy(feedbackMessage = "观察已保存到当前儿童档案。", feedbackNote = "")) }
        }
    }

    /** 家长端一键记录今天的状态：点一下表情即写入一条 mood 反馈，无需填写备注。 */
    fun quickRecordMood(mood: String) {
        viewModelScope.launch {
            val task = database.homeTaskDao().latestForChild(childId)
            database.homeFeedbackDao().insert(
                HomeFeedbackEntity(
                    feedbackId = newId("feedback"), childId = childId, taskId = task?.taskId,
                    mood = mood, fatigue = "",
                    note = "", createdAt = System.currentTimeMillis()
                )
            )
            _uiState.update { it.copy(parent = it.parent.copy(feedbackMood = mood, feedbackMessage = "已记录今天的状态：${mood}")) }
        }
    }

    /** 工作台「写下观察」补充的提交入口：把观察文本作为家庭观察提交给专业人员查看。 */
    fun submitParentObservation() {
        val parent = _uiState.value.parent
        val text = parent.query.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(parent = it.parent.copy(message = "请先写下一个具体观察。")) }
            return
        }
        viewModelScope.launch {
            database.homeFeedbackDao().insert(
                HomeFeedbackEntity(
                    feedbackId = newId("observation"), childId = childId, taskId = null,
                    mood = "", fatigue = "",
                    note = text, createdAt = System.currentTimeMillis()
                )
            )
            _uiState.update { it.copy(parent = it.parent.copy(message = "家庭观察已提交，专业人员可在家庭反馈中查看。", query = "")) }
        }
    }

    fun askParentQuestion() {
        val query = _uiState.value.parent.query.trim()
        if (query.isBlank()) {
            _uiState.update { it.copy(parent = it.parent.copy(message = "请先写下一个具体观察。", riskLabel = "需要补充")) }
            return
        }
        _uiState.update { it.copy(parent = it.parent.copy(isWorking = true, message = "正在读取本地已审核知识…", agentStatus = agentStateLabel(AgentRunState.CREATED))) }
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                val runId = newId("parent-run")
                val scopedChildId = childId
                val knowledge = database.knowledgeDao().verifiedItems().ifEmpty { SeedData.knowledgeItems }
                val records = database.trainingRecordDao().recentForChild(scopedChildId)
                val riskDecision = eventCoordinator.handle(
                    RiskDetectedEvent(newId("risk-event"), runId, scopedChildId, now, query)
                )
                eventCoordinator.handle(
                    ParentObservationAddedEvent(newId("parent-event"), runId, scopedChildId, now, query)
                )
                val retrieval = knowledgeRetriever.retrieve(query, Port.PARENT, items = knowledge)
                val orchestrationAgent = newOrchestrator(runId, scopedChildId, Port.PARENT)
                val orchestration = orchestrationAgent.run(
                    AgentOrchestrationRequest(
                        runId = runId,
                        taskType = "parent_support",
                        childId = scopedChildId,
                        input = AgentContextInput(Port.PARENT, sessionForChild(scopedChildId), query, knowledgeItems = knowledge, recentRecords = records)
                    )
                ) { state ->
                    _uiState.update { it.copy(parent = it.parent.copy(agentStatus = agentStateLabel(state))) }
                }
                recordAgentOutcome(orchestrationAgent, orchestration, now, scopedChildId)
                val localMessage = when (retrieval.route) {
                    KnowledgeRoute.NORMAL -> retrieval.reason
                    KnowledgeRoute.CLARIFY -> retrieval.reason
                    KnowledgeRoute.NOT_FOUND -> "本地已审核知识中暂未找到直接匹配项。请补充发生场景、持续时间和孩子当时的状态。"
                    KnowledgeRoute.REFER -> retrieval.reason
                    KnowledgeRoute.SAFETY_STOP -> riskDecision.message
                }
                val aiAcknowledgement = orchestration.output?.get("acknowledgement")
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                    ?.replace(Regex("[\r\n]+"), " ")?.trim()?.take(120)
                    ?.takeIf { orchestration.route == com.xingmou.core.agent.OrchestrationRoute.COMPLETED && it.isNotBlank() }
                val message = if (aiAcknowledgement != null && retrieval.route != KnowledgeRoute.SAFETY_STOP) {
                    "$aiAcknowledgement\n$localMessage"
                } else localMessage
                _uiState.update {
                    if (it.activeChildId != scopedChildId) it.copy(parent = it.parent.copy(isWorking = false)) else it.copy(
                        parent = it.parent.copy(
                            route = retrieval.route,
                            message = message,
                            suggestions = retrieval.matchedItems.map { item -> item.content },
                            sources = retrieval.matchedItems.map { item -> "${item.title} · ${item.sourceRef ?: "本地已审核资料"}" },
                            recordCount = records.size,
                            riskLabel = retrieval.riskLevel,
                            isWorking = false,
                            agentRunId = runId,
                            agentStatus = agentStateLabel(orchestration.state)
                        )
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(parent = it.parent.copy(isWorking = false, message = SafeResponses.INSUFFICIENT_DATA, agentStatus = agentStateLabel(AgentRunState.FAILED)))
                }
            }
        }
    }

    private suspend fun loadHomeSupport(childId: String) {
        val latestPlan = database.planDao().latestActive(childId)
        val existing = database.homeTaskDao().latestForChild(childId)
        val task = if (latestPlan != null && existing?.planId != latestPlan.planId) {
            createHomeTaskFromPlan(latestPlan).also { database.homeTaskDao().upsert(it) }
        } else if (existing == null) {
            defaultMatchingHomeTask(childId).also { database.homeTaskDao().upsert(it) }
        } else if (existing.source == "LOCAL_TEMPLATE") {
            // 本地模板文案升级：保留完成状态与示范进度
            defaultMatchingHomeTask(childId).copy(
                status = existing.status,
                demoStep = existing.demoStep
            ).also { database.homeTaskDao().upsert(it) }
        } else existing
        val safetyStopped = database.safetyFlagDao().observeActive(childId).first().isNotEmpty()
        val weekStart = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val weekTasks = database.homeTaskDao().allForChild(childId).filter { it.updatedAt >= weekStart }
        val weekFeedback = database.homeFeedbackDao().recentForChild(childId, 20).filter { it.createdAt >= weekStart }
        val completed = weekTasks.count { it.status == "completed" }
        val weekRate = if (weekTasks.isEmpty()) "暂无本周完成记录" else "${completed}/${weekTasks.size} 次（${completed * 100 / weekTasks.size}%）"
        val latestFeedback = weekFeedback.firstOrNull()
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
                homeDemoStep = task.demoStep.coerceIn(0, HOME_DEMO_STEPS.lastIndex),
                weekCompletionRate = weekRate,
                weekStatusSummary = latestFeedback?.let { "最近状态：${it.mood}" } ?: "本周还没有状态日记",
                weekSuggestion = when {
                    safetyStopped -> "当前有安全暂停标记，请先联系专业人员确认。"
                    latestFeedback?.mood == "睡眠不足" -> "注意休息和睡眠节律，下一次可适当缩短时长。"
                    latestFeedback?.mood == "情绪波动" -> "下一次以安抚为主，优先让孩子恢复平静。"
                    completed > 0 -> "保持短时、可停止的练习节奏。"
                    else -> "完成一次短时任务后，再记录孩子当时的状态。"
                }
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

    fun updateCareNote(value: String) {
        _uiState.update { it.copy(professional = it.professional.copy(careNote = value.take(300))) }
    }

    fun updateCareClosureReason(value: String) {
        _uiState.update { it.copy(professional = it.professional.copy(careClosureReason = value.take(240))) }
    }

    fun updateCareFollowUpPlan(value: String) {
        _uiState.update { it.copy(professional = it.professional.copy(careFollowUpPlan = value.take(240))) }
    }

    fun updateCareFollowUpDate(value: String) {
        _uiState.update { it.copy(professional = it.professional.copy(careFollowUpDate = value.take(32))) }
    }

    fun advanceCareStage() {
        viewModelScope.launch {
            runCatching {
                if (_uiState.value.selectedPort != Port.PROFESSIONAL) {
                    _uiState.update { it.copy(professional = it.professional.copy(carePermissionMessage = "只有专业端可以签署或推进个案阶段。")) }
                    return@runCatching
                }
                val current = database.careRecordDao().latestForChild(childId)
                val note = _uiState.value.professional.careNote.trim()
                val professional = _uiState.value.professional
                val nextIndex = (CARE_STAGES.indexOfFirst { it.first == current?.stage } + 1).coerceAtLeast(0).coerceAtMost(CARE_STAGES.lastIndex)
                val next = CARE_STAGES[nextIndex]
                if (next.first == "closure" && professional.careClosureReason.trim().isBlank()) {
                    _uiState.update { it.copy(professional = it.professional.copy(carePermissionMessage = "进入结案阶段前必须填写结案依据。")) }
                    return@runCatching
                }
                if (next.first == "follow_up" && (professional.careFollowUpPlan.trim().isBlank() || professional.careFollowUpDate.trim().isBlank())) {
                    _uiState.update { it.copy(professional = it.professional.copy(carePermissionMessage = "进入随访阶段前必须填写随访计划和日期。")) }
                    return@runCatching
                }
                if (current != null && current.stage in setOf("closure", "follow_up") && note.isBlank()) {
                    _uiState.update { it.copy(professional = it.professional.copy(carePermissionMessage = "结案或随访阶段必须填写专业备注后才能签署。")) }
                    return@runCatching
                }
                val latestPlan = database.planDao().latest(childId)
                val latestAssessment = database.assessmentRecordDao().recentForChild(childId, 1).firstOrNull()
                val now = System.currentTimeMillis()
                val record = CareRecordEntity(
                    recordId = newId("care"), childId = childId, stage = next.first, stageLabel = next.second,
                    status = if (nextIndex == CARE_STAGES.lastIndex) "completed" else "active",
                    summary = when (next.first) {
                        "intake" -> "已建立儿童与授权范围，等待目标确认。"
                        "goals" -> "目标基于训练过程表现和专业观察整理。"
                        "plan" -> "方案版本与人工审核状态已关联。"
                        "review" -> "复评量表与训练报表已进入当前个案链路。"
                        "closure" -> "结案由专业人员确认，并保留历史记录。"
                        else -> "随访记录保留后续观察与回访安排。"
                    },
                    linkedPlanId = latestPlan?.planId,
                    linkedAssessmentId = latestAssessment?.recordId,
                    professionalId = localUserId,
                    professionalSignedAt = now,
                    professionalSignature = localUserId,
                    note = note,
                    closureReason = professional.careClosureReason.trim().takeIf { next.first == "closure" || current?.stage == "closure" },
                    followUpPlan = professional.careFollowUpPlan.trim().takeIf { next.first == "follow_up" || current?.stage == "follow_up" },
                    followUpDate = professional.careFollowUpDate.trim().takeIf { next.first == "follow_up" || current?.stage == "follow_up" },
                    createdAt = now,
                    updatedAt = now
                )
                database.careRecordDao().insert(record)
                database.agentDao().upsertEvent(
                    AgentEventEntity(
                        eventId = newId("audit-care"),
                        runId = "care-${record.recordId}",
                        childId = childId,
                        eventType = "CARE_STAGE_SIGNED",
                        payloadSummary = "stage=${record.stage};professional=$localUserId;notePresent=${note.isNotBlank()};closurePresent=${record.closureReason != null};followUpPresent=${record.followUpPlan != null}",
                        status = "processed",
                        createdAt = now,
                        processedAt = now
                    )
                )
                _uiState.update { it.copy(professional = it.professional.copy(carePermissionMessage = "阶段已签署并记录。")) }
                refreshProfessionalAnalysis()
            }
        }
    }

    fun updatePlanTask(value: String) = _uiState.update { it.copy(professional = it.professional.copy(planTask = value.take(80))) }
    fun updatePlanGoal(value: String) = _uiState.update { it.copy(professional = it.professional.copy(planGoal = value.take(160))) }
    fun updatePlanDifficulty(value: String) = _uiState.update { it.copy(professional = it.professional.copy(planDifficulty = value.take(8))) }
    fun updatePlanSupportLevel(value: String) = _uiState.update { it.copy(professional = it.professional.copy(planSupportLevel = value.take(8))) }
    fun updatePlanFrequency(value: String) = _uiState.update { it.copy(professional = it.professional.copy(planFrequency = value.take(80))) }
    fun updatePlanDuration(value: String) = _uiState.update { it.copy(professional = it.professional.copy(planDuration = value.take(40))) }
    fun updatePlanStopConditions(value: String) = _uiState.update { it.copy(professional = it.professional.copy(planStopConditions = value.take(160))) }

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
                val recordId = newId("assessment")
                database.assessmentRecordDao().upsert(
                    AssessmentRecordEntity(
                        recordId = recordId,
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
                database.abilityProfileDao().latestForChild(childId)?.let { profile ->
                    database.abilityProfileDao().upsert(
                        profile.copy(
                            profileId = "profile-$childId-assessment-$version-$now",
                            status = "assessment_linked",
                            assessmentRecordIdsJson = appendJsonId(profile.assessmentRecordIdsJson, recordId),
                            createdAt = now
                        )
                    )
                }
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
        _uiState.update { it.copy(professional = it.professional.copy(isWorking = true, reviewMessage = "正在生成受控草案…", agentStatus = agentStateLabel(AgentRunState.CREATED))) }
        viewModelScope.launch {
            runCatching {
                check(planStateMachine.createDraft(PlanActor.MODEL).accepted)
                val now = System.currentTimeMillis()
                val runId = newId("professional-run")
                val scopedChildId = childId
                val childUiSnapshot = _uiState.value.child
                val records = database.trainingRecordDao().recentForChild(scopedChildId)
                val knowledge = database.knowledgeDao().verifiedItems().ifEmpty { SeedData.knowledgeItems }
                val orchestrationAgent = newOrchestrator(runId, scopedChildId, Port.PROFESSIONAL)
                val orchestration = orchestrationAgent.run(
                    AgentOrchestrationRequest(
                        runId = runId,
                        taskType = "plan_draft",
                        childId = scopedChildId,
                        input = AgentContextInput(Port.PROFESSIONAL, sessionForChild(scopedChildId), "基于现有训练记录生成待审核草案", knowledgeItems = knowledge, recentRecords = records)
                    )
                ) { state ->
                    _uiState.update { it.copy(professional = it.professional.copy(agentStatus = agentStateLabel(state))) }
                }
                recordAgentOutcome(orchestrationAgent, orchestration, now, scopedChildId)
                val modelGoal = orchestration.output?.get("plan")
                    ?.takeIf { it.isJsonObject }?.asJsonObject?.get("observable_goal")
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                    ?.replace(Regex("[\r\n]+"), " ")?.trim()?.take(160)
                    ?.takeIf { orchestration.route == com.xingmou.core.agent.OrchestrationRoute.COMPLETED && it.isNotBlank() }
                val draftGoal = modelGoal ?: professional.planGoal
                val version = (database.planDao().latest(scopedChildId)?.version ?: 0) + 1
                val plan = PlanVersionEntity(
                    planId = newId("plan"),
                    childId = scopedChildId,
                    version = version,
                    status = "draft",
                    reviewRequired = true,
                    payloadJson = """{"priority_domain":"A","observable_goal":"${jsonEscape(draftGoal)}","task":"图片配对","difficulty":${childUiSnapshot.difficulty},"support_level":"${childUiSnapshot.supportLevel.name}","frequency":"每日 1–2 次","duration":"5 分钟","stop_conditions":"出现疲劳、拒绝或风险时暂停"}""",
                    createdAt = now,
                    updatedAt = now
                )
                val review = ReviewRequestEntity(
                    reviewId = newId("review"),
                    childId = scopedChildId,
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
                if (activeChildId == scopedChildId) {
                    activePlan = plan
                    activeReview = review
                }
                _uiState.update {
                    if (it.activeChildId != scopedChildId) it.copy(professional = it.professional.copy(isWorking = false)) else it.copy(professional = it.professional.copy(
                        planStatus = PlanStatus.DRAFT,
                        planSummary = "优先领域 A · 图片配对 · 难度 ${childUiSnapshot.difficulty} · 支持 ${childUiSnapshot.supportLevel.name} · 每次 5–10 分钟",
                        planTask = "图片配对",
                        planGoal = draftGoal,
                        planDifficulty = childUiSnapshot.difficulty.toString(),
                        planSupportLevel = childUiSnapshot.supportLevel.name,
                        planFrequency = "每日 1–2 次",
                        planDuration = "5 分钟",
                        planStopConditions = "出现疲劳、拒绝或风险时暂停",
                        reviewMessage = if (modelGoal != null) "AI 待审核目标已写入草案；请核对后确认、签署。" else "本地草案已生成，需先确认，再签署生效。",
                        isWorking = false,
                        agentRunId = runId,
                        agentStatus = agentStateLabel(orchestration.state),
                        recentEvent = "PLAN_DRAFT_CREATED"
                    ))
                }
            }.onFailure { error ->
                _uiState.update { it.copy(professional = it.professional.copy(isWorking = false, reviewMessage = "草案生成失败：${error.message ?: "未知错误"}", agentStatus = agentStateLabel(AgentRunState.FAILED))) }
            }
        }
    }

    fun createPlanRevision() {
        val professional = _uiState.value.professional
        if (professional.isWorking || professional.planStatus != PlanStatus.ACTIVE) return
        _uiState.update { it.copy(professional = it.professional.copy(isWorking = true, reviewMessage = "正在创建方案新版本…")) }
        viewModelScope.launch {
            runCatching {
                val previous = database.planDao().latest(childId) ?: error("当前没有可编辑的已生效方案")
                check(planStateMachine.createDraft(PlanActor.MODEL).accepted)
                val now = System.currentTimeMillis()
                val runId = newId("professional-revision-run")
                val version = previous.version + 1
                val payload = planPayload(professional)
                val plan = PlanVersionEntity(newId("plan"), childId, version, "draft", true, payload, now, now)
                val diff = planDiffs(previous.payloadJson, payload)
                val review = ReviewRequestEntity(newId("review"), childId, runId, "plan", plan.planId, payload, diffJson(diff), "pending", null, null, now, null)
                database.planDao().upsert(plan)
                database.agentDao().upsertReview(review)
                activePlan = plan
                activeReview = review
                _uiState.update { it.copy(professional = it.professional.copy(
                    planStatus = PlanStatus.DRAFT,
                    planSummary = "当前方案 V$version · DRAFT",
                    planDiffs = diff,
                    reviewMessage = "新版本已创建，需确认后才能签署生效。",
                    isWorking = false,
                    agentRunId = runId,
                    agentStatus = "已由专业人员编辑",
                    recentEvent = "PLAN_REVISION_CREATED"
                )) }
            }.onFailure { error ->
                _uiState.update { it.copy(professional = it.professional.copy(isWorking = false, reviewMessage = "新版本创建失败：${error.message ?: "未知错误"}")) }
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
                    // 方案签署后立即刷新儿童端，避免仍显示旧的全量题库。
                    if (childId == _uiState.value.activeChildId) loadCourseProgress(childId)
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
                val adaptiveStates = database.moduleAdaptiveStateDao().allForChild(childId)
                    .map { state ->
                        ModuleAdaptiveUi(
                            moduleId = state.moduleId,
                            moduleName = TaskCatalog.find(state.moduleId)?.name ?: state.moduleId,
                            difficulty = state.currentDifficulty,
                            supportLevel = state.currentSupportLevel,
                            correctStreak = state.correctStreak,
                            errorStreak = state.errorStreak,
                            lastQuestionId = state.lastQuestionId,
                            baselineVersion = state.baselineVersion,
                            planVersion = state.planVersion,
                            updatedAt = state.updatedAt
                        )
                    }
                val analysis = analysisEngine.analyze(records)
                val latestPlan = database.planDao().latest(childId)
                val planVersions = database.planDao().observeVersions(childId).first()
                val review = latestPlan?.let { database.agentDao().latestReviewForTarget(it.planId) }
                val feedback = database.homeFeedbackDao().recentForChild(childId)
                val trendPoints = records.sortedBy { it.createdAt }
                    .chunked((records.size / 5).coerceAtLeast(1))
                    .takeLast(5)
                    .mapIndexed { index, chunk ->
                        val fmt = SimpleDateFormat("MM-dd", Locale.CHINA)
                        val range = if (chunk.size <= 1) {
                            fmt.format(Date(chunk.first().createdAt))
                        } else {
                            "${fmt.format(Date(chunk.first().createdAt))}~${fmt.format(Date(chunk.last().createdAt))}"
                        }
                        ReportTrendPointUi(
                            label = "${index + 1}",
                            accuracy = if (chunk.isEmpty()) 0f else chunk.count { it.correct }.toFloat() / chunk.size,
                            sampleCount = chunk.size,
                            dateRange = range
                        )
                    }
                val homeTasks = database.homeTaskDao().allForChild(childId).associateBy { it.taskId }
                val assessments = database.assessmentRecordDao().recentForChild(childId)
                val latestProfile = database.abilityProfileDao().latestForChild(childId)
                val careRecords = database.careRecordDao().recentForChild(childId)
                val latestCare = careRecords.firstOrNull()
                val assessmentById = assessments.associateBy { it.recordId }
                val profileEvidenceDetails = latestProfile?.let { profile ->
                    Regex("\\\"([^\\\"]+)\\\"").findAll(profile.assessmentRecordIdsJson)
                        .mapNotNull { assessmentById[it.groupValues[1]] }
                        .map { ProfileEvidenceUi(it.assessmentName, it.version, it.recordType, it.assessmentDate, it.source) }
                        .toList()
                } ?: emptyList()
                val assessmentChanges = assessments.groupBy { it.assessmentId }.values.mapNotNull { versions ->
                    val ordered = versions.sortedBy { it.version }
                    if (ordered.size < 2) return@mapNotNull null
                    val previous = ordered[ordered.lastIndex - 1]
                    val current = ordered.last()
                    val changes = scoreChanges(previous.scoresJson, current.scoresJson)
                    AssessmentChangeUi(current.assessmentName, previous.version, current.version, changes)
                }
                val previousPlan = planVersions.firstOrNull { it.planId != latestPlan?.planId }
                val planDiffs = if (latestPlan != null && previousPlan != null) planDiffs(previousPlan.payloadJson, latestPlan.payloadJson) else emptyList()
                val latestAssessment = assessments.firstOrNull()
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
                    val taskIdShort = item.taskId.substringBefore("-")
                    TrainingDetailUi(
                        timestamp = item.createdAt,
                        domain = DomainCatalog.find(item.domain)?.name ?: item.domain,
                        task = TaskCatalog.find(taskIdShort)?.name ?: item.taskId,
                        result = if (item.correct) "完成" else "需再试",
                        support = "${supportWord(item.supportLevel)} · 提示 ${item.promptLevel} 次",
                        reaction = item.reactionMs?.let { "$it ms" } ?: "—"
                    )
                }
                val domainOverview = DomainCatalog.all.map { domain ->
                    val group = records.filter { it.domain == domain.id }
                    ParentDomainStatUi(
                        id = domain.id,
                        name = domain.name,
                        emoji = domainEmoji(domain.id),
                        colorKey = domain.displayColor,
                        accuracy = if (group.isEmpty()) 0 else group.count { it.correct } * 100 / group.size,
                        count = group.size
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
                        parent = it.parent.copy(
                            recordCount = records.size,
                            domainOverview = domainOverview,
                            adaptiveOverview = adaptiveStates,
                            trendPoints = trendPoints,
                            recentTrainingDetails = trainingDetails
                        ),
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
                            adaptiveOverview = adaptiveStates,
                            reportTrend = trendPoints,
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
                            profileVersionSummary = latestAssessment?.let { "最近量表：${it.assessmentName} V${it.version} · ${it.recordType} · ${it.assessmentDate}" } ?: "尚无量表复评记录。",
                            profileEvidenceSummary = latestProfile?.let { profile ->
                                val count = Regex("\\\"([^\\\"]+)\\\"").findAll(profile.assessmentRecordIdsJson).count()
                                "能力画像 ${profile.status} · 证据版本 ${profile.profileId} · 已关联量表记录 $count 条"
                            } ?: "能力画像尚无可展示的证据引用。",
                            profileEvidenceDetails = profileEvidenceDetails,
                            assessmentChanges = assessmentChanges,
                            careStage = latestCare?.stageLabel ?: "接案",
                            careStageStatus = latestCare?.status ?: "待开始",
                            careStageSummary = latestCare?.summary ?: "尚未建立专业个案记录。",
                            careTimeline = careRecords.map { item -> CareRecordUi(item.stage, item.stageLabel, item.status, item.summary, item.createdAt, item.professionalSignedAt != null, item.note, item.closureReason, item.followUpPlan, item.followUpDate) },
                            careClosureReason = latestCare?.closureReason ?: it.professional.careClosureReason,
                            careFollowUpPlan = latestCare?.followUpPlan ?: it.professional.careFollowUpPlan,
                            careFollowUpDate = latestCare?.followUpDate ?: it.professional.careFollowUpDate,
                            planStatus = latestPlan?.status?.uppercase()?.let { status -> runCatching { PlanStatus.valueOf(status) }.getOrNull() } ?: it.professional.planStatus,
                            planSummary = if (latestPlan != null) "当前方案 V${latestPlan.version} · ${latestPlan.status.uppercase()}" else if (analysis.dataSufficient || it.professional.planStatus != null) it.professional.planSummary else "达到 3 条有效记录后，可生成方案草案。",
                            planDiffs = planDiffs,
                            planTask = latestPlan?.let { jsonValue(it.payloadJson, "task") } ?: it.professional.planTask,
                            planGoal = latestPlan?.let { jsonValue(it.payloadJson, "observable_goal") } ?: it.professional.planGoal,
                            planDifficulty = latestPlan?.let { jsonValue(it.payloadJson, "difficulty") } ?: it.professional.planDifficulty,
                            planSupportLevel = latestPlan?.let { jsonValue(it.payloadJson, "support_level") } ?: it.professional.planSupportLevel,
                            planFrequency = latestPlan?.let { jsonValue(it.payloadJson, "frequency") } ?: it.professional.planFrequency,
                            planDuration = latestPlan?.let { jsonValue(it.payloadJson, "duration") } ?: it.professional.planDuration,
                            planStopConditions = latestPlan?.let { jsonValue(it.payloadJson, "stop_conditions") } ?: it.professional.planStopConditions,
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

    fun refreshAgentAudit() {
        val scopedChildId = childId
        viewModelScope.launch {
            if (database.childBindingDao().findActive(localUserId, scopedChildId) == null) {
                _uiState.update { it.copy(professional = it.professional.copy(auditRuns = emptyList(),
                    auditReplay = emptyList(), auditSelectedRunId = null, auditMessage = "当前用户未绑定该儿童。")) }
                return@launch
            }
            val runs = database.agentDao().runsForChild(scopedChildId).take(20)
            val metrics = com.xingmou.core.agent.AgentMetricsRepository(database).forChild(scopedChildId)
            val metricsSummary = metrics.unsupportedJudgmentRate?.let { rate ->
                "无依据判断率 %.0f%% · 已标注 %d 条".format(rate * 100, metrics.evidenceAnnotationSampleCount)
            } ?: "依据标注：未采样"
            if (activeChildId != scopedChildId) return@launch
            _uiState.update { state -> state.copy(professional = state.professional.copy(
                auditRuns = runs.map { AgentRunAuditUi(it.runId, it.taskType, it.port, it.status, it.startedAt) },
                auditReplay = emptyList(), auditSelectedRunId = null,
                auditMessage = if (runs.isEmpty()) "当前儿童暂无 Agent 运行记录。" else "选择一次运行查看脱敏回放。",
                auditMetricsSummary = metricsSummary
            )) }
        }
    }

    fun openAgentAudit(runId: String) {
        val scopedChildId = childId
        viewModelScope.launch {
            val dao = database.agentDao()
            val run = dao.findRun(runId)
            if (run?.childId != scopedChildId || activeChildId != scopedChildId ||
                database.childBindingDao().findActive(localUserId, scopedChildId) == null) {
                _uiState.update { it.copy(professional = it.professional.copy(auditMessage = "无权查看该运行记录。")) }
                return@launch
            }
            val lines = buildList {
                dao.steps(runId).forEach { step ->
                    add(AgentReplayLineUi(step.createdAt, "步骤 ${step.stepIndex}",
                        "状态 ${step.state} · 动作 ${step.actionType ?: "—"} · 重试 ${step.retryCount}"))
                }
                dao.events(runId).filter { it.childId == scopedChildId }.forEach { event ->
                    add(AgentReplayLineUi(event.createdAt, "事件", "${event.eventType} · ${event.status}"))
                }
                dao.toolCalls(runId).forEach { call ->
                    add(AgentReplayLineUi(call.createdAt, "工具", "${call.toolName} · 授权 ${call.authorizationStatus} · 执行 ${call.executionStatus}"))
                }
                dao.traces(runId).forEach { trace ->
                    fun count(json: String): Int = runCatching { com.google.gson.JsonParser.parseString(json).asJsonArray.size() }.getOrDefault(0)
                    add(AgentReplayLineUi(trace.createdAt, "决策轨迹", "步骤 ${trace.stepIndex} · 提示 ${trace.promptVersion ?: "—"} · 模型 ${trace.modelVersion ?: "本地"} · 规则 ${count(trace.ruleRefsJson)} · 来源 ${count(trace.knowledgeRefsJson)} · 工具 ${count(trace.toolRefsJson)} · 人工决定 ${trace.humanDecisionLabel()}", trace.traceId, trace.humanDecision))
                }
                dao.reviews(runId).filter { it.childId == scopedChildId }.forEach { review ->
                    add(AgentReplayLineUi(review.createdAt, "人工审核", "${review.targetType} · ${review.status}"))
                }
            }.sortedBy { it.timestamp }
            if (activeChildId != scopedChildId) return@launch
            _uiState.update { it.copy(professional = it.professional.copy(
                auditSelectedRunId = runId, auditReplay = lines,
                auditMessage = if (lines.isEmpty()) "此运行没有可回放的脱敏节点。" else "仅展示状态与事件类型；原始提示词、儿童观察和工具参数不展示。"
            )) }
        }
    }

    fun annotateAgentTrace(runId: String, traceId: String, decision: String) {
        if (decision !in setOf("supported", "unsupported", "uncertain")) return
        val scopedChildId = childId
        viewModelScope.launch {
            val run = database.agentDao().findRun(runId)
            if (run?.childId != scopedChildId || database.childBindingDao().findActive(localUserId, scopedChildId) == null) return@launch
            if (database.agentDao().annotateTrace(runId, traceId, decision) == 1) openAgentAudit(runId)
        }
    }

    private fun com.xingmou.data.db.DecisionTraceEntity.humanDecisionLabel(): String = when (humanDecision) {
        "supported" -> "依据充分"
        "unsupported" -> "无依据"
        "uncertain" -> "不确定"
        else -> "未标注"
    }

    private suspend fun publishActivePlanToHomeTask(plan: PlanVersionEntity) {
        database.homeTaskDao().upsert(createHomeTaskFromPlan(plan))
        loadHomeSupport(childId)
    }

    private fun defaultMatchingHomeTask(childId: String) = HomeTaskEntity(
        taskId = "home-$childId-matching",
        childId = childId,
        title = "相同图片配对练习（5 分钟）",
        description = "准备 2～3 对完全相同的图片卡（孩子熟悉的日常物品照片）。先由家长示范：拿起一张，找到相同的放在一起，同时说出名称；再把卡片打乱摆开，请孩子找出相同的两张，配对成功立即肯定。熟练后再过渡到相似图片或相关事物配对（如牙刷和杯子）。出现疲劳或拒绝时暂停。",
        status = "pending",
        frequency = "每日 1–2 次",
        durationMinutes = 5,
        supportLevel = "L1",
        stopConditions = "出现疲劳、拒绝或风险时暂停",
        source = "LOCAL_TEMPLATE",
        updatedAt = System.currentTimeMillis()
    )

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

    private fun planPayload(state: ProfessionalUiState): String =
        """{"priority_domain":"A","observable_goal":"${jsonEscape(state.planGoal.ifBlank { "在当前支持等级下完成训练" })}","task":"${jsonEscape(state.planTask.ifBlank { "图片配对" })}","difficulty":${state.planDifficulty.toIntOrNull()?.coerceIn(1, 5) ?: 1},"support_level":"${jsonEscape(state.planSupportLevel.ifBlank { "L1" })}","frequency":"${jsonEscape(state.planFrequency.ifBlank { "每日 1–2 次" })}","duration":"${jsonEscape(state.planDuration.ifBlank { "5 分钟" })}","stop_conditions":"${jsonEscape(state.planStopConditions.ifBlank { "出现疲劳、拒绝或风险时暂停" })}"}"""

    private fun diffJson(diffs: List<PlanDiffUi>): String =
        diffs.joinToString(prefix = "[", postfix = "]") { "{\"field\":\"${jsonEscape(it.field)}\",\"previous\":\"${jsonEscape(it.previous)}\",\"current\":\"${jsonEscape(it.current)}\"}" }

    private fun jsonEscape(value: String): String = com.google.gson.Gson().toJson(value).removeSurrounding("\"")

    private fun appendJsonId(json: String, id: String): String {
        val existing = Regex("\\\"([^\\\"]+)\\\"").findAll(json).map { it.groupValues[1] }.toMutableList()
        if (id !in existing) existing += id
        return existing.joinToString(prefix = "[\"", postfix = "\"]", separator = "\",\"")
    }

    private fun scoreChanges(previous: String, current: String): List<String> {
        val before = scoreMap(previous)
        val after = scoreMap(current)
        return (before.keys + after.keys).distinct().sorted().mapNotNull { key ->
            val old = before[key] ?: return@mapNotNull "$key：新增 ${after[key]}"
            val new = after[key] ?: return@mapNotNull "$key：移除（原 $old）"
            if (old == new) null else "$key：$old → $new"
        }
    }

    private fun scoreMap(value: String): Map<String, String> =
        Regex("[\\\"]?([A-Za-z][A-Za-z0-9_-]*)[\\\"]?\\s*[:=]\\s*(-?\\d+(?:\\.\\d+)?)")
            .findAll(value)
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun planDiffs(previousJson: String, currentJson: String): List<PlanDiffUi> {
        val fields = listOf(
            "priority_domain" to "优先领域",
            "observable_goal" to "可观察目标",
            "task" to "训练任务",
            "difficulty" to "难度",
            "support_level" to "支持等级",
            "frequency" to "频率",
            "duration" to "时长",
            "stop_conditions" to "停止条件"
        )
        return fields.mapNotNull { (key, label) ->
            val previous = jsonValue(previousJson, key) ?: return@mapNotNull null
            val current = jsonValue(currentJson, key) ?: return@mapNotNull null
            if (previous == current) null else PlanDiffUi(label, previous, current)
        }
    }

    private fun jsonValue(json: String, key: String): String? {
        jsonString(json, key)?.let { return it }
        return Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*([^,}]+)").find(json)?.groupValues?.getOrNull(1)?.trim()?.trim('"')
    }

    private fun currentDateLabel(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun Double?.percentLabel(): String = this?.let { "%.0f%%".format(it * 100) } ?: "—"

    private fun com.xingmou.core.domain.Trend.label(): String = when (this) {
        com.xingmou.core.domain.Trend.IMPROVING -> "改善"
        com.xingmou.core.domain.Trend.STABLE -> "稳定"
        com.xingmou.core.domain.Trend.DECLINING -> "下降"
        com.xingmou.core.domain.Trend.INSUFFICIENT_DATA -> "数据不足"
    }

    private suspend fun sessionForChild(scopedChildId: String): SessionContext {
        val child = database.childDao().findById(scopedChildId) ?: error("child_not_found")
        return SessionContext(
            childAlias = child.alias,
            ageBand = child.ageBand,
            communicationLevel = runCatching { CommunicationLevel.valueOf(child.communicationLevel) }
                .getOrDefault(CommunicationLevel.SHORT_SENTENCE),
            supportLevel = runCatching { com.xingmou.core.model.SupportLevel.valueOf(child.supportLevel) }
                .getOrDefault(com.xingmou.core.model.SupportLevel.L1),
            currentDomain = "A",
            currentTask = "图片配对"
        )
    }

    private fun newOrchestrator(runId: String, scopedChildId: String, port: Port): AgentOrchestrator {
        val model = ModelGateway { systemPrompt, userMessage ->
            val consent = database.consentDao().find(scopedChildId, "remote_ai")?.status.toConsentStatus()
            val binding = database.childBindingDao().findActive(localUserId, scopedChildId)
            if (port == Port.CHILD || !apiKeyStore.isConfigured() || consent != ConsentStatus.GRANTED || binding == null) {
                localSafeModel.complete(systemPrompt, userMessage)
            } else {
                val remote = PolicyBackedModelGateway(
                    delegate = DirectDeepSeekGateway(apiKeyStore::get),
                    policy = GatewayPolicy(),
                    requestProvider = {
                        GatewayCallRequest(
                            runId = runId,
                            childId = if (database.childBindingDao().findActive(localUserId, scopedChildId) != null) scopedChildId else null,
                            port = port.name.lowercase(),
                            systemPrompt = "",
                            userText = "",
                            consentStatus = database.consentDao().find(scopedChildId, "remote_ai")?.status.toConsentStatus()
                        )
                    }
                )
                val started = System.currentTimeMillis()
                val result = remote.complete(systemPrompt, userMessage)
                val finished = System.currentTimeMillis()
                database.agentDao().upsertEvent(
                    AgentEventEntity(newId("audit-remote"), runId, scopedChildId,
                        if (result.isSuccess) "REMOTE_AI_CALL_SUCCEEDED" else "REMOTE_AI_CALL_FAILED",
                        "model=deepseek-chat;durationMs=${finished - started}", "processed", finished, finished)
                )
                result
            }
        }
        return AgentOrchestrator(model)
    }

    private suspend fun recordAgentOutcome(orchestrator: AgentOrchestrator, result: AgentOrchestrationResult, startedAt: Long, scopedChildId: String) {
        val completedAt = System.currentTimeMillis()
        val snapshot = orchestrator.snapshot(result.runId)
        val dao = database.agentDao()
        dao.upsertRun(
            com.xingmou.data.db.AgentRunEntity(
                result.runId, snapshot.taskType, snapshot.port.name, scopedChildId,
                snapshot.state.name, snapshot.status.name, startedAt, completedAt, null
            )
        )
        snapshot.steps.forEach { step ->
            dao.insertStep(step.copy(inputSummary = null, toolResultSummary = null,
                errorMessage = null, createdAt = completedAt))
        }
        snapshot.toolCalls.forEach { call ->
            dao.insertToolCall(call.copy(argumentsSummary = "[omitted]", resultSummary = null,
                createdAt = completedAt, finishedAt = completedAt))
        }
        val remoteEvent = dao.events(result.runId).firstOrNull { it.eventType.startsWith("REMOTE_AI_CALL_") }
        val sourceIds = if (result.route == com.xingmou.core.agent.OrchestrationRoute.COMPLETED) {
            result.output?.getAsJsonArray("sources")?.mapNotNull { source ->
                runCatching { source.asJsonObject.get("source_id")?.asString }.getOrNull()
            }.orEmpty()
        } else emptyList()
        dao.insertTrace(DecisionTraceFactory.create(
            traceId = newId("trace"), runId = result.runId, stepIndex = snapshot.steps.lastOrNull()?.stepIndex ?: 0,
            promptVersion = "v1.0", modelVersion = when (remoteEvent?.eventType) {
                "REMOTE_AI_CALL_SUCCEEDED" -> "deepseek-chat"
                "REMOTE_AI_CALL_FAILED" -> "deepseek-chat-failed"
                else -> "local-safe"
            },
            evidence = DecisionEvidence(
                ruleRefs = listOf("RiskEngine", "JsonValidator", "PortGuard") + if (remoteEvent != null) listOf("GatewayPolicy") else emptyList(),
                knowledgeRefs = sourceIds,
                toolRefs = snapshot.toolCalls.map { it.toolName }.distinct()
            ), createdAt = completedAt
        ))
        if (result.route == com.xingmou.core.agent.OrchestrationRoute.FALLBACK) {
            dao.upsertEvent(AgentEventEntity(newId("audit-agent"), result.runId, scopedChildId,
                "FALLBACK_TRIGGERED", "route=FALLBACK", "processed", completedAt, completedAt))
            if (result.error?.contains("JSON", ignoreCase = true) == true) {
                dao.upsertEvent(AgentEventEntity(newId("audit-agent"), result.runId, scopedChildId,
                    "JSON_VALIDATION_FAILED", "validation_failed", "processed", completedAt, completedAt))
            }
        }
    }

    private fun newId(prefix: String): String = "$prefix-${UUID.randomUUID()}"

    private fun toChildSummary(child: ChildEntity) = ChildSummaryUi(child.childId, child.alias, child.ageBand, child.status)
}

private val HOME_DEMO_STEPS = listOf("准备卡片", "家长示范", "孩子尝试", "及时肯定", "整理结束")

private val CARE_STAGES = listOf(
    "intake" to "接案",
    "goals" to "目标",
    "plan" to "方案",
    "review" to "复评",
    "closure" to "结案",
    "follow_up" to "随访"
)
