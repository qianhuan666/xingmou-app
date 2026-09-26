package com.xingmou.core.agent

import com.xingmou.data.db.AgentRunEntity
import com.xingmou.data.db.ToolCallEntity
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
}
