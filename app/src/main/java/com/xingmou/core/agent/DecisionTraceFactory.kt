package com.xingmou.core.agent

import com.google.gson.Gson
import com.xingmou.data.db.DecisionTraceEntity

data class DecisionEvidence(
    val ruleRefs: List<String> = emptyList(),
    val knowledgeRefs: List<String> = emptyList(),
    val toolRefs: List<String> = emptyList(),
    val humanDecision: String? = null
)

object DecisionTraceFactory {
    private val gson = Gson()

    fun create(
        traceId: String,
        runId: String,
        stepIndex: Int,
        promptVersion: String,
        modelVersion: String?,
        evidence: DecisionEvidence,
        createdAt: Long
    ): DecisionTraceEntity = DecisionTraceEntity(
        traceId = traceId, runId = runId, stepIndex = stepIndex,
        promptVersion = promptVersion, modelVersion = modelVersion,
        ruleRefsJson = gson.toJson(evidence.ruleRefs),
        knowledgeRefsJson = gson.toJson(evidence.knowledgeRefs),
        toolRefsJson = gson.toJson(evidence.toolRefs),
        humanDecision = evidence.humanDecision,
        createdAt = createdAt
    )
}
