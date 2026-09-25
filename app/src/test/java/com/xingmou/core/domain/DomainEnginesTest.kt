package com.xingmou.core.domain

import com.xingmou.core.model.Port
import com.xingmou.core.model.SupportLevel
import com.xingmou.data.db.KnowledgeItemEntity
import com.xingmou.data.db.SeedData
import com.xingmou.data.db.TrainingRecordEntity
import org.junit.Assert.*
import org.junit.Test

class DomainEnginesTest {
    @Test fun retrieverLimitsResultsAndExcludesUnverified() {
        val extra = KnowledgeItemEntity("x", "support", "A", "提示", "等待辅助", null, "unverified", "all_ports", "[\"提示\"]", true, 0)
        val result = KnowledgeRetriever().retrieve("提示 辅助 等待", Port.PARENT, items = SeedData.knowledgeItems + extra)
        assertEquals(KnowledgeRoute.NORMAL, result.route)
        assertTrue(result.matchedItems.size <= 3)
        assertTrue(result.matchedItems.groupingBy { it.domain }.eachCount().values.all { it <= 2 })
        assertTrue("x" in result.excludedIds)
    }

    @Test fun retrieverRoutesRiskAndNegation() {
        val engine = KnowledgeRetriever()
        assertEquals(KnowledgeRoute.SAFETY_STOP, engine.retrieve("孩子呼吸困难", Port.PARENT, items = SeedData.knowledgeItems).route)
        assertEquals(KnowledgeRoute.NOT_FOUND, engine.retrieve("不是家庭练习", Port.PARENT, items = SeedData.knowledgeItems).route)
    }

    @Test fun trainingWhitelistAndDifficultyAreBounded() {
        val engine = TrainingEngine()
        val task = TrainingTask("图片配对", "认知", TrainingGame.MEMORY_MATCH, 5, SupportLevel.L1, "卡片")
        assertTrue(engine.isTaskAllowed(task))
        val success = List(3) { TrainingResult(task.taskId, true, true, 500, null, 0) }
        assertEquals(5, engine.nextDifficulty(5, success, false))
        assertEquals(4, engine.nextDifficulty(3, success, false))
        assertEquals(3, engine.nextDifficulty(3, success, true))
        assertEquals(1, kotlin.math.abs(engine.nextDifficulty(3, listOf(TrainingResult("x", false, false, null, "timeout", 2), TrainingResult("x", false, false, null, "wrong", 2)), false) - 3))
    }

    @Test fun planCannotSkipHumanReview() {
        val machine = PlanStateMachine()
        assertTrue(machine.createDraft(PlanActor.MODEL).accepted)
        assertFalse(machine.transition(PlanStatus.DRAFT, PlanStatus.ACTIVE, PlanActor.MODEL).accepted)
        assertFalse(machine.transition(PlanStatus.DRAFT, PlanStatus.ACTIVE, PlanActor.PROFESSIONAL).accepted)
        assertTrue(machine.transition(PlanStatus.DRAFT, PlanStatus.CONFIRMED, PlanActor.PROFESSIONAL, "审核通过").accepted)
        assertTrue(machine.transition(PlanStatus.CONFIRMED, PlanStatus.ACTIVE, PlanActor.PROFESSIONAL, "签署激活").accepted)
        assertFalse(machine.transition(PlanStatus.ACTIVE, PlanStatus.SUPERSEDED, PlanActor.PROFESSIONAL).accepted)
    }

    @Test fun analysisNeedsThreeRecordsAndFlagsDecline() {
        val engine = AnalysisEngine()
        assertEquals(Trend.INSUFFICIENT_DATA, engine.analyze(listOf(record(1, true, 500, "L0"), record(2, true, 500, "L0"))).trend)
        val records = listOf(
            record(1, true, 500, "L0"), record(2, true, 600, "L0"),
            record(3, false, 1100, "L2"), record(4, false, 1300, "L2"), record(5, false, 1400, "L2")
        )
        val analysis = engine.analyze(records)
        assertEquals(Trend.DECLINING, analysis.trend)
        assertTrue(analysis.reviewRequired)
        assertTrue(analysis.warningSignals.any { it.contains("人工复核") })
    }

    @Test fun analysisProvidesReportMetricsForProfessionalView() {
        val analysis = AnalysisEngine().analyze(
            listOf(
                record(1, true, 500, "L0"),
                record(2, true, 700, "L1"),
                record(3, false, 900, "L2")
            )
        )
        assertEquals(3, analysis.sampleCount)
        assertEquals(2.0 / 3.0, analysis.accuracy!!, 0.0001)
        assertEquals(1.0 / 3.0, analysis.independentCompletionRate!!, 0.0001)
        assertEquals(700.0, analysis.averageReactionMs!!, 0.0001)
        assertEquals(4.0 / 3.0, analysis.averagePromptLevel!!, 0.0001)
    }

    private fun record(index: Int, correct: Boolean, reaction: Long, support: String) = TrainingRecordEntity(
        "r$index", "child", "认知", "图片配对", 2, support, reaction, if (correct) null else "wrong", correct, correct, if (support == "L0") 0 else 2, index.toLong()
    )
}
