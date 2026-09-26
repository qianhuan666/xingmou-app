package com.xingmou.core.agent

import com.xingmou.core.domain.KnowledgeRetrievalResult
import com.xingmou.core.domain.KnowledgeRetriever
import com.xingmou.core.model.Port
import com.xingmou.core.model.RiskLevel
import com.xingmou.core.model.SessionContext
import com.xingmou.core.llm.PromptBuilder
import com.xingmou.core.rule.RiskEngine
import com.xingmou.data.db.KnowledgeItemEntity
import com.xingmou.data.db.TrainingRecordEntity

data class AgentContextInput(
    val port: Port,
    val session: SessionContext,
    val userText: String,
    val riskFlags: List<String> = session.riskFlags,
    val knowledgeItems: List<KnowledgeItemEntity> = emptyList(),
    val recentRecords: List<TrainingRecordEntity> = emptyList(),
    val consecutiveFailures: Int = 0
)

data class AssembledContext(
    val risk: RiskEngine.RiskAssessment,
    val knowledge: KnowledgeRetrievalResult,
    val systemPrompt: String,
    val userMessage: String,
    val recordSummary: String,
    val approvedSourceIds: List<String>,
    val dataSufficient: Boolean
)

/** 按端口和风险裁剪最小必要上下文，禁止把原始敏感数据直接交给模型。 */
class ContextAssembler(
    private val knowledgeRetriever: KnowledgeRetriever = KnowledgeRetriever()
) {
    fun assemble(input: AgentContextInput): AssembledContext {
        val risk = RiskEngine.assessDetailed(input.userText, input.riskFlags, input.consecutiveFailures)
        val knowledge = knowledgeRetriever.retrieve(input.userText, input.port, input.riskFlags, input.knowledgeItems)
        val scopedRecords = input.recentRecords.takeLast(30)
        val recordSummary = scopedRecords.joinToString("；") { record ->
            "ID=record:${record.recordId};${record.domain}/${record.taskId}:correct=${record.correct},first=${record.firstCorrect},reaction=${record.reactionMs ?: "na"},prompt=${record.promptLevel}"
        }.ifBlank { "暂无训练记录" }
        val session = input.session.copy(
            recentSummary = "记录数=${input.recentRecords.size}；$recordSummary",
            riskFlags = risk.matchedTypes
        )
        val knowledgeContext = knowledge.matchedItems.joinToString("\n") { item ->
            "ID=${item.itemId}；标题=${PromptBuilder.sanitize(item.title).take(120)}；内容=${PromptBuilder.sanitize(item.content).take(500)}；来源=${PromptBuilder.sanitize(item.sourceRef.orEmpty()).take(120)}"
        }.ifBlank { "无已审核匹配条目；不得凭模型记忆补写来源。" }
        val userMessage = PromptBuilder.buildUserMessage(input.port, session, input.userText) +
            "\n本次已审核知识（可引用条目 ID；训练记录 ID 见近期摘要）：\n" + knowledgeContext
        return AssembledContext(
            risk = risk,
            knowledge = knowledge,
            systemPrompt = PromptBuilder.buildSystemPrompt(input.port),
            userMessage = userMessage,
            recordSummary = recordSummary,
            approvedSourceIds = knowledge.matchedItems.map { it.itemId } + scopedRecords.map { "record:${it.recordId}" },
            dataSufficient = input.recentRecords.size >= 3
        )
    }
}
