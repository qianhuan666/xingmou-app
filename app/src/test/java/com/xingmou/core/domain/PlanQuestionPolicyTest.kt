package com.xingmou.core.domain

import com.xingmou.core.model.SupportLevel
import com.xingmou.data.catalog.QuestionDefinition
import com.xingmou.data.catalog.QuestionType
import com.xingmou.data.catalog.QuestionCatalog
import com.xingmou.data.db.PlanVersionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanQuestionPolicyTest {
    @Test
    fun activePlanTaskMapsToModuleAndReadsDifficultyAndSupport() {
        val policy = PlanQuestionPolicy.from(
            PlanVersionEntity(
                planId = "plan-1", childId = "child-1", version = 3, status = "active",
                reviewRequired = false,
                payloadJson = """{"task":"分类整理","difficulty":3,"support_level":"L2","observable_goal":"按类别归类"}""",
                createdAt = 1L, updatedAt = 2L
            )
        )

        assertEquals("E02", policy?.moduleId)
        assertEquals(3, policy?.difficulty)
        assertEquals(SupportLevel.L2, policy?.supportLevel)
        assertEquals("按类别归类", policy?.observableGoal)
    }

    @Test
    fun inactivePlanDoesNotAffectChildQuestions() {
        val policy = PlanQuestionPolicy.from(
            PlanVersionEntity("plan-1", "child-1", 1, "draft", true, "{\"task\":\"分类整理\"}", 1L, 1L)
        )
        assertNull(policy)
    }

    @Test
    fun difficultyOneKeepsCorrectOptionAndReducesChoices() {
        val question = QuestionDefinition(
            id = "q", version = 1, moduleId = "P01", domain = "A", type = QuestionType.CHOICE,
            prompt = "点红色", options = listOf("🔵", "🟢", "🔴", "🟡"), correctOption = 2
        )
        val adapted = PlanQuestionPolicy.run { question.forDifficulty(1) }

        assertEquals(2, adapted.options.size)
        assertEquals("🔴", adapted.options[adapted.correctOption!!])
        assertEquals(0, adapted.correctOption)
    }

    @Test
    fun observedQuestionKeepsCompletionChoicesAtAnyDifficulty() {
        val question = QuestionDefinition(
            id = "q", version = 1, moduleId = "L01", domain = "D", type = QuestionType.OBSERVED,
            prompt = "说出名字", options = listOf("自己完成", "帮助后完成", "还没完成"), correctOption = null
        )
        val adapted = PlanQuestionPolicy.run { question.forDifficulty(5) }

        assertEquals(question.options, adapted.options)
        assertNull(adapted.correctOption)
        assertTrue(adapted.difficulty == 5)
    }

    @Test
    fun activePlanScopesFormalTrainingToItsModule() {
        val plan = PlanVersionEntity(
            "plan-2", "child-1", 2, "active", false,
            """{"module_id":"E03","difficulty":2,"support_level":"L1"}""",
            1L, 1L
        )
        val policy = PlanQuestionPolicy.from(plan)
        val questions = PlanQuestionPolicy.selectQuestions(QuestionCatalog.fullCourseQuestions, policy, 2)

        assertTrue(questions.isNotEmpty())
        assertTrue(questions.all { it.moduleId == "E03" })
        assertTrue(questions.all { it.options.size <= 3 })
    }
}

