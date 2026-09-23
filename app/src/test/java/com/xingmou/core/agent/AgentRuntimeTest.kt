package com.xingmou.core.agent

import com.xingmou.core.model.Port
import com.xingmou.core.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class AgentRuntimeTest {
    @Test fun registryBlocksUnknownPortAndSafetyCalls() {
        val registry = ToolRegistry()
        val unknown = registry.authorize(request("unknown", Port.PARENT))
        assertEquals(ToolAuthorizationStatus.DENIED, unknown.status)
        val childAnalysis = registry.authorize(request("analysis_read", Port.CHILD))
        assertEquals(ToolAuthorizationStatus.DENIED, childAnalysis.status)
        val safety = registry.authorize(request("knowledge_retrieve", Port.PARENT, RiskLevel.SAFETY_STOP))
        assertEquals(ToolAuthorizationStatus.SAFETY_STOP, safety.status)
    }

    @Test fun highRiskToolRequiresApproval() {
        val result = ToolRegistry().authorize(request("plan_activate", Port.PROFESSIONAL))
        assertEquals(ToolAuthorizationStatus.REQUIRES_APPROVAL, result.status)
    }

    @Test fun stateMachineRejectsSkippingAndRuntimeAuditsIdempotentCall() {
        val calls = mutableListOf<String>()
        val runtime = AgentRuntime(handlers = mapOf("knowledge_retrieve" to { calls += it.idempotencyKey; "知识结果" }))
        runtime.create("run-1", "parent_answer", Port.PARENT, "child-1")
        assertFalse(runtime.transition("run-1", AgentRunState.EXECUTING_TOOL).accepted)
        assertTrue(runtime.transition("run-1", AgentRunState.ROUTING, 1).accepted)
        assertTrue(runtime.transition("run-1", AgentRunState.PLANNING, 2).accepted)
        assertTrue(runtime.transition("run-1", AgentRunState.EXECUTING_TOOL, 3).accepted)
        val first = runtime.executeTool(request("knowledge_retrieve", Port.PARENT).copy(callId = "call-1"), 4)
        assertEquals("succeeded", first.executionStatus)
        assertTrue(runtime.get("run-1").state == AgentRunState.VALIDATING)
        assertTrue(runtime.transition("run-1", AgentRunState.EXECUTING_TOOL, 5).accepted)
        val second = runtime.executeTool(request("knowledge_retrieve", Port.PARENT).copy(callId = "call-2"), 6)
        assertEquals("duplicate", second.executionStatus)
        assertEquals("知识结果", second.resultSummary)
        assertEquals(1, calls.size)
        assertEquals(2, runtime.get("run-1").toolCalls.size)
        assertEquals("PARENT", runtime.toRunEntity("run-1", 0).port)
    }

    @Test fun safetyRequestStopsRuntimeAndRecordsBlockedCall() {
        val runtime = AgentRuntime()
        runtime.create("run-2", "safety", Port.PARENT)
        runtime.transition("run-2", AgentRunState.ROUTING)
        runtime.transition("run-2", AgentRunState.PLANNING)
        runtime.transition("run-2", AgentRunState.EXECUTING_TOOL)
        val result = runtime.executeTool(request("risk_assess", Port.PARENT, RiskLevel.SAFETY_STOP, "run-2"), 10)
        assertEquals(ToolAuthorizationStatus.SAFETY_STOP, result.authorization.status)
        assertEquals(AgentRunState.SAFETY_STOP, runtime.get("run-2").state)
        assertEquals("blocked_safety", runtime.get("run-2").toolCalls.single().executionStatus)
    }

    private fun request(toolName: String, port: Port, risk: RiskLevel = RiskLevel.NONE, runId: String = "run-1") = ToolCallRequest(
        runId = runId, callId = "call", toolName = toolName, port = port,
        argumentsSummary = "{}", idempotencyKey = "same-key", riskLevel = risk
    )
}
