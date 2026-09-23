package com.xingmou.core.agent

enum class AgentRunState {
    CREATED, ROUTING, PLANNING, WAITING_APPROVAL, EXECUTING_TOOL, VALIDATING,
    COMPLETED, PAUSED, SAFETY_STOP, FAILED, CANCELLED
}

data class AgentStateTransition(
    val accepted: Boolean,
    val state: AgentRunState,
    val reason: String? = null
)

/** Agent 运行状态机，禁止模型或调用方跳过安全检查与人工审核。 */
object ExecutionStateMachine {
    fun transition(current: AgentRunState, target: AgentRunState): AgentStateTransition {
        val allowed = setOf(
            AgentRunState.CREATED to AgentRunState.ROUTING,
            AgentRunState.ROUTING to AgentRunState.PLANNING,
            AgentRunState.ROUTING to AgentRunState.SAFETY_STOP,
            AgentRunState.ROUTING to AgentRunState.FAILED,
            AgentRunState.PLANNING to AgentRunState.WAITING_APPROVAL,
            AgentRunState.PLANNING to AgentRunState.EXECUTING_TOOL,
            AgentRunState.PLANNING to AgentRunState.VALIDATING,
            AgentRunState.PLANNING to AgentRunState.PAUSED,
            AgentRunState.PLANNING to AgentRunState.SAFETY_STOP,
            AgentRunState.PLANNING to AgentRunState.FAILED,
            AgentRunState.WAITING_APPROVAL to AgentRunState.EXECUTING_TOOL,
            AgentRunState.WAITING_APPROVAL to AgentRunState.PLANNING,
            AgentRunState.WAITING_APPROVAL to AgentRunState.PAUSED,
            AgentRunState.WAITING_APPROVAL to AgentRunState.FAILED,
            AgentRunState.EXECUTING_TOOL to AgentRunState.VALIDATING,
            AgentRunState.EXECUTING_TOOL to AgentRunState.WAITING_APPROVAL,
            AgentRunState.EXECUTING_TOOL to AgentRunState.PAUSED,
            AgentRunState.EXECUTING_TOOL to AgentRunState.SAFETY_STOP,
            AgentRunState.EXECUTING_TOOL to AgentRunState.FAILED,
            AgentRunState.VALIDATING to AgentRunState.EXECUTING_TOOL,
            AgentRunState.VALIDATING to AgentRunState.COMPLETED,
            AgentRunState.VALIDATING to AgentRunState.WAITING_APPROVAL,
            AgentRunState.VALIDATING to AgentRunState.FAILED,
            AgentRunState.PAUSED to AgentRunState.PLANNING,
            AgentRunState.PAUSED to AgentRunState.EXECUTING_TOOL,
            AgentRunState.PAUSED to AgentRunState.CANCELLED
        ).contains(current to target)
        return if (allowed) AgentStateTransition(true, target)
        else AgentStateTransition(false, current, "不允许从 $current 直接转换为 $target。")
    }
}
