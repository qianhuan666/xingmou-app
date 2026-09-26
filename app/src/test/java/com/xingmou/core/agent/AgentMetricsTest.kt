package com.xingmou.core.agent

import com.xingmou.data.db.AgentRunEntity
import com.xingmou.data.db.ToolCallEntity
import com.xingmou.data.db.DecisionTraceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentMetricsTest {
    @Test fun emptySampleIsUnknownNotZero() {
        val metrics = AgentMetricsCalculator.calculate(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertNull(metrics.taskCompletionRate)
        assertNull(metrics.unsupportedJudgmentRate)
        assertNull(metrics.averageResponseMs)
    }

    @Test fun countsOnlySelectedRuns() {
        val runs = listOf(
            AgentRunEntity("run-a", "parent", "PARENT", "child-a", "COMPLETED", "COMPLETED", 100, 200, null),
            AgentRunEntity("run-b", "parent", "PARENT", "child-a", "FAILED", "FAILED", 200, 400, null)
        )
        val calls = listOf(
            ToolCallEntity("call-a", "run-a", "knowledge_retrieve", "omitted", "ALLOWED", "succeeded", "key-a", null, 150, 160),
            ToolCallEntity("call-other", "other", "knowledge_retrieve", "omitted", "DENIED", "blocked", "key-b", null, 150, 160)
        )
        val metrics = AgentMetricsCalculator.calculate(runs, emptyList(), calls, emptyList(), emptyList())
        assertEquals(0.5, metrics.taskCompletionRate!!, 0.0)
        assertEquals(1.0, metrics.toolSuccessRate!!, 0.0)
        assertEquals(150.0, metrics.averageResponseMs!!, 0.0)
    }

    @Test fun unsupportedRateUsesOnlyExplicitSupportedOrUnsupportedLabels() {
        val runs = listOf(AgentRunEntity("run-a", "parent", "PARENT", "child-a", "COMPLETED", "COMPLETED", 100, 200, null))
        val traces = listOf(
            DecisionTraceEntity("trace-a", "run-a", 0, "v1", "local", "[]", "[]", "[]", "unsupported", 1),
            DecisionTraceEntity("trace-b", "run-a", 1, "v1", "local", "[]", "[]", "[]", "supported", 2),
            DecisionTraceEntity("trace-c", "run-a", 2, "v1", "local", "[]", "[]", "[]", "uncertain", 3),
            DecisionTraceEntity("trace-other", "other", 0, "v1", "local", "[]", "[]", "[]", "unsupported", 4)
        )
        val metrics = AgentMetricsCalculator.calculate(runs, emptyList(), emptyList(), emptyList(), emptyList(), traces)
        assertEquals(0.5, metrics.unsupportedJudgmentRate!!, 0.0)
        assertEquals(2, metrics.evidenceAnnotationSampleCount)
    }
}
