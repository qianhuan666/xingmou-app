package com.xingmou.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    @Test fun domainCatalogContainsSixDomains() {
        assertEquals(listOf("A", "B", "C", "D", "E", "F"), DomainCatalog.all.map { it.id })
    }

    @Test fun taskCatalogContainsTwentyTwoUniqueModules() {
        assertEquals(22, TaskCatalog.all.size)
        assertEquals(22, TaskCatalog.all.map { it.id }.toSet().size)
        assertTrue(TaskCatalog.all.all { DomainCatalog.find(it.domain) != null })
    }

    @Test fun professionalCatalogsAreAvailableForSeedMigration() {
        assertEquals(15, AssessmentCatalog.all.size)
        assertEquals(12, RehabilitationMethods.all.size)
        assertTrue(RehabilitationMethods.all.any { it.id == "FAMILY" })
    }

    @Test fun webQuestionBankContainsNinetyFiveQuestionsAcrossTwentyTwoModules() {
        assertEquals(26, QuestionCatalog.baselineQuestions.size)
        assertEquals(69, QuestionCatalog.moduleQuestionBank.size)
        assertEquals(69, QuestionCatalog.fullCourseQuestions.size)
        assertEquals(22, QuestionCatalog.moduleQuestionBank.map { it.moduleId }.toSet().size)
        assertEquals(95, QuestionCatalog.baselineQuestions.size + QuestionCatalog.moduleQuestionBank.size)
        assertTrue(QuestionCatalog.moduleQuestionBank.all { it.version == 1 && it.sourceRef == "WEB_BANK_V1" })
        assertTrue(QuestionCatalog.baselineQuestions.all { it.version == 1 && it.sourceRef == "WEB_BANK_V1" })
    }

    @Test fun webQuestionBankKeepsQuestionsGroupedPerUnit() {
        assertEquals(
            mapOf("A" to 4, "B" to 4, "C" to 4, "D" to 4, "E" to 4, "F" to 6),
            QuestionCatalog.baselineQuestions.groupingBy { it.domain }.eachCount()
        )
        val moduleCounts = QuestionCatalog.moduleQuestionBank.groupingBy { it.moduleId }.eachCount()
        // D03「跟着做」模块扩充了手势题（D03-04/05/06），其余模块仍保持每模块 3 题
        assertEquals(6, moduleCounts["D03"])
        assertTrue(moduleCounts.filterKeys { it != "D03" }.values.all { it == 3 })
    }

    @Test fun firstBaselineQuestionIncludesItsWebTargetCue() {
        assertEquals("🐶", QuestionCatalog.baselineQuestions.first { it.id == "BL-A-01" }.stimulus)
    }

    @Test fun courseQuestionsHaveCoherentStimulusAndTwoToFourOptions() {
        assertTrue(QuestionCatalog.fullCourseQuestions.all { it.options.size in 2..4 })
        assertTrue(QuestionCatalog.fullCourseQuestions.all { question ->
            question.correctOption == null || question.correctOption in question.options.indices
        })
        assertTrue(QuestionCatalog.fullCourseQuestions.filter { it.type == QuestionType.OBSERVED }.all { it.correctOption == null })
    }

    @Test fun memoryModeQuestionsHaveThreeSecondPreview() {
        assertTrue(QuestionCatalog.fullCourseQuestions.filter { it.type == QuestionType.MEMORY }.all { it.previewMs == 3_000L })
        assertEquals(3_000L, QuestionCatalog.fullCourseQuestions.filter { it.id.startsWith("M03-") }.first().previewMs)
        assertTrue(QuestionCatalog.fullCourseQuestions.all { it.previewMs == 0L || it.previewMs == 3_000L })
    }
}
