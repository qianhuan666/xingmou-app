package com.xingmou.core.agent

import com.google.gson.JsonObject
import com.xingmou.core.llm.DeepSeekClient
import com.xingmou.core.llm.JsonValidator
import com.xingmou.core.model.Port
import com.xingmou.core.model.RiskLevel
import com.xingmou.core.safety.SafeResponses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface ModelGateway {
    suspend fun complete(systemPrompt: String, userMessage: String): Result<String>
}

class DeepSeekModelGateway(private val client: DeepSeekClient) : ModelGateway {
    override suspend fun complete(systemPrompt: String, userMessage: String): Result<String> =
        client.complete(systemPrompt, userMessage)
}

data class AgentOrchestrationRequest(
    val runId: String,
    val taskType: String,
    val childId: String?,
    val input: AgentContextInput,
    val maxSteps: Int = 6
)

enum class OrchestrationRoute { COMPLETED, SAFETY_STOP, WAITING_APPROVAL, FALLBACK, FAILED }

data class AgentOrchestrationResult(
    val runId: String,
    val route: OrchestrationRoute,
    val state: AgentRunState,
    val output: JsonObject? = null,
    val fallbackText: String? = null,
    val toolResult: AgentToolResult? = null,
    val error: String? = null
)

/** AI 闭环编排：规则检查 → 上下文 → 模型 → JSON 校验 → 工具/降级 → 审计状态。 */
class AgentOrchestrator(
    private val model: ModelGateway,
    private val registry: ToolRegistry = ToolRegistry(),
    private val runtime: AgentRuntime = AgentRuntime(registry),
    private val contextAssembler: ContextAssembler = ContextAssembler()
) {
    suspend fun run(request: AgentOrchestrationRequest): AgentOrchestrationResult = withContext(Dispatchers.Default) {
        require(request.maxSteps in 1..12) { "maxSteps 必须在 1 到 12 之间。" }
        runtime.create(request.runId, request.taskType, request.input.port, request.childId)
        runtime.transition(request.runId, AgentRunState.ROUTING)
        val context = contextAssembler.assemble(request.input)
        if (context.risk.level == RiskLevel.SAFETY_STOP) {
            runtime.transition(request.runId, AgentRunState.SAFETY_STOP, reason = "规则引擎前置拦截")
            return@withContext AgentOrchestrationResult(request.runId, OrchestrationRoute.SAFETY_STOP, AgentRunState.SAFETY_STOP,
                fallbackText = safeText(request.input.port))
        }
        runtime.transition(request.runId, AgentRunState.PLANNING)
        var modelMessage = context.userMessage
        var lastToolResult: AgentToolResult? = null
        val observations = mutableListOf<String>()
        repeat(request.maxSteps) { stepIndex ->
            val raw = model.complete(context.systemPrompt, modelMessage).getOrElse { error ->
                runtime.transition(request.runId, AgentRunState.FAILED, reason = error.message)
                return@withContext AgentOrchestrationResult(request.runId, OrchestrationRoute.FALLBACK, AgentRunState.FAILED,
                    fallbackText = fallbackText(request.input.port), toolResult = lastToolResult, error = error.message)
            }
            val validated = JsonValidator.validate(request.input.port, raw).getOrElse { error ->
                runtime.transition(request.runId, AgentRunState.FAILED, reason = error.message)
                return@withContext AgentOrchestrationResult(request.runId, OrchestrationRoute.FALLBACK, AgentRunState.FAILED,
                    fallbackText = fallbackText(request.input.port), toolResult = lastToolResult, error = error.message)
            }
            val action = parseAction(validated)
            if (action == null) {
                if (runtime.get(request.runId).state == AgentRunState.PLANNING) {
                    runtime.transition(request.runId, AgentRunState.VALIDATING)
                }
                runtime.transition(request.runId, AgentRunState.COMPLETED, reason = "模型 JSON 和工具观察均已校验")
                return@withContext AgentOrchestrationResult(
                    request.runId, OrchestrationRoute.COMPLETED, AgentRunState.COMPLETED,
                    output = validated, toolResult = lastToolResult
                )
            }
            val toolRequest = ToolCallRequest(
                request.runId, "${request.runId}-call-$stepIndex", action.toolName, request.input.port,
                action.argumentsSummary, action.idempotencyKey, context.risk.level
            )
            if (action.requiresReview) {
                val authorization = registry.authorize(toolRequest)
                if (authorization.status == ToolAuthorizationStatus.DENIED) {
                    runtime.transition(request.runId, AgentRunState.FAILED, reason = authorization.reason)
                    return@withContext AgentOrchestrationResult(request.runId, OrchestrationRoute.FALLBACK, AgentRunState.FAILED, validated,
                        fallbackText = SafeResponses.INSUFFICIENT_DATA, error = authorization.reason)
                }
                runtime.transition(request.runId, AgentRunState.WAITING_APPROVAL, reason = "模型动作声明需要人工审核")
                return@withContext AgentOrchestrationResult(request.runId, OrchestrationRoute.WAITING_APPROVAL, AgentRunState.WAITING_APPROVAL, validated)
            }
            runtime.transition(request.runId, AgentRunState.EXECUTING_TOOL)
            val toolResult = runtime.executeTool(toolRequest)
            lastToolResult = toolResult
            if (toolResult.authorization.status == ToolAuthorizationStatus.SAFETY_STOP) {
                return@withContext AgentOrchestrationResult(request.runId, OrchestrationRoute.SAFETY_STOP, AgentRunState.SAFETY_STOP, validated, toolResult = toolResult)
            }
            if (toolResult.authorization.status == ToolAuthorizationStatus.REQUIRES_APPROVAL) {
                return@withContext AgentOrchestrationResult(request.runId, OrchestrationRoute.WAITING_APPROVAL, AgentRunState.WAITING_APPROVAL, validated, toolResult = toolResult)
            }
            if (toolResult.authorization.status != ToolAuthorizationStatus.ALLOWED && toolResult.authorization.status != ToolAuthorizationStatus.DUPLICATE) {
                return@withContext AgentOrchestrationResult(request.runId, OrchestrationRoute.FALLBACK, runtime.get(request.runId).state, validated, toolResult = toolResult,
                    fallbackText = SafeResponses.INSUFFICIENT_DATA)
            }
            observations += "工具观察[${action.toolName}]：${toolResult.resultSummary ?: toolResult.executionStatus}"
            modelMessage = buildString {
                append(context.userMessage)
                append("\n").append(observations.joinToString("\n"))
                append("\n请基于工具观察继续下一步；如无需工具，输出最终端口 JSON 且不含 action。")
            }
        }
        runtime.transition(request.runId, AgentRunState.FAILED, reason = "超过最大 Agent 步数")
        AgentOrchestrationResult(
            request.runId, OrchestrationRoute.FALLBACK, AgentRunState.FAILED,
            fallbackText = fallbackText(request.input.port), toolResult = lastToolResult, error = "超过最大 Agent 步数"
        )
    }

    private data class ParsedAction(val toolName: String, val argumentsSummary: String, val idempotencyKey: String, val requiresReview: Boolean)

    private fun parseAction(obj: JsonObject): ParsedAction? {
        val action = obj.get("action") ?: return null
        if (!action.isJsonObject) return null
        val actionObj = action.asJsonObject
        val toolName = actionObj.get("tool_name")?.asString ?: return null
        return ParsedAction(
            toolName,
            actionObj.get("arguments")?.toString() ?: "{}",
            actionObj.get("idempotency_key")?.asString ?: "action-$toolName",
            actionObj.get("requires_review")?.asBoolean ?: false
        )
    }

    private fun safeText(port: Port): String = when (port) {
        Port.CHILD -> SafeResponses.SAFETY_STOP_CHILD.joinToString(" ")
        Port.PARENT -> SafeResponses.SAFETY_STOP_PARENT
        Port.PROFESSIONAL -> SafeResponses.SAFETY_STOP_PROFESSIONAL
    }

    private fun fallbackText(port: Port): String = when (port) {
        Port.CHILD -> SafeResponses.LLM_FALLBACK_CHILD.joinToString(" ")
        Port.PARENT, Port.PROFESSIONAL -> SafeResponses.INSUFFICIENT_DATA
    }
}
