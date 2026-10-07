package com.xingmou

import com.xingmou.core.agent.AgentEventDecision
import com.xingmou.core.agent.AgentRunState
import com.xingmou.core.domain.KnowledgeRoute
import com.xingmou.core.domain.PlanActor
import com.xingmou.core.domain.PlanStateMachine
import com.xingmou.core.domain.PlanStatus
import com.xingmou.core.domain.TrainingResult
import com.xingmou.core.domain.BaselineStatus
import com.xingmou.core.llm.ChatLlmProvider
import com.xingmou.data.catalog.QuestionDefinition
import com.xingmou.data.catalog.QuestionType
import com.xingmou.core.model.Port
import com.xingmou.core.model.SupportLevel

data class XingmouUiState(
    val isLoggedIn: Boolean = false,
    val loginRole: Port? = null,
    val loginIdentifier: String = "",
    val loginPassword: String = "",
    val loginMessage: String = "请选择登录身份。",
    val showAdultLogin: Boolean = false,
    val selectedPort: Port = Port.CHILD,
    val activeChildId: String = "child-seed",
    val activeChildAlias: String = "小星",
    val availableChildren: List<ChildSummaryUi> = emptyList(),
    val remoteAiConsent: Boolean = false,
    val exportConsent: Boolean = false,
    val dataRightsWorking: Boolean = false,
    val dataRightsMessage: String = "尚未执行数据权利操作。",
    val importReady: Boolean = false,
    val child: ChildUiState = ChildUiState(),
    val baseline: BaselineUiState = BaselineUiState(),
    val parent: ParentUiState = ParentUiState(),
    val professional: ProfessionalUiState = ProfessionalUiState(),
    val accessibility: AccessibilityUiState = AccessibilityUiState(),
    val aiConfigured: Boolean = false,
    val apiKeyMessage: String = "未设置 API Key；当前使用本地安全模式。",
    val organizationName: String = "星眸本地试点机构",
    val localUserName: String = "本地专业人员",
    val localUserRole: String = "professional",
    val institutionMessage: String = "本地机构模式",
    val localUsers: List<LocalUserUi> = emptyList()
)

data class ChildSummaryUi(val childId: String, val alias: String, val ageBand: String, val status: String)

data class LocalUserUi(val userId: String, val displayName: String, val login: String, val role: String, val status: String)

data class BaselineUiState(
    val status: BaselineStatus = BaselineStatus.NOT_STARTED,
    val currentIndex: Int = 0,
    val totalCount: Int = 6,
    val question: QuestionDefinition? = null,
    val message: String = "先做几个小练习，帮助小星找到合适的起点。",
    val scores: Map<String, Int> = emptyMap(),
    val isWorking: Boolean = false,
    val isOpen: Boolean = false
)

data class AccessibilityUiState(
    val speechEnabled: Boolean = true,
    val speechRate: Float = 1.0f,
    val speechVolume: Float = 1.0f,
    val largeText: Boolean = false,
    val highContrast: Boolean = false,
    val slowMotion: Boolean = false
)

fun normalizeSpeechRate(rate: Float): Float = rate.coerceIn(0.75f, 1.25f)

fun normalizeSpeechVolume(volume: Float): Float = volume.coerceIn(0.5f, 1.0f)

/** 把 Agent 运行状态机映射为面向家长/专业人员的友好进度文案。 */
fun agentStateLabel(state: AgentRunState): String = when (state) {
    AgentRunState.CREATED -> "准备中"
    AgentRunState.ROUTING -> "正在识别风险与权限…"
    AgentRunState.PLANNING -> "正在规划分析步骤…"
    AgentRunState.EXECUTING_TOOL -> "正在检索已审核资料…"
    AgentRunState.VALIDATING -> "正在校验结论与来源…"
    AgentRunState.WAITING_APPROVAL -> "等待专业审核…"
    AgentRunState.PAUSED -> "已暂停"
    AgentRunState.SAFETY_STOP -> "已安全停止"
    AgentRunState.COMPLETED -> "分析完成"
    AgentRunState.FAILED -> "分析未完成，已转本地兜底"
    AgentRunState.CANCELLED -> "已取消"
}

/** 把家长端知识检索路由映射为友好说明，用于「回答依据」可解释性面板。 */
fun parentRouteLabel(route: KnowledgeRoute?): String = when (route) {
    KnowledgeRoute.NORMAL -> "已审核资料直接匹配"
    KnowledgeRoute.CLARIFY -> "信息不足，等待补充细节"
    KnowledgeRoute.NOT_FOUND -> "未匹配到已审核条目"
    KnowledgeRoute.REFER -> "涉及诊疗，建议专业人员评估"
    KnowledgeRoute.SAFETY_STOP -> "安全筛查已停止"
    null -> "尚未检索"
}

/** 把家长端风险等级映射为友好文案。 */
fun parentRiskLabel(raw: String): String = when (raw) {
    "NONE" -> "未发现风险"
    "PAUSE" -> "需关注"
    "SAFETY_STOP" -> "已安全停止"
    "需要补充" -> "信息不足"
    "未评估" -> "尚未评估"
    else -> raw
}

/** 交互模式聊天消息。 */
data class ChatMessageUi(
    val id: String,
    val role: String, // "user" | "assistant"
    val content: String,
    val isError: Boolean = false
)

enum class DetectMode { NONE, CAMERA, SPEECH }
enum class DetectPhase { IDLE, AWAIT_PERMISSION, DETECTING, RETRYING, MANUAL_FALLBACK, HIT }

/** 观察题自动检测的界面状态；NONE 时题目走普通选项渲染。 */
data class AutoDetectState(
    val questionId: String = "",
    val mode: DetectMode = DetectMode.NONE,
    val phase: DetectPhase = DetectPhase.IDLE,
    val hint: String = "",
    val remainingMs: Long = 0L,
    val timeoutMs: Long = 10_000L,
    val permission: String = "",
    val matchedKeyword: String? = null
)

data class ChildUiState(
    val instruction: String = "找到目标图形",
    val options: List<String> = listOf("●", "▲"),
    val courseProgress: Int = 0,
    val courseTotal: Int = 5,
    val currentCourseLevel: Int = 1,
    val courseTitle: String = "图片配对",
    val courseQuestionId: String? = null,
    val courseQuestionType: QuestionType = QuestionType.CHOICE,
    val courseStimulus: String = "",
    val coursePreviewMs: Long = 3_000L,
    /** 正式训练使用的专业方案快照；基线和无方案训练为空。 */
    val coursePlanId: String? = null,
    val coursePlanVersion: Int? = null,
    val coursePlanModuleId: String? = null,
    val coursePlanGoal: String = "",
    val assetKey: String = "training_star",
    val courseUnlocked: Boolean = false,
    val courseOpen: Boolean = true,
    val courseSummary: String = "尚无第一关记录",
    val coursePoints: Int = 0,
    val completedRounds: Int = 0,
    val encouragementTrend: String = "刚刚开始",
    val rewardMessage: String = "先完成一个小活动，就会点亮第一颗小星星。",
    val interest: String = "图片",
    val interestOptions: List<String> = listOf("图片", "动物", "交通", "生活用品"),
    val courseMap: List<CourseLevelUi> = V08_COURSE_LEVELS,
    val difficulty: Int = 1,
    val supportLevel: SupportLevel = SupportLevel.L1,
    val message: String = "慢慢看，选一个就好。",
    val recentResults: List<TrainingResult> = emptyList(),
    val consecutiveFailures: Int = 0,
    val isPaused: Boolean = false,
    val isSafetyStopped: Boolean = false,
    val isWorking: Boolean = false,
    val lastEvent: String = "等待开始",
    val curriculumMap: CurriculumMapUi = CurriculumMapUi(),
    val curriculumPlayer: CurriculumPlayerUi = CurriculumPlayerUi(),
    val rainbowProfile: RainbowProfileUi = RainbowProfileUi(),
    val chatMessages: List<ChatMessageUi> = emptyList(),
    val chatLoading: Boolean = false,
    val chatError: String? = null,
    val chatProvider: ChatLlmProvider = ChatLlmProvider.DEEPSEEK,
    val chatHint: String = "和小星说说话吧，任何问题都可以问。",
    /** 儿童端快捷话题：点击即发送，免去打字门槛。 */
    val chatQuickTopics: List<String> = listOf(
        "我今天好开心",
        "陪我玩个游戏",
        "讲个小故事",
        "我想听儿歌",
        "我有点难过",
        "教我念一首小诗"
    ),
    /** 当前所选供应商是否已配置 API Key。 */
    val chatProviderConfigured: Boolean = false,
    /** 感知系统状态：实时情感/专注度/反馈 */
    val perceptionEnabled: Boolean = false,
    val perceptionPreview: Boolean = false,
    val autoDetect: AutoDetectState = AutoDetectState(),
    val perceptionEmotion: String = "",
    val perceptionFocus: String = "",
    val perceptionFeedback: String = "",
    /** 最近一次作答结果，驱动答题音效；null = 不计分的观察题，lastAnswerAt 每次作答都变化 */
    val lastAnswerCorrect: Boolean? = null,
    val lastAnswerAt: Long = 0L
)

data class CourseLevelUi(
    val level: Int,
    val title: String,
    val status: String
)

/** 20 关彩虹冒险地图的关卡状态：锁定 → 可进入 → 已完成（解锁链）。 */
enum class CurriculumLevelStatus { LOCKED, AVAILABLE, COMPLETED }

data class CurriculumLevelUi(
    val order: Int,
    val title: String,
    val icon: String,
    val theme: String,
    val difficulty: Int,
    val status: CurriculumLevelStatus,
    val stars: Int = 0
)

/** 地图：20 关列表 + 解锁链 + 兴趣门槛（选择主题后才开放第一关）。 */
data class CurriculumMapUi(
    val levels: List<CurriculumLevelUi> = emptyList(),
    val completedLevels: Int = 0,
    val totalLevels: Int = 20,
    val activeLevel: Int = 1,
    val interest: String = "图片",
    val interestOptions: List<String> = listOf("图片", "动物", "交通", "生活用品"),
    val interestChosen: Boolean = false
)

/** 儿童端「我的彩虹画像」单域条：名称 + 表情 + 颜色键 + 训练起点分（0–100）。 */
data class RainbowDomainUi(
    val id: String,
    val name: String,
    val emoji: String,
    val colorKey: String,
    val score: Int,
    val description: String = ""
)

/** 儿童端「我的彩虹画像」数据画报：完成起点小测后生成，展示六域训练起点与鼓励叙述。 */
data class RainbowProfileUi(
    val present: Boolean = false,
    val domainBars: List<RainbowDomainUi> = emptyList(),
    val narrative: String = "",
    val parentSummary: String = "",
    val parentDetailAdvice: String = "",
    val createdLabel: String = ""
)

/** 播放器：某一关内 5 个活动顺序作答；通过判定与结果均为内存态（不动 DB）。 */
data class CurriculumPlayerUi(
    val levelOrder: Int? = null,
    val levelTitle: String = "",
    val activityIndex: Int = 0,
    val activityTotal: Int = 5,
    val activityLabel: String = "",
    val question: QuestionDefinition? = null,
    val runCompleted: Int = 0,
    val runCorrect: Int = 0,
    val runTotal: Int = 0,
    val isWorking: Boolean = false,
    val message: String = "",
    val finished: Boolean = false,
    val passed: Boolean = false
)

val V08_COURSE_LEVELS: List<CourseLevelUi> = listOf(
    "图片配对", "颜色辨别", "形状匹配", "目标搜索", "即时记忆",
    "顺序记忆", "分类推理", "大小排序", "按顺序放图片", "找不同",
    "指认物品", "跟读词语", "理解指令", "替代沟通", "情绪识别",
    "互动轮流", "情境选择", "生活工具", "模仿动作", "生活顺序"
).mapIndexed { index, title -> CourseLevelUi(index + 1, title, "待补齐") }

fun ChildUiState.apply(decision: AgentEventDecision): ChildUiState = copy(
    difficulty = decision.nextDifficulty ?: difficulty,
    supportLevel = decision.nextSupportLevel
        ?.let { runCatching { SupportLevel.valueOf(it) }.getOrNull() }
        ?: supportLevel,
    message = decision.message,
    isPaused = decision.targetState == AgentRunState.PAUSED,
    isSafetyStopped = decision.targetState == AgentRunState.SAFETY_STOP,
    isWorking = false,
    lastEvent = decision.event.eventType
)

/** 家长端「六域训练概览」单域统计：练习量 + 正确率。 */
data class ParentDomainStatUi(
    val id: String,
    val name: String,
    val emoji: String,
    val colorKey: String,
    val accuracy: Int,
    val count: Int
)

/** 家长端与专业端共用的模块自适应快照，来源于本地 Room。 */
data class ModuleAdaptiveUi(
    val moduleId: String,
    val moduleName: String,
    val difficulty: Int,
    val supportLevel: String,
    val correctStreak: Int,
    val errorStreak: Int,
    val lastQuestionId: String?,
    val baselineVersion: Int,
    val planVersion: Int?,
    val updatedAt: Long
)

data class ParentUiState(
    val query: String = "",
    val route: KnowledgeRoute? = null,
    val message: String = "写下一个具体观察，例如：孩子连续两次没完成图片配对。",
    val suggestions: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    val recordCount: Int = 0,
    val riskLabel: String = "未评估",
    val profile: RainbowProfileUi = RainbowProfileUi(),
    val domainOverview: List<ParentDomainStatUi> = emptyList(),
    val adaptiveOverview: List<ModuleAdaptiveUi> = emptyList(),
    val trendPoints: List<ReportTrendPointUi> = emptyList(),
    val recentTrainingDetails: List<TrainingDetailUi> = emptyList(),
    val isWorking: Boolean = false,
    val agentRunId: String? = null,
    val agentStatus: String = "本地待命",
    val homeTaskTitle: String = "相同图片配对练习（5 分钟）",
    val homeTaskDescription: String = "准备 2～3 对完全相同的图片卡（孩子熟悉的日常物品照片）。先由家长示范：拿起一张，找到相同的放在一起，同时说出名称；再把卡片打乱摆开，请孩子找出相同的两张，配对成功立即肯定。熟练后再过渡到相似图片或相关事物配对（如牙刷和杯子）。出现疲劳或拒绝时暂停。",
    val homeTaskStatus: String = "pending",
    val homeTaskPlanVersion: Int? = null,
    val homeTaskFrequency: String = "按需",
    val homeTaskDurationMinutes: Int = 5,
    val homeTaskSupportLevel: String = "L1",
    val homeTaskStopConditions: String = "出现疲劳、拒绝或风险时暂停",
    val homeTaskSafetyStopped: Boolean = false,
    val weekCompletionRate: String = "暂无本周完成记录",
    val weekStatusSummary: String = "本周状态等待记录",
    val weekSuggestion: String = "完成一次短时任务后，再记录孩子当时的状态。",
    val homeDemoStep: Int = 0,
    val feedbackMood: String = "状态平稳",
    val feedbackNote: String = "",
    val feedbackMessage: String = "记录今天的状态，帮助下一次安排支持。"
)

data class ProfessionalUiState(
    val recordCount: Int = 0,
    val dataSufficient: Boolean = false,
    val analysisSummary: List<String> = listOf("正在读取本地训练记录。"),
    val warningSignals: List<String> = emptyList(),
    val planStatus: PlanStatus? = null,
    val planSummary: String = "达到 3 条有效记录后，可生成方案草案。",
    val planDiffs: List<PlanDiffUi> = emptyList(),
    val planTask: String = "图片配对",
    val planGoal: String = "在当前支持等级下完成训练",
    val planDifficulty: String = "1",
    val planSupportLevel: String = "L1",
    val planFrequency: String = "每日 1–2 次",
    val planDuration: String = "5 分钟",
    val planStopConditions: String = "出现疲劳、拒绝或风险时暂停",
    val reviewComment: String = "",
    val reviewMessage: String = "方案需由专业人员确认后才能生效。",
    val isWorking: Boolean = false,
    val agentRunId: String? = null,
    val agentStatus: String = "本地待命",
    val recentEvent: String = "尚无事件",
    val evidence: List<String> = listOf("RiskEngine", "AnalysisEngine", "PlanStateMachine"),
    val auditRuns: List<AgentRunAuditUi> = emptyList(),
    val auditReplay: List<AgentReplayLineUi> = emptyList(),
    val auditSelectedRunId: String? = null,
    val auditMessage: String = "点击刷新查看当前儿童的 Agent 运行记录。",
    val auditMetricsSummary: String = "人工标注：暂无",
    val reportMetrics: List<ReportMetricUi> = listOf(
        ReportMetricUi("正确率", "—", "至少 3 条记录后计算"),
        ReportMetricUi("独立完成率", "—", "L0 或无需提示"),
        ReportMetricUi("提示依赖", "—", "提示等级越低越独立"),
        ReportMetricUi("平均反应时", "—", "仅统计有反应时记录"),
        ReportMetricUi("趋势", "数据不足", "按前后半段比较")
    ),
    val reportTrend: List<ReportTrendPointUi> = emptyList(),
    val reportGroups: List<ReportGroupUi> = emptyList(),
    val adaptiveOverview: List<ModuleAdaptiveUi> = emptyList(),
    val recentTrainingDetails: List<TrainingDetailUi> = emptyList(),
    val assessmentId: String = "GESELL",
    val assessmentName: String = "Gesell",
    val assessmentDate: String = "",
    val assessmentSource: String = "专业人员转录",
    val assessmentScores: String = "",
    val assessmentNotes: String = "",
    val assessmentMessage: String = "量表仅作为专业转录记录，不用于平台自动诊断。",
    val recentAssessments: List<AssessmentRecordUi> = emptyList(),
    val profileVersionSummary: String = "尚无量表复评记录。",
    val profileEvidenceSummary: String = "能力画像尚无可展示的证据引用。",
    val profileEvidenceDetails: List<ProfileEvidenceUi> = emptyList(),
    val assessmentChanges: List<AssessmentChangeUi> = emptyList(),
    val careStage: String = "接案",
    val careStageStatus: String = "待开始",
    val careStageSummary: String = "尚未建立专业个案记录。",
    val careNote: String = "",
    val careClosureReason: String = "",
    val careFollowUpPlan: String = "",
    val careFollowUpDate: String = "",
    val carePermissionMessage: String = "",
    val careSignature: String = "local-professional",
    val careTimeline: List<CareRecordUi> = emptyList(),
    val recentHomeFeedback: List<HomeFeedbackUi> = emptyList()
)

data class AgentRunAuditUi(
    val runId: String, val taskType: String, val port: String,
    val status: String, val startedAt: Long
)

data class AgentReplayLineUi(
    val timestamp: Long,
    val category: String,
    val description: String,
    val traceId: String? = null,
    val humanDecision: String? = null
)

data class ReportMetricUi(val label: String, val value: String, val detail: String)

data class ReportGroupUi(
    val domain: String,
    val task: String,
    val sampleCount: Int,
    val accuracy: String,
    val independentRate: String,
    val averageReaction: String
)

data class ReportTrendPointUi(val label: String, val accuracy: Float, val sampleCount: Int, val dateRange: String = "")

data class TrainingDetailUi(
    val timestamp: Long,
    val domain: String,
    val task: String,
    val result: String,
    val support: String,
    val reaction: String
)

/** 支持等级 L0-L4 翻译为家长可读的描述。 */
fun scoreLevelWord(score: Int): String = when {
    score >= 80 -> "表现不错"
    score >= 50 -> "中等水平"
    score >= 30 -> "发展中"
    score > 0 -> "起步阶段"
    else -> "尚未测评"
}

fun supportWord(level: String): String = when (level) {
    "L0" -> "独立完成"
    "L1" -> "需要少量提示"
    "L2" -> "需要一些提示"
    "L3" -> "需要较多帮助"
    "L4" -> "需要全程帮助"
    else -> "支持等级待定"
}

data class AssessmentRecordUi(
    val assessmentName: String,
    val version: Int,
    val recordType: String,
    val assessmentDate: String,
    val source: String,
    val scores: String,
    val notes: String
)

data class PlanDiffUi(val field: String, val previous: String, val current: String)

data class ProfileEvidenceUi(
    val assessmentName: String,
    val version: Int,
    val recordType: String,
    val date: String,
    val source: String
)

data class AssessmentChangeUi(
    val assessmentName: String,
    val fromVersion: Int,
    val toVersion: Int,
    val changes: List<String>
)

data class CareRecordUi(
    val stage: String,
    val stageLabel: String,
    val status: String,
    val summary: String,
    val createdAt: Long,
    val signed: Boolean,
    val note: String,
    val closureReason: String? = null,
    val followUpPlan: String? = null,
    val followUpDate: String? = null
)

data class HomeFeedbackUi(
    val createdAt: Long,
    val taskTitle: String,
    val mood: String,
    val fatigue: String,
    val note: String
)

enum class PlanUiAction { CONFIRM, ACTIVATE, REJECT }

fun reducePlanStatus(
    current: PlanStatus,
    action: PlanUiAction,
    comment: String,
    stateMachine: PlanStateMachine = PlanStateMachine()
): PlanStatus {
    val target = when (action) {
        PlanUiAction.CONFIRM -> PlanStatus.CONFIRMED
        PlanUiAction.ACTIVATE -> PlanStatus.ACTIVE
        PlanUiAction.REJECT -> PlanStatus.REJECTED
    }
    return stateMachine.transition(current, target, PlanActor.PROFESSIONAL, comment)
        .takeIf { it.accepted }
        ?.newStatus
        ?: current
}
