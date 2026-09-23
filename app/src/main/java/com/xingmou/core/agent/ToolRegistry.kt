package com.xingmou.core.agent

import com.xingmou.core.model.Port
import com.xingmou.core.model.RiskLevel

enum class ToolRisk { LOW, SENSITIVE, HIGH }

data class ToolDefinition(
    val name: String,
    val description: String,
    val allowedPorts: Set<Port>,
    val risk: ToolRisk = ToolRisk.LOW,
    val requiresHumanApproval: Boolean = false,
    val idempotent: Boolean = true
)

data class ToolCallRequest(
    val runId: String,
    val callId: String,
    val toolName: String,
    val port: Port,
    val argumentsSummary: String,
    val idempotencyKey: String,
    val riskLevel: RiskLevel = RiskLevel.NONE
)

enum class ToolAuthorizationStatus { ALLOWED, DENIED, SAFETY_STOP, REQUIRES_APPROVAL, DUPLICATE }

data class ToolAuthorization(
    val status: ToolAuthorizationStatus,
    val reason: String,
    val definition: ToolDefinition? = null
)

/** 工具白名单与调用前授权，任何工具都必须先经过此注册表。 */
class ToolRegistry(definitions: Collection<ToolDefinition> = defaultDefinitions()) {
    private val definitionsByName = definitions.associateBy { it.name }

    fun find(name: String): ToolDefinition? = definitionsByName[name]

    fun authorize(request: ToolCallRequest, usedIdempotencyKeys: Set<String> = emptySet()): ToolAuthorization {
        if (request.riskLevel == RiskLevel.SAFETY_STOP) {
            return ToolAuthorization(ToolAuthorizationStatus.SAFETY_STOP, "当前运行已进入安全停止，禁止调用工具。")
        }
        val definition = find(request.toolName)
            ?: return ToolAuthorization(ToolAuthorizationStatus.DENIED, "工具不在注册表白名单中。")
        if (request.port !in definition.allowedPorts) {
            return ToolAuthorization(ToolAuthorizationStatus.DENIED, "当前端口无权调用该工具。", definition)
        }
        if (definition.idempotent && request.idempotencyKey in usedIdempotencyKeys) {
            return ToolAuthorization(ToolAuthorizationStatus.DUPLICATE, "幂等键已执行过，拒绝重复副作用。", definition)
        }
        if (definition.requiresHumanApproval) {
            return ToolAuthorization(ToolAuthorizationStatus.REQUIRES_APPROVAL, "该工具需要专业人员确认后执行。", definition)
        }
        return ToolAuthorization(ToolAuthorizationStatus.ALLOWED, "工具授权通过。", definition)
    }

    companion object {
        fun defaultDefinitions(): List<ToolDefinition> = listOf(
            ToolDefinition("risk_assess", "执行确定性风险评估", setOf(Port.CHILD, Port.PARENT, Port.PROFESSIONAL)),
            ToolDefinition("knowledge_retrieve", "检索已审核知识条目", setOf(Port.CHILD, Port.PARENT, Port.PROFESSIONAL)),
            ToolDefinition("training_start", "启动白名单训练任务", setOf(Port.CHILD, Port.PARENT, Port.PROFESSIONAL)),
            ToolDefinition("training_record", "写入训练过程记录", setOf(Port.CHILD, Port.PARENT, Port.PROFESSIONAL)),
            ToolDefinition("analysis_read", "读取过程分析结果", setOf(Port.PARENT, Port.PROFESSIONAL), ToolRisk.SENSITIVE),
            ToolDefinition("plan_draft", "生成待审核训练方案草案", setOf(Port.PROFESSIONAL), ToolRisk.SENSITIVE),
            ToolDefinition("plan_activate", "激活已确认训练方案", setOf(Port.PROFESSIONAL), ToolRisk.HIGH, requiresHumanApproval = true),
            ToolDefinition("review_submit", "提交专业审核决定", setOf(Port.PROFESSIONAL), ToolRisk.SENSITIVE, requiresHumanApproval = true)
        )
    }
}
