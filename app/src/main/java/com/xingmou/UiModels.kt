package com.xingmou

import com.xingmou.core.agent.AgentEventDecision
import com.xingmou.core.agent.AgentRunState
import com.xingmou.core.domain.KnowledgeRoute
import com.xingmou.core.domain.PlanActor
import com.xingmou.core.domain.PlanStateMachine
import com.xingmou.core.domain.PlanStatus
import com.xingmou.core.domain.TrainingResult
import com.xingmou.core.model.Port
import com.xingmou.core.model.SupportLevel

data class XingmouUiState(
    val selectedPort: Port = Port.CHILD,
    val child: ChildUiState = ChildUiState(),
    val parent: ParentUiState = ParentUiState(),
    val professional: ProfessionalUiState = ProfessionalUiState(),
    val aiConfigured: Boolean = false
)

data class ChildUiState(
    val instruction: String = "找到圆形",
    val options: List<String> = listOf("圆形", "三角形"),
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
    val agentStatus: String = "本地待命"
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
