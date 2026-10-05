package com.xingmou.core.domain

import com.xingmou.data.catalog.QuestionCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleQuestionSelectorTest {
    @Test
    fun selectionIsStableForSameSessionAndRequest() {
        val request = ModuleQuestionSelectionRequest("M02", 3, "session-a", limit = 3)
        val first = ModuleQuestionSelector.select(QuestionCatalog.fullCourseQuestions, request).map { it.id }
        val second = ModuleQuestionSelector.select(QuestionCatalog.fullCourseQuestions, request).map { it.id }
        assertEquals(first, second)
    }

    @Test
    fun completedAndSeenQuestionsAreExcluded() {
        val bank = QuestionCatalog.fullCourseQuestions.filter { it.moduleId == "P01" }
        val excluded = bank.take(2).map { it.id }.toSet()
        val result = ModuleQuestionSelector.select(
            bank,
            ModuleQuestionSelectionRequest("P01", 3, "session-b", excluded, excluded, limit = 10)
        )
        assertTrue(result.none { it.id in excluded })
        assertEquals(result.size, result.map { it.id }.toSet().size)
    }

    @Test
    fun selectedQuestionKeepsTargetDifficultyAndValidAnswer() {
        val result = ModuleQuestionSelector.select(
            QuestionCatalog.fullCourseQuestions,
            ModuleQuestionSelectionRequest("P02", 1, "session-c", limit = 1)
        ).single()
        assertEquals(1, result.difficulty)
        assertTrue(result.correctOption == null || result.correctOption in result.options.indices)
        assertTrue(result.options.size <= 2 || result.type.name == "OBSERVED")
    }

    @Test
    fun missingExactDifficultyFallsBackToNearestAvailableQuestion() {
        val bank = QuestionCatalog.fullCourseQuestions.filter { it.moduleId == "P01" && it.difficulty <= 2 }
        val result = ModuleQuestionSelector.select(
            bank,
            ModuleQuestionSelectionRequest("P01", 5, "session-d", limit = 1)
        ).single()
        assertEquals(5, result.difficulty)
    }
}

