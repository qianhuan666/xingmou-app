package com.xingmou.core.agent

import com.xingmou.core.model.CommunicationLevel
import com.xingmou.core.model.Port
import com.xingmou.core.model.SessionContext
import com.xingmou.core.model.SupportLevel
import com.xingmou.data.db.SeedData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentOrchestratorTest {
    @Test fun childParentAndProfessionalFlowsPassWithMockModel() = runBlocking {
        val child = AgentOrchestrator(ModelGateway { _, _ -> Result.success(childJson()) })
            .run(request("child-run", Port.CHILD, "我想继续"))
        assertEquals(OrchestrationRoute.COMPLETED, child.route)
        assertEquals(AgentRunState.COMPLETED, child.state)

        var parentStep = 0
        val registry = ToolRegistry()
        val runtime = AgentRuntime(registry)
        val parent = AgentOrchestrator(
            model = ModelGateway { _, _ ->
                parentStep += 1
                Result.success(when (parentStep) {
                    1 -> parentJsonWithAction("knowledge_retrieve", "parent-k1")
                    2 -> parentJsonWithAction("analysis_read", "parent-k2")
                    else -> parentFinalJson()
                })
            },
            registry = registry,
            runtime = runtime
        )
            .run(request("parent-run", Port.PARENT, "怎么使用提示辅助"))
        assertEquals(OrchestrationRoute.COMPLETED, parent.route)
        assertEquals("succeeded", parent.toolResult?.executionStatus)
        assertEquals(2, runtime.get("parent-run").toolCalls.size)

        val professional = AgentOrchestrator(ModelGateway { _, _ -> Result.success(professionalJsonWithAction()) })
            .run(request("professional-run", Port.PROFESSIONAL, "激活方案"))
        assertEquals(OrchestrationRoute.WAITING_APPROVAL, professional.route)
        assertEquals(AgentRunState.WAITING_APPROVAL, professional.state)
    }

    @Test fun riskAndInvalidModelOutputUseDeterministicPaths() = runBlocking {
        val neverCalled = mutableListOf<Boolean>()
        val safety = AgentOrchestrator(ModelGateway { _, _ -> neverCalled += true; Result.success(childJson()) })
            .run(request("risk-run", Port.CHILD, "我呼吸困难"))
        assertEquals(OrchestrationRoute.SAFETY_STOP, safety.route)
        assertTrue(neverCalled.isEmpty())
        assertTrue(safety.fallbackText!!.contains("停下来"))

        val invalid = AgentOrchestrator(ModelGateway { _, _ -> Result.success("{\"state\":\"continue\",\"speech\":[\"这是一个超过十五个汉字的儿童端错误句子\"]}") })
            .run(request("invalid-run", Port.CHILD, "继续"))
        assertEquals(OrchestrationRoute.FALLBACK, invalid.route)
        assertTrue(invalid.fallbackText!!.contains("休息"))
    }

    @Test fun contextIsSanitizedAndMemoryRequiresSafeEvidence() {
        val input = request("context-run", Port.PARENT, "手机号13800138000怎么记录").input
        val context = ContextAssembler().assemble(input)
        assertFalse(context.userMessage.contains("13800138000"))
        assertTrue(context.userMessage.contains("[手机号已脱敏]"))

        val memory = MemoryManager()
        assertFalse(memory.save(MemoryDraft("m1", "c1", "session", "电话13800138000", null, "reported", null)))
        assertFalse(memory.save(MemoryDraft("m2", "c1", "session", "模型猜测能力提升", null, "possible", null)))
        assertTrue(memory.save(MemoryDraft("m3", "c1", "session", "连续完成三次图片配对", "record:r1", "observed", null)))
        assertEquals("active", memory.toEntity("m3", 1).status)
        assertTrue(memory.revoke("m3"))
        assertEquals("revoked", memory.toEntity("m3", 2).status)
    }

    @Test fun decisionTraceKeepsRuleKnowledgeAndToolEvidence() {
        val trace = DecisionTraceFactory.create(
            "trace-1", "run-1", 2, "prompt-v1", "mock-model",
            DecisionEvidence(listOf("RiskEngine.NONE"), listOf("KB-SUPPORT-001"), listOf("knowledge_retrieve"), "approved"), 10
        )
        assertTrue(trace.ruleRefsJson.contains("RiskEngine.NONE"))
        assertTrue(trace.knowledgeRefsJson.contains("KB-SUPPORT-001"))
        assertEquals("approved", trace.humanDecision)
    }

    @Test fun repeatedActionsStopAtConfiguredStepLimit() = runBlocking {
        val orchestrator = AgentOrchestrator(ModelGateway { _, _ ->
            Result.success(parentJsonWithAction("knowledge_retrieve", "loop-key"))
        })
        val result = orchestrator.run(request("loop-run", Port.PARENT, "继续检索").copy(maxSteps = 2))
        assertEquals(OrchestrationRoute.FALLBACK, result.route)
        assertEquals(AgentRunState.FAILED, result.state)
        assertTrue(result.error!!.contains("最大 Agent 步数"))
    }

    private fun request(runId: String, port: Port, text: String) = AgentOrchestrationRequest(
        runId = runId,
        taskType = "test",
        childId = "child-1",
        input = AgentContextInput(
            port = port,
            session = SessionContext("小星星", "6-8岁", CommunicationLevel.SHORT_SENTENCE, SupportLevel.L1),
            userText = text,
            knowledgeItems = SeedData.knowledgeItems
        )
    )

    private fun childJson() = """{
        "state":"continue","speech":["我们继续。"],"next_action":"等待选择","support_level":"L1",
        "ui":{"options":["继续","休息"],"show_break_button":true,"use_voice":true}
    }""".trimIndent()

    private fun parentJsonWithAction(toolName: String, key: String) = """{
        "mode":"answer","acknowledgement":"我理解你的关注。","observation":"这是一次训练过程观察。",
        "claims":[],"sources":[],"home_support":[],"disclaimer":"以上是训练过程支持信息，不构成医学诊断。",
        "action":{"tool_name":"$toolName","arguments":{"query":"提示辅助"},"idempotency_key":"$key","requires_review":false}
    }""".trimIndent()

    private fun parentFinalJson() = """{
        "mode":"answer","acknowledgement":"我理解你的关注。","observation":"已结合训练过程与审核知识。",
        "claims":[],"sources":[],"home_support":[],"disclaimer":"以上是训练过程支持信息，不构成医学诊断。"
    }""".trimIndent()

    private fun professionalJsonWithAction() = """{
        "status":"draft","claims":[],"sources":[],"facts":[],"inferences":[],"review_required":true,"review_items":["人工签署"],
        "action":{"tool_name":"plan_activate","arguments":{"plan_id":"p1"},"idempotency_key":"professional-k1","requires_review":false}
    }""".trimIndent()
}
