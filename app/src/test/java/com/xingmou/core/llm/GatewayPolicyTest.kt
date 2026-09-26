package com.xingmou.core.llm

import com.xingmou.core.consent.ConsentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayPolicyTest {
    private val policy = GatewayPolicy()

    private fun request(
        consentStatus: ConsentStatus = ConsentStatus.GRANTED,
        text: String = "请根据训练记录给出下一步支持。"
    ) = GatewayCallRequest(
        runId = "run-1",
        childId = "child-1",
        port = "parent",
        systemPrompt = "只输出 JSON。",
        userText = text,
        consentStatus = consentStatus
    )

    @Test
    fun remoteAiRequiresExplicitConsent() {
        val decision = policy.evaluate(request(consentStatus = ConsentStatus.REVOKED))

        assertFalse(decision.allowed)
        assertEquals(GatewayBlockReason.CONSENT_REQUIRED, decision.reason)
    }

    @Test
    fun redactsIdentityAndMedicalTextBeforeGateway() {
        val decision = policy.evaluate(
            request(
                text = "手机号：13812345678，身份证号：110101199001011234，诊断原文：内部记录"
            )
        )

        assertTrue(decision.allowed)
        val prepared = decision.prepared!!
        assertFalse(prepared.userText.contains("13812345678"))
        assertFalse(prepared.userText.contains("110101199001011234"))
        assertFalse(prepared.userText.contains("内部记录"))
        assertEquals(3, prepared.redactionCount)
        assertTrue(prepared.auditSummary.contains("childScope=authorized"))
    }

    @Test
    fun missingIdentityAndCostLimitAreBlocked() {
        val noChild = policy.evaluate(request().copy(childId = null))
        assertEquals(GatewayBlockReason.IDENTITY_REQUIRED, noChild.reason)

        val tooExpensive = policy.evaluate(request().copy(estimatedCostCents = 51))
        assertEquals(GatewayBlockReason.COST_LIMIT, tooExpensive.reason)
    }

    @Test
    fun oversizedPayloadIsBlocked() {
        val smallPolicy = GatewayPolicy(GatewayPolicyConfig(maxPromptChars = 10))
        val decision = smallPolicy.evaluate(request(text = "这是一段超过限制的文本"))

        assertFalse(decision.allowed)
        assertEquals(GatewayBlockReason.PAYLOAD_TOO_LARGE, decision.reason)
    }
}
