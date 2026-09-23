package com.xingmou.core.agent

import com.xingmou.core.model.Port
import com.xingmou.core.model.RiskLevel
import com.xingmou.data.db.AgentRunEntity
import com.xingmou.data.db.AgentStepEntity
import com.xingmou.data.db.ToolCallEntity

enum class AgentRunStatus { RUNNING, WAITING, COMPLETED, PAUSED, SAFETY_STOP, FAILED, CANCELLED }

data class AgentToolResult(
    val authorization: ToolAuthorization,
    val executionStatus: String,
    val resultSummary: String? = null
)

data class AgentRunSnapshot(
    val runId: String,
    val taskType: String,
    val port: Port,
    val childId: String?,
    val state: AgentRunState,
    val status: AgentRunStatus,
    val steps: List<AgentStepEntity> = emptyList(),
    val toolCalls: List<ToolCallEntity> = emptyList(),
    val errorMessage: String? = null
)

typealias ToolHandler = (ToolCallRequest) -> String

/**
 * 轻量 Agent Runtime：只管理路由、工具授权、幂等和审计轨迹；模型推理由上层注入。
 * 所有状态都可映射到阶段 1 已建立的 Room 表。
 */
class AgentRuntime(
    private val registry: ToolRegistry = ToolRegistry(),
    private val handlers: Map<String, ToolHandler> = emptyMap()
) {
    private val runs = linkedMapOf<String, AgentRunSnapshot>()
    private val idempotencyResults = linkedMapOf<String, String>()

    fun create(runId: String, taskType: String, port: Port, childId: String? = null): AgentRunSnapshot {
        check(runId !in runs) { "runId 已存在：$runId" }
        val snapshot = AgentRunSnapshot(runId, taskType, port, childId, AgentRunState.CREATED, AgentRunStatus.RUNNING)
        runs[runId] = snapshot
        return snapshot
    }

    fun get(runId: String): AgentRunSnapshot = runs[runId] ?: error("找不到 Agent 运行：$runId")

    fun findOrNull(runId: String): AgentRunSnapshot? = runs[runId]

    /** 从 Room 最近一致状态恢复运行；不会重新执行已记录的工具副作用。 */
    fun restore(
        run: AgentRunEntity,
        steps: List<AgentStepEntity> = emptyList(),
        toolCalls: List<ToolCallEntity> = emptyList()
    ): AgentRunSnapshot {
        val snapshot = AgentRunSnapshot(
            runId = run.runId,
            taskType = run.taskType,
            port = Port.from(run.port),
            childId = run.childId,
            state = AgentRunState.valueOf(run.state),
            status = AgentRunStatus.valueOf(run.status),
            steps = steps.sortedBy { it.stepIndex },
            toolCalls = toolCalls.sortedBy { it.createdAt },
            errorMessage = run.errorMessage
        )
        runs[run.runId] = snapshot
        toolCalls.filter { it.executionStatus == "succeeded" && it.resultSummary != null }.forEach {
            idempotencyResults[it.idempotencyKey] = requireNotNull(it.resultSummary)
        }
        return snapshot
    }

    fun transition(runId: String, target: AgentRunState, now: Long = 0L, reason: String? = null): AgentStateTransition {
        val current = get(runId)
        val result = ExecutionStateMachine.transition(current.state, target)
        if (!result.accepted) return result
        val status = when (target) {
            AgentRunState.WAITING_APPROVAL -> AgentRunStatus.WAITING
            AgentRunState.COMPLETED -> AgentRunStatus.COMPLETED
            AgentRunState.PAUSED -> AgentRunStatus.PAUSED
            AgentRunState.SAFETY_STOP -> AgentRunStatus.SAFETY_STOP
            AgentRunState.FAILED -> AgentRunStatus.FAILED
            AgentRunState.CANCELLED -> AgentRunStatus.CANCELLED
            else -> AgentRunStatus.RUNNING
        }
        val step = AgentStepEntity(
            stepId = "${runId}-step-${current.steps.size}", runId = runId, stepIndex = current.steps.size,
            state = target.name, actionType = "state_transition", inputSummary = reason,
            toolResultSummary = null, errorMessage = null, retryCount = 0, createdAt = now
        )
        runs[runId] = current.copy(state = target, status = status, steps = current.steps + step)
        return result
    }

    fun executeTool(request: ToolCallRequest, now: Long = 0L): AgentToolResult {
        val current = get(request.runId)
        require(current.state == AgentRunState.EXECUTING_TOOL) { "只有 EXECUTING_TOOL 状态可以调用工具。" }
        val usedKeys = current.toolCalls.map { it.idempotencyKey }.toSet()
        val authorization = registry.authorize(request, usedKeys)
        val cached = idempotencyResults[request.idempotencyKey]
        if (authorization.status == ToolAuthorizationStatus.DUPLICATE && cached != null) {
            val duplicate = authorization.copy(reason = "返回幂等缓存结果。")
            appendToolCall(request, duplicate, "duplicate", cached, now)
            return AgentToolResult(duplicate, "duplicate", cached)
        }
        if (authorization.status != ToolAuthorizationStatus.ALLOWED) {
            val execution = if (authorization.status == ToolAuthorizationStatus.SAFETY_STOP) "blocked_safety" else "blocked"
            appendToolCall(request, authorization, execution, null, now)
            if (authorization.status == ToolAuthorizationStatus.SAFETY_STOP) transition(request.runId, AgentRunState.SAFETY_STOP, now, authorization.reason)
            if (authorization.status == ToolAuthorizationStatus.REQUIRES_APPROVAL) transition(request.runId, AgentRunState.WAITING_APPROVAL, now, authorization.reason)
            return AgentToolResult(authorization, execution)
        }
        val result = handlers[request.toolName]?.invoke(request) ?: "tool:${request.toolName}:accepted"
        idempotencyResults[request.idempotencyKey] = result
        appendToolCall(request, authorization, "succeeded", result, now)
        transition(request.runId, AgentRunState.VALIDATING, now, "工具执行完成，等待结果校验。")
        return AgentToolResult(authorization, "succeeded", result)
    }

    fun toRunEntity(runId: String, startedAt: Long, finishedAt: Long? = null): AgentRunEntity {
        val run = get(runId)
        return AgentRunEntity(run.runId, run.taskType, run.port.name, run.childId, run.state.name, run.status.name,
            startedAt, finishedAt, run.errorMessage)
    }

    private fun appendToolCall(request: ToolCallRequest, authorization: ToolAuthorization, execution: String, result: String?, now: Long) {
        val current = get(request.runId)
        val call = ToolCallEntity(
            callId = request.callId, runId = request.runId, toolName = request.toolName,
            argumentsSummary = request.argumentsSummary, authorizationStatus = authorization.status.name,
            executionStatus = execution, idempotencyKey = request.idempotencyKey, resultSummary = result,
            createdAt = now, finishedAt = now
        )
        runs[request.runId] = current.copy(toolCalls = current.toolCalls + call)
    }
}
