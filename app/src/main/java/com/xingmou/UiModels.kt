package com.xingmou

import com.xingmou.core.agent.AgentEventDecision
import com.xingmou.core.agent.AgentRunState
import com.xingmou.core.domain.KnowledgeRoute
import com.xingmou.core.domain.PlanActor
import com.xingmou.core.domain.PlanStateMachine
import com.xingmou.core.domain.PlanStatus
import com.xingmou.core.domain.TrainingResult
import com.xingmou.core.domain.BaselineStatus
import com.xingmou.data.catalog.QuestionDefinition
import com.xingmou.core.model.Port
import com.xingmou.core.model.SupportLevel

data class XingmouUiState(
    val selectedPort: Port = Port.CHILD,
    val activeChildId: String = "child-seed",
    val activeChildAlias: String = "小星",
    val availableChildren: List<ChildSummaryUi> = emptyList(),
    val remoteAiConsent: Boolean = false,
    val exportConsent: Boolean = false,
    val child: ChildUiState = ChildUiState(),
    val baseline: BaselineUiState = BaselineUiState(),
    val parent: ParentUiState = ParentUiState(),
    val professional: ProfessionalUiState = ProfessionalUiState(),
    val accessibility: AccessibilityUiState = AccessibilityUiState(),
    val aiConfigured: Boolean = false
)

data class ChildSummaryUi(val childId: String, val alias: String, val ageBand: String, val status: String)

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
    val highContrast: Boolean = false
)

fun normalizeSpeechRate(rate: Float): Float = rate.coerceIn(0.75f, 1.25f)

fun normalizeSpeechVolume(volume: Float): Float = volume.coerceIn(0.5f, 1.0f)

data class ChildUiState(
    val instruction: String = "找到圆形",
    val options: List<String> = listOf("圆形", "三角形"),
    val courseProgress: Int = 0,
    val courseTotal: Int = 5,
    val courseQuestionId: String? = null,
    val courseUnlocked: Boolean = false,
    val courseOpen: Boolean = true,
    val courseSummary: String = "尚无第一关记录",
    val difficulty: Int = 1,
    val supportLevel: SupportLevel = SupportLevel.L1,
    val message: String = "慢慢看，选一个就好。",
    val recentResults: List<TrainingResult> = emptyList(),
    val consecutiveFailures: Int = 0,
    val isPaused: Boolean = false,
    val isSafetyStopped: Boolean = false,
    val isWorking: Boolean = false,
    val lastEvent: String = "等待开始"
)

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

data class ParentUiState(
    val query: String = "",
    val route: KnowledgeRoute? = null,
    val message: String = "写下一个具体观察，例如：孩子连续两次没完成图片配对。",
    val suggestions: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    val recordCount: Int = 0,
    val riskLabel: String = "未评估",
    val isWorking: Boolean = false,
    val agentRunId: String? = null,
    val agentStatus: String = "本地待命",
    val homeTaskTitle: String = "五分钟图片配对陪练",
    val homeTaskDescription: String = "准备两个熟悉的图片，先示范一次，再邀请孩子自己试试。出现疲劳或拒绝时暂停。",
    val homeTaskStatus: String = "pending",
    val feedbackMood: String = "平稳",
    val feedbackFatigue: String = "不确定",
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
    val reviewComment: String = "",
    val reviewMessage: String = "方案需由专业人员确认后才能生效。",
    val isWorking: Boolean = false,
    val agentRunId: String? = null,
    val agentStatus: String = "本地待命",
    val recentEvent: String = "尚无事件",
    val evidence: List<String> = listOf("RiskEngine", "AnalysisEngine", "PlanStateMachine")
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
