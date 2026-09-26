package com.xingmou.core.llm

import com.xingmou.core.consent.ConsentStatus

enum class GatewayBlockReason {
    NONE,
    CONSENT_REQUIRED,
    IDENTITY_REQUIRED,
    PAYLOAD_TOO_LARGE,
    COST_LIMIT
}

data class GatewayPolicyConfig(
    val maxPromptChars: Int = 12_000,
    val maxEstimatedCostCents: Int = 50,
    val timeoutMs: Long = 60_000
)

data class GatewayCallRequest(
    val runId: String,
    val childId: String?,
    val port: String,
    val systemPrompt: String,
    val userText: String,
    val consentStatus: ConsentStatus,
    val estimatedCostCents: Int = 1
)

data class PreparedGatewayRequest(
    val runId: String,
    val childId: String,
    val port: String,
    val systemPrompt: String,
    val userText: String,
    val timeoutMs: Long,
    val redactionCount: Int,
    val auditSummary: String
)

data class GatewayPolicyDecision(
    val allowed: Boolean,
    val reason: GatewayBlockReason,
    val prepared: PreparedGatewayRequest? = null
)

/** Android 端到 AI Gateway 的前置策略。 */
class GatewayPolicy(
    private val config: GatewayPolicyConfig = GatewayPolicyConfig()
) {
    private val phonePattern = Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)")
    private val idCardPattern = Regex("(?<!\\d)\\d{17}[\\dXx](?!\\d)")
    private val addressPattern = Regex("(手机号|电话|身份证号|住址|地址|病历原文|诊断原文)\\s*[:：=]?\\s*[^，。；;\\n]+")

    fun evaluate(request: GatewayCallRequest): GatewayPolicyDecision {
        if (request.consentStatus != ConsentStatus.GRANTED) {
            return GatewayPolicyDecision(false, GatewayBlockReason.CONSENT_REQUIRED)
        }
        if (request.runId.isBlank() || request.childId.isNullOrBlank()) {
            return GatewayPolicyDecision(false, GatewayBlockReason.IDENTITY_REQUIRED)
        }
        if (request.estimatedCostCents < 0 || request.estimatedCostCents > config.maxEstimatedCostCents) {
            return GatewayPolicyDecision(false, GatewayBlockReason.COST_LIMIT)
        }
        if (request.systemPrompt.length + request.userText.length > config.maxPromptChars) {
            return GatewayPolicyDecision(false, GatewayBlockReason.PAYLOAD_TOO_LARGE)
        }

        val redactedSystem = redact(request.systemPrompt)
        val redactedUser = redact(request.userText)
        val redactionCount = countRedactions(request.systemPrompt) + countRedactions(request.userText)
        return GatewayPolicyDecision(
            allowed = true,
            reason = GatewayBlockReason.NONE,
            prepared = PreparedGatewayRequest(
                runId = request.runId,
                childId = request.childId,
                port = request.port,
                systemPrompt = redactedSystem,
                userText = redactedUser,
                timeoutMs = config.timeoutMs,
                redactionCount = redactionCount,
                auditSummary = "runId=" + request.runId + ";childScope=authorized;port=" + request.port + ";redactions=" + redactionCount
            )
        )
    }

    private fun redact(text: String): String =
        text
            .replace(phonePattern, "[已脱敏电话]")
            .replace(idCardPattern, "[已脱敏证件号]")
            .replace(addressPattern) { match ->
                val label = match.value.substringBefore(":").substringBefore("：").substringBefore("=")
                label + "：[已脱敏敏感字段]"
            }

    private fun countRedactions(text: String): Int {
        val labelledRanges = addressPattern.findAll(text).map { it.range }.toList()
        val labelledCount = labelledRanges.size
        val phoneCount = phonePattern.findAll(text).count { match ->
            labelledRanges.none { match.range.first in it }
        }
        val idCardCount = idCardPattern.findAll(text).count { match ->
            labelledRanges.none { match.range.first in it }
        }
        return labelledCount + phoneCount + idCardCount
    }
}
